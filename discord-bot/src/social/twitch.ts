import { fetch } from 'undici';

import { child } from '../utils/logger.js';

const log = child({ mod: 'twitch' });

interface TwitchToken {
  token: string;
  expiresAt: number;
}

let cached: TwitchToken | undefined;

async function token(clientId: string, clientSecret: string): Promise<string> {
  if (cached && cached.expiresAt > Date.now() + 60_000) return cached.token;
  const resp = await fetch('https://id.twitch.tv/oauth2/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      grant_type: 'client_credentials',
    }).toString(),
  });
  if (!resp.ok) throw new Error(`twitch token ${resp.status}`);
  const data = (await resp.json()) as { access_token: string; expires_in: number };
  cached = { token: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 };
  return cached.token;
}

export interface TwitchLive {
  userName: string;
  title: string;
  url: string;
  thumbnail: string;
}

export async function pollTwitch(
  clientId: string,
  clientSecret: string,
  channels: string[],
): Promise<TwitchLive[]> {
  if (!channels.length) return [];
  try {
    const auth = await token(clientId, clientSecret);
    const params = channels.map((c) => `user_login=${encodeURIComponent(c)}`).join('&');
    const resp = await fetch(`https://api.twitch.tv/helix/streams?${params}`, {
      headers: {
        'client-id': clientId,
        authorization: `Bearer ${auth}`,
      },
    });
    if (!resp.ok) throw new Error(`twitch streams ${resp.status}`);
    const body = (await resp.json()) as {
      data: Array<{ id: string; user_login: string; user_name: string; title: string; thumbnail_url: string }>;
    };
    return body.data.map((s) => ({
      userName: s.user_name,
      title: s.title,
      url: `https://twitch.tv/${s.user_login}`,
      thumbnail: s.thumbnail_url.replace('{width}', '640').replace('{height}', '360'),
    }));
  } catch (err) {
    log.warn({ err: err instanceof Error ? err.message : String(err) }, 'twitch poll failed');
    return [];
  }
}
