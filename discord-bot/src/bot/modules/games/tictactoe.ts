import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  type ChatInputCommandInteraction,
} from 'discord.js';

import { recordGameResult } from '../../../db/queries.js';
import { COLOR, baseEmbed } from '../../../utils/embeds.js';

type Cell = 'x' | 'o' | null;

const LINES = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
];

function winner(cells: Cell[]): Cell | 'draw' | null {
  for (const line of LINES) {
    const a = line[0]!;
    const b = line[1]!;
    const c = line[2]!;
    if (cells[a] && cells[a] === cells[b] && cells[a] === cells[c]) return cells[a]!;
  }
  if (cells.every(Boolean)) return 'draw';
  return null;
}

function buildBoard(cells: Cell[], disabled: boolean): ActionRowBuilder<ButtonBuilder>[] {
  const rows: ActionRowBuilder<ButtonBuilder>[] = [];
  for (let r = 0; r < 3; r++) {
    const row = new ActionRowBuilder<ButtonBuilder>();
    for (let c = 0; c < 3; c++) {
      const i = r * 3 + c;
      const v = cells[i];
      const btn = new ButtonBuilder()
        .setCustomId(`ttt:${i}`)
        .setLabel(v ? (v === 'x' ? '✕' : '◯') : ' ')
        .setStyle(v === 'x' ? ButtonStyle.Danger : v === 'o' ? ButtonStyle.Primary : ButtonStyle.Secondary)
        .setDisabled(disabled || v !== null);
      row.addComponents(btn);
    }
    rows.push(row);
  }
  return rows;
}

export async function playTicTacToe(ix: ChatInputCommandInteraction): Promise<void> {
  const opponent = ix.options.getUser('adversaire', true);
  if (opponent.bot) {
    await ix.reply({ content: "Tu ne peux pas jouer contre un bot ici.", ephemeral: true });
    return;
  }
  if (opponent.id === ix.user.id) {
    await ix.reply({ content: "Il te faut un adversaire différent.", ephemeral: true });
    return;
  }

  const cells: Cell[] = Array(9).fill(null);
  let turn: 'x' | 'o' = 'x';
  const playerX = ix.user.id;
  const playerO = opponent.id;

  const render = (status: string) =>
    baseEmbed({
      title: 'Morpion',
      description: status,
      color: COLOR.accent,
    });

  const message = await ix.reply({
    embeds: [render(`<@${playerX}> (✕) contre <@${playerO}> (◯).\nAu tour de <@${playerX}>.`)],
    components: buildBoard(cells, false),
    fetchReply: true,
  });

  const collector = message.createMessageComponentCollector({ time: 300_000 });
  collector.on('collect', async (btn) => {
    const expected = turn === 'x' ? playerX : playerO;
    if (btn.user.id !== expected) {
      await btn.reply({ content: "Ce n'est pas ton tour.", ephemeral: true });
      return;
    }
    const idx = Number(btn.customId.split(':')[1]);
    if (cells[idx]) return;
    cells[idx] = turn;
    const w = winner(cells);
    if (w) {
      await btn.update({
        embeds: [
          render(
            w === 'draw'
              ? 'Match nul.'
              : `Victoire de <@${w === 'x' ? playerX : playerO}> (${w === 'x' ? '✕' : '◯'}).`,
          ),
        ],
        components: buildBoard(cells, true),
      });
      if (w === 'draw') {
        recordGameResult(playerX, 'tictactoe', 'draw');
        recordGameResult(playerO, 'tictactoe', 'draw');
      } else {
        recordGameResult(w === 'x' ? playerX : playerO, 'tictactoe', 'win');
        recordGameResult(w === 'x' ? playerO : playerX, 'tictactoe', 'loss');
      }
      collector.stop();
      return;
    }
    turn = turn === 'x' ? 'o' : 'x';
    await btn.update({
      embeds: [render(`Au tour de <@${turn === 'x' ? playerX : playerO}>.`)],
      components: buildBoard(cells, false),
    });
  });
  collector.on('end', async (_c, reason) => {
    if (reason === 'time') {
      await message.edit({ components: buildBoard(cells, true) }).catch(() => undefined);
    }
  });
}
