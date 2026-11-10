#!/usr/bin/env bash
# Verify the retry policy against a FLAKY OpenAI (local stack only):
#   mode=flaky      every other call returns 503 (transient blip)  -> retry should hide it
#   mode=sustained  every call returns 503 (real outage)           -> retry must stop once the breaker opens
#   ./load-test/retry-check.sh flaky|sustained
set -euo pipefail
cd "$(dirname "$0")/.."
MODE=${1:-flaky}; N=${N:-20}
ADMIN=http://127.0.0.1:8089/__admin; B=http://127.0.0.1:8080
P=/__admin/mappings

ok_body=$(python3 -c 'import json;print(json.dumps(json.load(open("observability/openai-mock/mappings/chat-completions.json"))["response"]["jsonBody"]))')
cleanup() { for id in a1 a2; do curl -s -o /dev/null -X DELETE "$ADMIN/mappings/7a1f0c9e-0000-4000-8000-0000000000$id"; done; curl -s -o /dev/null -X POST "$ADMIN/scenarios/reset"; }
trap cleanup EXIT
cleanup

if [ "$MODE" = flaky ]; then
  curl -s -o /dev/null "$ADMIN/mappings" -H 'Content-Type: application/json' -d '{"id":"7a1f0c9e-0000-4000-8000-0000000000a1","priority":1,
    "scenarioName":"flaky","requiredScenarioState":"Started","newScenarioState":"ok",
    "request":{"method":"POST","urlPath":"/v1/chat/completions"},"response":{"status":503}}'
  curl -s -o /dev/null "$ADMIN/mappings" -H 'Content-Type: application/json' -d "{\"id\":\"7a1f0c9e-0000-4000-8000-0000000000a2\",\"priority\":1,
    \"scenarioName\":\"flaky\",\"requiredScenarioState\":\"ok\",\"newScenarioState\":\"Started\",
    \"request\":{\"method\":\"POST\",\"urlPath\":\"/v1/chat/completions\"},
    \"response\":{\"status\":200,\"headers\":{\"Content-Type\":\"application/json\"},\"jsonBody\":$ok_body}}"
else
  curl -s -o /dev/null "$ADMIN/mappings" -H 'Content-Type: application/json' -d '{"id":"7a1f0c9e-0000-4000-8000-0000000000a1","priority":1,
    "request":{"method":"POST","urlPath":"/v1/chat/completions"},"response":{"status":503}}'
fi

metric() { curl -s http://127.0.0.1:8081/actuator/prometheus | awk -v k="$1" '$0 ~ "^resilience4j_retry_calls_total" && $0 ~ "kind=\""k"\"" {print int($NF)}'; }
before_ok=$(metric successful_with_retry); before_fail=$(metric failed_with_retry)
mock_before=$(curl -s "$ADMIN/requests" 2>/dev/null | python3 -c 'import json,sys;print(json.load(sys.stdin).get("meta",{}).get("total",0))' 2>/dev/null || echo "?")

curl -s -o /dev/null $B/api/auth/signup -H 'Content-Type: application/json' -d '{"email":"retry@test.local","password":"retry-pass-1","nickname":"retry"}'
TOK=$(curl -s $B/api/auth/login -H 'Content-Type: application/json' -d '{"email":"retry@test.local","password":"retry-pass-1"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["accessToken"])')

srcs=""
for i in $(seq 1 "$N"); do
  s=$(curl -s -G $B/api/recommend --data-urlencode keyword=홍대 -H "Authorization: Bearer $TOK" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["source"])')
  srcs="$srcs$s\n"
done
echo "mode=$MODE  requests=$N"
printf "$srcs" | sort | uniq -c | sed 's/^ */  source /'
echo "  retry successful_with_retry +$(( $(metric successful_with_retry) - before_ok ))   failed_with_retry +$(( $(metric failed_with_retry) - before_fail ))"
curl -s http://127.0.0.1:8081/actuator/prometheus | grep -E '^resilience4j_circuitbreaker_state\{.*name="openai".*\} 1' | grep -oE 'state="[a-z_]+"'
