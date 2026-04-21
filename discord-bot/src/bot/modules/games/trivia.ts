import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  type ChatInputCommandInteraction,
} from 'discord.js';

import { recordGameResult } from '../../../db/queries.js';
import { COLOR, baseEmbed } from '../../../utils/embeds.js';

interface Question {
  q: string;
  answers: [string, string, string, string];
  correct: number;
}

/** Hand-picked Minecraft-centric trivia. */
const BANK: Question[] = [
  {
    q: 'Quel bloc a un niveau de lumière de 15 ?',
    answers: ['Torche', 'Jack-o-Lantern', 'Pierre Lumineuse', 'Lanterne'],
    correct: 2,
  },
  {
    q: 'Combien de disques de musique existent dans le jeu vanilla ?',
    answers: ['10', '13', '16', '18'],
    correct: 2,
  },
  {
    q: 'À quelle Y le bedrock apparaît-il dans les mondes récents ?',
    answers: ['Y=0', 'Y=-32', 'Y=-64', 'Y=-128'],
    correct: 2,
  },
  {
    q: 'Quel mob drop une perle d\'Ender ?',
    answers: ['Endermite', 'Shulker', 'Enderman', 'Evoker'],
    correct: 2,
  },
  {
    q: 'Combien de diamants faut-il pour une enclume ?',
    answers: ['0', '1', '3', '5'],
    correct: 0,
  },
  {
    q: 'Combien d\'œufs de dragon peut-on obtenir dans un monde ?',
    answers: ['1', '2', '∞', 'Aucun'],
    correct: 2,
  },
  {
    q: 'Quelle plante donne des bâtons ?',
    answers: ['Sucre de canne', 'Bambou', 'Cactus', 'Roseaux'],
    correct: 1,
  },
  {
    q: 'Quel outil casse le plus vite un lingot de netherite ?',
    answers: ['Il ne peut pas', 'Pioche netherite', 'Pioche diamant', 'Pioche fer'],
    correct: 0,
  },
];

export async function playTrivia(ix: ChatInputCommandInteraction): Promise<void> {
  const q = BANK[Math.floor(Math.random() * BANK.length)];
  const msg = await ix.reply({
    embeds: [
      baseEmbed({
        title: 'Trivia',
        description: `**${q.q}**\n\n${q.answers.map((a, i) => `**${'ABCD'[i]}.** ${a}`).join('\n')}`,
        color: COLOR.accent,
        footer: 'Tu as 20 secondes',
      }),
    ],
    components: [
      new ActionRowBuilder<ButtonBuilder>().addComponents(
        ...q.answers.map((_, i) =>
          new ButtonBuilder()
            .setCustomId(`trivia:${i}`)
            .setLabel('ABCD'[i])
            .setStyle(ButtonStyle.Primary),
        ),
      ),
    ],
    fetchReply: true,
  });

  const collector = msg.createMessageComponentCollector({ time: 20_000 });
  const answered = new Set<string>();
  collector.on('collect', async (btn) => {
    if (answered.has(btn.user.id)) {
      await btn.reply({ content: 'Déjà répondu.', ephemeral: true });
      return;
    }
    answered.add(btn.user.id);
    const idx = Number(btn.customId.split(':')[1]);
    const right = idx === q.correct;
    recordGameResult(btn.user.id, 'trivia', right ? 'win' : 'loss');
    await btn.reply({
      embeds: [
        baseEmbed({
          description: right ? '✓ Bonne réponse.' : `✗ C'était **${'ABCD'[q.correct]}. ${q.answers[q.correct]}**.`,
          color: right ? COLOR.success : COLOR.danger,
        }),
      ],
      ephemeral: true,
    });
  });
  collector.on('end', async () => {
    await msg.edit({
      embeds: [
        baseEmbed({
          title: 'Trivia · terminé',
          description: `**${q.q}**\n\n✔ Bonne réponse : **${'ABCD'[q.correct]}. ${q.answers[q.correct]}**\n\n${answered.size} participant(s).`,
          color: COLOR.muted,
        }),
      ],
      components: [],
    }).catch(() => undefined);
  });
}
