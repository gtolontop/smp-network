import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  type ChatInputCommandInteraction,
} from 'discord.js';

import { recordGameResult } from '../../../db/queries.js';
import { COLOR, baseEmbed } from '../../../utils/embeds.js';

export async function playHigherLower(ix: ChatInputCommandInteraction): Promise<void> {
  let current = Math.floor(Math.random() * 100) + 1;
  let streak = 0;
  let lives = 3;

  const render = () =>
    baseEmbed({
      title: 'Plus ou moins',
      description: [
        `Nombre actuel : **${current}** (1-100)`,
        `Streak : **${streak}** · Vies : ${'◉'.repeat(lives)}${'◯'.repeat(3 - lives)}`,
        '',
        'Le prochain sera-t-il plus haut ou plus bas ?',
      ].join('\n'),
      color: COLOR.accent,
    });

  const buttons = (disabled: boolean) =>
    new ActionRowBuilder<ButtonBuilder>().addComponents(
      new ButtonBuilder().setCustomId('hl:higher').setLabel('Plus haut').setStyle(ButtonStyle.Success).setDisabled(disabled),
      new ButtonBuilder().setCustomId('hl:lower').setLabel('Plus bas').setStyle(ButtonStyle.Danger).setDisabled(disabled),
      new ButtonBuilder().setCustomId('hl:stop').setLabel('Encaisser').setStyle(ButtonStyle.Secondary).setDisabled(disabled),
    );

  const msg = await ix.reply({
    embeds: [render()],
    components: [buttons(false)],
    fetchReply: true,
  });

  const collector = msg.createMessageComponentCollector({ time: 180_000 });
  collector.on('collect', async (btn) => {
    if (btn.user.id !== ix.user.id) {
      await btn.reply({ content: "Ce n'est pas ta partie.", ephemeral: true });
      return;
    }
    if (btn.customId === 'hl:stop') {
      recordGameResult(ix.user.id, 'higherlower', 'win');
      await btn.update({
        embeds: [baseEmbed({ title: 'Plus ou moins', description: `Encaissé avec un streak de **${streak}**.`, color: COLOR.success })],
        components: [buttons(true)],
      });
      collector.stop('stop');
      return;
    }
    const next = Math.floor(Math.random() * 100) + 1;
    const guessHigher = btn.customId === 'hl:higher';
    const correct = guessHigher ? next > current : next < current;
    current = next;
    if (correct) {
      streak++;
      await btn.update({ embeds: [render()], components: [buttons(false)] });
    } else {
      lives--;
      if (lives <= 0) {
        recordGameResult(ix.user.id, 'higherlower', 'loss');
        await btn.update({
          embeds: [baseEmbed({ title: 'Plus ou moins', description: `Perdu. Streak final : **${streak}**.`, color: COLOR.danger })],
          components: [buttons(true)],
        });
        collector.stop('lost');
      } else {
        await btn.update({ embeds: [render()], components: [buttons(false)] });
      }
    }
  });
}
