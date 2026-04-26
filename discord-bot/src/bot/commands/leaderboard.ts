import { SlashCommandBuilder } from 'discord.js';

import { topPlayers } from '../../db/queries.js';
import { loadWorldPlayerStats } from '../../stats/worldStats.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { humanDuration } from '../../utils/time.js';
import type { SlashCommand } from '../client.js';

const FIELDS = {
  playtime: { col: 'playtime_sec', label: 'Temps de jeu', format: (n: number) => humanDuration(n) },
  kills: { col: 'kills', label: 'Kills', format: (n: number) => n.toString() },
  deaths: { col: 'deaths', label: 'Morts', format: (n: number) => n.toString() },
  mine: { col: 'blocks_broken', label: 'Blocs cassés', format: (n: number) => n.toLocaleString('fr-FR') },
  build: { col: 'blocks_placed', label: 'Blocs posés', format: (n: number) => n.toLocaleString('fr-FR') },
} as const;

type Key = keyof typeof FIELDS;

const leaderboard: SlashCommand = {
  name: 'leaderboard',
  data: new SlashCommandBuilder()
    .setName('leaderboard')
    .setDescription('Classement serveur.')
    .addStringOption((o) =>
      o
        .setName('critère')
        .setDescription('Critère de classement')
        .setRequired(true)
        .addChoices(
          { name: 'Temps de jeu', value: 'playtime' },
          { name: 'Kills', value: 'kills' },
          { name: 'Morts', value: 'deaths' },
          { name: 'Blocs cassés', value: 'mine' },
          { name: 'Blocs posés', value: 'build' },
        ),
    ),
  async execute(ix) {
    const key = ix.options.getString('critère', true) as Key;
    const spec = FIELDS[key];
    if (key === 'build') {
      await ix.reply({
        embeds: [
          baseEmbed({
            title: `Classement · ${spec.label}`,
            description: "*Ce classement n'est pas encore fiable côté bot, donc il est temporairement masqué au lieu d'afficher de fausses données.*",
            color: COLOR.warn,
          }),
        ],
      });
      return;
    }

    if (key === 'mine') {
      const rows = loadWorldPlayerStats().slice(0, 10);
      const body = rows.length
        ? rows
            .map((r, i) => {
              const medal = i < 3 ? ['◉', '◎', '◯'][i] : ` ${i + 1}.`;
              return `${medal} \`${r.mcName}\` — ${r.blocksBroken.toLocaleString('fr-FR')}`;
            })
            .join('\n')
        : '*Aucune donnée pour le moment.*';

      await ix.reply({
        embeds: [
          baseEmbed({
            title: `Classement · ${spec.label}`,
            description: body,
            color: COLOR.accent,
          }),
        ],
      });
      return;
    }

    const rows = topPlayers(spec.col as Parameters<typeof topPlayers>[0], 10);
    const body = rows.length
      ? rows
          .map((r, i) => {
            const value =
              key === 'playtime'
                ? r.playtimeSec
                : key === 'kills'
                  ? r.kills
                  : key === 'deaths'
                    ? r.deaths
                    : key === 'mine'
                      ? r.blocksBroken
                      : r.blocksPlaced;
            const medal = i < 3 ? ['◉', '◎', '◯'][i] : ` ${i + 1}.`;
            return `${medal} \`${r.mcName}\` — ${spec.format(value)}`;
          })
          .join('\n')
      : '*Aucune donnée pour le moment.*';

    await ix.reply({
      embeds: [
        baseEmbed({
          title: `Classement · ${spec.label}`,
          description: body,
          color: COLOR.accent,
        }),
      ],
    });
  },
};

export default leaderboard;
