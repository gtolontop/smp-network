import { hub } from '../../bridge/hub.js';
import { markGivesClaimed, markMailDelivered, pendingGivesFor, pendingMailFor, upsertPlayer, addPlaytime } from '../../db/queries.js';
import type { Packet } from '../../bridge/protocol.js';
import type { Peer } from '../../bridge/hub.js';
import { child } from '../../utils/logger.js';

const log = child({ mod: 'join-delivery' });
const sessionStarts = new Map<string, number>();

/**
 * On every join, delivers any mail the player had queued and pushes
 * pending /give items as give commands. Also records per-session
 * playtime when the player leaves.
 */
export function initJoinDelivery(): void {
  hub.on('packet', async (peer: Peer, pkt: Packet) => {
    if (pkt.kind === 'join') {
      sessionStarts.set(pkt.uuid, Math.floor(Date.now() / 1000));
      upsertPlayer(pkt.uuid, pkt.name);

      const mails = pendingMailFor(pkt.uuid);
      if (mails.length && peer.origin !== 'velocity') {
        for (const m of mails) {
          hub.send(peer.origin, {
            kind: 'tell',
            toUuid: pkt.uuid,
            message: `[mail · ${m.fromName}] ${m.body}`,
          });
        }
        markMailDelivered(mails.map((m) => m.id));
        log.info({ player: pkt.name, count: mails.length }, 'delivered mail');
      }

      const gives = pendingGivesFor(pkt.uuid);
      if (gives.length && peer.origin !== 'velocity') {
        for (const g of gives) {
          hub.send(peer.origin, {
            kind: 'console',
            id: `give-${g.id}`,
            target: peer.origin,
            command: `give ${g.toName} ${g.item} ${g.amount}`,
          });
        }
        markGivesClaimed(gives.map((g) => g.id));
        log.info({ player: pkt.name, count: gives.length }, 'flushed pending gives');
      }
    } else if (pkt.kind === 'leave') {
      const started = sessionStarts.get(pkt.uuid);
      if (started) {
        const delta = Math.floor(Date.now() / 1000) - started;
        if (delta > 0) addPlaytime(pkt.uuid, delta);
        sessionStarts.delete(pkt.uuid);
      }
    }
  });
}
