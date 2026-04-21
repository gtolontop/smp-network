import { ChannelType, type Client, type TextChannel } from 'discord.js';

import { config } from '../config.js';
import { socialSeen } from '../db/queries.js';
import { child } from '../utils/logger.js';
import { COLOR, baseEmbed } from '../utils/embeds.js';

import { pollTwitch } from './twitch.js';
import { pollYouTube } from './youtube.js';
import { pollRss } from './rss.js';

const log = child({ mod: 'socials' });
const POLL_MS = 120_000;

/**
 * Periodically polls Twitch streams, YouTube uploads and any
 * configured RSS feed. Each source posts into CHANNEL_SOCIALS and
 * dedupes via the social_seen table so the bot survives restarts.
 */
export function initSocials(client: Client): void {
  const channelId = config.channels.socials;
  if (!channelId) {
    log.info('no socials channel — skipping');
    return;
  }

  async function post(source: string, title: string, url: string, thumbnail?: string): Promise<void> {
    if (socialSeen(source, url)) return;
    const channel = await client.channels.fetch(channelId).catch(() => null);
    if (!channel || channel.type !== ChannelType.GuildText) return;
    await (channel as TextChannel).send({
      content: url,
      embeds: [
        baseEmbed({
          title,
          url,
          thumbnail,
          footer: source,
          color: COLOR.accent,
          timestamp: true,
        }),
      ],
    });
  }

  async function tick(): Promise<void> {
    try {
      if (config.socials.twitchClientId && config.socials.twitchClientSecret && config.socials.twitchChannels.length) {
        const live = await pollTwitch(
          config.socials.twitchClientId,
          config.socials.twitchClientSecret,
          config.socials.twitchChannels,
        );
        for (const s of live) await post('twitch', `${s.userName} est en live — ${s.title}`, s.url, s.thumbnail);
      }
      if (config.socials.youtubeChannelIds.length) {
        const uploads = await pollYouTube(config.socials.youtubeChannelIds);
        for (const v of uploads) await post('youtube', v.title, v.url, v.thumbnail);
      }
      if (config.socials.rssFeeds.length) {
        const items = await pollRss(config.socials.rssFeeds);
        for (const it of items) await post('rss', it.title, it.url);
      }
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err) }, 'social tick failed');
    }
  }

  tick();
  setInterval(tick, POLL_MS).unref();
}
