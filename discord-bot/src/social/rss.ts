import { fetch } from 'undici';

import { child } from '../utils/logger.js';

const log = child({ mod: 'rss' });

export interface RssItem {
  title: string;
  url: string;
}

/** Minimal RSS / Atom feed parser, zero deps. Pulls title + link. */
export async function pollRss(feeds: string[]): Promise<RssItem[]> {
  const out: RssItem[] = [];
  for (const url of feeds) {
    try {
      const resp = await fetch(url);
      if (!resp.ok) continue;
      const xml = await resp.text();
      out.push(...parse(xml));
    } catch (err) {
      log.warn({ err: err instanceof Error ? err.message : String(err), url }, 'rss poll failed');
    }
  }
  return out;
}

function parse(xml: string): RssItem[] {
  const out: RssItem[] = [];
  // RSS <item>
  const rssRe = /<item>[\s\S]*?<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?<\/title>[\s\S]*?<link>(.*?)<\/link>/g;
  let m: RegExpExecArray | null;
  while ((m = rssRe.exec(xml))) {
    out.push({ title: m[1].trim(), url: m[2].trim() });
    if (out.length >= 5) break;
  }
  if (out.length) return out;
  // Atom <entry>
  const atomRe = /<entry>[\s\S]*?<title[^>]*>(.*?)<\/title>[\s\S]*?<link[^>]*href="(.*?)"/g;
  while ((m = atomRe.exec(xml))) {
    out.push({ title: m[1].trim(), url: m[2].trim() });
    if (out.length >= 5) break;
  }
  return out;
}
