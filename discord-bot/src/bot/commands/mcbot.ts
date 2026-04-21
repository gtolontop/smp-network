import { SlashCommandBuilder } from 'discord.js';

import { mcbot } from '../../mcbot/index.js';
import { audit } from '../modules/auditLog.js';
import { assertMod } from '../../utils/perms.js';
import { COLOR, baseEmbed, ok, fail } from '../../utils/embeds.js';
import { humanDuration } from '../../utils/time.js';
import type { SlashCommand } from '../client.js';

const cmd: SlashCommand = {
  name: 'mcbot',
  data: new SlashCommandBuilder()
    .setName('mcbot')
    .setDescription('Piloter le bot qui joue en jeu.')
    .addSubcommand((s) => s.setName('status').setDescription('État actuel du bot'))
    .addSubcommand((s) =>
      s
        .setName('say')
        .setDescription('Faire parler le bot')
        .addStringOption((o) => o.setName('message').setDescription('Message').setRequired(true)),
    )
    .addSubcommand((s) =>
      s
        .setName('follow')
        .setDescription('Suivre un joueur')
        .addStringOption((o) => o.setName('joueur').setDescription('Pseudo').setRequired(true)),
    )
    .addSubcommand((s) =>
      s
        .setName('goto')
        .setDescription('Aller à des coordonnées')
        .addIntegerOption((o) => o.setName('x').setDescription('X').setRequired(true))
        .addIntegerOption((o) => o.setName('y').setDescription('Y').setRequired(true))
        .addIntegerOption((o) => o.setName('z').setDescription('Z').setRequired(true)),
    )
    .addSubcommand((s) => s.setName('stop').setDescription('Arrêter le bot de bouger')),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const sub = ix.options.getSubcommand();

    if (sub === 'status') {
      const s = mcbot.state();
      if (!s.online) {
        await ix.reply({ embeds: [fail('Bot hors ligne.')], ephemeral: true });
        return;
      }
      await ix.reply({
        embeds: [
          baseEmbed({
            title: 'MC bot',
            color: COLOR.success,
            sections: [
              { name: 'Position', value: s.position ? `\`${s.position.x} ${s.position.y} ${s.position.z}\` · ${s.position.world}` : '—', inline: true },
              { name: 'Vie / Faim', value: `${s.health?.toFixed(0) ?? '—'} / ${s.food?.toFixed(0) ?? '—'}`, inline: true },
              { name: 'Suit', value: s.following ?? '—', inline: true },
              {
                name: 'Uptime',
                value: s.uptimeSince ? humanDuration(Math.floor(Date.now() / 1000) - s.uptimeSince) : '—',
                inline: true,
              },
            ],
          }),
        ],
        ephemeral: true,
      });
      return;
    }

    if (sub === 'say') {
      const message = ix.options.getString('message', true);
      mcbot.say(message);
      await ix.reply({ embeds: [ok(`Envoyé: "${message}"`)], ephemeral: true });
      await audit({ actor: ix.user.tag, action: 'mcbot.say', details: message });
      return;
    }

    if (sub === 'follow') {
      const player = ix.options.getString('joueur', true);
      const res = await mcbot.follow(player);
      await ix.reply({ embeds: [ok(res)], ephemeral: true });
      await audit({ actor: ix.user.tag, action: 'mcbot.follow', target: player });
      return;
    }

    if (sub === 'goto') {
      const x = ix.options.getInteger('x', true);
      const y = ix.options.getInteger('y', true);
      const z = ix.options.getInteger('z', true);
      const res = await mcbot.gotoCoords(x, y, z);
      await ix.reply({ embeds: [ok(res)], ephemeral: true });
      await audit({ actor: ix.user.tag, action: 'mcbot.goto', details: `${x} ${y} ${z}` });
      return;
    }

    if (sub === 'stop') {
      await ix.reply({ embeds: [ok(mcbot.stopMoving())], ephemeral: true });
      await audit({ actor: ix.user.tag, action: 'mcbot.stop' });
      return;
    }
  },
};

export default cmd;
