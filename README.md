# 营销引擎 · Marketing Engine

面向营销活动的抽奖与返利中台，包含 Java 后端服务与 Next.js 演示前端，同仓管理。

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
└── web/                    Next.js 演示前端（转盘、九宫格、签到、额度）
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
| 后端 | Java 8、Spring Boot 2.7、MyBatis、Maven 多模块 |
| 存储 | MySQL 8（2 库 4 表分片）、Redis（Redisson） |
| 消息 | RabbitMQ（发奖、返利、库存清零） |
| 前端 | Next.js 13 App Router、React 18、TypeScript、Tailwind CSS |
| 部署 | Docker Compose |

## 快速开始

```bash
# 1. 起中间件（MySQL / Redis / RabbitMQ）
cd server/docs/dev-ops && docker compose -f docker-compose-environment.yml up -d

# 2. 初始化库表
#    执行 docs/dev-ops/mysql/sql/ 下三份脚本（marketing_engine / _01 / _02）

# 3. 启动后端
cd ../.. && mvn -DskipTests install
java -jar marketing-engine-app/target/marketing-engine-app.jar

# 4. 启动前端
cd web && npm install && npm run dev
```

访问 `http://localhost:3000/?userId=<用户ID>&activityId=<活动ID>`，先点「策略装配」完成预热，再抽奖。

## 设计要点

1. **概率计算前置到预热期**。按最小概率把奖品展开成整数区间查找表，乱序后写入 Redis Hash，抽奖时一次 `random` 加一次查表，O(1)。乱序是为了避免同一奖品占据连续槽位被人摸出规律。
2. **拦截与执行分开建模**。抽奖前的校验（黑名单、权重）是线性逐关判断，用责任链；抽奖中后的动作（扣库存、兜底奖品）是按条件走不同分支，用数据库配置的决策树。规则树的节点、连线、参数都在表里，改规则不需要发版。
3. **库存三段式**。Redis 原子扣减保证不超卖，扣减结果进队列异步落库避免峰值打穿数据库，最后一件卖完发 MQ 清除缓存并触发售罄处理。
4. **发奖不依赖 MQ 可靠性**。中奖记录与消息任务在同一事务落库（本地消息表），由定时任务扫表补偿投递，消息重投时由业务唯一键兜底幂等。

## 范围说明

- 抽奖与返利是完整链路；**奖品实际发放**目前只做消费者落点，未对接具体的发奖渠道。
- 无用户鉴权体系，前端通过 URL 传 `userId` 便于多账号验证额度与权重差异。
- 规则树的变更在重新装配后生效，不是运行时热更新。

## 模块细节

后端的分层约定、表结构与接口清单见 [`server/README.md`](server/README.md)；前端接口对照见 [`web/README.md`](web/README.md)。
