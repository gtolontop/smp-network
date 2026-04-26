import { SlashCommandBuilder } from 'discord.js';

import { COLOR, baseEmbed, fail, ok } from '../../utils/embeds.js';
import { kvGet, kvSet } from '../../db/queries.js';
import { assertMod } from '../../utils/perms.js';
import type { SlashCommand } from '../client.js';

const discord: SlashCommand = {
  name: 'discord',
  data: new SlashCommandBuilder()
    .setName('discord')
    .setDescription("Afficher le lien du serveur Discord.")
    .addSubcommand((sub) =>
      sub.setName('lien').setDescription("Afficher le lien d'invitation Discord."),
    )
    .addSubcommand((sub) =>
      sub
        .setName('config')
        .setDescription("Configurer le lien d'invitation Discord. (Staff)")
        .addStringOption((o) =>
          o.setName('url').setDescription("Lien d'invitation Discord").setRequired(true),
        ),
    ),
  async execute(ix) {
    const sub = ix.options.getSubcommand(true);

    if (sub === 'config') {
      if (!(await assertMod(ix))) return;
      const url = ix.options.getString('url', true);
      kvSet('discord.invite_url', url);
      await ix.reply({ embeds: [ok(`Lien Discord configuré : ${url}`)], ephemeral: true });
      return;
    }

    const url = kvGet('discord.invite_url');
    if (!url) {
      await ix.reply({
        embeds: [fail("Aucun lien Discord n'a été configuré. Demandez à un membre du staff.")],
        ephemeral: true,
      });
      return;
    }

    await ix.reply({
      embeds: [
        baseEmbed({
          title: 'Rejoindre le Discord',
          description: `[Clique ici pour rejoindre](${url})`,
          color: COLOR.accent,
        }),
      ],
    });
  },
};

export default discord;
