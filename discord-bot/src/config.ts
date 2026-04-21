import { config as loadEnv } from 'dotenv';
import { z } from 'zod';

loadEnv();

const required = (name: string) =>
  z.string().min(1, `${name} is required`);

const optional = () => z.string().optional().default('');

const bool = () =>
  z
    .union([z.boolean(), z.string()])
    .transform((v) => (typeof v === 'boolean' ? v : /^(1|true|yes|on)$/i.test(v)));

const port = () =>
  z
    .union([z.string(), z.number()])
    .transform((v) => (typeof v === 'number' ? v : Number.parseInt(v, 10)))
    .pipe(z.number().int().min(1).max(65535));

const percent = () =>
  z
    .union([z.string(), z.number()])
    .transform((v) => (typeof v === 'number' ? v : Number.parseFloat(v)))
    .pipe(z.number().min(0).max(100));

const csv = () =>
  z
    .string()
    .optional()
    .default('')
    .transform((v) =>
      v
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    );

const schema = z.object({
  discord: z.object({
    token: required('DISCORD_TOKEN'),
    clientId: required('DISCORD_CLIENT_ID'),
    guildId: required('DISCORD_GUILD_ID'),
  }),
  channels: z.object({
    chat: optional(),
    events: optional(),
    status: optional(),
    alerts: optional(),
    audit: optional(),
    recap: optional(),
    socials: optional(),
    tickets: optional(),
    suggestions: optional(),
    welcome: optional(),
  }),
  voice: z.object({
    playerCount: optional(),
    tps: optional(),
  }),
  roles: z.object({
    admin: optional(),
    mod: optional(),
    linked: optional(),
    booster: optional(),
  }),
  bridge: z.object({
    host: z.string().default('0.0.0.0'),
    port: port().default(8787),
    token: required('BRIDGE_TOKEN'),
  }),
  ai: z.object({
    baseUrl: z.string().url().default('https://api.z.ai/api/coding/paas/v4'),
    apiKey: optional(),
    model: z.string().default('glm-4.6'),
    moderation: bool().default(false),
  }),
  mcbot: z.object({
    enabled: bool().default(false),
    host: z.string().default('127.0.0.1'),
    port: port().default(25565),
    username: z.string().default('Assistant'),
    auth: z.enum(['offline', 'microsoft']).default('offline'),
    version: z.string().default('1.21.4'),
  }),
  host: z.object({
    alertCpu: percent().default(90),
    alertRam: percent().default(90),
    alertDisk: percent().default(95),
    alertCooldownS: z.coerce.number().int().default(600),
  }),
  socials: z.object({
    twitchClientId: optional(),
    twitchClientSecret: optional(),
    twitchChannels: csv(),
    youtubeChannelIds: csv(),
    rssFeeds: csv(),
  }),
  meta: z.object({
    logLevel: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
    timezone: z.string().default('Europe/Paris'),
    dataDir: z.string().default('./data'),
  }),
});

export type Config = z.infer<typeof schema>;

const raw = {
  discord: {
    token: process.env.DISCORD_TOKEN,
    clientId: process.env.DISCORD_CLIENT_ID,
    guildId: process.env.DISCORD_GUILD_ID,
  },
  channels: {
    chat: process.env.CHANNEL_CHAT,
    events: process.env.CHANNEL_EVENTS,
    status: process.env.CHANNEL_STATUS,
    alerts: process.env.CHANNEL_ALERTS,
    audit: process.env.CHANNEL_AUDIT,
    recap: process.env.CHANNEL_RECAP,
    socials: process.env.CHANNEL_SOCIALS,
    tickets: process.env.CHANNEL_TICKETS,
    suggestions: process.env.CHANNEL_SUGGESTIONS,
    welcome: process.env.CHANNEL_WELCOME,
  },
  voice: {
    playerCount: process.env.VOICE_PLAYER_COUNT,
    tps: process.env.VOICE_TPS,
  },
  roles: {
    admin: process.env.ROLE_ADMIN,
    mod: process.env.ROLE_MOD,
    linked: process.env.ROLE_LINKED,
    booster: process.env.ROLE_BOOSTER,
  },
  bridge: {
    host: process.env.BRIDGE_HOST,
    port: process.env.BRIDGE_PORT,
    token: process.env.BRIDGE_TOKEN,
  },
  ai: {
    baseUrl: process.env.AI_BASE_URL,
    apiKey: process.env.AI_API_KEY,
    model: process.env.AI_MODEL,
    moderation: process.env.AI_MODERATION,
  },
  mcbot: {
    enabled: process.env.MCBOT_ENABLED,
    host: process.env.MCBOT_HOST,
    port: process.env.MCBOT_PORT,
    username: process.env.MCBOT_USERNAME,
    auth: process.env.MCBOT_AUTH,
    version: process.env.MCBOT_VERSION,
  },
  host: {
    alertCpu: process.env.HOST_ALERT_CPU,
    alertRam: process.env.HOST_ALERT_RAM,
    alertDisk: process.env.HOST_ALERT_DISK,
    alertCooldownS: process.env.HOST_ALERT_COOLDOWN_S,
  },
  socials: {
    twitchClientId: process.env.TWITCH_CLIENT_ID,
    twitchClientSecret: process.env.TWITCH_CLIENT_SECRET,
    twitchChannels: process.env.TWITCH_CHANNELS,
    youtubeChannelIds: process.env.YOUTUBE_CHANNEL_IDS,
    rssFeeds: process.env.RSS_FEEDS,
  },
  meta: {
    logLevel: process.env.LOG_LEVEL,
    timezone: process.env.TIMEZONE,
    dataDir: process.env.DATA_DIR,
  },
};

const parsed = schema.safeParse(raw);

if (!parsed.success) {
  const issues = parsed.error.issues
    .map((i) => `  • ${i.path.join('.')}: ${i.message}`)
    .join('\n');
  // Fail fast so a broken boot is obvious.
  // eslint-disable-next-line no-console
  console.error(`\nConfig validation failed:\n${issues}\n`);
  process.exit(1);
}

export const config: Config = parsed.data;
