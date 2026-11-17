"""上游 AI 助手（演示用业务方）。

角色：营销中台的**上游业务系统**。
  - 用户在我的 App 里注册/登录（会话机制在我这里）
  - 用户签到 → 我调中台的签到接口；积分我自己记
  - 用户和我对话 → 我扣自己的积分
  - 用户去抽奖 → 我用 userId 换一个中台 JWT，跳转到中台抽奖页
  - 中台发奖后回调我 → 我验签、幂等、把奖品记进自己的账户

边界约定：积分、对话次数这些"业务资产"全部在我这里；中台只做「给谁、给什么」的判定。
登录也在我这里——中台不认识用户名密码，只认我换给它的 token。

技术栈：FastAPI + SQLite，刻意用 Python 是为了证明「上游可以不是 Java」。
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "upstream.db"

# ---------- 中台连接配置 ----------
MID_PLATFORM = os.environ.get("MID_PLATFORM", "http://127.0.0.1:8092")
API_KEY = os.environ.get("MID_API_KEY", "demo-client")
API_SECRET = os.environ.get("MID_API_SECRET", "demo-secret-please-change")
ACTIVITY_ID = int(os.environ.get("MID_ACTIVITY_ID", "100301"))
SKU = int(os.environ.get("MID_SKU", "9011"))

# 抽奖页所在的站点（中台前端）
WEB_BASE = os.environ.get("WEB_BASE", "http://localhost:3000")

CALLBACK_SECRET = os.environ.get("CALLBACK_SECRET", "upstream-callback-secret")

SIGN_CREDIT = int(os.environ.get("SIGN_CREDIT", "10"))
CHAT_COST = int(os.environ.get("CHAT_COST", "1"))

# 会话签名密钥与有效期
SESSION_SECRET = os.environ.get("SESSION_SECRET", "upstream-session-secret-change-me")
SESSION_TTL = int(os.environ.get("SESSION_TTL", str(7 * 24 * 3600)))

app = FastAPI(title="上游 AI 助手（营销中台上游演示）")


# ---------- 存储 ----------
@contextmanager
def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with db() as conn:
        conn.executescript(
            """
            create table if not exists users (
                user_id       text primary key,
                password_hash text,
                nickname      text,
                credit        integer not null default 0,
                llm_quota     integer not null default 0,
                created_at    text    not null
            );
            create table if not exists sign_log (
                user_id   text not null,
                sign_date text not null,
                primary key (user_id, sign_date)
            );
            create table if not exists flows (
                id         integer primary key autoincrement,
                user_id    text not null,
                kind       text not null,
                amount     integer not null,
                note       text,
                created_at text not null
            );
            create table if not exists callbacks (
                event_id    text primary key,
                payload     text not null,
                received_at text not null
            );
            """
        )
        # 老库补列（之前没有密码字段）
        cols = {r["name"] for r in conn.execute("pragma table_info(users)")}
        if "password_hash" not in cols:
            conn.execute("alter table users add column password_hash text")
        if "nickname" not in cols:
            conn.execute("alter table users add column nickname text")


def ensure_user(conn: sqlite3.Connection, user_id: str, nickname: str | None = None) -> sqlite3.Row:
    row = conn.execute("select * from users where user_id = ?", (user_id,)).fetchone()
    if row is None:
        conn.execute(
            "insert into users(user_id, credit, llm_quota, nickname, created_at) values (?,0,0,?,?)",
            (user_id, nickname or user_id, datetime.now().isoformat(timespec="seconds")),
        )
        row = conn.execute("select * from users where user_id = ?", (user_id,)).fetchone()
    return row


def add_flow(conn: sqlite3.Connection, user_id: str, kind: str, amount: int, note: str) -> None:
    conn.execute(
        "insert into flows(user_id, kind, amount, note, created_at) values (?,?,?,?,?)",
        (user_id, kind, amount, note, datetime.now().isoformat(timespec="seconds")),
    )


# ---------- 口令与会话 ----------
def hash_password(password: str, salt: str | None = None) -> str:
    """PBKDF2-SHA256，标准库实现，不引第三方依赖。"""
    salt = salt or secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 200_000)
    return f"pbkdf2${salt}${digest.hex()}"


def verify_password(password: str, stored: str | None) -> bool:
    if not stored:
        return False
    try:
        _, salt, _ = stored.split("$", 2)
    except ValueError:
        return False
    return hmac.compare_digest(hash_password(password, salt), stored)


def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _unb64(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def make_session(user_id: str) -> str:
    exp = int(time.time()) + SESSION_TTL
    payload = f"{user_id}|{exp}".encode()
    sig = hmac.new(SESSION_SECRET.encode(), payload, hashlib.sha256).digest()
    return _b64(payload) + "." + _b64(sig)


def read_session(token: str | None) -> str | None:
    if not token or "." not in token:
        return None
    try:
        payload_b64, sig_b64 = token.split(".", 1)
        payload = _unb64(payload_b64)
        expected = hmac.new(SESSION_SECRET.encode(), payload, hashlib.sha256).digest()
        if not hmac.compare_digest(expected, _unb64(sig_b64)):
            return None
        user_id, exp = payload.decode().rsplit("|", 1)
        if int(exp) < int(time.time()):
            return None
        return user_id
    except Exception:
        return None


def current_user(request: Request) -> str | None:
    return read_session(request.cookies.get("me_session"))


def require_user(request: Request) -> str:
    user_id = current_user(request)
    if not user_id:
        raise HTTPException(status_code=401, detail="未登录")
    return user_id


# ---------- 调中台 ----------
def _sign(method: str, path: str, timestamp: str, body: str) -> str:
    payload = f"{method}\n{path}\n{timestamp}\n{body}"
    return hmac.new(API_SECRET.encode(), payload.encode(), hashlib.sha256).hexdigest()


def call_mid(method: str, path: str, payload: dict[str, Any] | None = None,
             query: dict[str, Any] | None = None) -> dict[str, Any]:
    """调用中台接口。开放接口（/open/**）自动带 API Key + HMAC 签名。

    注意：签名只覆盖 path，不含 query string —— 服务端用的是 request.getRequestURI()。
    签名里带上 ?userId=x 会导致 401「签名不匹配」。
    """
    body = "" if payload is None else json.dumps(payload, ensure_ascii=False)
    qs = ("?" + urllib.parse.urlencode(query)) if query else ""
    url = f"{MID_PLATFORM}{path}{qs}"
    req = urllib.request.Request(url, data=body.encode() if body else None, method=method)
    req.add_header("Content-Type", "application/json")

    if path.startswith("/open/"):
        ts = str(int(time.time()))
        req.add_header("X-Api-Key", API_KEY)
        req.add_header("X-Timestamp", ts)
        req.add_header("X-Sign", _sign(method, path, ts, body))

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8", errors="replace")
        raise HTTPException(status_code=e.code, detail=f"中台返回 {e.code}: {text[:200]}")
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"中台不可达: {e}")


# ---------- 权益中心（中台）封装 ----------
def credit_balance(user_id: str) -> int:
    """余额以权益中心为准；中台不可达时返回 0 并在日志里留痕。"""
    try:
        result = call_mid("GET", "/open/v1/credit/balance", query={"userId": user_id})
    except HTTPException as e:
        print(f"[credit] 查询余额失败 user={user_id} detail={e.detail}")
        return 0
    if result.get("code") != "0000":
        print(f"[credit] 查询余额异常 user={user_id} result={result}")
        return 0
    return int(result.get("balance") or 0)


def credit_balance_detail(user_id: str) -> dict[str, Any]:
    """余额 + 即将过期提醒。中台不可达时降级为 0，不阻塞页面渲染。"""
    fallback = {"credit": 0, "expiringSoon": 0, "nearestExpireAt": None, "expiringWindowDays": 0}
    try:
        result = call_mid("GET", "/open/v1/credit/balance", query={"userId": user_id})
    except HTTPException as e:
        print(f"[credit] 查询余额失败 user={user_id} detail={e.detail}")
        return fallback
    if result.get("code") != "0000":
        print(f"[credit] 查询余额异常 user={user_id} result={result}")
        return fallback
    return {
        "credit": int(result.get("balance") or 0),
        "expiringSoon": int(result.get("expiringSoon") or 0),
        "nearestExpireAt": result.get("nearestExpireAt"),
        "expiringWindowDays": int(result.get("expiringWindowDays") or 0),
    }


def credit_grant(user_id: str, scene: str, amount: int, event_id: str) -> bool:
    """发放权益。eventId 是幂等键，重复调用不会重复入账。"""
    try:
        result = call_mid("POST", "/open/v1/credit/grant", {
            "userId": user_id, "scene": scene, "amount": amount, "eventId": event_id,
        })
    except HTTPException as e:
        print(f"[credit] 发放失败 user={user_id} event={event_id} detail={e.detail}")
        return False
    if result.get("code") != "0000":
        print(f"[credit] 发放异常 user={user_id} result={result}")
        return False
    return bool(result.get("granted"))


def credit_consume(user_id: str, scene: str, amount: int, biz_no: str) -> tuple[bool, int, str]:
    """消耗权益，返回 (是否成功, 消耗后余额, 失败原因)。"""
    try:
        result = call_mid("POST", "/open/v1/credit/consume", {
            "userId": user_id, "scene": scene, "amount": amount, "bizNo": biz_no,
        })
    except HTTPException as e:
        return False, 0, f"权益中心不可达: {e.detail}"
    if result.get("code") != "0000":
        return False, int(result.get("balance") or 0), result.get("info", "消耗失败")
    return True, int(result.get("balance") or 0), ""


# ---------- 请求体 ----------
class RegisterRequest(BaseModel):
    username: str
    password: str
    nickname: str | None = None


class LoginRequest(BaseModel):
    username: str
    password: str


class ChatRequest(BaseModel):
    message: str


class BuyRequest(BaseModel):
    sku: str


# ---------- 页面 ----------
@app.get("/", response_class=HTMLResponse)
def index(request: Request):
    if current_user(request) is None:
        return RedirectResponse("/login", status_code=302)
    return (BASE_DIR / "index.html").read_text(encoding="utf-8")


@app.get("/login", response_class=HTMLResponse)
def login_page():
    return (BASE_DIR / "login.html").read_text(encoding="utf-8")


# ---------- 认证接口 ----------
@app.post("/api/register")
def register(req: RegisterRequest) -> JSONResponse:
    username = req.username.strip()
    if len(username) < 3:
        return JSONResponse(status_code=400, content={"ok": False, "reason": "用户名至少 3 个字符"})
    if len(req.password) < 6:
        return JSONResponse(status_code=400, content={"ok": False, "reason": "密码至少 6 位"})

    with db() as conn:
        exists = conn.execute("select 1 from users where user_id = ?", (username,)).fetchone()
        if exists:
            return JSONResponse(status_code=409, content={"ok": False, "reason": "用户名已被注册"})
        conn.execute(
            "insert into users(user_id, password_hash, nickname, credit, llm_quota, created_at) "
            "values (?,?,?,0,0,?)",
            (username, hash_password(req.password), req.nickname or username,
             datetime.now().isoformat(timespec="seconds")),
        )
        # 本地 users.credit 只作历史留档，真正的余额在权益中心
        conn.execute("update users set credit = credit + 20 where user_id = ?", (username,))
        add_flow(conn, username, "REGISTER_BONUS", 20, "注册赠送")

    # 注册赠送必须发到权益中心，否则 /api/me 读到的余额是 0（它只认权益中心）
    credit_grant(username, "register", 20, f"register:{username}")

    resp = JSONResponse(content={"ok": True, "userId": username})
    resp.set_cookie("me_session", make_session(username), httponly=True, samesite="lax", max_age=SESSION_TTL)
    return resp


@app.post("/api/login")
def login(req: LoginRequest) -> JSONResponse:
    with db() as conn:
        row = conn.execute("select * from users where user_id = ?", (req.username.strip(),)).fetchone()
    if row is None or not verify_password(req.password, row["password_hash"]):
        return JSONResponse(status_code=401, content={"ok": False, "reason": "用户名或密码不正确"})

    resp = JSONResponse(content={"ok": True, "userId": row["user_id"]})
    resp.set_cookie("me_session", make_session(row["user_id"]), httponly=True, samesite="lax", max_age=SESSION_TTL)
    return resp


@app.post("/api/logout")
def logout() -> JSONResponse:
    resp = JSONResponse(content={"ok": True})
    resp.delete_cookie("me_session")
    return resp


# ---------- 业务接口（都需要登录） ----------
@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "UP", "midPlatform": MID_PLATFORM, "activityId": ACTIVITY_ID}


@app.get("/api/me")
def me(request: Request) -> dict[str, Any]:
    user_id = require_user(request)
    with db() as conn:
        user = ensure_user(conn, user_id)
        today = date.today().isoformat()
        signed = conn.execute(
            "select 1 from sign_log where user_id = ? and sign_date = ?", (user_id, today)
        ).fetchone() is not None
        flows = [
            dict(r)
            for r in conn.execute(
                "select kind, amount, note, created_at from flows where user_id = ? "
                "order by id desc limit 10",
                (user_id,),
            ).fetchall()
        ]
    # 余额与到期提醒都来自权益中心；本地 users.credit 仅作历史留档
    balance = credit_balance_detail(user_id)
    return {
        "userId": user_id,
        "nickname": user["nickname"] or user_id,
        "credit": balance["credit"],
        "expiringSoon": balance["expiringSoon"],
        "nearestExpireAt": balance["nearestExpireAt"],
        "expiringWindowDays": balance["expiringWindowDays"],
        "llmQuota": user["llm_quota"],
        "signedToday": signed,
        "flows": flows,
    }


@app.post("/api/sign")
def sign(request: Request) -> dict[str, Any]:
    """签到：中台判定 + 发抽奖次数，主站发积分。

    顺序刻意是「先发奖、最后写 sign_log」：sign_log 是「今天签过」的唯一事实，
    先写再发的话，一旦积分发放失败（中台抖动/网络），当天就再也补不上了。
    反过来，发放用 eventId=sign:{user}:{day} 幂等，重试不会重复加积分。

    收敛口径（P1-③）：积分的唯一发放出口在主站；中台只负责签到判定与抽奖次数，
    中台侧的 integral 返利配置已删除，避免同一笔签到一起发 20 积分。
    """
    user_id = require_user(request)
    today = date.today().isoformat()

    with db() as conn:
        ensure_user(conn, user_id)
        already = conn.execute(
            "select 1 from sign_log where user_id = ? and sign_date = ?", (user_id, today)
        ).fetchone()
    if already:
        # 自愈：昨天/上次可能签到成功但积分没到账，幂等补发一次（eventId 相同不会重复加）
        credit_grant(user_id, "sign", SIGN_CREDIT, f"sign:{user_id}:{today}")
        return {"ok": False, "reason": "今天已经签到过了", "credit": credit_balance(user_id)}

    # 1. 中台判定：重复签到返回 signed=false（返利单按「用户+类型+单号」唯一）
    #    签到走开放接口（系统对系统，API Key + HMAC 签名），不是 C 端接口
    mid_result = call_mid("POST", "/open/v1/sign", {"userId": user_id})
    if mid_result.get("code") != "0000":
        return {"ok": False, "reason": f"中台判定不可签到: {mid_result.get('info')}", "credit": credit_balance(user_id)}
    if not mid_result.get("signed", False):
        with db() as conn:
            ensure_user(conn, user_id)
            conn.execute("insert or ignore into sign_log(user_id, sign_date) values (?,?)", (user_id, today))
        credit_grant(user_id, "sign", SIGN_CREDIT, f"sign:{user_id}:{today}")
        return {"ok": False, "reason": "今天已经签到过了", "credit": credit_balance(user_id)}

    # 2. 主站发积分（幂等键与判定口径一致：用户 + 自然日）
    credit_grant(user_id, "sign", SIGN_CREDIT, f"sign:{user_id}:{today}")

    # 3. 最后落签到记录：失败可以重试，成功即当日闭环
    with db() as conn:
        ensure_user(conn, user_id)
        try:
            conn.execute("insert into sign_log(user_id, sign_date) values (?,?)", (user_id, today))
        except sqlite3.IntegrityError:
            pass  # 并发下另一请求已落库，幂等
        add_flow(conn, user_id, "SIGN", SIGN_CREDIT, f"{today} 签到奖励")
    return {"ok": True, "credit": credit_balance(user_id), "reason": "签到成功"}


@app.post("/api/chat")
def chat(request: Request, req: ChatRequest) -> dict[str, Any]:
    """对话消耗积分：扣分由权益中心执行（FIFO 扣批次 + 账户 + 流水，一个本地事务）。"""
    user_id = require_user(request)
    with db() as conn:
        ensure_user(conn, user_id)

    # 每次对话是一笔独立消费，bizNo 用随机串；重复请求不会重复扣（同一 bizNo 幂等）
    biz_no = f"chat:{uuid.uuid4().hex[:16]}"
    ok, balance, reason = credit_consume(user_id, "chat", CHAT_COST, biz_no)
    if not ok:
        return {"ok": False, "reason": "积分不足，去签到或抽奖试试？", "credit": balance, "detail": reason}

    with db() as conn:
        add_flow(conn, user_id, "CHAT", -CHAT_COST, "对话消耗")

    reply = f"（模拟回复）你说的是「{req.message[:40]}」，本次消耗 {CHAT_COST} 积分。"
    return {"ok": True, "reply": reply, "credit": balance}


@app.post("/api/recharge")
def recharge(request: Request) -> dict[str, Any]:
    """模拟"做任务送抽奖次数"：调中台开放接口充值一个 sku。"""
    user_id = require_user(request)
    out_business_no = f"TASK-{user_id}-{uuid.uuid4().hex[:12]}"
    result = call_mid(
        "POST",
        "/open/v1/quota/recharge",
        {"userId": user_id, "sku": SKU, "outBusinessNo": out_business_no},
    )
    if result.get("code") != "0000":
        return {"ok": False, "reason": result.get("info", "充值失败")}
    return {"ok": True, "outBusinessNo": out_business_no, "orderId": result.get("orderId")}


@app.get("/api/pay/products")
def pay_products(request: Request) -> dict[str, Any]:
    """商品目录：直接透传权益中心，前端不硬编码价格。"""
    require_user(request)
    return call_mid("GET", "/open/v1/pay/products")


@app.post("/api/pay/buy")
def pay_buy(request: Request, req: BuyRequest) -> dict[str, Any]:
    """模拟一次购买：下单 → 模拟支付平台回调 → 权益中心异步发放。

    真接支付平台时，这里会变成「跳转收银台」，回调由平台发起；
    演示里把第二步放在同一个接口，方便一点就走完整条链路。
    """
    user_id = require_user(request)
    order_no = f"ORD-{user_id}-{int(time.time() * 1000)}"

    order = call_mid("POST", "/open/v1/pay/order",
                     {"userId": user_id, "sku": req.sku, "orderNo": order_no})
    if order.get("code") != "0000":
        return {"ok": False, "reason": order.get("info", "下单失败")}

    # 模拟支付平台回调（真实场景由平台异步通知，且会重复通知多次）
    result = call_mid("POST", "/open/v1/pay/callback", {
        "outTradeNo": "MOCK-" + order_no,
        "orderNo": order_no,
        "amountCent": order["amountCent"],
        "notifySeq": 1,
        "raw": json.dumps({"mock": True, "orderNo": order_no}, ensure_ascii=False),
    })
    ok = result.get("code") == "0000" and bool(result.get("accepted"))

    with db() as conn:
        add_flow(conn, user_id, "PAY", order["amount"], f"{order['sku']} 购买，{order['amountCent']} 分")

    return {
        "ok": ok,
        "orderNo": order_no,
        "amount": order["amount"],
        "amountCent": order["amountCent"],
        "state": result.get("state"),
    }


@app.get("/api/play-link")
def play_link(request: Request) -> dict[str, Any]:
    """换一个中台 JWT，拼出抽奖页地址。

    抽奖页不需要用户再登录一次，但身份以这个 token 为准——直接改 URL 上的 userId 是无效的。
    """
    user_id = require_user(request)
    result = call_mid("POST", "/api/v1/auth/login", {"userId": user_id})
    token = result.get("token")
    if not token:
        raise HTTPException(status_code=502, detail="中台没有签发 token")
    url = f"{WEB_BASE}/play?token={urllib.parse.quote(token)}&activityId={ACTIVITY_ID}"
    return {"ok": True, "url": url}


@app.post("/webhook/award")
async def award_webhook(request: Request) -> JSONResponse:
    """接收中台的发奖回调：验签 + 幂等 + 入账。"""
    raw = (await request.body()).decode("utf-8")
    signature = request.headers.get("X-Sign", "")
    event_id = request.headers.get("X-Event-Id", "")

    expected = hmac.new(CALLBACK_SECRET.encode(), raw.encode(), hashlib.sha256).hexdigest()
    if signature and not hmac.compare_digest(expected, signature):
        return JSONResponse(status_code=401, content={"ok": False, "reason": "签名不匹配"})

    try:
        payload = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        return JSONResponse(status_code=400, content={"ok": False, "reason": "body 不是合法 JSON"})

    event_id = event_id or payload.get("eventId") or ""
    if not event_id:
        return JSONResponse(status_code=400, content={"ok": False, "reason": "缺少 eventId"})

    user_id = payload.get("userId")
    prize_type = (payload.get("prizeType") or "").upper()
    amount = int(payload.get("amount") or 0)

    pending_grant: tuple[str, int, str] | None = None
    with db() as conn:
        if conn.execute("select 1 from callbacks where event_id = ?", (event_id,)).fetchone():
            return JSONResponse(content={"ok": True, "duplicated": True})

        if user_id:
            ensure_user(conn, user_id)
            if prize_type in ("CREDIT", "INTEGRAL"):
                # 积分入账交给权益中心；eventId 用回调幂等键，重投不会重复加
                pending_grant = (user_id, amount, f"award:{event_id}")
                add_flow(conn, user_id, "AWARD_CREDIT", amount, payload.get("awardTitle", ""))
            elif prize_type in ("LLM_QUOTA", "OPENAI_USE_COUNT"):
                conn.execute("update users set llm_quota = llm_quota + ? where user_id = ?", (amount, user_id))
                add_flow(conn, user_id, "AWARD_QUOTA", amount, payload.get("awardTitle", ""))

        conn.execute(
            "insert into callbacks(event_id, payload, received_at) values (?,?,?)",
            (event_id, raw, datetime.now().isoformat(timespec="seconds")),
        )

    # 网络调用放在事务外，避免长事务
    if pending_grant:
        credit_grant(pending_grant[0], "lottery", pending_grant[1], pending_grant[2])
    return JSONResponse(content={"ok": True})


init_db()

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=int(os.environ.get("PORT", "9000")))
