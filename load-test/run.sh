#!/usr/bin/env bash
# Run a k6 scenario against the LOCAL observability stack and keep the summary.
#   ./load-test/run.sh <scenario> <label> [PROFILE]
# e.g. ./load-test/run.sh user-flow baseline
#      ./load-test/run.sh user-flow smoke smoke
set -euo pipefail
cd "$(dirname "$0")/.."

SCENARIO=${1:?scenario name (file in load-test/ without .js)}
LABEL=${2:?label, e.g. baseline / after-osiv-off}
PROFILE=${3:-local}
TS=$(date +%Y%m%d-%H%M%S)
OUT=load-test/results/${TS}-${SCENARIO}-${LABEL}
mkdir -p load-test/results

echo "k6 start: $(date -u +%FT%TZ)  (use this time range in Grafana)" | tee "${OUT}.meta"
docker run --rm -i --network meetingmate-obs_default \
  -v "$PWD/load-test:/scripts" \
  -e BASE_URL=http://backend:8080 -e PROFILE="$PROFILE" -e RUN_ID="$TS" -e USERS="${USERS:-60}" \
  grafana/k6:1.3.0 run --quiet \
  --summary-export "/scripts/results/${TS}-${SCENARIO}-${LABEL}.json" \
  "/scripts/${SCENARIO}.js" | tee "${OUT}.txt" || true
echo "k6 end:   $(date -u +%FT%TZ)" | tee -a "${OUT}.meta"
