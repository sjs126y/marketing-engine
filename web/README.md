# 营销引擎 · 演示前端

抽奖与返利活动中台的配套前端，用于验证后端抽奖链路（策略装配 → 责任链 → 规则树 → 库存扣减 → 异步发奖）的运行结果。

## 技术栈

- Next.js 13（App Router）+ React 18 + TypeScript
- Tailwind CSS
- `@lucky-canvas/react` 转盘 / 九宫格抽奖组件

## 功能

| 模块 | 说明 | 对应后端接口 |
|---|---|---|
| 策略装配 | 触发活动预热，把奖品概率装配为 Redis 区间查找表 | `GET /api/v1/raffle/activity/armory` |
| 奖品列表 | 按用户与活动查询可抽奖品及剩余库存 | `POST /api/v1/raffle/strategy/query_raffle_award_list` |
| 抽奖 | 转盘 / 九宫格两种形态，结果由服务端返回 | `POST /api/v1/raffle/activity/draw` |
| 账户额度 | 查询用户总 / 月 / 日三层剩余抽奖次数 | `POST /api/v1/raffle/activity/query_user_activity_account` |
| 签到返利 | 日历签到，按天幂等 | `POST /api/v1/raffle/activity/calendar_sign_rebate` |
| 权重校验 | 查询当前用户的策略权重规则 | `POST /api/v1/raffle/strategy/query_raffle_strategy_rule_weight` |

## 本地运行

```bash
# 1. 配置后端地址
echo "API_HOST_URL=http://127.0.0.1:8092" > .env.local

# 2. 安装依赖并启动
npm install
npm run dev
```

访问 `http://localhost:3000/?userId=<用户ID>&activityId=<活动ID>`。

> `userId` 与 `activityId` 从 URL 读取，方便直接用不同用户验证额度与权重差异，无需登录体系。
> 首次访问前需要先点「策略装配」完成活动预热，否则概率表未加载、抽奖会返回空奖品。

## 构建

```bash
npm run build
docker build -t marketing-engine-web:2.0 .
```
