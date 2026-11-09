Window: 264s

| Server metric | Value |
|---|---|
| Hikari active (max) | 1 |
| Hikari pending (max) | 0 |
| Hikari acquire timeouts | 0 |
| Hikari avg acquire wait (ms) | 0.1 |
| Hikari avg connection hold (ms) | 2.2 |
| Tomcat busy threads (max) | 28 |
| Heap used (max MB) | 100 |
| Process CPU (max %) | 81 |
| 5xx responses | nan |

| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |
|---|---|---|---|---|
| GET /api/recommend | 552 | 3370 | 6299 | 7891 |
| POST /api/recommend/selected-routes | 550 | 3343 | 6724 | 8798 |
| POST /api/auth/login | 206 | 105 | 132 | 134 |
| GET /api/user/me | 171 | 11 | 25 | 32 |
| POST /api/auth/signup | 32 | 79 | 89 | 108 |
