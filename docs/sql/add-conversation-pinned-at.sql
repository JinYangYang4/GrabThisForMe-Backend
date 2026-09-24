-- Apply once before deploying with Hibernate schema validation.
-- Skip if Hibernate ddl-auto=update has already added this nullable column.
ALTER TABLE conversation_user_state ADD COLUMN pinned_at BIGINT NULL;
