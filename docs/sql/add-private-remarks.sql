-- Apply only when the columns do not already exist. Back up the database first.
-- The default ddl-auto=update configuration adds these columns automatically.
-- 30 Unicode code points are validated by the service, independently of SQL length.
ALTER TABLE user_friend_relation ADD COLUMN remark VARCHAR(120) NULL;
ALTER TABLE conversation_user_state ADD COLUMN group_remark VARCHAR(120) NULL;
