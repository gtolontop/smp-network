import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  ComponentType,
  SlashCommandBuilder,
} from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { audit } from '../modules/auditLog.js';
import { assertAdmin } from '../../utils/perms.js';
import { ok, fail, baseEmbed, COLOR } from '../../utils/embeds.js';
import { ALL_TARGET, describeConnectedOrigins, normalizeTargetInput } from '../network.js';
import type { SlashCommand } from '../client.js';

const restart: SlashCommand = {
  name: 'restart',
  data: new SlashCommandBuilder()
    .setName('restart')
    .setDescription("Redémarrer un serveur (avec confirmation).")
    .addStringOption((o) =>
      o
        .setName('target')
        .setDescription('Serveur ou proxy')
        .setRequired(true)
    )
    .addStringOption((o) => o.setName('raison').setDescription('Raison (annoncée)')),
  async execute(ix) {
    if (!(await assertAdmin(ix))) return;
    const target = normalizeTargetInput(ix.options.getString('target', true));
    if (target === ALL_TARGET) {
      await ix.reply({ embeds: [fail('`all` n\'est pas autorisé pour `/restart`.')], ephemeral: true });
      return;
    }
    const reason = ix.options.getString('raison') ?? 'Maintenance';

    const confirm = new ButtonBuilder()
      .setCustomId('restart:yes')
      .setLabel(`Confirmer redémarrage ${target}`)
      .setStyle(ButtonStyle.Danger);
    const cancel = new ButtonBuilder()
      .setCustomId('restart:no')
      .setLabel('Annuler')
      .setStyle(ButtonStyle.Secondary);

    const msg = await ix.reply({
      embeds: [
        baseEmbed({
          title: 'Confirmation',
          description: `Tu vas redémarrer **${target}**. Les joueurs seront kick.`,
          color: COLOR.warn,
        }),
      ],
      components: [new ActionRowBuilder<ButtonBuilder>().addComponents(confirm, cancel)],
      ephemeral: true,
      fetchReply: true,
    });

    const btn = await msg
      .awaitMessageComponent({
        componentType: ComponentType.Button,
        filter: (b) => b.user.id === ix.user.id,
        time: 30_000,
      })
      .catch(() => null);

    if (!btn || btn.customId === 'restart:no') {
      await ix.editReply({ content: 'Annulé.', components: [], embeds: [] });
      return;
    }

    await btn.deferUpdate();
    hub.broadcast(
      { kind: 'broadcast', target, message: `Redémarrage imminent — ${reason}`, prefix: 'Système' },
      (p) => p.origin === target,
    );
    const res = await hub.rpc(target, 'console', { command: 'stop' });
    await ix.editReply({
      embeds: [
        res.ok
          ? ok(`Commande envoyée à ${target}.`)
          : fail(`${res.error ?? 'RPC échoué.'}\nCibles connectées: ${describeConnectedOrigins()}`),
      ],
      components: [],
    });
    await audit({ actor: ix.user.tag, action: 'restart', target, details: reason, ok: res.ok });
  },
};

export default restart;
