import { ChannelType, EmbedBuilder, type Client, type Message, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { hub } from '../../bridge/hub.js';
import { kvGet, kvSet } from '../../db/queries.js';
import { hostSnapshot } from '../../host/metrics.js';
import { child } from '../../utils/logger.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { bytesToHuman, pct } from '../../utils/format.js';
import { humanDuration } from '../../utils/time.js';

const log = child({ mod: 'status-panel' });
const REFRESH_MS = 10_000;
const KV_KEY = 'status.messageId';

/**
 * Holds a single self-editing embed in CHANNEL_STATUS so the server
 * status is always a fixed, readable panel rather than a wall of pings.
 */
export async function initStatusPanel(client: Client): Promise<void> {
  const channelId = config.channels.status;
  if (!channelId) {
    log.warn('CHANNEL_STATUS not set — status panel disabled');
    return;
  }
  const channel = await client.channels.fetch(channelId).catch(() => null);
  if (!channel || channel.type !== ChannelType.GuildText) {
    log.warn({ channelId }, 'status channel invalid');
    return;
  }

  const target = channel as TextChannel;
  let message = await resolveExistingMessage(target);

  async function tick(): Promise<void> {
    try {
      const embed = await renderPanel();
      if (!message) {
        message = await target.send({ embeds: [embed] });
        kvSet(KV_KEY, message.id);
      } else {
        await message.edit({ embeds: [embed] });
      }
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'status panel update failed');
      message = undefined;
    }
  }

  tick();
  setInterval(tick, REFRESH_MS).unref();
  log.info({ channelId }, 'status panel running');
}

async function resolveExistingMessage(channel: TextChannel): Promise<Message | undefined> {
  const saved = kvGet(KV_KEY);
  if (!saved) return;
  try {
    return await channel.messages.fetch(saved);
  } catch {
    return;
  }
}

async function renderPanel(): Promise<EmbedBuilder> {
  const peers = hub.list();
  const roster = hub.allRoster();
  const host = await hostSnapshot();

  const lines = ['velocity', 'lobby', 'survival'].map((origin) => {
    const peer = peers.find((p) => p.origin === origin);
    if (!peer) return `\`${origin.padEnd(9)}\` — \`offline\``;
    const t = peer.telemetry;
    if (!t) return `\`${origin.padEnd(9)}\` — connecté`;
    return `\`${origin.padEnd(9)}\` · TPS \`${t.tps1m.toFixed(1)}\` · MSPT \`${t.msptAvg.toFixed(1)}\` · ${t.online}/${t.maxOnline} joueurs`;
  });

  const names = roster
    .slice(0, 40)
    .map((p) => `\`${p.name}\``)
    .join(' ');

  return baseEmbed({
    title: 'Statut du réseau',
    color: peers.length ? COLOR.accent : COLOR.muted,
    description: lines.join('\n'),
    sections: [
      {
        name: `Joueurs en ligne · ${roster.length}`,
        value: names.length ? names : '*Personne pour le moment.*',
      },
      {
        name: 'Hôte',
        value: [
          `CPU \`${pct(host.cpuLoad)}\``,
          `RAM \`${bytesToHuman(host.memUsed)}/${bytesToHuman(host.memTotal)}\` (${pct(host.memPct)})`,
          `Disque \`${pct(host.diskPct)}\``,
          `Uptime \`${humanDuration(host.uptimeSec)}\``,
        ].join(' · '),
      },
    ],
    footer: 'Mise à jour toutes les 10s',
    timestamp: true,
  });
}
