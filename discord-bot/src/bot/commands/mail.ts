import { SlashCommandBuilder } from 'discord.js';

import { queueMail, playerByName, linkFor } from '../../db/queries.js';
import { hub } from '../../bridge/hub.js';
import { ok, fail } from '../../utils/embeds.js';
import { truncate } from '../../utils/format.js';
import type { SlashCommand } from '../client.js';

const mail: SlashCommand = {
  name: 'mail',
  data: new SlashCommandBuilder()
    .setName('mail')
    .setDescription('Laisser un message offline à un joueur.')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true))
    .addStringOption((o) =>
      o.setName('message').setDescription('Contenu (max 300 car.)').setRequired(true).setMaxLength(300),
    ),
  async execute(ix) {
    const link = linkFor(ix.user.id);
    if (!link) {
      await ix.reply({
        embeds: [fail('Lie ton compte avec `/link` avant de poster du mail.')],
        ephemeral: true,
      });
      return;
    }
    const name = ix.options.getString('joueur', true);
    const body = truncate(ix.options.getString('message', true), 300);
    const online = hub.playerByName(name);

    if (online) {
      hub.broadcast({
        kind: 'tell',
        toUuid: online.uuid,
        message: `[mail de ${link.mcName}] ${body}`,
      });
      await ix.reply({ embeds: [ok(`**${name}** est en ligne, le message lui a été délivré.`)], ephemeral: true });
      return;
    }

    const row = playerByName(name);
    if (!row) {
      await ix.reply({ embeds: [fail('Joueur inconnu.')], ephemeral: true });
      return;
    }
    queueMail(link.mcName, row.mcUuid, row.mcName, body);
    await ix.reply({
      embeds: [ok(`Mail laissé à **${name}**. Il le recevra à sa prochaine connexion.`)],
      ephemeral: true,
    });
  },
};

export default mail;
