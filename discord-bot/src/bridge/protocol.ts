/**
 * Wire protocol between the bot and Minecraft-side plugins.
 *
 * Every frame is a JSON object with a `kind` discriminator. The same
 * `kind` enum is mirrored by the Java plugins; additions here require
 * a matching Java update and a version bump.
 */

export const PROTOCOL_VERSION = 1;

export type Origin = 'velocity' | 'lobby' | 'survival' | 'bot';

// -- control ----------------------------------------------------------------

export interface HelloPacket {
  kind: 'hello';
  v: number;
  token: string;
  origin: Exclude<Origin, 'bot'>;
  software: string;
  mcVersion: string;
}

export interface WelcomePacket {
  kind: 'welcome';
  v: number;
  serverTime: number;
}

export interface HeartbeatPacket {
  kind: 'heartbeat';
  sentAt: number;
}

export interface PongPacket {
  kind: 'pong';
  sentAt: number;
  echoedAt: number;
}

// -- live telemetry (plugin -> bot) -----------------------------------------

export interface TelemetryPacket {
  kind: 'telemetry';
  tps1m: number;
  tps5m: number;
  tps15m: number;
  msptAvg: number;
  msptP95: number;
  online: number;
  maxOnline: number;
  uptimeSec: number;
  loadedChunks: number;
  entities: number;
  memUsedMb: number;
  memMaxMb: number;
}

export interface PlayerMeta {
  uuid: string;
  name: string;
  ping: number;
  server?: string;
  gamemode?: string;
  world?: string;
}

export interface RosterPacket {
  kind: 'roster';
  players: PlayerMeta[];
}

// -- events (plugin -> bot) -------------------------------------------------

export interface ChatEvent {
  kind: 'chat';
  uuid: string;
  name: string;
  message: string;
}

export interface JoinEvent {
  kind: 'join';
  uuid: string;
  name: string;
  firstTime: boolean;
}

export interface LeaveEvent {
  kind: 'leave';
  uuid: string;
  name: string;
}

export interface DeathEvent {
  kind: 'death';
  uuid: string;
  name: string;
  message: string;
  killer?: string;
}

export interface AdvancementEvent {
  kind: 'advancement';
  uuid: string;
  name: string;
  title: string;
  description?: string;
  frame: 'task' | 'goal' | 'challenge';
}

export interface RareDropEvent {
  kind: 'rare_drop';
  uuid: string;
  name: string;
  item: string;
  source: string;
}

export interface ServerLifecycleEvent {
  kind: 'lifecycle';
  state: 'starting' | 'started' | 'stopping' | 'stopped';
}

// -- commands (bot -> plugin) -----------------------------------------------

export interface ConsoleCommand {
  kind: 'console';
  id: string;
  target: Exclude<Origin, 'bot'>;
  command: string;
}

export interface ConsoleResult {
  kind: 'console_result';
  id: string;
  ok: boolean;
  output: string;
}

export interface BroadcastCommand {
  kind: 'broadcast';
  target: Exclude<Origin, 'bot'> | 'all';
  message: string;
  prefix?: string;
}

export interface ChatInjectCommand {
  kind: 'chat_inject';
  target: Exclude<Origin, 'bot'> | 'all';
  author: string;
  message: string;
  avatarUrl?: string;
}

export interface PrivateMessageCommand {
  kind: 'tell';
  toUuid: string;
  message: string;
}

export interface RpcCall {
  kind: 'rpc';
  id: string;
  method: string;
  args: Record<string, unknown>;
}

export interface RpcResult {
  kind: 'rpc_result';
  id: string;
  ok: boolean;
  data?: unknown;
  error?: string;
}

// -- union ------------------------------------------------------------------

export type Packet =
  | HelloPacket
  | WelcomePacket
  | HeartbeatPacket
  | PongPacket
  | TelemetryPacket
  | RosterPacket
  | ChatEvent
  | JoinEvent
  | LeaveEvent
  | DeathEvent
  | AdvancementEvent
  | RareDropEvent
  | ServerLifecycleEvent
  | ConsoleCommand
  | ConsoleResult
  | BroadcastCommand
  | ChatInjectCommand
  | PrivateMessageCommand
  | RpcCall
  | RpcResult;

export function isPacket(x: unknown): x is Packet {
  return typeof x === 'object' && x !== null && typeof (x as { kind?: unknown }).kind === 'string';
}
