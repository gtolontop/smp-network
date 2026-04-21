import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { audit } from '../modules/auditLog.js';
import { assertMod } from '../../utils/perms.js';
import { ok } from '../../utils/embeds.js';
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
        .setDescription('Serveur ciblé')
        .addChoices(
          { name: 'Tout le réseau', value: 'all' },
          { name: 'Lobby', value: 'lobby' },
          { name: 'Survival', value: 'survival' },
        ),
    ),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const message = ix.options.getString('message', true);
    const target = (ix.options.getString('target') ?? 'all') as 'all' | 'lobby' | 'survival';

    hub.broadcast(
      { kind: 'broadcast', target, message, prefix: 'Discord' },
      (p) => target === 'all' || p.origin === target,
    );
    await ix.reply({ embeds: [ok(`Diffusé sur \`${target}\`.`)], ephemeral: true });
    await audit({ actor: ix.user.tag, action: 'broadcast', details: message });
  },
};

export default broadcast;
