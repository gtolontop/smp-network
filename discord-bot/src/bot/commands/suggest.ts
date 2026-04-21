import { ChannelType, SlashCommandBuilder, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { COLOR, baseEmbed, fail, ok } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const suggest: SlashCommand = {
  name: 'suggest',
  data: new SlashCommandBuilder()
    .setName('suggest')
    .setDescription('Proposer une suggestion (vote communautaire).')
    .addStringOption((o) =>
      o.setName('idée').setDescription('Ta suggestion').setRequired(true).setMaxLength(1000),
    ),
  async execute(ix) {
    const channelId = config.channels.suggestions;
    if (!channelId) {
      await ix.reply({ embeds: [fail('Salon de suggestions non configuré.')], ephemeral: true });
      return;
    }
    const idea = ix.options.getString('idée', true);
    const channel = await ix.client.channels.fetch(channelId).catch(() => null);
    if (!channel || channel.type !== ChannelType.GuildText) {
      await ix.reply({ embeds: [fail('Salon invalide.')], ephemeral: true });
      return;
    }
    const msg = await (channel as TextChannel).send({
      embeds: [
        baseEmbed({
          title: 'Nouvelle suggestion',
          description: idea,
          color: COLOR.accent,
          footer: `par ${ix.user.tag}`,
          timestamp: true,
        }),
      ],
    });
    await msg.react('👍').catch(() => undefined);
    await msg.react('👎').catch(() => undefined);
    await ix.reply({ embeds: [ok('Suggestion publiée.')], ephemeral: true });
  },
};

export default suggest;
