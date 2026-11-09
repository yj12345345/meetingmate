#!/usr/bin/env bash
# Rebuild the backend jar and restart only the backend container (local stack).
set -euo pipefail
cd "$(dirname "$0")/.."
(cd backend && ./gradlew bootJar -x test -q)
docker compose -f docker-compose.observability.yml --env-file .env.observability up -d --force-recreate backend
curl -s --retry 40 --retry-delay 3 --retry-all-errors -o /dev/null -w "backend readiness: %{http_code}\n" \
  http://127.0.0.1:8081/actuator/health/readiness
