"""上游 AI 助手（演示用业务方）。

角色：营销中台的**上游业务系统**。
  - 用户在我的 App 里签到 → 我调中台的签到接口
  - 用户和我对话 → 我扣自己账上的积分
  - 用户去抽奖 → 我代理调中台抽奖接口，把结果返回给前端
  - 中台发奖后回调我 → 我验签、幂等、把奖品记进自己的账户

边界约定：积分、对话次数这些"业务资产"全部在我这里，中台只做「给谁、给什么」的判定，
然后通过回调告诉我。中台不持有资产，我也不碰抽奖的规则和概率。

技术栈：FastAPI + SQLite，刻意用 Python 是为了证明「上游可以不是 Java」。
"""

from __future__ import annotations

import hashlib
import hmac
import json
import os
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
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "upstream.db"

# ---------- 中台连接配置 ----------
# 中台地址、开放接口凭证、活动与 SKU 都通过环境变量注入，方便联调时切换。
MID_PLATFORM = os.environ.get("MID_PLATFORM", "http://127.0.0.1:8092")
API_KEY = os.environ.get("MID_API_KEY", "demo-client")
API_SECRET = os.environ.get("MID_API_SECRET", "demo-secret-please-change")
ACTIVITY_ID = int(os.environ.get("MID_ACTIVITY_ID", "100301"))
SKU = int(os.environ.get("MID_SKU", "9011"))

# 回调验签密钥：中台用同一把密钥对回调体签名
CALLBACK_SECRET = os.environ.get("CALLBACK_SECRET", "upstream-callback-secret")

# 签到奖励（上游自己的规则）：中台负责判定"能不能签到"，给多少积分是上游说了算
SIGN_CREDIT = int(os.environ.get("SIGN_CREDIT", "10"))
CHAT_COST = int(os.environ.get("CHAT_COST", "1"))

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
                user_id    text primary key,
                credit     integer not null default 0,
                llm_quota  integer not null default 0,
                created_at text    not null
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


def ensure_user(conn: sqlite3.Connection, user_id: str) -> sqlite3.Row:
    row = conn.execute("select * from users where user_id = ?", (user_id,)).fetchone()
    if row is None:
        conn.execute(
            "insert into users(user_id, credit, llm_quota, created_at) values (?, 0, 0, ?)",
            (user_id, datetime.now().isoformat(timespec="seconds")),
        )
        row = conn.execute("select * from users where user_id = ?", (user_id,)).fetchone()
    return row


def add_flow(conn: sqlite3.Connection, user_id: str, kind: str, amount: int, note: str) -> None:
    conn.execute(
        "insert into flows(user_id, kind, amount, note, created_at) values (?,?,?,?,?)",
        (user_id, kind, amount, note, datetime.now().isoformat(timespec="seconds")),
    )


# ---------- 调中台 ----------
def _sign(method: str, path: str, timestamp: str, body: str) -> str:
    payload = f"{method}\n{path}\n{timestamp}\n{body}"
    return hmac.new(API_SECRET.encode(), payload.encode(), hashlib.sha256).hexdigest()


def call_mid(method: str, path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    """调用中台接口。

    对 /open/** 的开放接口自动附加 API Key + HMAC 签名；
    对 /api/** 的 C 端接口按普通 JSON 调用。中台鉴权关闭时签名不校验，开启后自动生效。
    """
    body = "" if payload is None else json.dumps(payload, ensure_ascii=False)
    url = f"{MID_PLATFORM}{path}"
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
    except Exception as e:  # 连接失败等
        raise HTTPException(status_code=502, detail=f"中台不可达: {e}")


# ---------- 接口 ----------
class SignRequest(BaseModel):
    userId: str


class ChatRequest(BaseModel):
    userId: str
    message: str = "你好"


class DrawRequest(BaseModel):
    userId: str


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "UP", "midPlatform": MID_PLATFORM, "activityId": ACTIVITY_ID}


@app.get("/api/me")
def me(userId: str) -> dict[str, Any]:
    with db() as conn:
        user = ensure_user(conn, userId)
        today = date.today().isoformat()
        signed = conn.execute(
            "select 1 from sign_log where user_id = ? and sign_date = ?", (userId, today)
        ).fetchone() is not None
        flows = [
            dict(r)
            for r in conn.execute(
                "select kind, amount, note, created_at from flows where user_id = ? "
                "order by id desc limit 10",
                (userId,),
            ).fetchall()
        ]
    return {
        "userId": userId,
        "credit": user["credit"],
        "llmQuota": user["llm_quota"],
        "signedToday": signed,
        "flows": flows,
    }


