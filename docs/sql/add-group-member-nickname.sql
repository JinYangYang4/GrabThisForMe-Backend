-- Back up first and check that nickname does not already exist.
-- With Hibernate ddl-auto=update the application adds this column automatically.
ALTER TABLE conversation_participant ADD COLUMN nickname VARCHAR(120) NULL;
