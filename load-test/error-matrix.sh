#!/usr/bin/env bash
# Print the HTTP status the backend returns for common client-error cases.
# LOCAL STACK ONLY.   ./load-test/error-matrix.sh
set -uo pipefail
B=${BASE_URL:-http://127.0.0.1:8080}
EMAIL=errmatrix@test.local; PW=errmatrix-pass-1
curl -s -o /dev/null "$B/api/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PW\",\"nickname\":\"errm\"}"
TOK=$(curl -s "$B/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$PW\"}" \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["accessToken"])')
AUTH="Authorization: Bearer $TOK"

row() { # label, expected, curl args...
  local label=$1 expected=$2; shift 2
  local out code ctype
  out=$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$@")
  code=${out%% *}; ctype=${out#* }
  printf '| %s | %s | %s | %s |\n' "$label" "$expected" "$code" "${ctype:-"-"}"
}

echo '| Case | Expected | Actual | Content-Type |'
echo '|---|---|---|---|'
row "Unknown path (logged in)"              404 "$B/api/does-not-exist" -H "$AUTH"
row "Unknown non-API path"                  404 "$B/nope"
row "Wrong HTTP method (DELETE /api/recommend)" 405 -X DELETE "$B/api/recommend?keyword=x" -H "$AUTH"
row "Malformed JSON body"                   400 -X POST "$B/api/auth/login" -H 'Content-Type: application/json' -d '{bad json'
row "Unsupported Content-Type"              415 -X POST "$B/api/auth/login" -H 'Content-Type: text/plain' -d 'x'
row "Missing required param (keyword)"      400 "$B/api/recommend" -H "$AUTH"
row "Validation error (short password)"     400 -X POST "$B/api/auth/login" -H 'Content-Type: application/json' -d '{"email":"a@b.c","password":"x"}'
row "No token on protected API"             401 "$B/api/user/me"
row "Invalid/expired token"                 401 "$B/api/user/me" -H "Authorization: Bearer invalid.token.value"
row "Valid request"                         200 "$B/api/user/me" -H "$AUTH"
