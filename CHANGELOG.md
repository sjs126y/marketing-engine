# 更新日志

本项目从「跟教程实现的抽奖系统」重构为「可讲清楚取舍的营销中台」的过程记录。
每个版本对应一次技术决策，详细背景见 [docs/ROADMAP.md](docs/ROADMAP.md)。

## 权益中心 P1：资产运营（过期 / 对账 / 提醒）

P0 把「资产底座」立起来（账户 + 批次 + 流水、FIFO 消耗、发放幂等），P1 让它跑得住：
会过期的钱要能自动回收、账要能自己查出错、用户要在过期前知道。

### 过期回收

- `CreditExpireJob` 每小时整点跑，**按自然日分桶**扫「今天-回看天数 ~ 今天」，每段走 `idx_state_expire(state, expires_at)`；
  回看窗口以外的遗留由全量兜底补齐，停机不会漏回收
- 回收靠 `update ... where state='active'` 条件更新「抢到才动钱」，多实例幂等
- **修 P0 遗留缺陷**：`expire()` 写流水时 `balance_after` 传的是 null，而 V7 该列是 NOT NULL，
  第一次真实到期就会 1048 整笔回滚——也就是原来的过期链路一次都没真正跑通
- 演示路径：`ttlDays = -1` 造「立即过期」批次（受 `app.credit.demo-instant-expire` 保护）；
  `POST /open/v1/credit/expire-run` 手动触发一轮，联调和现场演示不用等一小时

### 对账

- 三值比对：账户余额 / 未核销批次剩余之和 / 流水累计；差异落 `credit_reconcile_diff` 并打 error 日志
- **只暴露不修正**：自动修数会把「记账 bug」变成「数据被系统悄悄改过」，事后查不出痕迹
- 全量对账按 `user_id` 游标分页（`id` 是各分片自增的，跨库 `order by id limit` 会漏账/重账）
- 「未核销批次剩余」刻意**不按 `expires_at` 过滤**：到期但尚未回收的批次仍在账户余额里，排除掉会报假差异

### 即将过期提醒

- `credit/balance` 增加 `expiringSoon` / `nearestExpireAt` / `expiringWindowDays`（默认 7 天窗口，向后兼容）
- 主站 `/api/me` 透出，侧栏提示「你有 X 积分将于 Y 到期」

### 签到收敛

- 签到仍然由中台发**抽奖次数**（`rebate_type=sku` 保留），**积分只由主站发**
- 删除 `daily_behavior_rebate` 里 `sign/integral` 那条（V9）：返利消费者本来就不处理非 sku 类型，
  这条配置是「哪天有人实现了 integral 就开始双发」的定时炸弹
- 主站签到顺序改成「先发奖、最后写 sign_log」：先写记录再发奖，一旦发放失败当天就补不上了；
  反过来用 `eventId=sign:{user}:{day}` 幂等，重试不会重复加，重复签到还会做一次幂等补发（自愈）

### 上游对接修复

- 注册赠送的 20 积分此前只写本地 `users.credit`，而 `/api/me` 已改读权益中心 → 新用户注册后显示 0 分。
  现在注册时同步调 `credit/grant`（`scene=register`，`eventId=register:{user}`）

### 验证

- 单测 28 → 37（新增 `CreditExpireWindowTest`、`CreditReconcileDiffTest`）
- 联调 28 → 39 项全绿：造立即过期批次 → 回收 → 余额减少 + expire 流水 → 到期提醒 → 单用户/全量对账一致；
  另外手工改一条数据验证差异能被报出来（inconsistent=1，reason 落库）


## 奖品收敛为「积分 / 对话次数」

- 策略 `100006` 的奖池从「积分 + 实物 + 模型解锁」收敛为两类：
  积分（5/10/20 分）与对话次数（1/3/5/10 次），共 8 个奖品
- 概率重新归一：原来 8 个奖品各 0.03/0.02，之和只有 0.23；现为 0.4000/0.2500/0.1000/
  0.1500/0.0600/0.0300/0.0090/0.0010，之和正好 1.0000
