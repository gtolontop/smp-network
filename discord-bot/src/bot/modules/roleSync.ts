import { Events, type Client, type GuildMember } from 'discord.js';

import { config } from '../../config.js';
import { hub } from '../../bridge/hub.js';
import { linkFor } from '../../db/queries.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'role-sync' });

/**
 * Keeps a small set of Discord roles mirrored into LuckPerms groups on
 * the survival backend. Fires on add/remove and on link completion.
 */
export function initRoleSync(client: Client): void {
  const mapping: Array<{ role: string | undefined; group: string }> = [
    { role: config.roles.admin, group: 'admin' },
    { role: config.roles.mod, group: 'mod' },
    { role: config.roles.booster, group: 'booster' },
  ].filter((m) => !!m.role);

  if (!mapping.length) {
    log.info('no role mapping configured — skipping');
    return;
  }

  client.on(Events.GuildMemberUpdate, async (oldMember, newMember) => {
    try {
      const link = linkFor(newMember.id);
      if (!link) return;
      for (const m of mapping) {
        const before = oldMember.roles.cache.has(m.role!);
        const after = newMember.roles.cache.has(m.role!);
        if (before === after) continue;
        const cmd = after
          ? `lp user ${link.mcName} parent add ${m.group}`
          : `lp user ${link.mcName} parent remove ${m.group}`;
        await hub.rpc('survival', 'console', { command: cmd });
        log.info({ player: link.mcName, group: m.group, add: after }, 'role sync');
      }
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'role sync failed');
    }
  });

  client.on(Events.GuildMemberRemove, async (member) => {
    const link = linkFor(member.id);
    if (!link) return;
    for (const m of mapping) {
      try {
        await hub.rpc('survival', 'console', {
          command: `lp user ${link.mcName} parent remove ${m.group}`,
        });
      } catch {
        /* best effort */
      }
    }
  });

  // Light-weight initial sync on boot.
  setTimeout(async () => {
    const guild = client.guilds.cache.get(config.discord.guildId);
    if (!guild) return;
    const members = await guild.members.fetch();
    for (const member of members.values()) {
      const link = linkFor(member.id);
      if (!link) continue;
      for (const m of mapping) {
        if (member.roles.cache.has(m.role!)) {
          await hub.rpc('survival', 'console', {
            command: `lp user ${link.mcName} parent add ${m.group}`,
          });
        }
      }
    }
  }, 60_000).unref();
}
