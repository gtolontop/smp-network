import type { Bot as MfBot } from 'mineflayer';

import type { Logger } from 'pino';

/**
 * Drives the bot through the core-paper auth flow (/register then /login).
 *
 * The server freezes cracked joins and sends a MiniMessage prompt asking for
 * /register <mdp> <mdp> if the account doesn't exist, or /login <mdp> if it
 * does. We match those prompts on the plain-text message stream and respond
 * once. If the server pushes back ("aucun mot de passe enregistré" / "ce
 * compte a déjà un mot de passe") we flip to the other command. Hard failures
 * ("incorrect", "verrouillé") stop the loop so we don't get locked out.
 */
type Stage = 'idle' | 'pending' | 'logging_in' | 'registering' | 'authenticated' | 'locked';

export interface AuthDriverOptions {
  username: string;
  password: string;
  log: Logger;
}

export class AuthDriver {
  private stage: Stage = 'idle';
  private readonly log: Logger;

  constructor(private readonly opts: AuthDriverOptions) {
    this.log = opts.log.child({ mod: 'mcbot-auth' });
  }

  attach(bot: MfBot): void {
    this.stage = 'pending';
    bot.on('messagestr', (text) => this.onMessage(bot, text));
    bot.once('end', () => {
      this.stage = 'idle';
    });
  }

  isAuthenticated(): boolean {
    return this.stage === 'authenticated';
  }

  private onMessage(bot: MfBot, raw: string): void {
    const text = raw.toLowerCase();

    if (this.stage === 'authenticated' || this.stage === 'locked') return;

    if (text.includes('connecté') && text.includes('bon jeu')) {
      this.success('login');
      return;
    }
    if (text.includes('compte créé')) {
      this.success('register');
      return;
    }

    if (text.includes('mot de passe incorrect')) {
      this.log.error('bot password rejected — check MCBOT_PASSWORD');
      this.stage = 'locked';
      return;
    }
    if (text.includes('verrouillé') || text.includes('délai d’authentification') || text.includes("délai d'authentification")) {
      this.log.error({ raw }, 'bot auth locked out / timed out');
      this.stage = 'locked';
      return;
    }
    if (text.includes('pseudo appartient à un compte premium')) {
      this.log.error('bot pseudo is a premium Mojang name — rename MCBOT_USERNAME');
      this.stage = 'locked';
      return;
    }

    if (text.includes('aucun mot de passe enregistré')) {
      this.sendRegister(bot);
      return;
    }
    if (text.includes('ce compte a déjà un mot de passe')) {
      this.sendLogin(bot);
      return;
    }

    // Initial prompts at join time.
    if (this.stage === 'pending') {
      if (text.includes('crée ton mot de passe') || text.includes('/register <mdp>')) {
        this.sendRegister(bot);
        return;
      }
      if (text.includes('connexion requise') || text.includes('tape /login') || text.includes('/login <mdp>')) {
        this.sendLogin(bot);
        return;
      }
    }
  }

  private sendLogin(bot: MfBot): void {
    if (!this.opts.password) {
      this.log.error('MCBOT_PASSWORD is empty — cannot /login');
      this.stage = 'locked';
      return;
    }
    this.stage = 'logging_in';
    this.log.info('sending /login');
    bot.chat(`/login ${this.opts.password}`);
  }

  private sendRegister(bot: MfBot): void {
    if (!this.opts.password) {
      this.log.error('MCBOT_PASSWORD is empty — cannot /register');
      this.stage = 'locked';
      return;
    }
    if (this.opts.password.toLowerCase() === this.opts.username.toLowerCase()) {
      this.log.error('MCBOT_PASSWORD cannot equal MCBOT_USERNAME — server will reject');
      this.stage = 'locked';
      return;
    }
    this.stage = 'registering';
    this.log.info('sending /register');
    bot.chat(`/register ${this.opts.password} ${this.opts.password}`);
  }

  private success(via: 'login' | 'register'): void {
    this.stage = 'authenticated';
    this.log.info({ via }, 'bot authenticated');
  }
}
