import { SlashCommandBuilder } from 'discord.js';

import { ask } from '../../ai/glm.js';
import { COLOR, baseEmbed, fail } from '../../utils/embeds.js';
import { truncate } from '../../utils/format.js';
import type { SlashCommand } from '../client.js';

const ai: SlashCommand = {
  name: 'ai',
  data: new SlashCommandBuilder()
    .setName('ai')
    .setDescription("Poser une question à l'assistant IA (connecté au serveur).")
    .addStringOption((o) =>
      o.setName('question').setDescription('Ce que tu veux demander').setRequired(true).setMaxLength(1000),
    ),
  async execute(ix) {
    const prompt = ix.options.getString('question', true);
    await ix.deferReply();
    try {
      const answer = await ask({ prompt, userName: ix.user.username });
      await ix.editReply({
        embeds: [
          baseEmbed({
            title: 'Assistant',
            description: truncate(answer, 3900),
            color: COLOR.accent,
            footer: 'GLM · z.ai',
          }),
        ],
      });
    } catch (err) {
      await ix.editReply({ embeds: [fail(err instanceof Error ? err.message : String(err))] });
    }
  },
};

export default ai;
