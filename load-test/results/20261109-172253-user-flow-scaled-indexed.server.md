Window: 267s

| Server metric | Value |
|---|---|
| Hikari active (max) | 10 |
| Hikari pending (max) | 16 |
| Hikari acquire timeouts | 0 |
| Hikari avg acquire wait (ms) | 9.5 |
| Hikari avg connection hold (ms) | 38.6 |
| Tomcat busy threads (max) | 37 |
| Heap used (max MB) | 95 |
| Process CPU (max %) | 55 |
| 5xx responses | nan |

| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |
|---|---|---|---|---|
| GET /api/groups/{groupId} | 988 | 8 | 84 | 921 |
| GET /api/groups/{groupId}/places | 988 | 7 | 32 | 901 |
| GET /api/groups/me | 988 | 8 | 66 | 968 |
| GET /api/user/me | 988 | 7 | 92 | 989 |
| GET /api/recommend | 984 | 3523 | 6770 | 8486 |
| POST /api/groups/{groupId}/places | 298 | 9 | 21 | 34 |
| POST /api/auth/login | 60 | 99 | 122 | 144 |
| POST /api/auth/signup | 60 | 105 | 135 | 152 |
| POST /api/groups | 60 | 12 | 37 | 77 |
