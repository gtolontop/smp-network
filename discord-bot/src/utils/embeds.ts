import { EmbedBuilder } from 'discord.js';

/**
 * Uniform palette for the bot. Sober, muted tones so embeds read as a
 * single product rather than a rainbow of competing modules.
 */
export const COLOR = {
  primary: 0x2b2d31,
  accent: 0x5865f2,
  success: 0x57f287,
  warn: 0xfee75c,
  danger: 0xed4245,
  info: 0x5865f2,
  muted: 0x4e5058,
  alive: 0x57f287,
  dead: 0xed4245,
} as const;

type Section = { name: string; value: string; inline?: boolean };

export interface BaseEmbedOpts {
  title?: string;
  description?: string;
  color?: number;
  footer?: string;
  sections?: Section[];
  thumbnail?: string;
  image?: string;
  url?: string;
  timestamp?: boolean | Date;
}

export function baseEmbed(opts: BaseEmbedOpts = {}): EmbedBuilder {
  const e = new EmbedBuilder().setColor(opts.color ?? COLOR.primary);
  if (opts.title) e.setTitle(opts.title);
  if (opts.description) e.setDescription(opts.description);
  if (opts.footer) e.setFooter({ text: opts.footer });
  if (opts.sections?.length) e.addFields(opts.sections);
  if (opts.thumbnail) e.setThumbnail(opts.thumbnail);
  if (opts.image) e.setImage(opts.image);
  if (opts.url) e.setURL(opts.url);
  if (opts.timestamp) e.setTimestamp(opts.timestamp === true ? new Date() : opts.timestamp);
  return e;
}

export const ok = (msg: string) =>
  baseEmbed({ description: `✓ ${msg}`, color: COLOR.success });

export const fail = (msg: string) =>
  baseEmbed({ description: `✗ ${msg}`, color: COLOR.danger });

export const warn = (msg: string) =>
  baseEmbed({ description: `! ${msg}`, color: COLOR.warn });

export const info = (msg: string) =>
  baseEmbed({ description: msg, color: COLOR.muted });
