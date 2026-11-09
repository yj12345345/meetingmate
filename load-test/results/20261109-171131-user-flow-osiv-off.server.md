Window: 259s

| Server metric | Value |
|---|---|
| Hikari active (max) | 1 |
| Hikari pending (max) | 0 |
| Hikari acquire timeouts | 0 |
| Hikari avg acquire wait (ms) | 0.0 |
| Hikari avg connection hold (ms) | 1.0 |
| Tomcat busy threads (max) | 29 |
| Heap used (max MB) | 92 |
| Process CPU (max %) | 58 |
| 5xx responses | nan |

| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |
|---|---|---|---|---|
| GET /api/groups/{groupId} | 1025 | 8 | 22 | 33 |
| GET /api/groups/{groupId}/places | 1025 | 6 | 14 | 22 |
| GET /api/groups/me | 1024 | 7 | 19 | 31 |
| GET /api/user/me | 1024 | 7 | 17 | 27 |
| GET /api/recommend | 1023 | 3147 | 6309 | 8254 |
| POST /api/groups/{groupId}/places | 223 | 4 | 8 | 14 |
| POST /api/auth/login | 45 | 78 | 88 | 89 |
| POST /api/groups | 45 | 6 | 10 | 13 |
| POST /api/auth/signup | 44 | 79 | 89 | 96 |
