#!/usr/bin/env python3
"""抽奖并发压测 + 守恒校验（本地验证用）。

前置：应用已启动（默认 http://127.0.0.1:8092），鉴权关闭（app.auth.enabled=false）。

流程：
    1. 通过开放接口给 N 个测试用户充值 SKU（每个用户一次，outBusinessNo 唯一）
    2. 并发抽奖 M 次
    3. 打印 QPS / 成功率，并给出守恒校验 SQL

用法：
    python load_test.py --users 20 --draws 200 --concurrency 20
"""

import argparse
import concurrent.futures
import json
import time
import urllib.error
import urllib.request

OK = "0000"


def http_post(url, payload, timeout=15):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json;charset=utf-8")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": str(e.code), "info": e.read().decode("utf-8", errors="replace")[:200]}
    except Exception as e:
        return {"code": "EXC", "info": str(e)[:200]}


def http_get(url, timeout=15):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        return {"code": "EXC", "info": str(e)[:200]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", default="http://127.0.0.1:8092")
    ap.add_argument("--activity-id", type=int, default=100301)
    ap.add_argument("--sku", type=int, default=9011)
    ap.add_argument("--users", type=int, default=20)
    ap.add_argument("--draws", type=int, default=200)
    ap.add_argument("--concurrency", type=int, default=20)
    ap.add_argument("--tag", default=None, help="本次压测的标识，用于生成唯一 outBusinessNo")
    args = ap.parse_args()

    tag = args.tag or str(int(time.time()))
    users = [f"load-{tag}-{i:03d}" for i in range(args.users)]

    print(f"[1/3] 充值 {len(users)} 个用户（sku={args.sku}）...")
    recharge_ok = 0
    for i, user in enumerate(users):
        r = http_post(f"{args.base_url}/open/v1/quota/recharge",
                      {"userId": user, "sku": args.sku, "outBusinessNo": f"{tag}-{i}"})
        if r.get("code") == OK:
            recharge_ok += 1
        else:
            print("   充值失败:", user, r)
    print(f"   成功充值 {recharge_ok}/{len(users)}")

    print(f"[2/3] 并发抽奖 {args.draws} 次（并发 {args.concurrency}）...")
    started = time.time()
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        futs = [pool.submit(http_post, f"{args.base_url}/api/v1/raffle/activity/draw",
                            {"userId": users[i % len(users)], "activityId": args.activity_id})
                for i in range(args.draws)]
        for f in concurrent.futures.as_completed(futs):
            results.append(f.result())
    elapsed = time.time() - started

    ok = sum(1 for r in results if r.get("code") == OK)
    errors = {}
    for r in results:
        if r.get("code") != OK:
            errors[r.get("code")] = errors.get(r.get("code"), 0) + 1

    print(f"   耗时 {elapsed:.2f}s  QPS {args.draws / elapsed:.1f}")
    print(f"   成功 {ok}  失败 {args.draws - ok}  {errors if errors else ''}")

    print("[3/3] 守恒校验（在库上执行）")
    print("   ① SKU：库存总量 - 剩余 == applied_consumed，且剩余 >= 0")
    print("      select sku, stock_count, stock_count_surplus, applied_consumed,")
    print("             stock_count - stock_count_surplus as expected_consumed")
    print("        from raffle_activity_sku where sku = %d;" % args.sku)
    print("   ② 奖品：总量 - 剩余 == 中奖记录数（按 award_id 分组）")
    print("      select award_id, award_count, award_count_surplus,")
    print("             award_count - award_count_surplus as given")
    print("        from strategy_award where strategy_id = 100006 order by award_id;")
    print("   ③ 中奖记录数 == 本次成功抽奖数")
    print("      select count(*) from user_award_record where user_id like 'load-%s%%';" % tag)

    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
