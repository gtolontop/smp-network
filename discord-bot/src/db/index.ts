import { existsSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

import Database from 'better-sqlite3';

import { config } from '../config.js';
import { logger } from '../utils/logger.js';

import { MIGRATIONS } from './schema.js';

const dataDir = resolve(config.meta.dataDir);
if (!existsSync(dataDir)) mkdirSync(dataDir, { recursive: true });

export const db = new Database(join(dataDir, 'bot.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.pragma('synchronous = NORMAL');

function runMigrations(): void {
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      id INTEGER PRIMARY KEY,
      applied_at INTEGER NOT NULL
    );
  `);
  const applied = new Set(
    db
      .prepare('SELECT id FROM _migrations')
      .all()
      .map((r) => (r as { id: number }).id),
  );
  for (const m of MIGRATIONS) {
    if (applied.has(m.id)) continue;
    const tx = db.transaction(() => {
      db.exec(m.sql);
      db.prepare('INSERT INTO _migrations (id, applied_at) VALUES (?, ?)').run(
        m.id,
        Math.floor(Date.now() / 1000),
      );
    });
    tx();
    logger.info({ migration: m.id }, 'applied migration');
  }
}

runMigrations();
