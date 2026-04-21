import { REST, Routes } from 'discord.js';
import type { RESTPostAPIChatInputApplicationCommandsJSONBody } from 'discord.js';

import { config } from '../config.js';
import { child } from '../utils/logger.js';

import { commands } from './client.js';
import type { SlashCommand } from './client.js';

const log = child({ mod: 'registry' });

export function register(cmd: SlashCommand): void {
  commands.set(cmd.name, cmd);
}

export async function publishCommands(): Promise<void> {
  const rest = new REST({ version: '10' }).setToken(config.discord.token);
  const body = [...commands.values()].map((c) => {
    const data = c.data as unknown;
    if (data && typeof data === 'object' && 'toJSON' in (data as object)) {
      return (data as { toJSON: () => RESTPostAPIChatInputApplicationCommandsJSONBody }).toJSON();
    }
    return data as RESTPostAPIChatInputApplicationCommandsJSONBody;
  });
  const route =
    config.discord.guildId && config.discord.guildId.length > 0
      ? Routes.applicationGuildCommands(config.discord.clientId, config.discord.guildId)
      : Routes.applicationCommands(config.discord.clientId);
  const res = await rest.put(route, { body });
  log.info(
    { count: Array.isArray(res) ? res.length : body.length, guild: config.discord.guildId || 'global' },
    'commands published',
  );
}
