Window: 261s

| Server metric | Value |
|---|---|
| Hikari active (max) | 10 |
| Hikari pending (max) | 39 |
| Hikari acquire timeouts | 0 |
| Hikari avg acquire wait (ms) | 590.6 |
| Hikari avg connection hold (ms) | 293.5 |
| Tomcat busy threads (max) | 49 |
| Heap used (max MB) | 94 |
| Process CPU (max %) | 58 |
| 5xx responses | nan |

| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |
|---|---|---|---|---|
| GET /api/user/me | 663 | 1200 | 5463 | 6976 |
| GET /api/groups/me | 662 | 547 | 3799 | 5569 |
| GET /api/groups/{groupId} | 662 | 664 | 3984 | 6639 |
| GET /api/groups/{groupId}/places | 662 | 308 | 3564 | 5649 |
| GET /api/recommend | 657 | 4608 | 9476 | 11297 |
| POST /api/groups/{groupId}/places | 256 | 4 | 8 | 20 |
| POST /api/auth/login | 51 | 79 | 89 | 235 |
| POST /api/groups | 51 | 7 | 12 | 25 |
| POST /api/auth/signup | 50 | 79 | 89 | 106 |
