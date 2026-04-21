import { randomBytes } from 'node:crypto';

import { db } from './index.js';

// ---------- links -----------------------------------------------------------

export interface Link {
  discordId: string;
  mcUuid: string;
  mcName: string;
  linkedAt: number;
}

const qLinkByDiscord = db.prepare<[string]>(
  'SELECT discord_id as discordId, mc_uuid as mcUuid, mc_name as mcName, linked_at as linkedAt FROM links WHERE discord_id = ?',
);
const qLinkByUuid = db.prepare<[string]>(
  'SELECT discord_id as discordId, mc_uuid as mcUuid, mc_name as mcName, linked_at as linkedAt FROM links WHERE mc_uuid = ?',
);
const qLinkByName = db.prepare<[string]>(
  'SELECT discord_id as discordId, mc_uuid as mcUuid, mc_name as mcName, linked_at as linkedAt FROM links WHERE mc_name = ? COLLATE NOCASE',
);

export function linkFor(discordId: string): Link | undefined {
  return qLinkByDiscord.get(discordId) as Link | undefined;
}
export function linkForUuid(uuid: string): Link | undefined {
  return qLinkByUuid.get(uuid) as Link | undefined;
}
export function linkForName(name: string): Link | undefined {
  return qLinkByName.get(name) as Link | undefined;
}

export function unlink(discordId: string): void {
  db.prepare('DELETE FROM links WHERE discord_id = ?').run(discordId);
}

export function completeLink(code: string, uuid: string, name: string): Link | undefined {
  const row = db
    .prepare('SELECT discord_id as discordId, expires_at as expiresAt FROM link_codes WHERE code = ?')
    .get(code) as { discordId: string; expiresAt: number } | undefined;
  if (!row) return;
  if (row.expiresAt < Math.floor(Date.now() / 1000)) {
    db.prepare('DELETE FROM link_codes WHERE code = ?').run(code);
    return;
  }
  const tx = db.transaction(() => {
    db.prepare('DELETE FROM link_codes WHERE code = ?').run(code);
    db.prepare('DELETE FROM links WHERE mc_uuid = ? OR discord_id = ?').run(uuid, row.discordId);
    db.prepare('INSERT INTO links (discord_id, mc_uuid, mc_name, linked_at) VALUES (?, ?, ?, ?)').run(
      row.discordId,
      uuid,
      name,
      Math.floor(Date.now() / 1000),
    );
  });
  tx();
  return linkFor(row.discordId);
}

export function createLinkCode(discordId: string, ttlSec = 600): string {
  const code = randomBytes(3).toString('hex').toUpperCase();
  db.prepare('DELETE FROM link_codes WHERE discord_id = ?').run(discordId);
  db.prepare('INSERT INTO link_codes (code, discord_id, expires_at) VALUES (?, ?, ?)').run(
    code,
    discordId,
    Math.floor(Date.now() / 1000) + ttlSec,
  );
  return code;
}

// ---------- players ---------------------------------------------------------

export interface PlayerRow {
  mcUuid: string;
  mcName: string;
  firstSeen: number;
  lastSeen: number;
  playtimeSec: number;
  deaths: number;
  kills: number;
  blocksBroken: number;
  blocksPlaced: number;
}

const qPlayer = db.prepare<[string]>(
  `SELECT mc_uuid as mcUuid, mc_name as mcName, first_seen as firstSeen, last_seen as lastSeen,
          playtime_sec as playtimeSec, deaths, kills, blocks_broken as blocksBroken, blocks_placed as blocksPlaced
     FROM players WHERE mc_uuid = ?`,
);
const qPlayerByName = db.prepare<[string]>(
  `SELECT mc_uuid as mcUuid, mc_name as mcName, first_seen as firstSeen, last_seen as lastSeen,
          playtime_sec as playtimeSec, deaths, kills, blocks_broken as blocksBroken, blocks_placed as blocksPlaced
     FROM players WHERE mc_name = ? COLLATE NOCASE`,
);

