# 优化路线图（Roadmap）

> 目标：把 `marketing-engine` 从「教程复刻」升级为「可讲清楚的中台项目」。
> 每条完成后勾选，并在 `CHANGELOG.md` 记录。

## 原则

1. 每阶段可运行、可验收、可回滚（tag）
2. 先可靠性、再业务、最后加分
3. 不引入自己讲不清的组件
4. 每阶段结束更新 README / CHANGELOG

## 目标技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| JDK | 21 LTS | |
| Spring Boot | 3.5.16 | 不用 4.x：生态未跟上 |
| MyBatis | starter 3.0.5 | jakarta 命名空间 |
| 分片 | ShardingSphere-JDBC 5.5.3 | 替换 `cn.bugstack:db-router` |
| JSON | Jackson | 替换 fastjson（1.x / 2.x 全移除） |
| 存储 | MySQL 8、Redis、Redisson 3.x | |
| 消息 | RabbitMQ 3.12 | |
| 构建 | maven-compiler-plugin 3.13、Lombok 1.18.34 | |
| 测试 | JUnit 5 + AssertJ | |

## 阶段

### P0 技术栈升级 → tag `v0.1.0`

- [x] `build(deps)` JDK 21 + Lombok 1.18.34 + compiler 3.13
- [x] `refactor(json)` fastjson → Jackson（封装 JsonUtils）
- [x] `build(spring)` Spring Boot 2.7 → 3.5，`javax` → `jakarta`（38 处）
- [x] `build(sharding)` ShardingSphere 替换 db-router（先单库跑通，再上分片）
- [x] `chore(deps)` 清理无用依赖、密码外置、README 更新

**验收**：`mvn clean package` 通过；应用启动；`armory` + `draw` 跑通；SQL 落到正确库表。

### P1 统一补偿框架 → tag `v0.2.0`

- [x] `outbox` 表（pending / sending / sent / consumed / dead 五态）
- [x] dispatcher：扫表投递、指数退避、8 次进 dead、积压指标
- [x] 消费回执：`sent → consumed`
- [x] 反查器：`sent` 超时回查业务表，标记或重投
- [x] 接入发奖、返利两条链路；移除裸扫表 job

**验收**：停 MQ 抽 100 次后重启可全部恢复；重放 10 次不重复；异常可见退避与 dead。

### P2 库存一致性 → tag `v0.3.0`

- [x] 库存扣减走 outbox，`applied_consumed` 绝对值 + 单调保护
- [x] 事务失败 `INCR` 回补
- [x] 对账任务（Redis 已扣 vs DB 已扣）
- [x] 删除 Redisson 延迟队列与 `UpdateActivitySkuStockJob`

**验收**：`kill` 消费进程后一个对账周期内收敛；500 并发下 `stock_count - surplus == 成功订单数`。

### P3 三套鉴权 → tag `v0.4.0`

- [x] C 端用户：登录换 JWT，`userId` 从 token 解析
- [x] B 端运营：Spring Security + BCrypt + 角色 + 操作审计
- [x] 上游系统：API Key + HMAC 签名 + 时间戳 + 限流

**验收**：三种身份各自能访问/被拒绝；越权被拦。

### P4 上游闭环 + 运营台 → tag `v0.5.0`

- [ ] 开放 API：`/open/v1/quota/recharge`、`/open/v1/activity/{id}`
- [ ] 配置校验器：概率归一、奖品存在、树连通、Bean 存在、库存类奖品必须挂 `rule_stock`
- [ ] 发布流程：草稿 → 校验 → 版本号 → 清缓存 → 预热
- [ ] 运营台：活动 / 策略权重 / SKU / 发布记录 / 数据看板

**验收**：API 调通「下单送次数 → 抽奖 → 看板出数」；错误配置在发布期被拦并说明原因。

### P5 验证体系 → tag `v1.0.0`

- [ ] 单测：概率分布卡方、权重选档边界、树引擎走线、绝对值写入幂等
- [ ] 压测脚本与报告（500 并发 + 守恒校验 SQL）
- [ ] Micrometer 指标 + traceId 透传
- [ ] GitHub Actions：单测（不依赖中间件）

### P6 加分（可选）

- [ ] 灰度发布（版本并存 + userId 分桶）
- [ ] 库存分桶
- [ ] 规则树 `GT/LT` 扩展

## Git 规范

- 分支：`feat/p{N}-{主题}`，合并回 `main` 用 `--no-ff`
- 提交：Conventional Commits，type 英文 + 描述中文
- 每个提交必须能编译
- 阶段完成打 tag：`v0.1.0` … `v1.0.0`
- 不提交密码、`target/`、`.idea/`、`.zread/`
