#!/usr/bin/env bash
# Count SQL statements executed by ONE HTTP request (local stack only).
#   ./load-test/count-queries.sh <TOKEN> <path>
set -euo pipefail
TOKEN=$1; P=$2
sqlx() { docker exec -i meetingmate-obs-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B 2>/dev/null' <<<"$1"; }
sqlx "TRUNCATE performance_schema.events_statements_summary_by_digest;"
curl -s -o /dev/null -w "HTTP %{http_code}  " "http://127.0.0.1:8080$P" -H "Authorization: Bearer $TOKEN"
sqlx "SELECT CONCAT('queries=', COALESCE(SUM(COUNT_STAR),0)) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='meetingmate' AND DIGEST_TEXT LIKE 'SELECT%';"
sqlx "SELECT COUNT_STAR, LEFT(DIGEST_TEXT,110) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='meetingmate' AND DIGEST_TEXT LIKE 'SELECT%' ORDER BY COUNT_STAR DESC;"
