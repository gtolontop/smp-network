-- PtrFoundation V001 — initial schema.
--
-- Foundation only emits one schema:
--   ptr_audit         — admin-action audit trail (filled by PtrAuditLog).
--
-- PRAGMAs (journal_mode = WAL, synchronous = NORMAL, foreign_keys = ON) are
-- applied per connection in PtrDatabase#open via Hikari's connectionInitSql.
-- They cannot live in the migration: Flyway refuses to mix non-transactional
-- PRAGMA statements with the transactional CREATE TABLEs below.
--
-- Future content layers (block placements, item ownership, mob kills) will
-- add their own tables in V002+ migrations.

CREATE TABLE IF NOT EXISTS ptr_audit (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    ts_millis    INTEGER NOT NULL,
    actor_uuid   TEXT,
    op_type      TEXT NOT NULL,
    target_id    TEXT,
    payload_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_ptr_audit_ts ON ptr_audit (ts_millis DESC);
CREATE INDEX IF NOT EXISTS idx_ptr_audit_actor ON ptr_audit (actor_uuid);
CREATE INDEX IF NOT EXISTS idx_ptr_audit_op ON ptr_audit (op_type);
