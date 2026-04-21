import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { audit } from '../modules/auditLog.js';
import { assertAdmin } from '../../utils/perms.js';
import { COLOR, baseEmbed, fail } from '../../utils/embeds.js';
import { truncate } from '../../utils/format.js';
import type { SlashCommand } from '../client.js';

const consoleCmd: SlashCommand = {
  name: 'console',
  data: new SlashCommandBuilder()
    .setName('console')
    .setDescription('Exécuter une commande console sur un serveur.')
    .addStringOption((o) =>
      o
        .setName('target')
        .setDescription('Cible')
        .setRequired(true)
        .addChoices(
          { name: 'lobby', value: 'lobby' },
          { name: 'survival', value: 'survival' },
          { name: 'velocity', value: 'velocity' },
        ),
    )
    .addStringOption((o) => o.setName('commande').setDescription('Commande sans /').setRequired(true)),
  async execute(ix) {
    if (!(await assertAdmin(ix))) return;
    const target = ix.options.getString('target', true) as 'lobby' | 'survival' | 'velocity';
    const command = ix.options.getString('commande', true);
    await ix.deferReply({ ephemeral: true });
    const res = await hub.rpc(target, 'console', { command });
    if (!res.ok) {
      await ix.editReply({ embeds: [fail(res.error ?? 'RPC échoué.')] });
      await audit({ actor: ix.user.tag, action: 'console', target, details: command, ok: false });
      return;
    }
    const data = res.data as { output?: string } | undefined;
    const output = truncate(data?.output ?? '(aucune sortie)', 3500);
    await ix.editReply({
      embeds: [
        baseEmbed({
          title: `console · ${target}`,
          description: ['```', `> ${command}`, output, '```'].join('\n'),
          color: COLOR.muted,
        }),
      ],
    });
    await audit({ actor: ix.user.tag, action: 'console', target, details: command, ok: true });
  },
};

export default consoleCmd;
