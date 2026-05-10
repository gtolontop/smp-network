-- PtrFoundation V001 — initial schema.
--
-- Sets the database to WAL mode so future reads can run concurrently with the
-- single writer. Foundation only emits one schema:
--   ptr_audit         — admin-action audit trail (filled by PtrAuditLog).
--
-- Future content layers (block placements, item ownership, boss kills) will
-- add their own tables in V002+ migrations.

PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;

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
