import { SlashCommandBuilder } from 'discord.js';

import { createLinkCode, linkFor, unlink } from '../../db/queries.js';
import { COLOR, baseEmbed, ok } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const link: SlashCommand = {
  name: 'link',
  data: new SlashCommandBuilder()
    .setName('link')
    .setDescription('Lier ton compte Discord à ton compte Minecraft.'),
  async execute(ix) {
    const existing = linkFor(ix.user.id);
    if (existing) {
      await ix.reply({
        embeds: [
          baseEmbed({
            title: 'Déjà lié',
            description: `Tu es déjà lié à **${existing.mcName}**. Utilise \`/unlink\` pour casser la liaison.`,
            color: COLOR.muted,
          }),
        ],
        ephemeral: true,
      });
      return;
    }
    const code = createLinkCode(ix.user.id);
    await ix.reply({
      embeds: [
        baseEmbed({
          title: 'Liaison de compte',
          description: [
            'Connecte-toi en jeu puis tape :',
            `\`\`\`/link ${code}\`\`\``,
            'Le code expire dans 10 minutes.',
          ].join('\n'),
          color: COLOR.accent,
        }),
      ],
      ephemeral: true,
    });
  },
};

export const unlinkCmd: SlashCommand = {
  name: 'unlink',
  data: new SlashCommandBuilder().setName('unlink').setDescription('Retirer la liaison Discord ↔ Minecraft.'),
  async execute(ix) {
    const existing = linkFor(ix.user.id);
    if (!existing) {
      await ix.reply({
        content: "Aucune liaison active.",
        ephemeral: true,
      });
      return;
    }
    unlink(ix.user.id);
    await ix.reply({
      embeds: [ok(`Liaison avec **${existing.mcName}** retirée.`)],
      ephemeral: true,
    });
  },
};

export default link;
