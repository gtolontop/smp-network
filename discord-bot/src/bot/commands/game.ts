import { SlashCommandBuilder } from 'discord.js';

import { playTicTacToe } from '../modules/games/tictactoe.js';
import { playConnect4 } from '../modules/games/connect4.js';
import { playHigherLower } from '../modules/games/higherlower.js';
import { playTrivia } from '../modules/games/trivia.js';
import type { SlashCommand } from '../client.js';

const game: SlashCommand = {
  name: 'game',
  data: new SlashCommandBuilder()
    .setName('game')
    .setDescription('Lancer un mini-jeu.')
    .addSubcommand((s) =>
      s
        .setName('tictactoe')
        .setDescription('Morpion contre un autre joueur')
        .addUserOption((o) => o.setName('adversaire').setDescription('Adversaire').setRequired(true)),
    )
    .addSubcommand((s) =>
      s
        .setName('connect4')
        .setDescription('Puissance 4 contre un autre joueur')
        .addUserOption((o) => o.setName('adversaire').setDescription('Adversaire').setRequired(true)),
    )
    .addSubcommand((s) => s.setName('higherlower').setDescription('Plus ou moins solo'))
    .addSubcommand((s) => s.setName('trivia').setDescription('Question de culture Minecraft')),
  async execute(ix) {
    const sub = ix.options.getSubcommand();
    if (sub === 'tictactoe') await playTicTacToe(ix);
    else if (sub === 'connect4') await playConnect4(ix);
    else if (sub === 'higherlower') await playHigherLower(ix);
    else if (sub === 'trivia') await playTrivia(ix);
  },
};

export default game;
