import { Events } from 'discord.js';

import { child } from '../../utils/logger.js';
import { client } from '../client.js';

const log = child({ mod: 'ready' });

client.once(Events.ClientReady, (c) => {
  log.info({ tag: c.user.tag, guilds: c.guilds.cache.size }, 'bot ready');
});
