import { ActivityType, type Client } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'presence' });
const ROTATE_MS = 30_000;

/**
 * Cycles the bot presence through a few live statistics so the bot
 * tile itself tells people what's happening on the server.
 */
export function initPresenceRotation(client: Client): void {
  let step = 0;
  const tick = () => {
    const roster = hub.allRoster();
    const survival = hub.byOrigin('survival')?.telemetry;
    const msgs: Array<{ text: string; type: ActivityType }> = [
      { text: `${roster.length} joueur(s) en ligne`, type: ActivityType.Watching },
      { text: 'le SMP · /help', type: ActivityType.Watching },
    ];
    if (survival) {
      msgs.push({
        text: `TPS ${survival.tps1m.toFixed(1)} · ${survival.online}/${survival.maxOnline}`,
        type: ActivityType.Playing,
      });
    }
    const pick = msgs[step % msgs.length]!;
    try {
      client.user?.setPresence({
        status: 'online',
        activities: [{ name: pick.text, type: pick.type }],
      });
    } catch (err) {
      log.debug({ err: err instanceof Error ? err.message : String(err) }, 'presence update failed');
    }
    step++;
  };
  tick();
  setInterval(tick, ROTATE_MS).unref();
}
