import mineflayer from 'mineflayer';
import type { Bot as MfBot } from 'mineflayer';
import pathfinderPkg from 'mineflayer-pathfinder';
const { pathfinder, Movements, goals } = pathfinderPkg;

import { config } from '../config.js';
import { child } from '../utils/logger.js';
import { AuthDriver } from './auth.js';

const log = child({ mod: 'mcbot' });

export interface McBotState {
  online: boolean;
  position?: { x: number; y: number; z: number; world: string };
  health?: number;
  food?: number;
  following?: string;
  uptimeSince?: number;
}

export class McBotController {
  private bot: MfBot | undefined;
  private reconnectTimer: NodeJS.Timeout | undefined;
  private following: string | undefined;
  private uptimeSince: number | undefined;
  private auth: AuthDriver | undefined;
  private readonly listeners = new Set<(b: MfBot) => void>();

  start(): void {
    if (!config.mcbot.enabled) {
      log.info('mcbot disabled in config');
      return;
    }
    this.connect();
  }

  stop(): void {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.bot?.end('shutdown');
    this.bot = undefined;
  }

  onReady(fn: (b: MfBot) => void): void {
    this.listeners.add(fn);
    if (this.bot) fn(this.bot);
  }

  state(): McBotState {
    if (!this.bot) return { online: false };
    const pos = this.bot.entity?.position;
    return {
      online: true,
      position: pos
        ? { x: Math.round(pos.x), y: Math.round(pos.y), z: Math.round(pos.z), world: this.bot.game.dimension }
        : undefined,
      health: this.bot.health,
      food: this.bot.food,
      following: this.following,
      uptimeSince: this.uptimeSince,
    };
  }

  say(message: string): void {
    this.bot?.chat(message);
  }

  async follow(playerName: string): Promise<string> {
    if (!this.bot) return 'Bot hors ligne.';
    const target = this.bot.players[playerName]?.entity;
    if (!target) return `Joueur ${playerName} introuvable ou hors de vue.`;
    this.following = playerName;
    const mv = new Movements(this.bot);
    this.bot.pathfinder.setMovements(mv);
    this.bot.pathfinder.setGoal(new goals.GoalFollow(target, 2), true);
    return `Je suis maintenant ${playerName}.`;
  }

  async gotoCoords(x: number, y: number, z: number): Promise<string> {
    if (!this.bot) return 'Bot hors ligne.';
    this.following = undefined;
    const mv = new Movements(this.bot);
    this.bot.pathfinder.setMovements(mv);
    this.bot.pathfinder.setGoal(new goals.GoalBlock(x, y, z));
    return `Cap sur ${x}, ${y}, ${z}.`;
  }

  stopMoving(): string {
    if (!this.bot) return 'Bot hors ligne.';
    this.following = undefined;
    this.bot.pathfinder.setGoal(null);
    return 'Bot arrêté.';
  }

  private connect(): void {
    log.info({ host: config.mcbot.host, port: config.mcbot.port }, 'mcbot connecting');
    const bot = mineflayer.createBot({
      host: config.mcbot.host,
      port: config.mcbot.port,
      username: config.mcbot.username,
      auth: config.mcbot.auth,
      version: config.mcbot.version,
      checkTimeoutInterval: 60_000,
    });
    bot.loadPlugin(pathfinder);

    // Attach the auth driver before spawn so we catch the /register or /login
    // prompt the server pushes at join time.
    this.auth = new AuthDriver({
      username: config.mcbot.username,
      password: config.mcbot.password,
      log,
    });
    this.auth.attach(bot);

    bot.once('spawn', () => {
      this.bot = bot;
      this.uptimeSince = Math.floor(Date.now() / 1000);
      log.info('mcbot spawned');
      for (const fn of this.listeners) fn(bot);
    });

    bot.on('chat', (username, message) => {
      if (username === bot.username) return;
      if (/^!assistant\s+/i.test(message)) {
        // The controller owner can wire this to the AI, see brain.ts.
      }
    });

    bot.on('kicked', (reason) => log.warn({ reason }, 'mcbot kicked'));
    bot.on('error', (err) => log.warn({ err: err.message }, 'mcbot error'));
    bot.on('end', (reason) => {
      log.warn({ reason }, 'mcbot disconnected — retry in 15s');
      this.bot = undefined;
      this.uptimeSince = undefined;
      this.reconnectTimer = setTimeout(() => this.connect(), 15_000);
    });
  }
}

export const mcbot = new McBotController();
