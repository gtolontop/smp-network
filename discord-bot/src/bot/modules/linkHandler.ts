import type { Client } from 'discord.js';

import { config } from '../../config.js';
import { hub } from '../../bridge/hub.js';
import type { LinkAttemptPacket, Packet } from '../../bridge/protocol.js';
import type { Peer } from '../../bridge/hub.js';
import { completeLink, linkFor } from '../../db/queries.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'link-handler' });

export function initLinkHandler(client: Client): void {
  hub.on('packet', async (peer: Peer, pkt: Packet) => {
    if (pkt.kind !== 'link_attempt') return;

    const { code, uuid, name } = pkt as LinkAttemptPacket;

    const link = completeLink(code, uuid, name);

    if (!link) {
      peer.socket.send(
        JSON.stringify({
          kind: 'link_result',
          uuid,
          ok: false,
          discordTag: '',
          error: 'Code invalide ou expiré.',
        }),
      );
      log.info({ uuid, name, code }, 'link attempt failed — invalid/expired code');
      return;
    }

    log.info({ discordId: link.discordId, mcName: link.mcName }, 'account linked');

    let discordTag = link.mcName;

    try {
      const guild = client.guilds.cache.get(config.discord.guildId);
      if (guild) {
        const member = await guild.members.fetch(link.discordId);
        discordTag = member.user.tag;

        if (config.roles.linked) {
          await member.roles.add(config.roles.linked, 'Account linked via /link');
        }
      }
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'failed to assign linked role');
    }

    peer.socket.send(
      JSON.stringify({
        kind: 'link_result',
        uuid,
        ok: true,
        discordTag,
      }),
    );
  });

  log.info('link handler ready');
}
