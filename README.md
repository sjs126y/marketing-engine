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
│   ├── /                   三端入口（说明每个端做什么）
│   ├── /play               用户活动页（九宫格抽奖，需上游换发的 JWT）
│   └── /console            运营台（登录、看板、策略配置、发奖记录）
├── upstream-ai-app/        上游业务方（Python + FastAPI，AI 对话助手）
├── docs/
│   ├── scripts/            start-all / stop-all / integration_check
│   ├── load-test/          压测脚本与守恒校验
│   └── dev-ops/mysql/sql/  建库脚本与迁移 V2–V6
└── logs/                   运行日志（gitignore）
```

## 核心链路

```
活动预热 ──► 把权重与概率编译成 Redis 查找表（抽奖时 O(1) 定位）
   │
   ▼
抽奖请求 ──► 前置校验链（黑名单 / 权重档位）──► 决策树（库存扣减 / 兜底）
   │                                              │
   ▼                                              ▼
参与订单落库                                 中奖记录 + outbox（同事务）
   │                                              │
   │                            ┌─────────────────┴─────────────────┐
   └──► 定时扫表补偿 ──► MQ（内部事件）        HTTP 回调（通知上游入账）
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

### 一键启动（推荐）

```powershell
git clone https://github.com/sjs126y/marketing-engine.git
cd marketing-engine
pwsh -File docs/scripts/start-all.ps1
```

脚本按顺序完成：检查 Docker 中间件（缺了会自动 `compose up`）→ 构建 jar（首次）→ 安装前端依赖（首次）
→ 创建上游 venv（首次）→ 拉起三个服务 → 轮询健康检查 → 打印访问地址。日志写到 `logs/`。

```powershell
pwsh -File docs/scripts/start-all.ps1 -Restart        # 先停再起
pwsh -File docs/scripts/start-all.ps1 -Only mid       # 只起中台（mid / web / upstream 可组合）
pwsh -File docs/scripts/stop-all.ps1                  # 停止三端
pwsh -File docs/scripts/stop-all.ps1 -Middleware      # 连 Docker 中间件一起停
```

### 首次初始化数据库

中间件起来后，按顺序执行 `server/docs/dev-ops/mysql/sql/` 下的脚本：

| 脚本 | 目标库 | 说明 |
| --- | --- | --- |
| `marketing_engine.sql` | `marketing_engine` | 配置库：活动、策略、奖品、规则树、返利配置 |
| `marketing_engine_01.sql` | `marketing_engine_01` | 分片库 1 |
| `marketing_engine_02.sql` | `marketing_engine_02` | 分片库 2 |
| `migration/V2__outbox.sql` | `_01`、`_02` **各执行一次** | 本地消息表 |
| `migration/V3__sku_applied_consumed.sql` | `marketing_engine` | SKU 已扣数 |
| `migration/V4__award_applied_consumed.sql` | `marketing_engine` | 奖品已扣数 |
| `migration/V5__outbox_http_delivery.sql` | `_01`、`_02` **各执行一次** | outbox 支持 HTTP 回调 |

> `V6`（奖池收敛为积分与对话次数）已合并进种子文件，新建库不需要再执行。
> 执行任何含中文的 SQL 都要带 `--default-character-set=utf8mb4`，否则中文会双重编码成乱码。

### 访问入口

| 入口 | 地址 | 账号 |
| --- | --- | --- |
| 上游 AI 助手 | http://127.0.0.1:9000 | 注册新账号，或 `alice` / `alice123` |
| 中台首页（三端入口） | http://localhost:3000 | — |
| 用户活动页 | http://localhost:3000/play?token=… | 从上游点「去抽奖」进入（带 JWT） |
| 运营台 | http://localhost:3000/console | `admin` / `admin123`（或 `operator` / `operator123`） |

**推荐走法**：上游登录 → 签到（+10 积分）→ 点「去抽奖」→ 抽一次 → 回上游等 8 秒，
看对话额度到账（中台异步回调写入）。

### 端口

| 端口 | 服务 |
| --- | --- |
| 8092 | 营销中台（Java 21 / Spring Boot 3.5） |
| 3000 | 前端（Next.js） |
| 9000 | 上游 AI 助手（Python / FastAPI） |
| 13306 / 16379 / 5672 | MySQL / Redis / RabbitMQ（Docker） |

### 验证

```powershell
# 24 个纯单测，不依赖任何中间件
cd server; mvn test

# 28 项跨服务联调：注册→签到→充值→换 token→抽奖→回调入账→运营台→页面可达
pwsh -File docs/scripts/integration_check.ps1
```
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
mvn test                  # 默认只跑纯单测，不依赖中间件
mvn test -P integration   # 需要本地 MySQL / Redis / RabbitMQ
```

CI 见 `.github/workflows/ci.yml`；跨服务联调见 `docs/scripts/integration_check.ps1`；
压测与守恒校验 SQL 见 `docs/load-test/`。

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
