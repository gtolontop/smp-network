import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { audit } from '../modules/auditLog.js';
import { assertMod } from '../../utils/perms.js';
import { fail, ok } from '../../utils/embeds.js';
import {
  ALL_TARGET,
  describeConnectedOrigins,
  normalizeTargetInput,
  resolveConnectedOrigin,
} from '../network.js';
import type { SlashCommand } from '../client.js';

const broadcast: SlashCommand = {
  name: 'broadcast',
  data: new SlashCommandBuilder()
    .setName('broadcast')
    .setDescription('Diffuser un message sur tout le réseau.')
    .addStringOption((o) => o.setName('message').setDescription('Message à diffuser').setRequired(true))
    .addStringOption((o) =>
      o
        .setName('target')
        .setDescription('all, proxy, ou nom du serveur'),
    ),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const message = ix.options.getString('message', true);
    const target = normalizeTargetInput(ix.options.getString('target') ?? ALL_TARGET);

    const sent =
      target === ALL_TARGET
        ? hub.broadcast({ kind: 'broadcast', target, message, prefix: 'Discord' })
        : hub.broadcast(
            { kind: 'broadcast', target, message, prefix: 'Discord' },
            (peer) => peer.origin === (resolveConnectedOrigin(target) ?? target),
          );

    if (sent === 0) {
      await ix.reply({
        embeds: [fail(`Aucune cible connectée pour \`${target}\`. Cibles: ${describeConnectedOrigins()}`)],
        ephemeral: true,
      });
      await audit({ actor: ix.user.tag, action: 'broadcast', target, details: message, ok: false });
      return;
    }

    await ix.reply({ embeds: [ok(`Diffusé sur \`${target}\`.`)], ephemeral: true });
    await audit({ actor: ix.user.tag, action: 'broadcast', target, details: message, ok: true });
  },
};

export default broadcast;
