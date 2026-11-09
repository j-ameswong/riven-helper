#!/usr/bin/env python3
"""Paced warframe.market rate limit probe. Stops at the first non-2xx and logs every request.

  uv run --with 'httpx[http2]' scripts/rate-probe/probe.py selfcheck
  uv run --with 'httpx[http2]' scripts/rate-probe/probe.py run S1-1 search --interval 0 --max 40 --cold-hours 12
"""
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import sys
import time

LOG = pathlib.Path(__file__).with_name("results.jsonl")
BASE = "https://api.warframe.market"
ENDPOINTS = {
    # Every weapon fills the 100 result page (about 105 KB), so a polarity that never matches keeps it light.
    "search": "/v1/auctions/search?type=riven&weapon_url_name=enkaus&sort_by=price_asc&polarity=zenurik",
    "auctions": "/v1/auctions",
    "versions": "/v2/versions",
}
HEADERS = {
    "User-Agent": "RivenHelper/0.0.1 (+https://github.com/j-ameswong/riven-helper)",
    "Accept": "application/json",
    "Platform": "pc",
    "Language": "en",
}
MAX_429_PER_DAY = 6


def refusal(rows, now, cold_hours):
    """Why a run may not start, or None. Rows are parsed log entries."""
    failures = [r for r in rows if not 200 <= r["status"] < 300]
    if failures:
        hours = (now - failures[-1]["t"]) / 3600
        if hours < cold_hours:
            return f"last non-2xx was {hours:.2f}h ago, need {cold_hours}h"
    recent = sum(r["status"] == 429 and now - r["t"] < 86400 for r in failures)
    if recent >= MAX_429_PER_DAY:
        return f"{recent} 429s in the last 24h"
    return None


def abort_reason(response_status, headers, body):
    """Anything other than a plain rate limit means back off for 24h."""
    if response_status == 429 and "1015" in body and "cf-mitigated" not in headers:
        return None
    if 200 <= response_status < 300:
        return None
    return f"unexpected {response_status}: abort for 24h"


def read_log():
    if not LOG.exists():
        return []
    return [json.loads(line) for line in LOG.read_text().splitlines() if line]


def run(args):
    import httpx

    reason = refusal(read_log(), time.time(), args.cold_hours)
    if reason:
        sys.exit(f"refusing to start: {reason}")
    path = ENDPOINTS[args.endpoint]
    with httpx.Client(http2=True, headers=HEADERS, timeout=30) as client, LOG.open("a") as log:
        start = time.monotonic()
        for n in range(1, args.max + 1):
            time.sleep(max(0, start + (n - 1) * args.interval - time.monotonic()))
            sent = time.time()
            client.cookies.clear()  # the JDK client in the app keeps no cookies
            response = client.get(BASE + path)
            ok = response.is_success
            row = {
                "trial": args.trial, "endpoint": args.endpoint, "path": path, "n": n, "t": sent,
                "at": dt.datetime.fromtimestamp(sent, dt.UTC).isoformat(timespec="milliseconds"),
                "ms": round(response.elapsed.total_seconds() * 1000), "status": response.status_code,
                "http": response.http_version, "retry_after": response.headers.get("retry-after"),
                "colo": response.headers.get("cf-ray", "-").rsplit("-", 1)[-1],
                "bytes": len(response.content), "sha": hashlib.sha256(response.content).hexdigest()[:12],
            }
            if n == 1 or not ok:
                row["headers"] = {k: v for k, v in response.headers.items() if k != "set-cookie"}
            if not ok:
                row["body"] = response.text[:300]
            log.write(json.dumps(row) + "\n")
            log.flush()
            print(n, row["status"], f'{row["ms"]}ms', row["colo"], row["bytes"], row["retry_after"] or "")
            if not ok:
                print(abort_reason(response.status_code, response.headers, response.text) or
                      f"first failure at request {n}, stopping")
                return
    print(f"no failure in {args.max} requests")


def selfcheck():
    now = 100_000.0
    ok, bad = {"status": 200, "t": now - 10}, {"status": 429, "t": now - 3600}
    assert refusal([], now, 12) is None
    assert refusal([ok], now, 12) is None
    assert "1.00h" in refusal([bad, ok], now, 2)
    assert refusal([bad], now, 0.5) is None
    assert "429s" in refusal([{"status": 429, "t": now - 7200}] * 6, now, 1)
    assert refusal([{"status": 429, "t": now - 90_000}] * 6, now, 1) is None
    assert abort_reason(200, {}, "") is None
    assert abort_reason(429, {}, "error code: 1015") is None
    assert "abort" in abort_reason(403, {}, "")
    assert "abort" in abort_reason(429, {"cf-mitigated": "challenge"}, "error code: 1015")
    assert "abort" in abort_reason(429, {}, "origin says no")
    print("selfcheck ok")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("selfcheck")
    runner = commands.add_parser("run")
    runner.add_argument("trial")
    runner.add_argument("endpoint", choices=ENDPOINTS)
    runner.add_argument("--interval", type=float, required=True, help="seconds between request starts")
    runner.add_argument("--max", type=int, required=True)
    runner.add_argument("--cold-hours", type=float, default=2)
    args = parser.parse_args()
    selfcheck() if args.command == "selfcheck" else run(args)
