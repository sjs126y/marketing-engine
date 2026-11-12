# 营销引擎 · Marketing Engine

面向 AI 对话产品的用户增长与权益中台：Java 后端服务 + Next.js 前端 + 一个 Python 上游示例，同仓管理。

业务场景——用户在上游 AI 对话应用里签到得积分，对话按 token 消耗积分；积分不足时参与活动赢取对话额度。
中台只负责「给谁、给什么」的判定与可靠通知，积分与额度这些业务资产留在业务侧。

活动类业务的共同难点在于三件事：规则变化频繁（每次改规则不该发版）、流量峰值集中（不能把数据库打穿）、奖励只能发一次（重复发就是资损）。这个项目围绕这三点组织代码。

## 目录结构

```
marketing-engine/
├── server/                 Java 后端（Maven 多模块，DDD 分层）
│   ├── marketing-engine-api              接口与 DTO
│   ├── marketing-engine-app              启动模块、配置、测试
│   ├── marketing-engine-domain           实体、值对象、聚合、领域服务、规则
│   ├── marketing-engine-trigger          Controller、定时任务、MQ 消费者
│   ├── marketing-engine-infrastructure   仓储实现、MyBatis、Redis、消息
│   ├── marketing-engine-types            枚举、异常、常量
│   └── docs/dev-ops                      MySQL / Redis / RabbitMQ 编排脚本
├── web/                    Next.js 前端
│   ├── 用户侧演示页        转盘、九宫格、签到、额度
│   └── console/            运营台（登录、数据看板、策略配置、发奖记录）
└── upstream-ai-app/        上游业务方演示（Python + FastAPI，模拟 AI 助手）
```

## 抽奖主链路

```
活动预热 ──► 策略装配：概率表展开为 Redis 区间查找表
   │
   ▼
抽奖请求 ──► 责任链（黑名单 / 权重）──► 规则树（库存扣减 / 兜底奖品）
   │                                          │
   ▼                                          ▼
参与订单落库                              中奖记录 + 本地消息表（同事务）
   │                                          │
   └──────────────► 定时任务扫表补偿 ──► MQ ──► 发奖消费
```

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、MyBatis、Maven 多模块、Jackson |
| 存储 | MySQL 8（ShardingSphere-JDBC 2 库 4 表分片）、Redis（Redisson） |
| 消息 | RabbitMQ（发奖、返利、库存清零） |
| 前端 | Next.js 13 App Router、React 18、TypeScript、Tailwind CSS |
| 部署 | Docker Compose |

## 快速开始

```bash
# 1. 起中间件（MySQL / Redis / RabbitMQ）
cd server/docs/dev-ops && docker compose -f docker-compose-environment.yml up -d

# 2. 初始化库表
#    执行 docs/dev-ops/mysql/sql/ 下三份脚本（marketing_engine / _01 / _02）
#    再执行升级脚本 docs/dev-ops/mysql/sql/migration/ 下的 V2（outbox）、V3（applied_consumed）
#    注意：V2 需要在 _01、_02 两个分片库各执行一次

# 3. 启动后端（默认 dev profile，读环境变量 DB_USERNAME / DB_PASSWORD）
cd ../.. && mvn -DskipTests install
java -jar marketing-engine-app/target/marketing-engine-app.jar

# 4. 启动前端
cd web && npm install && npm run dev
```

访问 `http://localhost:3000/?userId=<用户ID>&activityId=<活动ID>`，先点「策略装配」完成预热，再抽奖。

## 设计要点

1. **概率计算前置到预热期**。按最小概率把奖品展开成整数区间查找表，乱序后写入 Redis Hash，抽奖时一次 `random` 加一次查表，O(1)。乱序是为了避免同一奖品占据连续槽位被人摸出规律。
2. **拦截与执行分开建模**。抽奖前的校验（黑名单、权重）是线性逐关判断，用责任链；抽奖中后的动作（扣库存、兜底奖品）是按条件走不同分支，用数据库配置的决策树。规则树的节点、连线、参数都在表里，改规则不需要发版。
3. **库存绝对量回写**。Redis 原子扣减保证不超卖；扣减后的「累计已扣数」与订单在同一事务写进 outbox，消费端按 `库存 = 总量 - 已扣数` 回写，并用 `applied_consumed < consumedAfter` 保证单调——重复投递和乱序到达都幂等。另有对账任务比对 Redis 与数据库的已扣数。
4. **补偿收敛成一个框架**。发奖、返利、库存三处共用一张 outbox 表：`pending → sending → sent → consumed → dead` 五态，指数退避重试、超时未回执重投、超限进死信。可靠性来自数据库而不是 MQ，MQ 只是加速通道。
5. **中台不持有业务资产，只做判定与通知**。积分、对话次数留在上游；中台把「用户中了什么」放进同一条 outbox，用 **HTTP 回调**推给上游（带 `X-Event-Id` 幂等键与 HMAC 签名），上游验签入账后中台把 `award_state` 推进到 `complete`。回调失败同样走退避重试与死信。
5. **配置错误在发布期被拦住**。规则、树、概率全部数据驱动，配错不会编译失败也不会抛异常。校验器检查概率归一、权重档位非空、树根/边/环/孤立节点、`rule_key` 是否有对应 Bean、有库存的奖品是否挂了 `rule_stock`，不过就不允许发布。

