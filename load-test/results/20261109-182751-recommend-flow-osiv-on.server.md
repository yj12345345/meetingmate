Window: 265s

| Server metric | Value |
|---|---|
| Hikari active (max) | 10 |
| Hikari pending (max) | 12 |
| Hikari acquire timeouts | 0 |
| Hikari avg acquire wait (ms) | 179.3 |
| Hikari avg connection hold (ms) | 820.9 |
| Tomcat busy threads (max) | 29 |
| Heap used (max MB) | 98 |
| Process CPU (max %) | 77 |
| 5xx responses | nan |

| Endpoint | Requests | Server p50 (ms) | Server p95 (ms) | Server p99 (ms) |
|---|---|---|---|---|
| GET /api/recommend | 546 | 3404 | 6670 | 8473 |
| POST /api/recommend/selected-routes | 545 | 3375 | 6514 | 8189 |
| POST /api/auth/login | 216 | 116 | 991 | 1768 |
| GET /api/user/me | 162 | 31 | 1432 | 1861 |
| POST /api/auth/signup | 51 | 79 | 97 | 123 |
