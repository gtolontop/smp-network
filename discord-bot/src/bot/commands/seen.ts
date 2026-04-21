import { SlashCommandBuilder } from 'discord.js';

import { playerByName } from '../../db/queries.js';
import { hub } from '../../bridge/hub.js';
import { COLOR, baseEmbed, fail } from '../../utils/embeds.js';
import { avatarUrlFor } from '../../utils/format.js';
import { discordRelative } from '../../utils/time.js';
import type { SlashCommand } from '../client.js';

const seen: SlashCommand = {
  name: 'seen',
  data: new SlashCommandBuilder()
    .setName('seen')
    .setDescription('Quand un joueur est passé pour la dernière fois.')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true)),
  async execute(ix) {
    const name = ix.options.getString('joueur', true);
    const row = playerByName(name);
    if (!row) {
      await ix.reply({ embeds: [fail('Joueur inconnu.')], ephemeral: true });
      return;
    }
    const online = hub.playerByName(row.mcName);
    await ix.reply({
      embeds: [
        baseEmbed({
          title: row.mcName,
          thumbnail: avatarUrlFor(row.mcName, 64),
          color: online ? COLOR.success : COLOR.muted,
          description: online
            ? `Actuellement en ligne sur **${online.server ?? '?'}**.`
            : `Dernière apparition ${discordRelative(row.lastSeen)}.`,
        }),
      ],
    });
  },
};

export default seen;
