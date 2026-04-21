import { ChannelType, type Client, type TextChannel } from 'discord.js';

import { config } from '../../config.js';
import { hub } from '../../bridge/hub.js';
import { recordEvent, bumpStat, upsertPlayer } from '../../db/queries.js';
import type { Packet } from '../../bridge/protocol.js';
import type { Peer } from '../../bridge/hub.js';
import { child } from '../../utils/logger.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { avatarUrlFor, escapeMd, stripMinecraft } from '../../utils/format.js';

const log = child({ mod: 'event-feed' });

/**
 * Pushes noteworthy in-game events to #events with richer embeds than
 * the chat bridge shows. Persists a compact row per event so /recap
 * and leaderboards can look back.
 */
export function initEventFeed(client: Client): void {
  const channelId = config.channels.events;
  if (!channelId) {
    log.warn('CHANNEL_EVENTS not set — event feed disabled');
    return;
  }

  hub.on('packet', async (peer: Peer, pkt: Packet) => {
    const channel = await client.channels.fetch(channelId).catch(() => null);
    if (!channel || channel.type !== ChannelType.GuildText) return;
    const target = channel as TextChannel;

    switch (pkt.kind) {
      case 'join': {
        upsertPlayer(pkt.uuid, pkt.name);
        recordEvent(peer.origin, 'join', pkt.uuid, pkt.name, { firstTime: pkt.firstTime });
        if (pkt.firstTime) {
          await target.send({
            embeds: [
              baseEmbed({
                title: 'Premier login',
                description: `**${escapeMd(pkt.name)}** rejoint le réseau pour la première fois.`,
                thumbnail: avatarUrlFor(pkt.name, 128),
                color: COLOR.success,
                timestamp: true,
              }),
            ],
          });
        }
        break;
      }
      case 'death': {
        bumpStat(pkt.uuid, 'deaths');
        recordEvent(peer.origin, 'death', pkt.uuid, pkt.name, {
          message: pkt.message,
          killer: pkt.killer,
        });
        await target.send({
          embeds: [
            baseEmbed({
              title: '☠ Mort',
              description: escapeMd(stripMinecraft(pkt.message)),
              thumbnail: avatarUrlFor(pkt.name, 64),
              color: COLOR.danger,
              footer: peer.origin,
              timestamp: true,
            }),
          ],
        });
        break;
      }
      case 'advancement': {
        recordEvent(peer.origin, 'advancement', pkt.uuid, pkt.name, {
          title: pkt.title,
          description: pkt.description,
          frame: pkt.frame,
        });
        if (pkt.frame !== 'task') {
          await target.send({
            embeds: [
              baseEmbed({
                title: pkt.frame === 'challenge' ? '◆ Défi complété' : '✦ Progrès',
                description: `**${escapeMd(pkt.name)}** — ${escapeMd(pkt.title)}${pkt.description ? `\n*${escapeMd(pkt.description)}*` : ''}`,
                thumbnail: avatarUrlFor(pkt.name, 64),
                color: pkt.frame === 'challenge' ? COLOR.accent : COLOR.info,
                footer: peer.origin,
                timestamp: true,
              }),
            ],
          });
        }
        break;
      }
      case 'rare_drop': {
        recordEvent(peer.origin, 'rare_drop', pkt.uuid, pkt.name, {
          item: pkt.item,
          source: pkt.source,
        });
        await target.send({
          embeds: [
            baseEmbed({
              title: '◎ Drop rare',
              description: `**${escapeMd(pkt.name)}** a obtenu **${escapeMd(pkt.item)}** depuis *${escapeMd(pkt.source)}*.`,
              thumbnail: avatarUrlFor(pkt.name, 64),
              color: COLOR.accent,
              footer: peer.origin,
              timestamp: true,
            }),
          ],
        });
        break;
      }
      case 'lifecycle': {
        recordEvent(peer.origin, 'lifecycle', null, null, { state: pkt.state });
        await target.send({
          embeds: [
            baseEmbed({
              title: `${peer.origin} · ${pkt.state}`,
              color: pkt.state === 'started' ? COLOR.success : pkt.state === 'stopped' ? COLOR.danger : COLOR.warn,
              timestamp: true,
            }),
          ],
        });
        break;
      }
    }
  });

  log.info({ channelId }, 'event feed ready');
}
