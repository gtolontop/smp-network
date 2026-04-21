import { ask } from '../ai/glm.js';
import { child } from '../utils/logger.js';

import { mcbot } from './index.js';

const log = child({ mod: 'mcbot-brain' });

/**
 * Listens for `!assistant <text>` in in-game chat and answers back in
 * chat using the GLM model. Reactions are kept short so the chat stays
 * readable.
 */
export function initBrain(): void {
  mcbot.onReady((bot) => {
    bot.on('chat', async (username, message) => {
      if (username === bot.username) return;
      const trigger = /^!assistant\s+(.+)$/i.exec(message);
      if (!trigger) return;
      const prompt = trigger[1].trim();
      if (!prompt) return;
      log.info({ username, prompt }, 'mcbot brain prompt');
      try {
        const answer = await ask({
          prompt,
          userName: username,
          extraContext: `Tu parles dans le chat Minecraft. Sois concis, 1 ou 2 phrases max, pas de markdown.`,
        });
        for (const line of splitForChat(answer)) {
          bot.chat(line);
          await sleep(400);
        }
      } catch (err) {
        bot.chat(`erreur: ${err instanceof Error ? err.message : String(err)}`);
      }
    });

    bot.on('playerJoined', (p) => {
      if (p.username === bot.username) return;
      setTimeout(() => bot.chat(`salut ${p.username}`), 1500);
    });
  });
}

function splitForChat(input: string): string[] {
  const flat = input.replace(/[\r\n]+/g, ' ').trim();
  const out: string[] = [];
  for (let i = 0; i < flat.length; i += 240) out.push(flat.slice(i, i + 240));
  return out.slice(0, 3);
}

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}
