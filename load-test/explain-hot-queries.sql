-- The 3 hot lookups behind GET /places, GET /groups/me and POST /auth/login.
EXPLAIN ANALYZE SELECT * FROM place_candidate WHERE group_id = 66;
EXPLAIN ANALYZE SELECT * FROM group_members WHERE user_id = 1;
EXPLAIN ANALYZE SELECT * FROM user WHERE email = 'probe@test.local' AND provider = 'LOCAL';
