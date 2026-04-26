import { ChannelType, type Client, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { eventsSince, kvGet, kvSet, topPlayers } from '../../db/queries.js';
import { loadWorldPlayerStats } from '../../stats/worldStats.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { child } from '../../utils/logger.js';
import { escapeMd, stripMinecraft } from '../../utils/format.js';
import { humanDuration } from '../../utils/time.js';

const log = child({ mod: 'daily-recap' });
const MINING_SNAPSHOT_KEY = 'dailyRecap.miningSnapshot';

interface MiningSnapshotPlayer {
  mcName: string;
  totalBlocks: number;
}

interface MiningSnapshot {
  capturedAt: string;
  players: Record<string, MiningSnapshotPlayer>;
}

interface MiningRow {
  uuid: string;
  mcName: string;
  totalBlocks: number;
  blocksToday: number;
}

/**
 * Fires once a day at ~23:59 local. Picks the day's highlights from
 * the events log plus the top playtimes. Runs safely even if started
 * mid-day — it only fires when the minute-of-day crosses the target.
 */
export function initDailyRecap(client: Client): void {
  const channelId = config.channels.recap;
  if (!channelId) return;

  let lastFireDay = -1;
  setInterval(async () => {
    const now = new Date();
    if (now.getHours() !== 23 || now.getMinutes() !== 59) return;
    const day = now.getDate();
    if (day === lastFireDay) return;
    lastFireDay = day;
    await send(client, channelId).catch((err) =>
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'recap failed'),
    );
  }, 30_000).unref();
}

async function send(client: Client, channelId: string): Promise<void> {
  const channel = await client.channels.fetch(channelId).catch(() => null);
  if (!channel || channel.type !== ChannelType.GuildText) return;
  const target = channel as TextChannel;

  const start = Math.floor(Date.now() / 1000) - 86_400;
  const deaths = eventsSince(start, 'death', 200);
  const advs = eventsSince(start, 'advancement', 500).filter((e) => {
    try {
      return (JSON.parse(e.payload) as { frame?: string }).frame !== 'task';
    } catch {
      return false;
    }
  });
  const joins = eventsSince(start, 'join', 500);
  const firstTimes = joins.filter((e) => {
    try {
      return (JSON.parse(e.payload) as { firstTime?: boolean }).firstTime === true;
    } catch {
      return false;
    }
  });

  const deathsBy = tally(deaths.map((e) => e.mcName ?? 'inconnu'));
  const worstVictim = deathsBy[0];

  const topPlay = topPlayers('playtime_sec', 3);
  const miningRows = loadMiningRows();
  const topMiner =
    miningRows[0] ??
    (() => {
      const row = topPlayers('blocks_broken', 1)[0];
      return row
        ? {
            uuid: row.mcUuid,
            mcName: row.mcName,
            totalBlocks: row.blocksBroken,
            blocksToday: row.blocksBroken,
          }
        : undefined;
    })();

  const lines = [
    `**Morts** · ${deaths.length}${worstVictim ? ` — malchance pour **${escapeMd(worstVictim[0])}** (${worstVictim[1]})` : ''}`,
    `**Progrès notables** · ${advs.length}`,
    `**Nouveaux joueurs** · ${firstTimes.length}`,
  ];

  const sections = [
    {
      name: 'Top temps de jeu',
      value: topPlay.length
        ? topPlay.map((p, i) => `**${i + 1}.** \`${p.mcName}\` — ${humanDuration(p.playtimeSec)}`).join('\n')
        : '*aucune donnée*',
    },
    {
      name: 'Mineur du jour',
      value: topMiner ? `\`${topMiner.mcName}\` · ${topMiner.blocksToday.toLocaleString('fr-FR')} blocs` : '*aucune donnée*',
    },
  ];

  if (deaths.length) {
    const highlights = deaths
      .slice(0, 3)
      .map((e) => {
        try {
          const payload = JSON.parse(e.payload) as { message?: string };
          return `• ${escapeMd(stripMinecraft(payload.message ?? ''))}`;
        } catch {
          return '';
        }
      })
      .filter(Boolean)
      .join('\n');
    if (highlights) sections.push({ name: 'Moments du jour', value: highlights });
  }

  await target.send({
    embeds: [
      baseEmbed({
        title: `Récap · ${new Date().toLocaleDateString('fr-FR', { timeZone: config.meta.timezone })}`,
        description: lines.join('\n'),
        sections,
        color: COLOR.accent,
        timestamp: true,
      }),
    ],
  });

  if (miningRows.length) saveMiningSnapshot(miningRows);
}

function tally<T extends string>(items: T[]): Array<[T, number]> {
  const m = new Map<T, number>();
  for (const it of items) m.set(it, (m.get(it) ?? 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1]);
}

function loadMiningRows(): MiningRow[] {
  const snapshot = loadMiningSnapshot();
  return loadWorldPlayerStats().map((row) => ({
    uuid: row.uuid,
    mcName: row.mcName,
    totalBlocks: row.blocksBroken,
    blocksToday: snapshot ? Math.max(0, row.blocksBroken - (snapshot.players[row.uuid]?.totalBlocks ?? 0)) : row.blocksBroken,
  })).sort(
    (a, b) => b.blocksToday - a.blocksToday || b.totalBlocks - a.totalBlocks || a.mcName.localeCompare(b.mcName, 'fr'),
  );
}

function saveMiningSnapshot(rows: MiningRow[]): void {
  const snapshot: MiningSnapshot = {
    capturedAt: new Date().toISOString(),
    players: Object.fromEntries(
      rows.map((row) => [
        row.uuid,
        {
          mcName: row.mcName,
          totalBlocks: row.totalBlocks,
        },
      ]),
    ),
  };
  kvSet(MINING_SNAPSHOT_KEY, JSON.stringify(snapshot));
}

function loadMiningSnapshot(): MiningSnapshot | undefined {
  const raw = kvGet(MINING_SNAPSHOT_KEY);
  if (!raw) return;

  try {
    const parsed = JSON.parse(raw) as Partial<MiningSnapshot>;
    const players = parsed.players;
    if (!players || typeof players !== 'object') return;

    const normalized: Record<string, MiningSnapshotPlayer> = {};
    for (const [uuid, player] of Object.entries(players)) {
      if (!player || typeof player !== 'object') continue;
      const mcName = typeof player.mcName === 'string' && player.mcName ? player.mcName : uuid;
      const totalBlocks = Number.isFinite(player.totalBlocks) ? player.totalBlocks : 0;
      normalized[uuid.toLowerCase()] = { mcName, totalBlocks };
    }

    return {
      capturedAt: typeof parsed.capturedAt === 'string' ? parsed.capturedAt : '',
      players: normalized,
    };
  } catch {
    return;
  }
}
