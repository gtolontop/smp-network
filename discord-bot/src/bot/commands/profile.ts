import { SlashCommandBuilder } from 'discord.js';

import { linkFor, player, playerByName } from '../../db/queries.js';
import { hub } from '../../bridge/hub.js';
import { COLOR, baseEmbed, fail } from '../../utils/embeds.js';
import { avatarUrlFor, bodyUrlFor } from '../../utils/format.js';
import { discordRelative, humanDuration } from '../../utils/time.js';
import type { SlashCommand } from '../client.js';

const profile: SlashCommand = {
  name: 'profile',
  data: new SlashCommandBuilder()
    .setName('profile')
    .setDescription("Afficher le profil d'un joueur (toi si omis).")
    .addStringOption((o) =>
      o.setName('joueur').setDescription('Pseudo Minecraft').setRequired(false),
    ),
  async execute(ix) {
    const target = ix.options.getString('joueur');
    let row = target ? playerByName(target) : undefined;

    if (!target) {
      const link = linkFor(ix.user.id);
      if (!link) {
        await ix.reply({
          embeds: [fail('Lie ton compte avec `/link` pour voir ton profil sans argument.')],
          ephemeral: true,
        });
        return;
      }
      row = player(link.mcUuid);
    }

    if (!row) {
      await ix.reply({ embeds: [fail('Joueur inconnu.')], ephemeral: true });
      return;
    }

    const online = hub.playerByName(row.mcName);

    await ix.reply({
      embeds: [
        baseEmbed({
          title: row.mcName,
          thumbnail: avatarUrlFor(row.mcName, 128),
          image: bodyUrlFor(row.mcName),
          color: online ? COLOR.success : COLOR.muted,
          sections: [
            { name: 'Statut', value: online ? `En ligne · *${online.server ?? '?'}*` : 'Hors ligne', inline: true },
            { name: 'Temps de jeu', value: humanDuration(row.playtimeSec), inline: true },
            { name: 'Première co', value: discordRelative(row.firstSeen), inline: true },
            { name: 'Dernière co', value: discordRelative(row.lastSeen), inline: true },
            { name: 'Kills / Morts', value: `${row.kills} / ${row.deaths}`, inline: true },
            {
              name: 'Blocs cassés / posés',
              value: `${row.blocksBroken.toLocaleString('fr-FR')} / ${row.blocksPlaced.toLocaleString('fr-FR')}`,
              inline: true,
            },
          ],
        }),
      ],
    });
  },
};

export default profile;
