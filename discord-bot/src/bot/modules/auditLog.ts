import { ChannelType, type Client, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'audit' });

let client: Client | undefined;

export function initAuditLog(c: Client): void {
  client = c;
}

export interface AuditEntry {
  actor: string;
  action: string;
  target?: string;
  details?: string;
  ok?: boolean;
}

export async function audit(entry: AuditEntry): Promise<void> {
  if (!client) return;
  const channelId = config.channels.audit;
  if (!channelId) return;
  try {
    const channel = await client.channels.fetch(channelId);
    if (!channel || channel.type !== ChannelType.GuildText) return;
    await (channel as TextChannel).send({
      embeds: [
        baseEmbed({
          title: entry.action,
          description: [
            `**Auteur** \`${entry.actor}\``,
            entry.target ? `**Cible** \`${entry.target}\`` : undefined,
            entry.details ? entry.details : undefined,
          ]
            .filter(Boolean)
            .join('\n'),
          color: entry.ok === false ? COLOR.danger : COLOR.muted,
          timestamp: true,
        }),
      ],
    });
  } catch (err) {
    log.warn({ err: err instanceof Error ? err.message : String(err) }, 'audit failed');
  }
}
