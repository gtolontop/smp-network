/**
 * Schema migrations applied at boot. Never rewrite history, only append.
 * Each migration is wrapped in a transaction by the migration runner.
 */
export const MIGRATIONS: Array<{ id: number; sql: string }> = [
  {
    id: 1,
    sql: `
      CREATE TABLE links (
        discord_id TEXT PRIMARY KEY,
        mc_uuid    TEXT NOT NULL UNIQUE,
        mc_name    TEXT NOT NULL,
        linked_at  INTEGER NOT NULL
      );

      CREATE TABLE link_codes (
        code       TEXT PRIMARY KEY,
        discord_id TEXT NOT NULL,
        expires_at INTEGER NOT NULL
      );
      CREATE INDEX idx_link_codes_discord ON link_codes(discord_id);

      CREATE TABLE players (
        mc_uuid        TEXT PRIMARY KEY,
        mc_name        TEXT NOT NULL,
        first_seen     INTEGER NOT NULL,
        last_seen      INTEGER NOT NULL,
        playtime_sec   INTEGER NOT NULL DEFAULT 0,
        deaths         INTEGER NOT NULL DEFAULT 0,
        kills          INTEGER NOT NULL DEFAULT 0,
        blocks_broken  INTEGER NOT NULL DEFAULT 0,
        blocks_placed  INTEGER NOT NULL DEFAULT 0
      );
      CREATE INDEX idx_players_name ON players(mc_name);

      CREATE TABLE events (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        origin     TEXT NOT NULL,
        kind       TEXT NOT NULL,
        mc_uuid    TEXT,
        mc_name    TEXT,
        payload    TEXT NOT NULL,
        created_at INTEGER NOT NULL
      );
      CREATE INDEX idx_events_created ON events(created_at);
      CREATE INDEX idx_events_kind ON events(kind);

      CREATE TABLE mail (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        from_name  TEXT NOT NULL,
        to_uuid    TEXT NOT NULL,
        to_name    TEXT NOT NULL,
        body       TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        delivered  INTEGER NOT NULL DEFAULT 0
      );
      CREATE INDEX idx_mail_to ON mail(to_uuid, delivered);

      CREATE TABLE pending_gives (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        to_uuid    TEXT NOT NULL,
        to_name    TEXT NOT NULL,
        item       TEXT NOT NULL,
        amount     INTEGER NOT NULL,
        from_name  TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        claimed    INTEGER NOT NULL DEFAULT 0
      );
      CREATE INDEX idx_pending_gives_to ON pending_gives(to_uuid, claimed);

      CREATE TABLE game_stats (
        discord_id   TEXT NOT NULL,
        game         TEXT NOT NULL,
        wins         INTEGER NOT NULL DEFAULT 0,
        losses       INTEGER NOT NULL DEFAULT 0,
        draws        INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (discord_id, game)
      );

      CREATE TABLE alerts (
        kind         TEXT PRIMARY KEY,
        fired_at     INTEGER NOT NULL
      );

      CREATE TABLE social_seen (
        source       TEXT NOT NULL,
        item_id      TEXT NOT NULL,
        seen_at      INTEGER NOT NULL,
        PRIMARY KEY (source, item_id)
      );

      CREATE TABLE kv (
        k TEXT PRIMARY KEY,
        v TEXT NOT NULL
      );
    `,
  },
];
