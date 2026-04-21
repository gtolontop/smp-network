import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  type ChatInputCommandInteraction,
} from 'discord.js';

import { recordGameResult } from '../../../db/queries.js';
import { COLOR, baseEmbed } from '../../../utils/embeds.js';

const ROWS = 6;
const COLS = 7;

type Slot = 0 | 1 | 2; // 0 empty, 1 red, 2 yellow

function emptyBoard(): Slot[][] {
  return Array.from({ length: ROWS }, () => Array(COLS).fill(0) as Slot[]);
}

function drop(board: Slot[][], col: number, player: 1 | 2): number {
  for (let r = ROWS - 1; r >= 0; r--) {
    if (board[r][col] === 0) {
      board[r][col] = player;
      return r;
    }
  }
  return -1;
}

function winsAt(board: Slot[][], r: number, c: number): boolean {
  const player = board[r][c];
  if (!player) return false;
  const dirs = [
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1],
  ];
  for (const [dr, dc] of dirs) {
    let count = 1;
    for (let k = 1; k < 4; k++) {
      const nr = r + dr * k;
      const nc = c + dc * k;
      if (board[nr]?.[nc] === player) count++;
      else break;
    }
    for (let k = 1; k < 4; k++) {
      const nr = r - dr * k;
      const nc = c - dc * k;
      if (board[nr]?.[nc] === player) count++;
      else break;
    }
    if (count >= 4) return true;
  }
  return false;
}

function boardToString(board: Slot[][]): string {
  const icon = ['⚫', '🔴', '🟡'] as const;
  return board.map((row) => row.map((s) => icon[s]).join('')).join('\n') + '\n1️⃣2️⃣3️⃣4️⃣5️⃣6️⃣7️⃣';
}

export async function playConnect4(ix: ChatInputCommandInteraction): Promise<void> {
  const opponent = ix.options.getUser('adversaire', true);
  if (opponent.bot || opponent.id === ix.user.id) {
    await ix.reply({ content: 'Choisis un adversaire humain différent de toi.', ephemeral: true });
    return;
  }
  const board = emptyBoard();
  const players: Record<1 | 2, string> = { 1: ix.user.id, 2: opponent.id };
  let turn: 1 | 2 = 1;
  let done = false;

  const row = () =>
    new ActionRowBuilder<ButtonBuilder>().addComponents(
      ...Array.from({ length: 7 }, (_, i) =>
        new ButtonBuilder()
          .setCustomId(`c4:${i}`)
          .setLabel(`${i + 1}`)
          .setStyle(ButtonStyle.Secondary)
          .setDisabled(done || board[0][i] !== 0),
      ),
    );

  const render = (status: string) =>
    baseEmbed({
      title: 'Puissance 4',
      description: `${status}\n\n${boardToString(board)}`,
      color: COLOR.accent,
    });

  const msg = await ix.reply({
    embeds: [
      render(
        `🔴 <@${players[1]}> contre 🟡 <@${players[2]}>.\nAu tour de <@${players[turn]}>.`,
      ),
    ],
    components: [row()],
    fetchReply: true,
  });

  const collector = msg.createMessageComponentCollector({ time: 600_000 });
  collector.on('collect', async (btn) => {
    if (btn.user.id !== players[turn]) {
      await btn.reply({ content: "Ce n'est pas ton tour.", ephemeral: true });
      return;
    }
    const col = Number(btn.customId.split(':')[1]);
    const r = drop(board, col, turn);
    if (r < 0) {
      await btn.reply({ content: 'Colonne pleine.', ephemeral: true });
      return;
    }
    if (winsAt(board, r, col)) {
      done = true;
      recordGameResult(players[turn], 'connect4', 'win');
      recordGameResult(players[turn === 1 ? 2 : 1], 'connect4', 'loss');
      await btn.update({
        embeds: [render(`Victoire de <@${players[turn]}> (${turn === 1 ? '🔴' : '🟡'}).`)],
        components: [row()],
      });
      collector.stop('win');
      return;
    }
    if (board.every((rw) => rw.every((s) => s !== 0))) {
      done = true;
      recordGameResult(players[1], 'connect4', 'draw');
      recordGameResult(players[2], 'connect4', 'draw');
      await btn.update({ embeds: [render('Match nul.')], components: [row()] });
      collector.stop('draw');
      return;
    }
    turn = turn === 1 ? 2 : 1;
    await btn.update({
      embeds: [render(`Au tour de <@${players[turn]}>.`)],
      components: [row()],
    });
  });
  collector.on('end', async (_c, reason) => {
    if (reason === 'time') {
      done = true;
      await msg.edit({ components: [row()] }).catch(() => undefined);
    }
  });
}
