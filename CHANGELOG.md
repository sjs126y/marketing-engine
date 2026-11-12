# 更新日志

本项目从「跟教程实现的抽奖系统」重构为「可讲清楚取舍的营销中台」的过程记录。
每个版本对应一次技术决策，详细背景见 [docs/ROADMAP.md](docs/ROADMAP.md)。

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
