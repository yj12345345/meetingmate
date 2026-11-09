-- Synthetic "grown service" data for index experiments. LOCAL STACK ONLY.
--   ./load-test/sql.sh < load-test/seed-scale.sql
-- Rows are marked (emails @seed.local, group names 'seed-%') so they can be removed:
--   DELETE FROM place_candidate WHERE name LIKE 'seed-%'; ... (see bottom)
SET SESSION cte_max_recursion_depth = 1000000;

-- 200k users
INSERT INTO user (created_at, email, nickname, password, provider, provider_id)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 200000)
SELECT NOW(6), CONCAT('seed-', i, '@seed.local'), CONCAT('seed', i), NULL,
       ELT(1 + i % 3, 'LOCAL', 'KAKAO', 'GOOGLE'), CONCAT('seedpid-', i)
FROM n;

-- 50k groups
INSERT INTO meeting_group (name, description, owner_id, invite_code, created_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 50000)
SELECT CONCAT('seed-group-', i), 'seed', i, CONCAT('S', LPAD(CONV(i, 10, 36), 5, '0')), NOW(6)
FROM n;

SET @g0 = (SELECT MIN(id) FROM meeting_group WHERE name LIKE 'seed-group-%');
SET @u0 = (SELECT MIN(id) FROM user WHERE email LIKE '%@seed.local');

-- 500k place candidates (10 per group)
INSERT INTO place_candidate (category, created_by, group_id, lat, lng, name)
WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 499999)
SELECT ELT(1 + i % 3, 'FOOD', 'CAFE', 'ACTIVITY'), @u0 + (i % 200000), @g0 + (i DIV 10),
       37.5 + (i % 1000) / 10000, 127.0 + (i % 1000) / 10000, CONCAT('seed-place-', i)
FROM n;

-- 200k memberships (4 per group, users spread across all seed users)
INSERT INTO group_members (joined_at, user_id, group_id)
WITH RECURSIVE n(i) AS (SELECT 0 UNION ALL SELECT i + 1 FROM n WHERE i < 199999)
SELECT NOW(6), @u0 + ((i * 7919) % 200000), @g0 + (i DIV 4)
FROM n;

ANALYZE TABLE user, meeting_group, place_candidate, group_members;

-- Cleanup (run manually):
-- DELETE FROM group_members WHERE group_id IN (SELECT id FROM meeting_group WHERE name LIKE 'seed-group-%');
-- DELETE FROM place_candidate WHERE name LIKE 'seed-place-%';
-- DELETE FROM meeting_group WHERE name LIKE 'seed-group-%';
-- DELETE FROM user WHERE email LIKE '%@seed.local';