@app.post("/api/sign")
def sign(req: SignRequest) -> dict[str, Any]:
    """签到：先让中台判定（是否可签、应该给什么），再由上游自己记账。

    幂等：同一用户同一天只签一次，靠 sign_log 的唯一键兜住。
    """
    today = date.today().isoformat()
    with db() as conn:
        ensure_user(conn, req.userId)
        already = conn.execute(
            "select 1 from sign_log where user_id = ? and sign_date = ?", (req.userId, today)
        ).fetchone()
        if already:
            return {"ok": False, "reason": "今天已经签到过了", "credit": SIGN_CREDIT}

    # 调中台签到（中台负责判定与记录返利链路）
    mid_result = call_mid(
        "POST",
        f"/api/v1/raffle/activity/calendar_sign_rebate?userId={urllib.parse.quote(req.userId)}",
    )
    if mid_result.get("code") != "0000":
        return {"ok": False, "reason": f"中台判定不可签到: {mid_result.get('info')}", "credit": 0}

    with db() as conn:
        ensure_user(conn, req.userId)
        # 唯一键兜底：并发下只有一个能插入成功
        try:
            conn.execute(
                "insert into sign_log(user_id, sign_date) values (?,?)", (req.userId, today)
            )
        except sqlite3.IntegrityError:
            return {"ok": False, "reason": "今天已经签到过了", "credit": 0}
        conn.execute(
            "update users set credit = credit + ? where user_id = ?", (SIGN_CREDIT, req.userId)
        )
        add_flow(conn, req.userId, "SIGN", SIGN_CREDIT, f"{today} 签到奖励")
    return {"ok": True, "credit": SIGN_CREDIT, "reason": "签到成功"}


@app.post("/api/chat")
def chat(req: ChatRequest) -> dict[str, Any]:
    """对话：扣公共积分余额。余额不足时提示去抽奖。"""
    with db() as conn:
        user = ensure_user(conn, req.userId)
        if user["credit"] < CHAT_COST:
            return {"ok": False, "reason": "积分不足，去抽奖试试？", "credit": user["credit"]}
        # 条件更新保证并发安全：余额够才扣
        cur = conn.execute(
            "update users set credit = credit - ? where user_id = ? and credit >= ?",
            (CHAT_COST, req.userId, CHAT_COST),
        )
        if cur.rowcount != 1:
            return {"ok": False, "reason": "积分不足", "credit": user["credit"]}
        add_flow(conn, req.userId, "CHAT", -CHAT_COST, "对话消耗")
        balance = conn.execute(
            "select credit from users where user_id = ?", (req.userId,)
        ).fetchone()["credit"]

    reply = f"（模拟回复）你说的是「{req.message[:40]}」，本次消耗 {CHAT_COST} 积分。"
    return {"ok": True, "reply": reply, "credit": balance}


@app.post("/api/draw")
def draw(req: DrawRequest) -> dict[str, Any]:
    """抽奖：代理调中台，把结果透传给前端。"""
    result = call_mid(
        "POST",
        "/api/v1/raffle/activity/draw",
        {"userId": req.userId, "activityId": ACTIVITY_ID},
    )
    if result.get("code") != "0000":
        return {"ok": False, "reason": result.get("info", "抽奖失败")}
    data = result.get("data") or {}
    return {
        "ok": True,
        "awardId": data.get("awardId"),
        "awardTitle": data.get("awardTitle"),
        "awardIndex": data.get("awardIndex"),
    }


@app.post("/api/recharge")
def recharge(req: SignRequest) -> dict[str, Any]:
    """模拟"做任务送抽奖次数"：调中台开放接口充值一个 sku。"""
    out_business_no = f"TASK-{req.userId}-{uuid.uuid4().hex[:12]}"
    result = call_mid(
        "POST",
        "/open/v1/quota/recharge",
        {"userId": req.userId, "sku": SKU, "outBusinessNo": out_business_no},
    )
    if result.get("code") != "0000":
        return {"ok": False, "reason": result.get("info", "充值失败")}
    return {"ok": True, "outBusinessNo": out_business_no, "orderId": result.get("orderId")}


@app.post("/webhook/award")
async def award_webhook(request: Request) -> JSONResponse:
    """接收中台的发奖回调。

    要点（和真实系统一致）：
      1. 验签：中台用 CALLBACK_SECRET 对 body 做 HMAC-SHA256，放在 X-Sign
      2. 幂等：eventId 唯一，重复回调直接返回 200，不重复入账
      3. 入账：按 prizeType 记到本地账户
    """
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

    with db() as conn:
        exists = conn.execute(
            "select 1 from callbacks where event_id = ?", (event_id,)
        ).fetchone()
        if exists:
            return JSONResponse(content={"ok": True, "duplicated": True})

        if user_id:
            ensure_user(conn, user_id)
            if prize_type in ("CREDIT", "INTEGRAL"):
                conn.execute(
                    "update users set credit = credit + ? where user_id = ?", (amount, user_id)
                )
                add_flow(conn, user_id, "AWARD_CREDIT", amount, payload.get("awardTitle", ""))
            elif prize_type in ("LLM_QUOTA", "OPENAI_USE_COUNT"):
                conn.execute(
                    "update users set llm_quota = llm_quota + ? where user_id = ?", (amount, user_id)
                )
                add_flow(conn, user_id, "AWARD_QUOTA", amount, payload.get("awardTitle", ""))

        conn.execute(
            "insert into callbacks(event_id, payload, received_at) values (?,?,?)",
            (event_id, raw, datetime.now().isoformat(timespec="seconds")),
        )
    return JSONResponse(content={"ok": True})


@app.get("/", response_class=HTMLResponse)
def index() -> str:
    return (BASE_DIR / "index.html").read_text(encoding="utf-8")


init_db()

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=int(os.environ.get("PORT", "9000")))
