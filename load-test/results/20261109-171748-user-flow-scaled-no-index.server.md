Window: 262s

| Server metric | Value |
|---|---|
| Hikari active (max) | 10 |
| Hikari pending (max) | 0 |
| Hikari acquire timeouts | 0 |
| Hikari avg acquire wait (ms) | 0.5 |
| Hikari avg connection hold (ms) | 47.6 |
| Tomcat busy threads (max) | 28 |
| Heap used (max MB) | 98 |
| Process CPU (max %) | 41 |
| 5xx responses | nan |

| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |
|---|---|---|---|---|
| GET /api/groups/me | 990 | 49 | 135 | 252 |
| GET /api/groups/{groupId} | 990 | 5 | 24 | 80 |
| GET /api/groups/{groupId}/places | 990 | 144 | 410 | 502 |
| GET /api/user/me | 990 | 4 | 21 | 73 |
| GET /api/recommend | 989 | 3319 | 6551 | 8294 |
| POST /api/groups/{groupId}/places | 267 | 4 | 8 | 11 |
| POST /api/auth/login | 53 | 112 | 134 | 167 |
| POST /api/groups | 53 | 7 | 13 | 15 |
| POST /api/auth/signup | 52 | 105 | 115 | 130 |