export function player(uuid: string): PlayerRow | undefined {
  return qPlayer.get(uuid) as PlayerRow | undefined;
}
export function playerByName(name: string): PlayerRow | undefined {
  return qPlayerByName.get(name) as PlayerRow | undefined;
}

export function upsertPlayer(uuid: string, name: string, now = Math.floor(Date.now() / 1000)): void {
  db.prepare(
    `INSERT INTO players (mc_uuid, mc_name, first_seen, last_seen)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(mc_uuid) DO UPDATE SET mc_name = excluded.mc_name, last_seen = excluded.last_seen`,
  ).run(uuid, name, now, now);
}

export function addPlaytime(uuid: string, secs: number): void {
  db.prepare('UPDATE players SET playtime_sec = playtime_sec + ? WHERE mc_uuid = ?').run(secs, uuid);
}

export function bumpStat(uuid: string, key: 'deaths' | 'kills' | 'blocksBroken' | 'blocksPlaced'): void {
  const col = key === 'blocksBroken' ? 'blocks_broken' : key === 'blocksPlaced' ? 'blocks_placed' : key;
  db.prepare(`UPDATE players SET ${col} = ${col} + 1 WHERE mc_uuid = ?`).run(uuid);
}

export function topPlayers(
  field: 'playtime_sec' | 'deaths' | 'kills' | 'blocks_broken' | 'blocks_placed',
  limit = 10,
): PlayerRow[] {
  return db
    .prepare(
      `SELECT mc_uuid as mcUuid, mc_name as mcName, first_seen as firstSeen, last_seen as lastSeen,
              playtime_sec as playtimeSec, deaths, kills, blocks_broken as blocksBroken, blocks_placed as blocksPlaced
         FROM players ORDER BY ${field} DESC LIMIT ?`,
    )
    .all(limit) as PlayerRow[];
}

// ---------- events ----------------------------------------------------------

export function recordEvent(
  origin: string,
  kind: string,
  mcUuid: string | null,
  mcName: string | null,
  payload: unknown,
): void {
  db.prepare(
    'INSERT INTO events (origin, kind, mc_uuid, mc_name, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)',
  ).run(origin, kind, mcUuid, mcName, JSON.stringify(payload ?? {}), Math.floor(Date.now() / 1000));
}

export interface EventRow {
  id: number;
  origin: string;
  kind: string;
  mcUuid: string | null;
  mcName: string | null;
  payload: string;
  createdAt: number;
}

export function eventsSince(since: number, kind?: string, limit = 500): EventRow[] {
  const sql = kind
    ? `SELECT id, origin, kind, mc_uuid as mcUuid, mc_name as mcName, payload, created_at as createdAt
         FROM events WHERE created_at >= ? AND kind = ? ORDER BY id DESC LIMIT ?`
    : `SELECT id, origin, kind, mc_uuid as mcUuid, mc_name as mcName, payload, created_at as createdAt
         FROM events WHERE created_at >= ? ORDER BY id DESC LIMIT ?`;
  return (kind ? db.prepare(sql).all(since, kind, limit) : db.prepare(sql).all(since, limit)) as EventRow[];
}

// ---------- mail / gives ----------------------------------------------------

export function queueMail(fromName: string, toUuid: string, toName: string, body: string): void {
  db.prepare(
    'INSERT INTO mail (from_name, to_uuid, to_name, body, created_at) VALUES (?, ?, ?, ?, ?)',
  ).run(fromName, toUuid, toName, body, Math.floor(Date.now() / 1000));
}

export interface MailRow {
  id: number;
  fromName: string;
  toUuid: string;
  toName: string;
  body: string;
  createdAt: number;
}

