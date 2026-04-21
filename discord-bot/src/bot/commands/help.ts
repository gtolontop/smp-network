import { SlashCommandBuilder } from 'discord.js';

import { COLOR, baseEmbed } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const help: SlashCommand = {
  name: 'help',
  data: new SlashCommandBuilder().setName('help').setDescription("Liste des commandes du bot."),
  async execute(ix) {
    await ix.reply({
      embeds: [
        baseEmbed({
          title: 'Commandes',
          color: COLOR.accent,
          description: [
            '**Joueurs**',
            '`/link` — lier un compte Discord à un compte Minecraft',
            '`/unlink` — retirer la liaison',
            '`/profile [joueur]` — voir les stats d\'un joueur',
            '`/who` — liste des joueurs en ligne',
            '`/status` — état du réseau',
            '`/host` — stats de la machine',
            '`/leaderboard <critère>` — classement serveur',
            '`/seen <joueur>` — dernière apparition',
            '`/pay <joueur> <montant>` — envoyer des pièces',
            '`/mail <joueur> <message>` — message offline',
            '`/ai <question>` — assistant IA (GLM)',
            '',
            '**Jeux**',
            '`/game tictactoe`, `/game connect4`, `/game higherlower`, `/game trivia`',
            '',
            '**Admin & modération**',
            '`/broadcast <message>` · `/kick` · `/ban` · `/unban` · `/mute` · `/whitelist`',
            '`/console <target> <cmd>` · `/give <joueur> <item> [n]`',
            '`/mcbot say|follow|goto|come|status` — piloter le bot in-game',
            '`/restart <target>` — redémarrage contrôlé',
          ].join('\n'),
          footer: 'smp-bot',
        }),
      ],
      ephemeral: true,
    });
  },
};

export default help;
