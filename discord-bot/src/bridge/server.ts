import { randomUUID } from 'node:crypto';

import { WebSocketServer } from 'ws';
import type { WebSocket } from 'ws';

import { config } from '../config.js';
import { child } from '../utils/logger.js';

import { hub } from './hub.js';
import { PROTOCOL_VERSION, isPacket } from './protocol.js';
import type { HelloPacket, Packet } from './protocol.js';

const log = child({ mod: 'bridge' });

const HEARTBEAT_TIMEOUT_MS = 40_000;

export function startBridgeServer(): WebSocketServer {
  const wss = new WebSocketServer({
    host: config.bridge.host,
    port: config.bridge.port,
    clientTracking: true,
    maxPayload: 1_024 * 1_024,
  });

  wss.on('listening', () => {
    log.info(
      { host: config.bridge.host, port: config.bridge.port },
      'bridge server listening',
    );
  });

  wss.on('connection', (socket, req) => {
    const remote = req.socket.remoteAddress ?? 'unknown';
    const abortTimer = setTimeout(() => {
      log.warn({ remote }, 'client closed: hello timeout');
      socket.terminate();
    }, 10_000);

    let peerId: string | undefined;

    const cleanup = () => {
      clearTimeout(abortTimer);
      if (peerId) hub.remove(peerId);
    };

    socket.on('close', (code) => {
      log.info({ remote, code }, 'bridge client disconnected');
      cleanup();
    });
    socket.on('error', (err) => {
      log.warn({ remote, err: err.message }, 'bridge socket error');
    });

    socket.on('message', (raw) => {
      let pkt: Packet;
      try {
        const parsed = JSON.parse(raw.toString('utf8'));
        if (!isPacket(parsed)) {
          log.warn({ remote }, 'dropped malformed packet');
          return;
        }
        pkt = parsed;
      } catch {
        log.warn({ remote }, 'dropped non-JSON frame');
        return;
      }

      if (!peerId) {
        // First frame must be the handshake.
        if (pkt.kind !== 'hello') {
          log.warn({ remote, kind: pkt.kind }, 'closing — hello expected');
          socket.close(4001, 'hello expected');
          return;
        }
        if (!acceptHello(socket, pkt as HelloPacket, remote)) return;
        clearTimeout(abortTimer);
        peerId = randomUUID();
        hub.add({
          id: peerId,
          origin: pkt.origin,
          software: pkt.software,
          mcVersion: pkt.mcVersion,
          socket,
          connectedAt: Date.now(),
          lastHeartbeatAt: Date.now(),
          roster: [],
        });
        socket.send(
          JSON.stringify({ kind: 'welcome', v: PROTOCOL_VERSION, serverTime: Date.now() }),
        );
        log.info(
          { origin: pkt.origin, software: pkt.software, mcVersion: pkt.mcVersion },
          'bridge client accepted',
        );
        return;
      }

      const peer = hub.list().find((p) => p.id === peerId);
      if (!peer) return;

      // Handle transport-level packets here, fan everything else to the hub.
      switch (pkt.kind) {
        case 'heartbeat': {
          peer.lastHeartbeatAt = Date.now();
          socket.send(
            JSON.stringify({ kind: 'pong', sentAt: pkt.sentAt, echoedAt: Date.now() }),
          );
          return;
        }
        case 'telemetry': {
          peer.telemetry = pkt;
          peer.lastHeartbeatAt = Date.now();
          break;
        }
        case 'roster': {
          peer.roster = pkt.players;
          break;
        }
        case 'rpc_result': {
          hub.resolveRpc(pkt);
          return;
        }
      }
      hub.emit('packet', peer, pkt);
    });
  });

  // Reaper: drop peers whose heartbeat has gone stale.
  setInterval(() => {
    const now = Date.now();
    for (const peer of hub.list()) {
      if (now - peer.lastHeartbeatAt > HEARTBEAT_TIMEOUT_MS) {
        log.warn({ origin: peer.origin }, 'reaping stale peer');
        try {
          peer.socket.terminate();
        } catch {
          /* ignore */
        }
        hub.remove(peer.id);
      }
    }
  }, 10_000).unref();

  return wss;
}

function acceptHello(socket: WebSocket, hello: HelloPacket, remote: string): boolean {
  if (hello.v !== PROTOCOL_VERSION) {
    log.warn({ remote, v: hello.v }, 'rejecting — protocol mismatch');
    socket.close(4002, 'protocol version mismatch');
    return false;
  }
  if (!timingSafeEqualString(hello.token, config.bridge.token)) {
    log.warn({ remote }, 'rejecting — bad token');
    socket.close(4003, 'auth failed');
    return false;
  }
  if (!isValidPeerOrigin(hello.origin)) {
    socket.close(4004, 'bad origin');
    return false;
  }
  return true;
}

function isValidPeerOrigin(origin: string): boolean {
  return /^[a-z0-9_-]+$/.test(origin) && origin !== 'bot';
}

function timingSafeEqualString(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
