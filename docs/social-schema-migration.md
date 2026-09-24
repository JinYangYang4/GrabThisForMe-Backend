# Social schema v1: migration and rollout

The application now uses shared conversation IDs, direct user pairs, one participant table for groups and DMs, and separate friend requests. The source changes do **not** migrate the existing application database automatically.

## Current local audit

The configured file was inspected read-only on 2026-09-18: 25 direct conversations, 50 participants, 50 personal states, 103 messages, 1 group / 3 legacy group members, 48 accepted directional friendships. The group had no conversation. No duplicate direct pairs or invalid direct member counts were found. Actual message indexes only contained the primary key, despite newer entity declarations.

An SQL snapshot was restored to an isolated H2 database and migrated successfully. The expected result is 26 conversations, 25 direct mappings, 53 participants, 53 states, 103 unchanged messages, 1 group linked to its conversation, 48 friendships. Old states must match exactly; new group states start unread=0. Group member roles/times must match legacy records.

The original configured local H2 file was subsequently migrated after filesystem approval. A single exclusive connection exported a fresh full backup before applying v1. Verification compared every message value (SHA-256), all original personal state values and all group member roles/times; all matched. The counts above are now actual results. The fresh SQL backup is in the Android workspace at `.tmp/social-refactor/original-pre-migration-20260918.sql`; source backups are at `.tmp/social-refactor/backend-before-apply/`.

## Approval boundary

Do not run the following against the original database until the migration result and restoration plan have been reviewed. Code tests and isolated rehearsals are safe to run first. Do not drop the original database or silently merge duplicate conversations.

## Offline migration

1. Stop every writer using this database, including the old backend. Record the current source/artifact and database file paths.
2. Create a fresh consistent H2 `org.h2.tools.Script` export (credentials from deployment configuration, never commit the export). The rehearsal export is not a substitute for a fresh cutover backup.
3. Restore that export with `org.h2.tools.RunScript` into a **new** temporary database. Build with `mvnw.cmd -DskipTests package`.
4. Run the migration against the restored copy first:

   ```text
   java -cp "target/classes;<path-to-h2-2.3.232.jar>" com.study.grabthisforme.migration.SocialSchemaMigration "jdbc:h2:file:<copy-path>;MODE=MySQL;IFEXISTS=TRUE" audit
   java -cp "target/classes;<path-to-h2-2.3.232.jar>" com.study.grabthisforme.migration.SocialSchemaMigration "jdbc:h2:file:<copy-path>;MODE=MySQL;IFEXISTS=TRUE" apply
   ```

   The tool accepts `DB_USER` and `DB_PASSWORD` environment variables; defaults are the current local sa/empty-password setup. `audit` reports counts, never message contents or credentials. Any anomaly stops before writes and needs an explicit repair mapping.
5. Verify message count and content, old per-user unread/hidden/read-time rows, friend rows, group roles/times and unique identities against the backup. Start the new backend on the copy with chat cleanup disabled for validation. Keep retention disabled until data comparison completes.
6. Only after review, repeat audit/apply on the stopped original database (or switch configuration to the verified new file). Deploy the compatible Android client with Room 49→50 alongside the backend contract.

H2 DDL commits implicitly: if apply fails partway through, **restore from the export into another clean file and retry**; do not assume transaction rollback undoes schema changes. A fully completed version is recorded in `social_schema_version` and subsequent apply is a no-op.

## Restore

Stop the new backend before restoration. Restore the pre-cutover SQL export to a new H2 file, point the prior backend artifact/configuration at that file, then verify the pre-cutover counts. Preserve the failed/new database separately for diagnosis. Do not overwrite a database file while any process has it open. Writes accepted after cutover require an explicit reconciliation before rollback; a stale snapshot would otherwise lose them.

## Retained legacy data

Application entities no longer use `user_group_relation`, `conversation.target_id`, relationship string IDs, or friendship status. Migration retains those old columns/tables and `social_v1_backup_*` snapshots for comparison; it does not delete messages or old personal states. Rejected request direction/history that the old schema cannot establish remains in the backup, not invented in `friend_request`.

Physical removal of legacy tables/columns is a later reviewed cleanup after successful rollout. No original database cleanup is included in this code change.

## Constraints and endpoints

Private scalar-ID entities use database foreign keys established by `SocialSchemaConstraints` after schema initialization and before seed data. They deliberately avoid JPA lazy object graphs; queries batch by IDs. Composite keys identify participants, user states and accepted friendships. Direct pairs have unique `(user_low_id,user_high_id)` and low<high. Group `conversation_id` is unique. Pending requests have a unique nullable pair key, cleared on handling.

Both group creation endpoints require `Idempotency-Key`; keys are scoped to creator and validated against request content. Request acceptance/rejection uses `/api/social/friend-requests/{requestId}/accept|reject`, not the peer user ID. Mixed request paging retains OR and uses `(createdAt,requestId)`. Message paging accepts `(beforeTime,beforeId)`.

## Checks

`mvnw.cmd test` includes SocialConversationTests and SocialSchemaMigrationTests. The Android repository provides `scripts/verify_social_room_migration.py` and its own Kotlin build/unit tests. No real device was connected during the initial refactor verification.

Verification results: six new business tests, two migration tests and the opt-in restored-database startup test passed. The complete 82-test backend suite has one pre-existing failure at `VideoMediaTests.java:87` (mixed photo/video posting expected to throw); the same failure was reproduced on the original unmodified backend. Android compilation and all 82 unit tests passed, as did the SQLite migration/schema checks. `MigratedSocialDatabaseTests` is opt-in and must only be pointed at an isolated restored database via `-Dsocial.rehearsal.url=...`.
