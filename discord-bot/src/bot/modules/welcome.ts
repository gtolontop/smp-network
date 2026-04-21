import { ChannelType, Events, type Client, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'welcome' });

export function initWelcome(client: Client): void {
  const channelId = config.channels.welcome;
  if (!channelId) {
    log.info('no welcome channel — skipping');
    return;
  }

  client.on(Events.GuildMemberAdd, async (member) => {
    try {
      const channel = await client.channels.fetch(channelId);
      if (!channel || channel.type !== ChannelType.GuildText) return;
      await (channel as TextChannel).send({
        content: `<@${member.id}>`,
        embeds: [
          baseEmbed({
            title: 'Bienvenue sur le SMP',
            description: [
              `Salut **${member.displayName}** — content de te voir.`,
              '',
              '• Lie ton compte Minecraft avec `/link`.',
              '• Va dire bonjour dans le salon chat — il est bridge avec le jeu.',
              '• `/help` liste toutes les commandes disponibles.',
            ].join('\n'),
            thumbnail: member.user.displayAvatarURL({ size: 128 }),
            color: COLOR.accent,
            timestamp: true,
          }),
        ],
      });
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'welcome failed');
    }
  });
}
