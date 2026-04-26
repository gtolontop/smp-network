import { SlashCommandBuilder } from 'discord.js';

import { audit } from '../modules/auditLog.js';
import { assertAdmin } from '../../utils/perms.js';
import { COLOR, baseEmbed, fail } from '../../utils/embeds.js';
import { truncate } from '../../utils/format.js';
import {
  ALL_TARGET,
  describeConnectedOrigins,
  listConnectedOrigins,
  normalizeTargetInput,
  runConsoleRpc,
  runConsoleRpcBatch,
} from '../network.js';
import type { SlashCommand } from '../client.js';

const consoleCmd: SlashCommand = {
  name: 'console',
  data: new SlashCommandBuilder()
    .setName('console')
    .setDescription('Exécuter une commande console sur un serveur ou tout le réseau.')
    .addStringOption((o) =>
      o
        .setName('target')
        .setDescription('Serveur, proxy, ou all')
        .setRequired(true)
    )
    .addStringOption((o) => o.setName('commande').setDescription('Commande sans /').setRequired(true)),
  async execute(ix) {
    if (!(await assertAdmin(ix))) return;
    const target = normalizeTargetInput(ix.options.getString('target', true));
    const command = ix.options.getString('commande', true);
    await ix.deferReply({ ephemeral: true });

    if (target === ALL_TARGET) {
      const origins = listConnectedOrigins();
      if (!origins.length) {
        await ix.editReply({ embeds: [fail('Aucun bridge connecté.')] });
        await audit({ actor: ix.user.tag, action: 'console', target, details: command, ok: false });
        return;
      }

      const results = await runConsoleRpcBatch(origins, command);
      const okAll = results.every(({ result }) => result.ok);
      const description = truncate(
        results
          .map(({ origin, result }) => {
            const data = result.data as { output?: string } | undefined;
            const output = result.ok ? data?.output ?? '(aucune sortie)' : result.error ?? 'RPC échoué.';
            return `**${origin}**\n\`\`\`\n> ${command}\n${truncate(output, 700)}\n\`\`\``;
          })
          .join('\n'),
        3900,
      );
      await ix.editReply({
        embeds: [
          baseEmbed({
            title: 'console · all',
            description,
            color: okAll ? COLOR.muted : COLOR.warn,
          }),
        ],
      });
      await audit({ actor: ix.user.tag, action: 'console', target, details: command, ok: okAll });
      return;
    }

    const res = await runConsoleRpc(target, command);
    if (!res.ok) {
      await ix.editReply({
        embeds: [
          fail(`${res.error ?? 'RPC échoué.'}\nCibles connectées: ${describeConnectedOrigins()}`),
        ],
      });
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
