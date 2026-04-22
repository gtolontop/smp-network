import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { audit } from '../modules/auditLog.js';
import { assertAdmin } from '../../utils/perms.js';
import { ok, fail } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const backup: SlashCommand = {
  name: 'backup',
  data: new SlashCommandBuilder()
    .setName('backup')
    .setDescription('Déclencher une sauvegarde du monde survival (save-all + save-off).')
    .addStringOption((o) =>
      o
        .setName('mode')
        .setDescription('Mode')
        .addChoices(
          { name: 'quick (save-all)', value: 'quick' },
          { name: 'frozen (save-off + save-all flush + save-on)', value: 'frozen' },
        ),
    ),
  async execute(ix) {
    if (!(await assertAdmin(ix))) return;
    const mode = ix.options.getString('mode') ?? 'quick';
    await ix.deferReply({ ephemeral: true });
    try {
      if (mode === 'frozen') {
        await hub.rpc('survival', 'console', { command: 'save-off' });
        await hub.rpc('survival', 'console', { command: 'save-all flush' });
        await hub.rpc('survival', 'console', { command: 'save-on' });
      } else {
        await hub.rpc('survival', 'console', { command: 'save-all' });
      }
      await ix.editReply({ embeds: [ok(`Sauvegarde \`${mode}\` effectuée.`)] });
      await audit({ actor: ix.user.tag, action: 'backup', details: mode, ok: true });
    } catch (err) {
      await ix.editReply({ embeds: [fail(err instanceof Error ? err.message : String(err))] });
      await audit({ actor: ix.user.tag, action: 'backup', details: mode, ok: false });
    }
  },
};

export default backup;
