import { hub } from '../bridge/hub.js';
import type { PeerOrigin, RpcResult } from '../bridge/protocol.js';

export const ALL_TARGET = 'all';
const ALL_TARGET_ALIASES = new Set(['all', 'network']);
const PROXY_TARGET_ALIASES = new Set(['proxy', 'velocity']);

function sortOrigins(a: string, b: string): number {
  if (a === 'velocity' && b !== 'velocity') return -1;
  if (b === 'velocity' && a !== 'velocity') return 1;
  return a.localeCompare(b);
}

export function normalizeTargetInput(input: string): string {
  const normalized = input.trim().toLowerCase();
  if (ALL_TARGET_ALIASES.has(normalized)) return ALL_TARGET;
  if (PROXY_TARGET_ALIASES.has(normalized)) return 'velocity';
  return normalized;
}

export function listConnectedOrigins(): PeerOrigin[] {
  return [...new Set(hub.list().map((peer) => peer.origin))].sort(sortOrigins);
}

export function listConnectedPaperOrigins(): PeerOrigin[] {
  return listConnectedOrigins().filter((origin) => origin !== 'velocity');
}

export function describeConnectedOrigins(origins = listConnectedOrigins()): string {
  return origins.length ? origins.map((origin) => `\`${origin}\``).join(', ') : 'aucune';
}

export function resolveConnectedOrigin(input: string): PeerOrigin | undefined {
  const normalized = normalizeTargetInput(input);
  return hub.list().find((peer) => peer.origin.toLowerCase() === normalized)?.origin;
}

export function resolveOnlinePlayerOrigin(playerName: string): PeerOrigin | undefined {
  const server = hub.playerByName(playerName)?.server;
  if (!server) return undefined;
  return resolveConnectedOrigin(server) ?? server;
}

export function resolvePreferredPaperOrigin(playerName?: string): PeerOrigin | undefined {
  const liveOrigin = playerName ? resolveOnlinePlayerOrigin(playerName) : undefined;
  if (liveOrigin && liveOrigin !== 'velocity') return liveOrigin;
  return listConnectedPaperOrigins()[0];
}

export async function runConsoleRpc(
  origin: PeerOrigin,
  command: string,
): Promise<RpcResult> {
  return hub.rpc(origin, 'console', { command });
}

export async function runConsoleRpcBatch(
  origins: PeerOrigin[],
  command: string,
): Promise<Array<{ origin: PeerOrigin; result: RpcResult }>> {
  return Promise.all(
    origins.map(async (origin) => ({
      origin,
      result: await runConsoleRpc(origin, command),
    })),
  );
}