## 接口概览

| 分类 | 路径 | 鉴权 |
| --- | --- | --- |
| C 端 | `POST /api/v1/auth/login`、`/api/v1/raffle/activity/**` | 用户 JWT |
| B 端 | `POST /console/auth/login`、`/console/v1/strategy/{id}/validate`、`/publish`、`/console/v1/dashboard/overview` | 运营 JWT + 角色 |
| 上游 | `POST /open/v1/quota/recharge`、`GET /open/v1/activity/{id}` | API Key + HMAC 签名 |
| 观测 | `GET /actuator/health`、`/actuator/prometheus` | 无 |

鉴权由 `app.auth.enabled` 控制，**默认开启**：

- **上游做登录**（用户名 + 口令，PBKDF2），抽奖页不再要求用户登录
- 上游点「去抽奖」时，用 `userId` 换一个中台 **JWT**，跳转 `/play?token=…`
- 中台侧 `userId` 一律以 token 为准，**改 URL 上的 userId 无效**
- 运营端需 `admin/admin123` 或 `operator/operator123`；开放接口需 API Key + HMAC 签名

想只做本地演示不登录，把 `app.auth.enabled` 改成 `false`，所有接口放行。

## 测试

```bash
cd server
mvn test                  # 只跑不依赖中间件的纯单测（概率装配、权重选档、决策树、JSON、配置校验）
mvn test -P integration   # 需要本地 MySQL / Redis / RabbitMQ
```

CI 见 `.github/workflows/ci.yml`；压测脚本与守恒校验 SQL 见 `docs/load-test/`。

## 本地验证结果

以下都是在本地 Docker（MySQL 8 / Redis 6 / RabbitMQ 3.12）跑出来的真实结果，不是估算：

| 项 | 结果 |
| --- | --- |
| 应用启动 | `GET /actuator/health` → `{"status":"UP"}` |
| 策略预热 | `GET /armory?activityId=100301` → 成功，概率表写入 Redis |
| 抽奖 | `POST /draw` → 返回真实奖品，中奖记录与抽奖单状态正确落库 |
| ShardingSphere 路由 | 按 `user_id` 正确落到 ds1/ds2 的对应分表 |
| outbox 投递 | 压测产生 ~300 条消息，全部 `consumed`，无 pending/dead |
| 压测 | 20 并发 × 300 次，QPS ≈ 53，成功 293 / 失败 7 |
| 用户鉴权 | 无 token → 403；登录取 JWT 后可抽奖 |
| 越权防护 | token 属于 A、请求体写 B，扣减仍落在 A（userId 以 token 为准） |
| 运营鉴权 | admin 登录 → `role=ADMIN`，看板可访问；用运营 token 调开放接口 → 401 |
| 开放接口 | 无 API Key 签名 → 401 |

**守恒校验（本次压测）**

| 校验 | 结果 |
| --- | --- |
| 充值 20 次 → `applied_consumed` | 20，且 `stock_count - surplus = 20` ✅ |
| 成功抽奖 293 次 → 中奖记录 | 293 条（按 `user_id like 'load-final%'` 统计）✅ |
| 每个奖品的 `applied_consumed` | 与 `award_count - surplus` 逐项一致 ✅ |
| outbox 最终状态 | 全部 `consumed`，无积压 ✅ |

复现：

```bash
cd docs/load-test
python load_test.py --users 20 --draws 300 --concurrency 20
```

## 范围说明

- 抽奖与返利是完整链路；**奖品实际发放**目前只做消费者落点与回执闭环，未对接具体的发奖渠道。
- 运营账号来自配置文件（BCrypt 散列），未做运营后台的用户管理；生产应落库并接 SSO。
- 开放接口的限流是单机滑动窗口，多实例部署应换成 Redis 令牌桶。
- 规则树的变更需要走发布流程（校验 → 失效缓存 → 预热），不是运行时热更新。
- 灰度发布、库存分桶、规则树 `GT/LT` 条件分支尚未实现，见 ROADMAP 的 P6。
- **同一用户并发抽奖会返回 `0003 唯一索引冲突`**：参与单按「查询未使用单」复用，
  并发下两个请求拿到同一张单，第二条中奖记录撞 `uq_order_id`。这是有意保留的安全网
  （宁可失败也不重复发奖），但生产应加 per-user 串行化或把参与单改成显式状态机。
- 奖品库存的抽奖扣减在 Redis，数据库由 `UpdateAwardStockJob` 异步回写；
  压测中曾观察到旧实现（每 30 秒一条）严重滞后，现已改为每轮排空，上限 500 条。
- 种子数据里 `strategy_award` 的 `award_count` 与 `award_count_surplus` 本身不自洽
  （例如奖品 105 是 100 / 38），所以「已发数量」不能直接当成本次扣减量看。

## 模块细节

后端的分层约定、表结构与接口清单见 [`server/README.md`](server/README.md)；前端接口对照见 [`web/README.md`](web/README.md)。
