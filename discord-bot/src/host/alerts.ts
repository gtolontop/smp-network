import { ChannelType, type Client, type TextChannel } from 'discord.js';

import { config } from '../config.js';
import { shouldFireAlert } from '../db/queries.js';
import { child } from '../utils/logger.js';
import { COLOR, baseEmbed } from '../utils/embeds.js';
import { bytesToHuman, pct } from '../utils/format.js';

import { hostSnapshot } from './metrics.js';

const log = child({ mod: 'host-alerts' });
const CHECK_EVERY_MS = 30_000;

export function initAlerts(client: Client): void {
  const channelId = config.channels.alerts;
  if (!channelId) {
    log.warn('CHANNEL_ALERTS not set — host alerts disabled');
    return;
  }

  async function tick(): Promise<void> {
    try {
      const s = await hostSnapshot();
      const channel = await client.channels.fetch(channelId).catch(() => null);
      if (!channel || channel.type !== ChannelType.GuildText) return;
      const target = channel as TextChannel;
      const cooldown = config.host.alertCooldownS;

      if (s.cpuLoad >= config.host.alertCpu && shouldFireAlert('cpu', cooldown)) {
        await target.send({
          embeds: [
            baseEmbed({
              title: 'Alerte CPU',
              description: `Charge CPU \`${pct(s.cpuLoad)}\` (seuil ${pct(config.host.alertCpu)}).`,
              color: COLOR.danger,
              timestamp: true,
            }),
          ],
        });
      }
      if (s.memPct >= config.host.alertRam && shouldFireAlert('ram', cooldown)) {
        await target.send({
          embeds: [
            baseEmbed({
              title: 'Alerte RAM',
              description: `Mémoire \`${bytesToHuman(s.memUsed)}/${bytesToHuman(s.memTotal)}\` (${pct(s.memPct)}).`,
              color: COLOR.danger,
              timestamp: true,
            }),
          ],
        });
      }
      if (s.diskPct >= config.host.alertDisk && shouldFireAlert('disk', cooldown)) {
        await target.send({
          embeds: [
            baseEmbed({
              title: 'Alerte disque',
              description: `Occupation \`${pct(s.diskPct)}\` sur le disque principal.`,
              color: COLOR.danger,
              timestamp: true,
            }),
          ],
        });
      }
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'host alert tick failed');
    }
  }

  setInterval(tick, CHECK_EVERY_MS).unref();
}
