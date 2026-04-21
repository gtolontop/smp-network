import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { audit } from '../modules/auditLog.js';
import { assertMod, assertAdmin } from '../../utils/perms.js';
import { ok, fail } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

async function runConsole(
  target: 'lobby' | 'survival',
  command: string,
): Promise<{ ok: boolean; output: string }> {
  const res = await hub.rpc(target, 'console', { command });
  if (!res.ok) return { ok: false, output: res.error ?? 'unknown error' };
  const data = res.data as { output?: string } | undefined;
  return { ok: true, output: data?.output ?? '' };
}

export const kick: SlashCommand = {
  name: 'kick',
  data: new SlashCommandBuilder()
    .setName('kick')
    .setDescription('Kick un joueur.')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true))
    .addStringOption((o) => o.setName('raison').setDescription('Raison').setRequired(false)),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const player = ix.options.getString('joueur', true);
    const reason = ix.options.getString('raison') ?? 'Kick par un modérateur';
    const res = await runConsole('survival', `kick ${player} ${reason}`);
    await ix.reply({
      embeds: [res.ok ? ok(`Kick: **${player}**.`) : fail(`Échec: ${res.output}`)],
      ephemeral: true,
    });
    await audit({ actor: ix.user.tag, action: 'kick', target: player, details: reason, ok: res.ok });
  },
};

export const ban: SlashCommand = {
  name: 'ban',
  data: new SlashCommandBuilder()
    .setName('ban')
    .setDescription('Bannir un joueur (Velocity).')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true))
    .addStringOption((o) => o.setName('raison').setDescription('Raison').setRequired(false))
    .addIntegerOption((o) =>
      o.setName('durée_min').setDescription('Durée en minutes (permanent si absent)').setMinValue(1),
    ),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const player = ix.options.getString('joueur', true);
    const reason = ix.options.getString('raison') ?? 'Ban';
    const dur = ix.options.getInteger('durée_min');
    const cmd = dur ? `tempban ${player} ${dur}m ${reason}` : `ban ${player} ${reason}`;
    const res = await runConsole('survival', cmd);
    await ix.reply({
      embeds: [res.ok ? ok(`Ban: **${player}**${dur ? ` (${dur} min)` : ' (permanent)'}.`) : fail(`Échec: ${res.output}`)],
      ephemeral: true,
    });
    await audit({
      actor: ix.user.tag,
      action: dur ? 'tempban' : 'ban',
      target: player,
      details: reason,
      ok: res.ok,
    });
  },
};

export const unban: SlashCommand = {
  name: 'unban',
  data: new SlashCommandBuilder()
    .setName('unban')
    .setDescription('Dé-bannir un joueur.')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true)),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const player = ix.options.getString('joueur', true);
    const res = await runConsole('survival', `pardon ${player}`);
    await ix.reply({
      embeds: [res.ok ? ok(`Pardon: **${player}**.`) : fail(`Échec: ${res.output}`)],
      ephemeral: true,
    });
    await audit({ actor: ix.user.tag, action: 'unban', target: player, ok: res.ok });
  },
};

export const mute: SlashCommand = {
  name: 'mute',
  data: new SlashCommandBuilder()
    .setName('mute')
    .setDescription('Rendre muet un joueur.')
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true))
    .addIntegerOption((o) =>
      o.setName('durée_min').setDescription('Durée en minutes').setMinValue(1).setRequired(true),
    )
    .addStringOption((o) => o.setName('raison').setDescription('Raison')),
  async execute(ix) {
    if (!(await assertMod(ix))) return;
    const player = ix.options.getString('joueur', true);
    const dur = ix.options.getInteger('durée_min', true);
    const reason = ix.options.getString('raison') ?? 'Mute';
    const res = await runConsole('survival', `mute ${player} ${dur}m ${reason}`);
    await ix.reply({
      embeds: [res.ok ? ok(`Mute: **${player}** ${dur} min.`) : fail(`Échec: ${res.output}`)],
      ephemeral: true,
    });
    await audit({ actor: ix.user.tag, action: 'mute', target: player, details: reason, ok: res.ok });
  },
};

export const whitelist: SlashCommand = {
  name: 'whitelist',
  data: new SlashCommandBuilder()
    .setName('whitelist')
    .setDescription('Gérer la whitelist.')
    .addStringOption((o) =>
      o
        .setName('action')
        .setDescription('Action')
        .setRequired(true)
        .addChoices({ name: 'add', value: 'add' }, { name: 'remove', value: 'remove' }),
    )
    .addStringOption((o) => o.setName('joueur').setDescription('Pseudo MC').setRequired(true)),
  async execute(ix) {
    if (!(await assertAdmin(ix))) return;
    const action = ix.options.getString('action', true);
    const player = ix.options.getString('joueur', true);
    const res = await runConsole('survival', `whitelist ${action} ${player}`);
    await ix.reply({
      embeds: [res.ok ? ok(`whitelist ${action}: **${player}**.`) : fail(`Échec: ${res.output}`)],
      ephemeral: true,
    });
    await audit({ actor: ix.user.tag, action: `whitelist-${action}`, target: player, ok: res.ok });
  },
};
