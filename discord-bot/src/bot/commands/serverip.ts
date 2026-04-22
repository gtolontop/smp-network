import { SlashCommandBuilder } from 'discord.js';

import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { kvGet } from '../../db/queries.js';
import type { SlashCommand } from '../client.js';

const serverip: SlashCommand = {
  name: 'serverip',
  data: new SlashCommandBuilder().setName('serverip').setDescription("Afficher l'adresse du serveur."),
  async execute(ix) {
    const ip = kvGet('server.ip') ?? process.env.SERVER_IP ?? 'ip non configurée';
    const version = kvGet('server.version') ?? process.env.SERVER_VERSION ?? 'latest';
    await ix.reply({
      embeds: [
        baseEmbed({
          title: 'Adresse',
          description: `\`${ip}\`\n**Version** \`${version}\``,
          color: COLOR.accent,
        }),
      ],
    });
  },
};

export default serverip;
