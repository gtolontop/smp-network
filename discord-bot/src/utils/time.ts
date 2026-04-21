const UNITS: Array<[number, string]> = [
  [86_400, 'j'],
  [3_600, 'h'],
  [60, 'min'],
  [1, 's'],
];

export function humanDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '0s';
  if (seconds < 1) return '<1s';
  const parts: string[] = [];
  let rem = Math.floor(seconds);
  for (const [u, label] of UNITS) {
    if (rem >= u) {
      const n = Math.floor(rem / u);
      rem -= n * u;
      parts.push(`${n}${label}`);
      if (parts.length === 2) break;
    }
  }
  return parts.join(' ') || '0s';
}

export function nowSec(): number {
  return Math.floor(Date.now() / 1000);
}

export function discordRelative(unixSec: number): string {
  return `<t:${unixSec}:R>`;
}

export function discordTime(unixSec: number): string {
  return `<t:${unixSec}:f>`;
}
