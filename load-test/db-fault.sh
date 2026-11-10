#!/usr/bin/env bash
# MySQL outage on the same timeline as openai-fault.js (LOCAL STACK ONLY):
#   t0+60s  MySQL container stopped  (like the OOM kill we hit on a 512MB MySQL)
#   t0+180s MySQL container started again
#   ./load-test/db-fault.sh <label>
set -euo pipefail
cd "$(dirname "$0")/.."
LABEL=${1:?label}
MYSQL=meetingmate-obs-mysql-1

FAULT=none AI_RATE=${AI_RATE:-4} ./load-test/run.sh openai-fault "$LABEL" > /dev/null 2>&1 &
K6=$!

until f=$(ls -t load-test/results/*-openai-fault-"$LABEL".txt 2>/dev/null | head -1) && grep -q '\[t0\]' "$f"; do sleep 1; done
echo "t0 seen in $f"
sleep 60
echo "[db-fault] $(date -u +%FT%TZ) docker stop $MYSQL" | tee -a "${f%.txt}.meta"
docker stop -t 2 "$MYSQL" > /dev/null
sleep 120
echo "[db-fault] $(date -u +%FT%TZ) docker start $MYSQL" | tee -a "${f%.txt}.meta"
docker start "$MYSQL" > /dev/null
until docker exec "$MYSQL" sh -c 'mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; do sleep 1; done
echo "[db-fault] $(date -u +%FT%TZ) mysql accepting connections" | tee -a "${f%.txt}.meta"
wait $K6 || true
echo "done: $f"
