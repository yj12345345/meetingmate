#!/usr/bin/env python3
"""Server-side metrics per phase (normal / fault / recovery) for openai-fault.js runs.

    python3 load-test/phase-collect.py load-test/results/<run>.meta

Phase boundaries are offsets from the "[t0]" line that openai-fault.js prints.
"""
import json
import re
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone

PROM = "http://127.0.0.1:9090"
APP = 'application="meetingmate"'


def q(expr, at):
    url = f"{PROM}/api/v1/query?" + urllib.parse.urlencode({"query": expr, "time": at})
    return json.load(urllib.request.urlopen(url))["data"]["result"]


def scalar(expr, at):
    r = q(expr, at)
    return float(r[0]["value"][1]) if r else 0.0


def ts(s):
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc).timestamp()


# openai-fault.js logs "[t0] <ISO time>" when setup finishes (= phase clock start).
out = open(sys.argv[1].replace(".meta", ".txt")).read()
iso = re.findall(r"\[t0\] (\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)", out)[0]
t0 = datetime.strptime(iso, "%Y-%m-%dT%H:%M:%S").replace(tzinfo=timezone.utc).timestamp()
phases = [("normal", t0 + 5, t0 + 60), ("fault", t0 + 60, t0 + 180), ("recovery", t0 + 180, t0 + 300)]

print("| Phase | Tomcat busy threads (max) | Hikari pending (max) | recommend p95 (ms, server) | 5xx (all) |")
print("|---|---|---|---|---|")
for name, a, b in phases:
    w = f"{int(b - a)}s"
    busy = scalar(f"max_over_time(tomcat_threads_busy_threads{{{APP}}}[{w}])", b)
    pend = scalar(f"max_over_time(sum(hikaricp_connections_pending{{{APP}}})[{w}:5s])", b)
    p95 = 1000 * scalar(
        f'histogram_quantile(0.95, sum by (le) (increase(http_server_requests_seconds_bucket{{{APP}, uri="/api/recommend"}}[{w}])))', b)
    e5 = scalar(f'sum(increase(http_server_requests_seconds_count{{{APP}, status=~"5.."}}[{w}]))', b)
    print(f"| {name} | {busy:.0f} | {pend:.0f} | {p95:.0f} | {e5:.0f} |")
