import { Events, type Client } from 'discord.js';

import { ask } from '../../ai/glm.js';
import { child } from '../../utils/logger.js';
import { truncate } from '../../utils/format.js';

const log = child({ mod: 'ai-mention' });

/**
 * Lets users talk to the assistant by mentioning the bot. Keeps each
 * reply short and references the author so threads stay readable.
 */
export function initAiMention(client: Client): void {
  client.on(Events.MessageCreate, async (msg) => {
    if (msg.author.bot || !client.user) return;
    if (!msg.mentions.users.has(client.user.id)) return;
    const content = msg.content
      .replace(new RegExp(`<@!?${client.user.id}>`, 'g'), '')
      .trim();
    if (!content) return;

    try {
      await msg.channel.sendTyping().catch(() => undefined);
      const answer = await ask({
        prompt: content,
        userName: msg.member?.displayName ?? msg.author.username,
      });
      await msg.reply({ content: truncate(answer, 1800), allowedMentions: { parse: [] } });
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'ai mention failed');
    }
  });
}
