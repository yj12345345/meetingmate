#!/usr/bin/env python3
"""Pull server-side metrics from the local Prometheus for a k6 run window.

    python3 load-test/collect.py load-test/results/<run>.meta

Reads the k6 start/end timestamps from the .meta file and prints a markdown
table that goes straight into docs/ops-log.
"""
import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone

PROM = "http://127.0.0.1:9090"
SEL = 'application="meetingmate", uri!~"/actuator.*"'


def ts(s):
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc).timestamp()


def q(expr, at):
    url = f"{PROM}/api/v1/query?" + urllib.parse.urlencode({"query": expr, "time": at})
    res = json.load(urllib.request.urlopen(url))["data"]["result"]
    return res


def scalar(expr, at):
    r = q(expr, at)
    return float(r[0]["value"][1]) if r else float("nan")


meta = open(sys.argv[1]).read()
start, end = [ts(m) for m in re.findall(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ)", meta)[:2]]
win = f"{int(end - start)}s"

rows = [
    ("Hikari active (max)", f"max_over_time(sum(hikaricp_connections_active{{application=\"meetingmate\"}})[{win}:5s])", "{:.0f}"),
    ("Hikari pending (max)", f"max_over_time(sum(hikaricp_connections_pending{{application=\"meetingmate\"}})[{win}:5s])", "{:.0f}"),
    ("Hikari acquire timeouts", f"sum(increase(hikaricp_connections_timeout_total{{application=\"meetingmate\"}}[{win}]))", "{:.0f}"),
    ("Hikari avg acquire wait (ms)",
     f"1000 * sum(increase(hikaricp_connections_acquire_seconds_sum{{application=\"meetingmate\"}}[{win}])) / sum(increase(hikaricp_connections_acquire_seconds_count{{application=\"meetingmate\"}}[{win}]))", "{:.1f}"),
    ("Hikari avg connection hold (ms)",
     f"1000 * sum(increase(hikaricp_connections_usage_seconds_sum{{application=\"meetingmate\"}}[{win}])) / sum(increase(hikaricp_connections_usage_seconds_count{{application=\"meetingmate\"}}[{win}]))", "{:.1f}"),
    ("Tomcat busy threads (max)", f"max_over_time(tomcat_threads_busy_threads{{application=\"meetingmate\"}}[{win}])", "{:.0f}"),
    ("Heap used (max MB)", f"max_over_time(sum(jvm_memory_used_bytes{{application=\"meetingmate\", area=\"heap\"}})[{win}:5s]) / 1048576", "{:.0f}"),
    ("Process CPU (max %)", f"100 * max_over_time(process_cpu_usage{{application=\"meetingmate\"}}[{win}])", "{:.0f}"),
    ("5xx responses", f"sum(increase(http_server_requests_seconds_count{{{SEL}, status=~\"5..\"}}[{win}]))", "{:.0f}"),
]

print(f"Window: {win}\n")
print("| Server metric | Value |\n|---|---|")
for name, expr, fmt in rows:
    print(f"| {name} | {fmt.format(scalar(expr, end))} |")

print("\n| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |\n|---|---|---|---|---|")
counts = q(f"sum by (method, uri) (increase(http_server_requests_seconds_count{{{SEL}}}[{win}]))", end)
for c in sorted(counts, key=lambda r: -float(r["value"][1])):
    m, u = c["metric"]["method"], c["metric"]["uri"]
    n = float(c["value"][1])
    if n < 1:
        continue
    pct = []
    for p in (0.5, 0.95, 0.99):
        pct.append(1000 * scalar(
            f'histogram_quantile({p}, sum by (le) (increase(http_server_requests_seconds_bucket{{{SEL}, method="{m}", uri="{u}"}}[{win}])))', end))
    print(f"| {m} {u} | {n:.0f} | {pct[0]:.0f} | {pct[1]:.0f} | {pct[2]:.0f} |")
