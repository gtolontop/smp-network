import {
  WebhookClient,
  type Client,
  type TextChannel,
  type Webhook,
  type Message,
  ChannelType,
} from 'discord.js';

import { config } from '../../config.js';
import { hub } from '../../bridge/hub.js';
import type { ChatEvent, Packet } from '../../bridge/protocol.js';
import type { Peer } from '../../bridge/hub.js';
import { child } from '../../utils/logger.js';
import { avatarUrlFor, escapeMd, stripMinecraft, truncate } from '../../utils/format.js';

const log = child({ mod: 'chat-bridge' });
const WEBHOOK_NAME = 'SMP Bridge';

/**
 * Bi-directional chat bridge. MC chat is displayed in Discord as a
 * webhook message so each line shows the player's face and name.
 * Discord chat in the bridge channel is injected back into MC on every
 * connected backend.
 */
export async function initChatBridge(client: Client): Promise<void> {
  const channelId = config.channels.chat;
  if (!channelId) {
    log.warn('CHANNEL_CHAT not set — chat bridge disabled');
    return;
  }
  const channel = await client.channels.fetch(channelId).catch(() => null);
  if (!channel || channel.type !== ChannelType.GuildText) {
    log.warn({ channelId }, 'chat channel invalid or not a text channel');
    return;
  }

  const webhook = await getOrCreateWebhook(channel);
  const hookClient = new WebhookClient({ id: webhook.id, token: webhook.token! });

  hub.on('packet', async (peer: Peer, pkt: Packet) => {
    if (pkt.kind === 'chat') {
      await renderChat(hookClient, peer.origin, pkt);
    } else if (pkt.kind === 'join' || pkt.kind === 'leave') {
      await hookClient.send({
        username: WEBHOOK_NAME,
        avatarURL: client.user?.displayAvatarURL(),
        content:
          pkt.kind === 'join'
            ? `**${escapeMd(pkt.name)}** a rejoint *${peer.origin}*${pkt.firstTime ? ' — premier login 🎉' : ''}`
            : `**${escapeMd(pkt.name)}** a quitté *${peer.origin}*`,
      });
    } else if (pkt.kind === 'death') {
      await hookClient.send({
        username: WEBHOOK_NAME,
        avatarURL: client.user?.displayAvatarURL(),
        content: `☠ ${escapeMd(stripMinecraft(pkt.message))}`,
      });
    }
  });

  client.on('messageCreate', async (msg: Message) => {
    if (msg.author.bot || msg.webhookId) return;
    if (msg.channelId !== channelId) return;
    const content = msg.cleanContent.trim();
    if (!content) return;
    hub.broadcast(
      {
        kind: 'chat_inject',
        target: 'all',
        author: msg.member?.displayName ?? msg.author.username,
        message: truncate(content, 256),
        avatarUrl: msg.author.displayAvatarURL({ size: 64 }),
      },
      (p) => p.origin !== 'velocity',
    );
  });

  log.info({ channelId }, 'chat bridge ready');
}

async function renderChat(hook: WebhookClient, origin: string, ev: ChatEvent): Promise<void> {
  await hook.send({
    username: `${ev.name} · ${origin}`,
    avatarURL: avatarUrlFor(ev.name, 64),
    content: truncate(escapeMd(stripMinecraft(ev.message)), 1800),
    allowedMentions: { parse: [] },
  });
}

async function getOrCreateWebhook(channel: TextChannel): Promise<Webhook> {
  const existing = await channel.fetchWebhooks();
  const match = existing.find((w) => w.name === WEBHOOK_NAME && w.owner?.id === channel.client.user?.id);
  if (match) return match;
  return channel.createWebhook({ name: WEBHOOK_NAME, reason: 'SMP bot chat bridge' });
}
