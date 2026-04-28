import { Events } from 'discord.js';
import type { Interaction } from 'discord.js';

import { child } from '../../utils/logger.js';
import { client, commands } from '../client.js';

const log = child({ mod: 'interaction' });

client.on(Events.InteractionCreate, async (ix: Interaction) => {
  try {
    if (ix.isAutocomplete()) {
      const cmd = commands.get(ix.commandName);
      if (cmd?.autocomplete) {
        await cmd.autocomplete(ix);
      }
      return;
    }
    if (ix.isChatInputCommand()) {
      const cmd = commands.get(ix.commandName);
      if (!cmd) {
        await ix.reply({ content: 'Commande inconnue.', ephemeral: true });
        return;
      }
      await cmd.execute(ix);
    }
  } catch (err) {
    log.error(
      { err: err instanceof Error ? err.message : String(err), cmd: (ix as { commandName?: string }).commandName },
      'interaction failed',
    );
    if (ix.isRepliable()) {
      const content = 'Erreur interne, regarde les logs.';
      try {
        if (ix.deferred || ix.replied) await ix.followUp({ content, ephemeral: true });
        else await ix.reply({ content, ephemeral: true });
      } catch {
        /* best effort */
      }
    }
  }
});
