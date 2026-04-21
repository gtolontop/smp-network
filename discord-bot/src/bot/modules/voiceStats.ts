import type { Client, VoiceChannel } from 'discord.js';
import { ChannelType } from 'discord.js';

import { config } from '../../config.js';
import { hub } from '../../bridge/hub.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'voice-stats' });
const UPDATE_MS = 60_000; // Discord rate-limits channel renames to ~2/10min.

/**
 * Rewrites the names of dedicated voice channels to expose live
 * numbers without anyone needing to check a web UI.
 */
export function initVoiceStats(client: Client): void {
  const ids = [config.voice.playerCount, config.voice.tps].filter(Boolean);
  if (!ids.length) {
    log.info('no voice-stat channels configured — skipping');
    return;
  }

  let lastPlayers = -1;
  let lastTps = -1;

  async function tick(): Promise<void> {
    const roster = hub.allRoster();
    const players = roster.length;
    const survival = hub.byOrigin('survival')?.telemetry;
    const tps = survival ? Math.min(20, Math.round(survival.tps1m * 10) / 10) : 0;

    if (config.voice.playerCount && players !== lastPlayers) {
      await rename(client, config.voice.playerCount, `◉ Joueurs · ${players}`);
      lastPlayers = players;
    }
    if (config.voice.tps && tps !== lastTps) {
      await rename(client, config.voice.tps, `◈ TPS · ${tps.toFixed(1)}`);
      lastTps = tps;
    }
  }

  tick();
  setInterval(tick, UPDATE_MS).unref();
}

async function rename(client: Client, id: string, name: string): Promise<void> {
  try {
    const channel = await client.channels.fetch(id);
    if (!channel || channel.type !== ChannelType.GuildVoice) return;
    await (channel as VoiceChannel).setName(name);
  } catch (err) {
    log.debug({ err: err instanceof Error ? err.message : String(err), id }, 'voice rename skipped');
  }
}
