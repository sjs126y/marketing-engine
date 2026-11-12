# 上游 AI 助手（营销中台上游演示）

这是「营销中台」的**上游业务系统**演示：一个极简的 AI 对话应用。
它自己持有积分与对话次数，把「签到判定」「抽奖」交给中台，并接收中台的发奖回调。

## 为什么要单独一个服务

中台的价值在于「被别的系统调用」。如果上游只是同一个前端里的一个页面，那跨系统边界就是假的。
这里用一个 **Python 服务**去调 **Java 中台**，边界是真实存在的——也顺便证明中台不绑语言。

## 登录在这里，抽奖那边不登录

- 注册/登录发生在**本服务**（用户名 + PBKDF2 口令，签名 cookie 会话）
- 点「去抽奖」时，本服务拿 `userId` 去中台换一个 **JWT**，跳转 `/play?token=…`
- 中台开启鉴权后，`userId` 以 token 为准——**改 URL 上的 userId 是无效的**

## 它做了什么

| 动作 | 本服务做什么 | 调中台哪个接口 |
|---|---|---|
| 注册/登录 | PBKDF2 校验，签发签名 cookie | 无（中台不认识账号密码） |
| 签到 | 先问中台能不能签，成功后自己给 10 积分 | `POST /api/v1/raffle/activity/calendar_sign_rebate` |
| 做任务 | 让中台给用户加抽奖次数 | `POST /open/v1/quota/recharge`（API Key + HMAC 签名） |
| 对话 | 扣自己的积分（条件更新保证并发安全） | 无 |
| 去抽奖 | 换中台 JWT，跳转中台抽奖页 | `POST /api/v1/auth/login` |
| 收回调 | 验签 + 幂等 + 把奖品记进本地账户 | 中台回调本服务 `POST /webhook/award` |

## 启动

```powershell
cd "C:\Users\MR\Desktop\Java Learn Project Summary\upstream-ai-app"

# 首次：创建虚拟环境并安装依赖（已创建可跳过）
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -i https://mirrors.aliyun.com/pypi/simple/ fastapi uvicorn

# 启动（默认 9000 端口）
.\.venv\Scripts\python.exe app.py
```

打开 <http://127.0.0.1:9000>。

**前置**：营销中台要跑在 `http://127.0.0.1:8092`。若端口不同，用环境变量覆盖：

```powershell
$env:MID_PLATFORM = "http://127.0.0.1:8092"
$env:MID_API_KEY  = "demo-client"
$env:MID_API_SECRET = "demo-secret-please-change"
$env:MID_ACTIVITY_ID = "100301"
$env:MID_SKU = "9011"
.\.venv\Scripts\python.exe app.py
```

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `MID_PLATFORM` | `http://127.0.0.1:8092` | 中台地址 |
| `MID_API_KEY` | `demo-client` | 开放接口的 API Key |
| `MID_API_SECRET` | `demo-secret-please-change` | HMAC 密钥，要与中台 `app.auth.api-clients` 一致 |
| `MID_ACTIVITY_ID` | `100301` | 活动 ID |
| `MID_SKU` | `9011` | 充值用的 SKU |
| `SIGN_CREDIT` | `10` | 签到送多少积分（上游自己的规则） |
| `CHAT_COST` | `1` | 每轮对话扣多少积分 |
| `CALLBACK_SECRET` | `upstream-callback-secret` | 回调验签密钥 |
| `PORT` | `9000` | 本服务端口 |

## 数据

SQLite（`upstream.db`，首次启动自动建表）：

- `users` —— 积分、对话次数
- `sign_log` —— 签到记录，`(user_id, sign_date)` 唯一键做幂等
- `flows` —— 积分/次数流水
- `callbacks` —— 回调去重，`event_id` 唯一键做幂等

## 演示路径

1. 打开页面 → 点「签到」→ 积分 +10，今日签到变「已签到」
2. 再点一次「签到」→ 提示已签过（幂等）
3. 在对话框发消息 → 积分 -1，不足时提示去抽奖
4. 点「做任务送抽奖次数」→ 走开放接口（带 HMAC 签名）
5. 点「去抽奖」→ 中台返回中奖结果
6. 中台发奖回调到达后，页面每 8 秒自动刷新，能看到奖品入账、流水多一条

## 已知边界

- 没有登录体系，`userId` 从输入框取并存在 localStorage——演示够用，真实系统这里应该是会话
- 「AI 回复」是固定拼装的模拟文本，没有接大模型
- 回调验签在中台未开启签名时会被跳过（代码里只在 `X-Sign` 存在时校验），联调阶段方便，生产应强制