export function pendingMailFor(uuid: string): MailRow[] {
  return db
    .prepare(
      'SELECT id, from_name as fromName, to_uuid as toUuid, to_name as toName, body, created_at as createdAt FROM mail WHERE to_uuid = ? AND delivered = 0 ORDER BY id ASC',
    )
    .all(uuid) as MailRow[];
}

export function markMailDelivered(ids: number[]): void {
  if (!ids.length) return;
  const placeholders = ids.map(() => '?').join(',');
  db.prepare(`UPDATE mail SET delivered = 1 WHERE id IN (${placeholders})`).run(...ids);
}

export function queueGive(
  fromName: string,
  toUuid: string,
  toName: string,
  item: string,
  amount: number,
): void {
  db.prepare(
    'INSERT INTO pending_gives (to_uuid, to_name, item, amount, from_name, created_at) VALUES (?, ?, ?, ?, ?, ?)',
  ).run(toUuid, toName, item, amount, fromName, Math.floor(Date.now() / 1000));
}

export interface GiveRow {
  id: number;
  toUuid: string;
  toName: string;
  item: string;
  amount: number;
  fromName: string;
  createdAt: number;
}

export function pendingGivesFor(uuid: string): GiveRow[] {
  return db
    .prepare(
      'SELECT id, to_uuid as toUuid, to_name as toName, item, amount, from_name as fromName, created_at as createdAt FROM pending_gives WHERE to_uuid = ? AND claimed = 0 ORDER BY id ASC',
    )
    .all(uuid) as GiveRow[];
}

export function markGivesClaimed(ids: number[]): void {
  if (!ids.length) return;
  const placeholders = ids.map(() => '?').join(',');
  db.prepare(`UPDATE pending_gives SET claimed = 1 WHERE id IN (${placeholders})`).run(...ids);
}

// ---------- alerts (cooldown store) -----------------------------------------

export function shouldFireAlert(kind: string, cooldownSec: number): boolean {
  const now = Math.floor(Date.now() / 1000);
  const row = db.prepare('SELECT fired_at as firedAt FROM alerts WHERE kind = ?').get(kind) as
    | { firedAt: number }
    | undefined;
  if (!row || now - row.firedAt >= cooldownSec) {
    db.prepare(
      'INSERT INTO alerts (kind, fired_at) VALUES (?, ?) ON CONFLICT(kind) DO UPDATE SET fired_at = excluded.fired_at',
    ).run(kind, now);
    return true;
  }
  return false;
}

// ---------- game stats ------------------------------------------------------

export function recordGameResult(
  discordId: string,
  game: string,
  outcome: 'win' | 'loss' | 'draw',
): void {
  const col = outcome === 'win' ? 'wins' : outcome === 'loss' ? 'losses' : 'draws';
  db.prepare(
    `INSERT INTO game_stats (discord_id, game, wins, losses, draws) VALUES (?, ?, 0, 0, 0)
       ON CONFLICT(discord_id, game) DO NOTHING`,
  ).run(discordId, game);
  db.prepare(`UPDATE game_stats SET ${col} = ${col} + 1 WHERE discord_id = ? AND game = ?`).run(
    discordId,
    game,
  );
}

// ---------- kv --------------------------------------------------------------

export function kvGet(key: string): string | undefined {
  const row = db.prepare('SELECT v FROM kv WHERE k = ?').get(key) as { v: string } | undefined;
  return row?.v;
}
export function kvSet(key: string, value: string): void {
  db.prepare('INSERT INTO kv (k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v').run(
    key,
    value,
  );
}

// ---------- social seen -----------------------------------------------------

export function socialSeen(source: string, itemId: string): boolean {
  const row = db
    .prepare('SELECT item_id FROM social_seen WHERE source = ? AND item_id = ?')
    .get(source, itemId);
  if (row) return true;
  db.prepare('INSERT INTO social_seen (source, item_id, seen_at) VALUES (?, ?, ?)').run(
    source,
    itemId,
    Math.floor(Date.now() / 1000),
  );
  return false;
}
