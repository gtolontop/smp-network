import { SlashCommandBuilder } from 'discord.js';

import { db } from '../../db/index.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

interface GameStatRow {
  game: string;
  wins: number;
  losses: number;
  draws: number;
}

const stats: SlashCommand = {
  name: 'stats',
  data: new SlashCommandBuilder()
    .setName('stats')
    .setDescription('Tes statistiques de mini-jeux.')
    .addUserOption((o) => o.setName('joueur').setDescription('Autre joueur').setRequired(false)),
  async execute(ix) {
    const user = ix.options.getUser('joueur') ?? ix.user;
    const rows = db
      .prepare('SELECT game, wins, losses, draws FROM game_stats WHERE discord_id = ? ORDER BY game')
      .all(user.id) as unknown as GameStatRow[];
    if (!rows.length) {
      await ix.reply({
        embeds: [
          baseEmbed({
            title: `Stats · ${user.username}`,
            description: `Aucune partie jouée pour l'instant.`,
            color: COLOR.muted,
          }),
        ],
      });
      return;
    }
    const sections = rows.map((r) => {
      const total = r.wins + r.losses + r.draws;
      const rate = total ? (r.wins / total) * 100 : 0;
      return {
        name: r.game,
        value: `Victoires \`${r.wins}\` · Défaites \`${r.losses}\` · Nuls \`${r.draws}\` (${rate.toFixed(0)}% WR)`,
      };
    });
    await ix.reply({
      embeds: [
        baseEmbed({
          title: `Stats · ${user.username}`,
          color: COLOR.accent,
          sections,
          thumbnail: user.displayAvatarURL({ size: 128 }),
        }),
      ],
    });
  },
};

export default stats;
