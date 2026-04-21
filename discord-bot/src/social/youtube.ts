import { fetch } from 'undici';

import { child } from '../utils/logger.js';

const log = child({ mod: 'youtube' });

export interface YouTubeUpload {
  id: string;
  title: string;
  url: string;
  thumbnail: string;
}

/**
 * Uses the public Atom feed — no API key required. Each channel has a
 * stable URL at /feeds/videos.xml?channel_id=...
 */
export async function pollYouTube(channelIds: string[]): Promise<YouTubeUpload[]> {
  const out: YouTubeUpload[] = [];
  for (const id of channelIds) {
    try {
      const resp = await fetch(`https://www.youtube.com/feeds/videos.xml?channel_id=${encodeURIComponent(id)}`);
      if (!resp.ok) continue;
      const xml = await resp.text();
      const items = parseFeed(xml);
      out.push(...items);
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err), id }, 'youtube poll failed');
    }
  }
  return out;
}

function parseFeed(xml: string): YouTubeUpload[] {
  const entries: YouTubeUpload[] = [];
  const re = /<entry>[\s\S]*?<yt:videoId>(.*?)<\/yt:videoId>[\s\S]*?<title>(.*?)<\/title>[\s\S]*?<media:thumbnail\s+url="(.*?)"/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(xml))) {
    entries.push({
      id: m[1],
      title: decodeEntities(m[2]),
      url: `https://youtu.be/${m[1]}`,
      thumbnail: m[3],
    });
    if (entries.length >= 5) break;
  }
  return entries;
}

function decodeEntities(s: string): string {
  return s
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}
