import { existsSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { DatabaseSync } from 'node:sqlite';

import { config } from '../config.js';
import { logger } from '../utils/logger.js';

import { MIGRATIONS } from './schema.js';

const dataDir = resolve(config.meta.dataDir);
if (!existsSync(dataDir)) mkdirSync(dataDir, { recursive: true });

export const db = new DatabaseSync(join(dataDir, 'bot.db'));

db.exec('PRAGMA journal_mode = WAL');
db.exec('PRAGMA foreign_keys = ON');
db.exec('PRAGMA synchronous = NORMAL');

function runMigrations(): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      id INTEGER PRIMARY KEY,
      applied_at INTEGER NOT NULL
    );
  `);
  const rows = db.prepare('SELECT id FROM _migrations').all() as Array<{ id: number }>;
  const applied = new Set(rows.map((r) => r.id));
  for (const m of MIGRATIONS) {
    if (applied.has(m.id)) continue;
    db.exec('BEGIN');
    try {
      db.exec(m.sql);
      db.prepare('INSERT INTO _migrations (id, applied_at) VALUES (?, ?)').run(
        m.id,
        Math.floor(Date.now() / 1000),
      );
      db.exec('COMMIT');
    } catch (err) {
      db.exec('ROLLBACK');
      throw err;
    }
    logger.info({ migration: m.id }, 'applied migration');
  }
}

runMigrations();
