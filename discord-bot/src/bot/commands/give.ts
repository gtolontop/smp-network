import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { queueGive, playerByName, upsertPlayer } from '../../db/queries.js';
import { audit } from '../modules/auditLog.js';
import { assertAdmin } from '../../utils/perms.js';
import { ok, fail, baseEmbed, COLOR } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const give: SlashCommand = {
  name: 'give',
  data: new SlashCommandBuilder()
    .setName('give')
    .setDescription('Donner un item à un joueur (in-game ou en attente).')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true))
    .addStringOption((o) =>
      o.setName('item').setDescription('Identifiant item (ex: minecraft:diamond)').setRequired(true),
    )
    .addIntegerOption((o) =>
      o.setName('quantité').setDescription('Quantité').setMinValue(1).setMaxValue(64 * 36),
    ),
  async execute(ix) {
    if (!(await assertAdmin(ix))) return;
    const name = ix.options.getString('joueur', true);
    const item = ix.options.getString('item', true);
    const amount = ix.options.getInteger('quantité') ?? 1;

    const online = hub.playerByName(name);
    if (online) {
      const res = await hub.rpc('survival', 'console', {
        command: `give ${name} ${item} ${amount}`,
      });
      await ix.reply({
        embeds: [res.ok ? ok(`**${name}** a reçu ${amount} × ${item}.`) : fail(res.error ?? 'RPC échoué.')],
        ephemeral: true,
      });
      await audit({ actor: ix.user.tag, action: 'give', target: name, details: `${amount} × ${item}`, ok: res.ok });
      return;
    }

    const row = playerByName(name);
    if (!row) {
      await ix.reply({ embeds: [fail('Joueur inconnu. Il doit s\'être connecté au moins une fois.')], ephemeral: true });
      return;
    }
    queueGive(ix.user.tag, row.mcUuid, row.mcName, item, amount);
    upsertPlayer(row.mcUuid, row.mcName);
    await ix.reply({
      embeds: [
        baseEmbed({
          color: COLOR.warn,
          description: `**${name}** est hors ligne — ${amount} × \`${item}\` sera remis à sa prochaine connexion.`,
        }),
      ],
      ephemeral: true,
    });
    await audit({ actor: ix.user.tag, action: 'give(queued)', target: name, details: `${amount} × ${item}` });
  },
};

export default give;
