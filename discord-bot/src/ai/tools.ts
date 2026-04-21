import { hub } from '../bridge/hub.js';
import {
  eventsSince,
  player,
  playerByName,
  topPlayers,
  linkForName,
} from '../db/queries.js';
import { hostSnapshot } from '../host/metrics.js';
import { humanDuration } from '../utils/time.js';
import { bytesToHuman, pct } from '../utils/format.js';

/**
 * Tools exposed to the LLM through OpenAI-style function calling. Each
 * returns a compact JSON-serialisable snapshot so the model can reason
 * over live server data without us pre-baking answers.
 */
export const AI_TOOLS = [
  {
    type: 'function' as const,
    function: {
      name: 'list_online',
      description: "Liste des joueurs en ligne avec leur serveur et leur ping.",
      parameters: { type: 'object', properties: {}, additionalProperties: false },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'server_status',
      description: "TPS, MSPT, uptime et joueurs pour chaque serveur connecté.",
      parameters: { type: 'object', properties: {}, additionalProperties: false },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'player_stats',
      description: "Stats cumulées d'un joueur: temps de jeu, morts, kills, blocs.",
      parameters: {
        type: 'object',
        properties: { name: { type: 'string', description: 'Pseudo Minecraft' } },
        required: ['name'],
        additionalProperties: false,
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'leaderboard',
      description: "Top N joueurs sur un critère donné.",
      parameters: {
        type: 'object',
        properties: {
          field: {
            type: 'string',
            enum: ['playtime_sec', 'kills', 'deaths', 'blocks_broken', 'blocks_placed'],
          },
          limit: { type: 'integer', minimum: 1, maximum: 20 },
        },
        required: ['field'],
        additionalProperties: false,
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'recent_events',
      description: "Derniers évènements du serveur (kind optionnel).",
      parameters: {
        type: 'object',
        properties: {
          kind: {
            type: 'string',
            enum: ['join', 'leave', 'death', 'advancement', 'rare_drop', 'lifecycle'],
          },
          since_minutes: { type: 'integer', minimum: 1, maximum: 1440 },
          limit: { type: 'integer', minimum: 1, maximum: 50 },
        },
        additionalProperties: false,
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'host_metrics',
      description: "Charge CPU, RAM, disque, réseau, uptime de la machine hôte.",
      parameters: { type: 'object', properties: {}, additionalProperties: false },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'is_player_linked',
      description: "Indique si un joueur Minecraft est lié à un compte Discord.",
      parameters: {
        type: 'object',
        properties: { name: { type: 'string' } },
        required: ['name'],
        additionalProperties: false,
      },
    },
  },
];

export async function runTool(name: string, args: Record<string, unknown>): Promise<unknown> {
  switch (name) {
    case 'list_online':
      return hub.allRoster().map((p) => ({ name: p.name, server: p.server, ping: p.ping }));
    case 'server_status':
      return hub.list().map((p) => ({
        origin: p.origin,
        online: p.telemetry?.online ?? 0,
        tps1m: p.telemetry?.tps1m,
        tps15m: p.telemetry?.tps15m,
        mspt: p.telemetry?.msptAvg,
        uptime: p.telemetry ? humanDuration(p.telemetry.uptimeSec) : undefined,
      }));
    case 'player_stats': {
      const nameArg = String(args.name ?? '');
      const row = playerByName(nameArg) ?? (linkForName(nameArg) ? player(linkForName(nameArg)!.mcUuid) : undefined);
      if (!row) return { error: 'unknown player' };
      return {
        name: row.mcName,
        playtime: humanDuration(row.playtimeSec),
        deaths: row.deaths,
        kills: row.kills,
        blocksBroken: row.blocksBroken,
        blocksPlaced: row.blocksPlaced,
      };
    }
    case 'leaderboard': {
      const field = args.field as 'playtime_sec' | 'kills' | 'deaths' | 'blocks_broken' | 'blocks_placed';
      const limit = typeof args.limit === 'number' ? args.limit : 5;
      return topPlayers(field, limit).map((r) => ({
        name: r.mcName,
        value:
          field === 'playtime_sec'
            ? humanDuration(r.playtimeSec)
            : field === 'kills'
              ? r.kills
              : field === 'deaths'
                ? r.deaths
                : field === 'blocks_broken'
                  ? r.blocksBroken
                  : r.blocksPlaced,
      }));
    }
    case 'recent_events': {
      const since = Math.floor(Date.now() / 1000) - (Number(args.since_minutes ?? 60) * 60);
      const kind = typeof args.kind === 'string' ? args.kind : undefined;
      const limit = typeof args.limit === 'number' ? args.limit : 20;
      return eventsSince(since, kind, limit).map((e) => ({
        kind: e.kind,
        player: e.mcName,
        origin: e.origin,
        at: new Date(e.createdAt * 1000).toISOString(),
        payload: safeJson(e.payload),
      }));
    }
    case 'host_metrics': {
      const s = await hostSnapshot();
      return {
        cpu: pct(s.cpuLoad),
        ram: `${bytesToHuman(s.memUsed)}/${bytesToHuman(s.memTotal)} (${pct(s.memPct)})`,
        disk: `${bytesToHuman(s.diskUsed)}/${bytesToHuman(s.diskTotal)} (${pct(s.diskPct)})`,
        netRxKbps: Math.round(s.netRxKbps),
        netTxKbps: Math.round(s.netTxKbps),
        uptime: humanDuration(s.uptimeSec),
        tempC: s.tempC,
      };
    }
    case 'is_player_linked': {
      const link = linkForName(String(args.name ?? ''));
      return { linked: !!link, discordId: link?.discordId };
    }
    default:
      return { error: `unknown tool ${name}` };
  }
}

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}
