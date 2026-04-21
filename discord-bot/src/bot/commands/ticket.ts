import { ChannelType, SlashCommandBuilder, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { COLOR, baseEmbed, fail } from '../../utils/embeds.js';
import { truncate } from '../../utils/format.js';
import type { SlashCommand } from '../client.js';

const ticket: SlashCommand = {
  name: 'ticket',
  data: new SlashCommandBuilder()
    .setName('ticket')
    .setDescription('Ouvrir un ticket privé avec le staff.')
    .addStringOption((o) =>
      o.setName('sujet').setDescription('Sujet court').setRequired(true).setMaxLength(80),
    )
    .addStringOption((o) =>
      o.setName('détail').setDescription('Détail').setRequired(true).setMaxLength(1000),
    ),
  async execute(ix) {
    const channelId = config.channels.tickets;
    if (!channelId) {
      await ix.reply({ embeds: [fail('Salon de tickets non configuré.')], ephemeral: true });
      return;
    }
    const subject = ix.options.getString('sujet', true);
    const detail = ix.options.getString('détail', true);
    const channel = await ix.client.channels.fetch(channelId).catch(() => null);
    if (!channel || channel.type !== ChannelType.GuildText) {
      await ix.reply({ embeds: [fail('Salon invalide.')], ephemeral: true });
      return;
    }

    const thread = await (channel as TextChannel).threads.create({
      name: truncate(`${ix.user.username} · ${subject}`, 90),
      autoArchiveDuration: 1440,
      type: ChannelType.PrivateThread,
      invitable: false,
    });
    await thread.members.add(ix.user.id);
    if (config.roles.mod) {
      await thread.send({ content: `<@&${config.roles.mod}>` }).catch(() => undefined);
    }
    await thread.send({
      embeds: [
        baseEmbed({
          title: `Ticket · ${subject}`,
          description: detail,
          color: COLOR.accent,
          footer: `ouvert par ${ix.user.tag}`,
          timestamp: true,
        }),
      ],
    });
    await ix.reply({
      embeds: [
        baseEmbed({ description: `Ticket ouvert: <#${thread.id}>.`, color: COLOR.success }),
      ],
      ephemeral: true,
    });
  },
};

export default ticket;