- 权重档位改为「消耗越高可抽越好」：`10:101,102 70:103,104 1000:105,106,107`
- 修掉 `tree_lock_3` 的错误连线：`rule_lock` 的 ALLOW 边原本直连兜底节点，
  导致 `rule_stock` 成孤岛、该奖品既不扣库存也永远发不出去
- 迁移脚本：`server/docs/dev-ops/mysql/sql/migration/V6__prizes_credit_and_quota_only.sql`
- 执行后需清理 Redis 中该策略的缓存并重新预热（`armory?activityId=100301`）

## [v1.0.0]

### 新增

- **本地消息表（outbox）统一补偿框架**：`pending → sending → sent → consumed → dead` 五态，
  指数退避重试、超时反查、消费回执，替代原先分散在三处的补偿逻辑（发奖 `task` 表、
  返利 `task` 表、库存 Redisson 延迟队列）。
- **库存绝对量回写**：Redis 扣减后把「累计已扣数」写进 outbox，消费端按
  `库存 = 总量 - 已扣数` 回写，并用 `WHERE applied_consumed < consumedAfter` 保证单调，
  重复投递与乱序到达都幂等。
- **SKU 库存对账任务**：定时比对 Redis 与数据库的已扣数，偏差超阈值告警。
- **三套鉴权**：C 端用户 JWT、B 端运营 BCrypt + 角色、上游 API Key + HMAC 签名 + 时间戳 + 限流。
- **配置校验器**：概率归一、奖品概率合法、权重档位非空、树根/边/环/孤立节点、
  `rule_key` 必须有对应 Bean、有库存的奖品必须挂 `rule_stock`。把「配错不报错」
  的静默错误变成发布期失败。
- **发布流程**：校验 → 失效缓存 → 重新预热，一条链路完成配置生效。
- **开放 API**：`POST /open/v1/quota/recharge`（充值，`outBusinessNo` 幂等）、
  `GET /open/v1/activity/{id}`。
- **运营看板**：库存水位、outbox 积压、策略概率配置。
- **可观测**：Actuator + Prometheus，暴露 `outbox.pending` 等指标。
- **测试与 CI**：24 个纯单测（概率装配、权重选档、决策树走线、JSON 序列化、配置校验），
  GitHub Actions 无需中间件即可跑通；压测脚本见 `docs/load-test/`。

### 变更

- **JDK 8 → 21**，Lombok 升到 1.18.34，`maven-compiler-plugin` 升到 3.13。
- **Spring Boot 2.7 → 3.5**，`javax.annotation` 全量迁移到 `jakarta.annotation`。
- **fastjson（1.x / 2.x 混用）→ Jackson**，统一封装 `JsonUtils`。
- **分库分表从自研 starter 换成 ShardingSphere-JDBC 5.5.3**：
  用 `ShardingTableRuleConfiguration` + 自定义 `UserIdModShardingAlgorithm` 复现 2 库 4 表，
  并删掉所有手工 `doRouter / clear` 调用。
- MySQL 驱动换坐标 `com.mysql:mysql-connector-j`；Redisson 换成核心包 3.52.0。
- 数据库账号密码外置为环境变量，新增 `.env.example`。

### 修复

- `SendMessageTaskJob` 遍历分库时 `i < dbCount` 漏掉最后一个库，且异步线程丢失路由上下文；
  换 ShardingSphere 后由 SQL 里的 `user_id` 路由，问题消失。
- Redis 库存扣减在兜底锁抢占失败时仍返回「扣减成功」，导致库存被扣却不认；
  现在会回补计数并拒绝请求。
- 规则树 `rule_stock` 无论成败都返回 `TAKE_OVER`，而配置里的出边是 `ALLOW`，
  导致库存耗尽时兜底节点不会被触发；校验器会在发布期拦截这类配置。

### 移除

- `task` 表相关代码（DAO / PO / Repository / 扫表任务），由 outbox 取代。
- Redisson 延迟队列与 `UpdateActivitySkuStockJob`。
- 未使用依赖：`dom4j`、`xstream`、`jjwt 0.9`、`java-jwt`、`commons-codec`。

## [v0.1.0]

技术栈升级（JDK 21 / Spring Boot 3.5 / Jackson / ShardingSphere），详见上方「变更」。
