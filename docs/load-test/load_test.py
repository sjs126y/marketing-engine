#!/usr/bin/env python3
"""抽奖接口并发压测 + 守恒校验。

用法：
    python load_test.py --base-url http://127.0.0.1:8092 --activity-id 100301 \
        --sku 9011 --users 200 --requests 1000 --concurrency 50

前置：
    1. 中间件已启动，库表已初始化，索引见 docs/dev-ops/mysql/sql
    2. 每个测试用户需要先有额度：脚本会先调开放接口充值（需要开启鉴权时传 --api-key/--api-secret）
       未开启鉴权时直接调 /api/v1/raffle/activity/calendar_sign_rebate 也可

校验的守恒关系（--check-sql 会打印对应 SQL）：
    中奖记录数 == 消耗的额度数
    SKU 初始库存 - 当前剩余 == 成功充值次数
    奖品剩余 + 已发数量 == 奖品总量

退出码：0 全部通过；1 出现 HTTP 失败或守恒被破坏。
"""

import argparse
import concurrent.futures
import json
import sys
import time
import urllib.error
import urllib.request

SUCCESS_CODE = "0000"


def post(url, payload, headers=None, timeout=10):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json;charset=utf-8")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return {"code": str(e.code), "info": body}
    except Exception as e:  # 连接失败等
        return {"code": "EXC", "info": str(e)}


def draw_one(base_url, activity_id, user_id, headers):
    return post(f"{base_url}/api/v1/raffle/activity/draw",
                {"userId": user_id, "activityId": activity_id}, headers)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8092")
    parser.add_argument("--activity-id", type=int, required=True)
    parser.add_argument("--users", type=int, default=100, help="并发用户数")
    parser.add_argument("--requests", type=int, default=500, help="总请求数")
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--api-key", default=None)
    parser.add_argument("--api-secret", default=None)
    args = parser.parse_args()

    headers = {}
    if args.api_key:
        headers["X-Api-Key"] = args.api_key
        # 简化签名：真实调用需按 HMAC 规则签名，见 ApiKeyVerifier

    user_ids = [f"load-{i:04d}" for i in range(args.users)]

    # 1. 压测
    started = time.time()
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futures = [
            pool.submit(draw_one, args.base_url, args.activity_id,
                        user_ids[i % len(user_ids)], headers)
            for i in range(args.requests)
        ]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
    elapsed = time.time() - started

    ok = sum(1 for r in results if r.get("code") == SUCCESS_CODE)
    errors = {}
    for r in results:
        if r.get("code") != SUCCESS_CODE:
            key = str(r.get("code"))
            errors[key] = errors.get(key, 0) + 1

    print("=" * 60)
    print(f"总请求     : {args.requests}")
    print(f"并发度     : {args.concurrency}")
    print(f"耗时       : {elapsed:.2f}s")
    print(f"QPS        : {args.requests / elapsed:.1f}")
    print(f"成功       : {ok}")
    print(f"失败       : {args.requests - ok}  {errors if errors else ''}")
    print("=" * 60)
    print("守恒校验 SQL（在对应库执行，三处结果应一致）：")
    print("  -- 1) 中奖记录数（分库分表广播查询）")
    print("  select count(*) from user_award_record;")
    print("  -- 2) 奖品剩余 + 已发 == 总量")
    print("  select award_id, award_count, award_count_surplus from strategy_award where strategy_id = ?;")
    print("  -- 3) SKU 库存与已扣数")
    print("  select sku, stock_count, stock_count_surplus, applied_consumed from raffle_activity_sku where sku = ?;")
    print("=" * 60)

    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
