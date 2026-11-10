-- READ-ONLY. Run on the production MySQL to decide how to retire the removed
-- group / place / availability feature. Only counts rows; prints no personal data.
--   mysql -u <user> -p meetingmate < legacy-group-tables-check.sql
SELECT 'meeting_group'      AS tbl, COUNT(*) AS rows_ FROM meeting_group
UNION ALL SELECT 'group_members',      COUNT(*) FROM group_members
UNION ALL SELECT 'place_candidate',    COUNT(*) FROM place_candidate
UNION ALL SELECT 'place_vote',         COUNT(*) FROM place_vote
UNION ALL SELECT 'availabilities',     COUNT(*) FROM availabilities
UNION ALL SELECT 'availability_slots', COUNT(*) FROM availability_slots;
