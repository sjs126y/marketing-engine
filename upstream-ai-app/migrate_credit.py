# -*- coding: utf-8 -*-
"""把上游 SQLite 里的历史积分迁移到权益中心（幂等，可重复执行）。

迁移口径：
  - 每个用户一条批次，scene = migrate，eventId = migrate:{userId}
  - 有效期 30 天（与线上发放一致）
  - eventId 幂等 → 重复执行不会重复加钱

用法：
  .venv/Scripts/python.exe migrate_credit.py [--dry-run]
"""
from __future__ import annotations

import hashlib
import hmac
import json
import sqlite3
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = BASE_DIR / "upstream.db"
MID_PLATFORM = "http://127.0.0.1:8092"
API_KEY = "demo-client"
API_SECRET = "demo-secret-please-change"
TTL_DAYS = 30


def call_mid(method: str, path: str, payload: dict | None = None, query: dict | None = None) -> dict:
    body = "" if payload is None else json.dumps(payload, ensure_ascii=False)
    qs = ("?" + urllib.parse.urlencode(query)) if query else ""
    req = urllib.request.Request(f"{MID_PLATFORM}{path}{qs}", data=body.encode() if body else None, method=method)
    req.add_header("Content-Type", "application/json")
    ts = str(int(time.time()))
    # 签名只覆盖 path，不含 query（服务端用 requestURI）
    payload_str = f"{method}\n{path}\n{ts}\n{body}"
    req.add_header("X-Api-Key", API_KEY)
    req.add_header("X-Timestamp", ts)
    req.add_header("X-Sign", hmac.new(API_SECRET.encode(), payload_str.encode(), hashlib.sha256).hexdigest())
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    rows = conn.execute("select user_id, credit from users where credit > 0 order by credit desc").fetchall()
    print(f"待迁移用户 {len(rows)} 个，合计 {sum(r['credit'] for r in rows)} 积分")

    migrated = skipped = failed = 0
    for row in rows:
        user_id, amount = row["user_id"], int(row["credit"])
        event_id = f"migrate:{user_id}"
        if dry_run:
            print(f"  [dry] {user_id:<16} {amount:>5} -> eventId={event_id}")
            continue
        try:
            result = call_mid("POST", "/open/v1/credit/grant", {
                "userId": user_id, "scene": "migrate", "amount": amount,
                "eventId": event_id, "ttlDays": TTL_DAYS,
            })
        except urllib.error.HTTPError as e:
            failed += 1
            print(f"  [fail] {user_id} HTTP {e.code} {e.read().decode('utf-8', 'replace')[:120]}")
            continue
        except Exception as e:  # noqa: BLE001
            failed += 1
            print(f"  [fail] {user_id} {e}")
            continue

        if result.get("code") != "0000":
            failed += 1
            print(f"  [fail] {user_id} {result}")
        elif result.get("granted"):
            migrated += 1
            print(f"  [ok]   {user_id:<16} +{amount:<5} balance={result.get('balance')}")
        else:
            skipped += 1
            print(f"  [skip] {user_id:<16} 已迁移过 balance={result.get('balance')}")

    print(f"\n完成：新入账 {migrated}，幂等跳过 {skipped}，失败 {failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
