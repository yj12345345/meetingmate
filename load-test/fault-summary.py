#!/usr/bin/env python3
"""Per-phase client-side table from an openai-fault.js k6 output (.txt)."""
import re, sys
txt = open(sys.argv[1]).read()
sec = txt.split("THRESHOLDS")[1].split("TOTAL RESULTS")[0]
vals = dict(re.findall(r"(\S+\{name:\w+,phase:\w+\})\s*\n\s*✓ '\w+>=0' (\w+)=([^\s]+)", sec) and
            [(m[0], m[2]) for m in re.findall(r"(\S+\{name:\w+,phase:\w+\})\s*\n\s*✓ '\w+>=0' (\w+)=([^\s]+)", sec)])
print("| Phase | recommend fail % | recommend max | login fail % | login max | my-page fail % | my-page max |")
print("|---|---|---|---|---|---|---|")
for ph in ("normal", "fault", "recovery"):
    g = lambda m, n: vals.get(f"{m}{{name:{n},phase:{ph}}}", "-")
    print(f"| {ph} | {g('http_req_failed','recommend')} | {g('http_req_duration','recommend')} | "
          f"{g('http_req_failed','login')} | {g('http_req_duration','login')} | "
          f"{g('http_req_failed','user_me')} | {g('http_req_duration','user_me')} |")
