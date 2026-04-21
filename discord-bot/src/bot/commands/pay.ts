import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { linkFor } from '../../db/queries.js';
import { audit } from '../modules/auditLog.js';
import { ok, fail } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const pay: SlashCommand = {
  name: 'pay',
  data: new SlashCommandBuilder()
    .setName('pay')
    .setDescription('Envoyer des pièces à un autre joueur (depuis Discord).')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true))
    .addIntegerOption((o) =>
      o.setName('montant').setDescription('Montant').setRequired(true).setMinValue(1),
    ),
  async execute(ix) {
    const link = linkFor(ix.user.id);
    if (!link) {
      await ix.reply({ embeds: [fail('Lie ton compte avec `/link` avant de payer.')], ephemeral: true });
      return;
    }
    const target = ix.options.getString('joueur', true);
    const amount = ix.options.getInteger('montant', true);
    if (target.toLowerCase() === link.mcName.toLowerCase()) {
      await ix.reply({ embeds: [fail('Tu ne peux pas te payer toi-même.')], ephemeral: true });
      return;
    }

    const res = await hub.rpc('survival', 'economy_transfer', {
      fromUuid: link.mcUuid,
      fromName: link.mcName,
      toName: target,
      amount,
    });
    if (!res.ok) {
      await ix.reply({ embeds: [fail(res.error ?? 'Transfert échoué.')], ephemeral: true });
      return;
    }
    const data = res.data as { newBalance?: number } | undefined;
    await ix.reply({
      embeds: [
        ok(
          `**${link.mcName}** → **${target}** · ${amount} pièces.${
            typeof data?.newBalance === 'number' ? ` Solde: ${data.newBalance}.` : ''
          }`,
        ),
      ],
      ephemeral: true,
    });
    await audit({ actor: ix.user.tag, action: 'pay', target, details: `${amount} · ${link.mcName}`, ok: true });
  },
};

export default pay;
