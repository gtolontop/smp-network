import { EventEmitter } from 'node:events';
import { randomUUID } from 'node:crypto';

import type { WebSocket } from 'ws';

import type {
  Packet,
  PeerOrigin,
  PlayerMeta,
  RpcResult,
  TelemetryPacket,
} from './protocol.js';

export interface Peer {
  id: string;
  origin: PeerOrigin;
  software: string;
  mcVersion: string;
  socket: WebSocket;
  connectedAt: number;
  lastHeartbeatAt: number;
  telemetry?: TelemetryPacket;
  roster: PlayerMeta[];
}

type HubEvents = {
  peer_connect: [Peer];
  peer_disconnect: [Peer];
  packet: [Peer, Packet];
};

/**
 * The bridge hub holds the live set of connected plugins plus a handful
 * of helpers (broadcast, RPC, await-reply). It is the public surface the
 * rest of the bot uses; commands never talk to a raw WebSocket.
 */
export class Hub extends EventEmitter<HubEvents> {
  private readonly peers = new Map<string, Peer>();
  private readonly awaiting = new Map<string, (result: RpcResult) => void>();

  add(peer: Peer): void {
    this.peers.set(peer.id, peer);
    this.emit('peer_connect', peer);
  }

  remove(peerId: string): void {
    const peer = this.peers.get(peerId);
    if (!peer) return;
    this.peers.delete(peerId);
    this.emit('peer_disconnect', peer);
  }

  list(): Peer[] {
    return [...this.peers.values()];
  }

  byOrigin(origin: PeerOrigin): Peer | undefined {
    return this.list().find((p) => p.origin === origin);
  }

  isOnline(origin: PeerOrigin): boolean {
    return this.byOrigin(origin) !== undefined;
  }

  allRoster(): PlayerMeta[] {
    const out: PlayerMeta[] = [];
    for (const peer of this.peers.values()) {
      if (peer.origin === 'velocity') continue; // velocity roster is a union
      for (const p of peer.roster) {
        if (!out.find((o) => o.uuid === p.uuid)) {
          out.push({ ...p, server: peer.origin });
        }
      }
    }
    return out;
  }

  playerByName(name: string): PlayerMeta | undefined {
    const lower = name.toLowerCase();
    return this.allRoster().find((p) => p.name.toLowerCase() === lower);
  }

  send(origin: PeerOrigin, packet: Packet): boolean {
    const peer = this.byOrigin(origin);
    if (!peer) return false;
    peer.socket.send(JSON.stringify(packet));
    return true;
  }

  broadcast(packet: Packet, filter?: (p: Peer) => boolean): number {
    let sent = 0;
    for (const peer of this.peers.values()) {
      if (filter && !filter(peer)) continue;
      peer.socket.send(JSON.stringify(packet));
      sent++;
    }
    return sent;
  }

  /** Dispatch an RPC and resolve with the paired reply. */
  async rpc(
    origin: PeerOrigin,
    method: string,
    args: Record<string, unknown> = {},
    timeoutMs = 5000,
  ): Promise<RpcResult> {
    const peer = this.byOrigin(origin);
    if (!peer) {
      return { kind: 'rpc_result', id: '-', ok: false, error: `${origin} not connected` };
    }
    const id = randomUUID();
    return new Promise<RpcResult>((resolve) => {
      const timer = setTimeout(() => {
        this.awaiting.delete(id);
        resolve({ kind: 'rpc_result', id, ok: false, error: 'timeout' });
      }, timeoutMs);
      this.awaiting.set(id, (result) => {
        clearTimeout(timer);
        resolve(result);
      });
      peer.socket.send(
        JSON.stringify({
          kind: 'rpc',
          id,
          method,
          args,
        }),
      );
    });
  }

  /** Feed an RPC result received from a peer back to whoever's waiting. */
  resolveRpc(result: RpcResult): void {
    const waiter = this.awaiting.get(result.id);
    if (waiter) {
      this.awaiting.delete(result.id);
      waiter(result);
    }
  }
}

export const hub = new Hub();
