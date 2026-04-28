package fr.smp.logger.db;

import fr.smp.logger.SMPLogger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the long-lived (cross-day) tables and indexes. Daily event partitions
 * are created on demand by {@link PartitionManager}; we never put them here.
 *
 * Schema is intentionally compact:
 *   - INTEGER ids replace strings everywhere via dict tables
 *   - timestamps in events_* are SECONDS-OF-DAY (4 bytes) not millis (8 bytes)
 *   - precious item NBT is deduped by 16-byte truncated SHA-256
 */
final class SchemaInit {

    private final SMPLogger plugin;
    private final String url;

    SchemaInit(SMPLogger plugin, String url) {
        this.plugin = plugin;
        this.url = url;
    }

    void run() {
        String[] stmts = {
            // ---------- Dictionary tables ----------
            """
            CREATE TABLE IF NOT EXISTS dict_players (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              uuid BLOB NOT NULL UNIQUE,           -- 16 raw bytes, not text
              last_name TEXT NOT NULL,
              first_seen INTEGER NOT NULL,
              last_seen INTEGER NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_dict_players_name ON dict_players(last_name COLLATE NOCASE)",

            """
            CREATE TABLE IF NOT EXISTS dict_materials (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS dict_worlds (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS dict_strings (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              hash INTEGER NOT NULL,               -- xxhash-ish, fast lookup
              content TEXT NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_dict_strings_hash ON dict_strings(hash)",

            // ---------- Precious-item dedup ----------
            """
            CREATE TABLE IF NOT EXISTS precious_items (
              hash BLOB PRIMARY KEY,               -- 16-byte SHA-256 prefix of canonical bytes
              kind TEXT NOT NULL,                  -- BOOK/SHULKER/HEAD/NAMED/...
              material_id INTEGER NOT NULL,
              summary TEXT,                        -- short human summary (book title, head owner, ...)
              nbt_zlib BLOB NOT NULL,              -- DEFLATEd canonical Bukkit serialization
              nbt_size INTEGER NOT NULL,
              first_seen INTEGER NOT NULL,
              ref_count INTEGER NOT NULL DEFAULT 0
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_precious_kind ON precious_items(kind)",
            "CREATE INDEX IF NOT EXISTS idx_precious_mat ON precious_items(material_id)",

            // ---------- Cross-day session table ----------
            """
            CREATE TABLE IF NOT EXISTS sessions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_id INTEGER NOT NULL,
              ip TEXT NOT NULL,
              brand TEXT,
              locale TEXT,
              version TEXT,
              joined_at INTEGER NOT NULL,
              left_at INTEGER NOT NULL DEFAULT 0,
              last_world_id INTEGER,
              last_x INTEGER, last_y INTEGER, last_z INTEGER,
              kicked INTEGER NOT NULL DEFAULT 0,
              quit_reason TEXT
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_sessions_player ON sessions(player_id, joined_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_ip ON sessions(ip, joined_at DESC)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_open ON sessions(left_at) WHERE left_at = 0",

            // ---------- Cross-day trades table ----------
            """
            CREATE TABLE IF NOT EXISTS trades (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              t INTEGER NOT NULL,                  -- epoch millis
              from_player INTEGER NOT NULL,
              to_player INTEGER NOT NULL,
              method INTEGER NOT NULL,             -- Action.id (TRADE_*)
              material_id INTEGER NOT NULL,
              amount INTEGER NOT NULL,
              item_hash BLOB,                      -- NULL if non-precious
              world_id INTEGER NOT NULL,
              x INTEGER, y INTEGER, z INTEGER
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_trades_from ON trades(from_player, t DESC)",
            "CREATE INDEX IF NOT EXISTS idx_trades_to ON trades(to_player, t DESC)",
            "CREATE INDEX IF NOT EXISTS idx_trades_pair ON trades(from_player, to_player, t DESC)",
            "CREATE INDEX IF NOT EXISTS idx_trades_t ON trades(t)",

            // ---------- Cross-day rare-resource table ----------
            """
            CREATE TABLE IF NOT EXISTS rare_resources (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              t INTEGER NOT NULL,                  -- epoch millis
              action INTEGER NOT NULL,             -- BREAK/PLACE/PICKUP/DROP/SPAWNER_TYPE_CHANGE
              player_id INTEGER NOT NULL,
              world_id INTEGER NOT NULL,
              x INTEGER, y INTEGER, z INTEGER,
              material_id INTEGER NOT NULL,
              amount INTEGER NOT NULL,
              tool_material_id INTEGER,
              fortune INTEGER NOT NULL DEFAULT 0,
              silktouch INTEGER NOT NULL DEFAULT 0,
              biome TEXT,
              spawner_type TEXT,                   -- only for spawner ops
              extra TEXT
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_rare_player ON rare_resources(player_id, t DESC)",
            "CREATE INDEX IF NOT EXISTS idx_rare_mat ON rare_resources(material_id, t DESC)",
            "CREATE INDEX IF NOT EXISTS idx_rare_t ON rare_resources(t)",

            // ---------- Partition registry ----------
            """
            CREATE TABLE IF NOT EXISTS partitions (
              date TEXT PRIMARY KEY,               -- YYYYMMDD
              table_name TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              row_count INTEGER NOT NULL DEFAULT 0
            )
            """,

            // ---------- Inventory snapshot mirror (migrated from core) ----------
            """
            CREATE TABLE IF NOT EXISTS inv_snapshots (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              player_id INTEGER NOT NULL,
              source TEXT NOT NULL,                -- periodic | quit | manual | preapply | shutdown
              server TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              yaml_zlib BLOB NOT NULL              -- compressed YAML
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_inv_snap_player ON inv_snapshots(player_id, created_at DESC)",

            // ---------- Logger metadata ----------
            """
            CREATE TABLE IF NOT EXISTS logger_meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            )
            """
        };

        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            for (String sql : stmts) s.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Schema init failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
