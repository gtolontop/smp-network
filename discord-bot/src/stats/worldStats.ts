import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const STATS_DIR_CANDIDATES = ['../survival/world/players/stats', './survival/world/players/stats'] as const;
const USERCACHE_CANDIDATES = ['../survival/usercache.json', './survival/usercache.json'] as const;
const CACHE_TTL_MS = 15_000;

export interface WorldPlayerStatsRow {
  uuid: string;
  mcName: string;
  blocksBroken: number;
}

interface CachedWorldStats {
  loadedAt: number;
  rows: WorldPlayerStatsRow[];
}

interface MinecraftStatsFile {
  stats?: {
    'minecraft:mined'?: Record<string, number>;
  };
}

interface UsercacheEntry {
  uuid: string;
  name: string;
}

let cachedWorldStats: CachedWorldStats | undefined;

export function loadWorldPlayerStats(forceReload = false): WorldPlayerStatsRow[] {
  if (!forceReload && cachedWorldStats && Date.now() - cachedWorldStats.loadedAt < CACHE_TTL_MS) {
    return cachedWorldStats.rows;
  }

  const statsDir = resolveExistingPath(STATS_DIR_CANDIDATES);
  if (!statsDir) return rememberRows([]);

  const names = loadUsercacheNames();
  const rows: WorldPlayerStatsRow[] = [];

  for (const fileName of readdirSync(statsDir)) {
    if (!fileName.endsWith('.json')) continue;

    const uuid = fileName.slice(0, -'.json'.length).toLowerCase();
    const raw = readJsonFile<MinecraftStatsFile>(join(statsDir, fileName));
    if (!raw) continue;

    rows.push({
      uuid,
      mcName: names.get(uuid) ?? uuid,
      blocksBroken: sumMinedBlocks(raw.stats?.['minecraft:mined']),
    });
  }

  rows.sort((a, b) => b.blocksBroken - a.blocksBroken || a.mcName.localeCompare(b.mcName, 'fr'));
  return rememberRows(rows);
}

export function worldPlayerStatsByUuid(uuid: string): WorldPlayerStatsRow | undefined {
  const target = uuid.trim().toLowerCase();
  if (!target) return;
  return loadWorldPlayerStats().find((row) => row.uuid === target);
}

export function worldPlayerStatsByName(name: string): WorldPlayerStatsRow | undefined {
  const target = name.trim().toLowerCase();
  if (!target) return;
  return loadWorldPlayerStats().find((row) => row.mcName.toLowerCase() === target);
}

function rememberRows(rows: WorldPlayerStatsRow[]): WorldPlayerStatsRow[] {
  cachedWorldStats = {
    loadedAt: Date.now(),
    rows,
  };
  return rows;
}

function loadUsercacheNames(): Map<string, string> {
  const filePath = resolveExistingPath(USERCACHE_CANDIDATES);
  if (!filePath) return new Map();

  const entries = readJsonFile<UsercacheEntry[]>(filePath);
  if (!entries) return new Map();

  const names = new Map<string, string>();
  for (const entry of entries) {
    names.set(entry.uuid.toLowerCase(), entry.name);
  }
  return names;
}

function sumMinedBlocks(mined: Record<string, number> | undefined): number {
  if (!mined) return 0;

  let total = 0;
  for (const value of Object.values(mined)) {
    const amount = Number(value);
    if (Number.isFinite(amount)) total += amount;
  }
  return total;
}

function resolveExistingPath(candidates: readonly string[]): string | undefined {
  for (const candidate of candidates) {
    const filePath = resolve(process.cwd(), candidate);
    if (existsSync(filePath)) return filePath;
  }
  return;
}

function readJsonFile<T>(filePath: string): T | undefined {
  try {
    return JSON.parse(readFileSync(filePath, 'utf8')) as T;
  } catch {
    return;
  }
}
