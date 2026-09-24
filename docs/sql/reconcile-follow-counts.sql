-- One-time baseline repair before enabling incremental counters on an existing database.
-- Run in a maintenance window with follow writes paused and in one transaction.
-- Never execute this aggregation from a request handler or on every startup.
INSERT INTO user_statistics (user_id, like_count, fan_count, follow_count)
SELECT p.user_id, 0, 0, 0 FROM user_profile p
WHERE NOT EXISTS (SELECT 1 FROM user_statistics s WHERE s.user_id = p.user_id);
UPDATE user_statistics SET
    fan_count = (SELECT COUNT(*) FROM user_follow f WHERE f.target_id = user_statistics.user_id),
    follow_count = (SELECT COUNT(*) FROM user_follow f WHERE f.user_id = user_statistics.user_id);
