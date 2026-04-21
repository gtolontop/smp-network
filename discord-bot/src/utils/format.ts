/**
 * Small formatters used across embeds and logs. Kept dependency-free so
 * they can be used anywhere in the bot.
 */

const MINI = /<(?:\/)?(?:[a-zA-Z_]+)(?::[^>]+)?>/g;
const COLOR_HEX = /<#([0-9a-fA-F]{6})>/g;
const GRADIENT = /<gradient:[^>]+>|<\/gradient>/g;
const LEGACY = /§[0-9a-fk-or]/gi;

/**
 * Strip MiniMessage, legacy (§) and hex color tags from a string so it
 * can be shown in a Discord embed without visual noise.
 */
export function stripMinecraft(raw: string): string {
  return raw
    .replace(GRADIENT, '')
    .replace(COLOR_HEX, '')
    .replace(MINI, '')
    .replace(LEGACY, '')
    .trim();
}

/** Escape Discord markdown so user-submitted chat doesn't break embeds. */
export function escapeMd(input: string): string {
  return input.replace(/([\\`*_~|>])/g, '\\$1');
}

export function bytesToHuman(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB', 'PB'];
  let i = -1;
  let n = bytes;
  do {
    n /= 1024;
    i++;
  } while (n >= 1024 && i < units.length - 1);
  return `${n.toFixed(n >= 10 ? 0 : 1)} ${units[i]}`;
}

export function pct(value: number, digits = 1): string {
  return `${value.toFixed(digits)}%`;
}

const MOJANG_RENDER = 'https://mc-heads.net/avatar';
export function avatarUrlFor(playerName: string, size = 64): string {
  const safe = encodeURIComponent(playerName);
  return `${MOJANG_RENDER}/${safe}/${size}`;
}

const MOJANG_BODY = 'https://mc-heads.net/body';
export function bodyUrlFor(playerName: string): string {
  return `${MOJANG_BODY}/${encodeURIComponent(playerName)}`;
}

export function truncate(input: string, max: number): string {
  if (input.length <= max) return input;
  return `${input.slice(0, max - 1)}…`;
}
