import './db/index.js'; // runs migrations on import

import { config } from './config.js';
import { logger } from './utils/logger.js';
import { client, commands, login } from './bot/client.js';
import { publishCommands, register } from './bot/registry.js';
import './bot/events/ready.js';
import './bot/events/interactionCreate.js';

import { startBridgeServer } from './bridge/server.js';
import { initChatBridge } from './bot/modules/chatBridge.js';
import { initStatusPanel } from './bot/modules/statusPanel.js';
import { initEventFeed } from './bot/modules/eventFeed.js';
import { initVoiceStats } from './bot/modules/voiceStats.js';
import { initAuditLog } from './bot/modules/auditLog.js';
import { initWelcome } from './bot/modules/welcome.js';
import { initDailyRecap } from './bot/modules/dailyRecap.js';
import { initAiMention } from './bot/modules/aiMention.js';
import { initPresenceRotation } from './bot/modules/presenceRotation.js';
import { initAlerts } from './host/alerts.js';
import { mcbot } from './mcbot/index.js';
import { initBrain } from './mcbot/brain.js';
import { initJoinDelivery } from './bot/modules/joinDelivery.js';
import { initRoleSync } from './bot/modules/roleSync.js';
import { initLinkHandler } from './bot/modules/linkHandler.js';
import { initSocials } from './social/index.js';

// Commands
import help from './bot/commands/help.js';
import link, { unlinkCmd } from './bot/commands/link.js';
import profile from './bot/commands/profile.js';
import who from './bot/commands/who.js';
import status from './bot/commands/status.js';
import hostCmd from './bot/commands/host.js';
import leaderboard from './bot/commands/leaderboard.js';
import seen from './bot/commands/seen.js';
import broadcast from './bot/commands/broadcast.js';
import { kick, ban, unban, mute, whitelist } from './bot/commands/moderation.js';
import consoleCmd from './bot/commands/console.js';
import give from './bot/commands/give.js';
import mail from './bot/commands/mail.js';
import pay from './bot/commands/pay.js';
import mcbotCmd from './bot/commands/mcbot.js';
import aiCmd from './bot/commands/ai.js';
import game from './bot/commands/game.js';
import restart from './bot/commands/restart.js';
import suggest from './bot/commands/suggest.js';
import ticket from './bot/commands/ticket.js';
import stats from './bot/commands/stats.js';
import backup from './bot/commands/backup.js';
import serverip from './bot/commands/serverip.js';
import discord from './bot/commands/discord.js';

async function main(): Promise<void> {
  logger.info(
    {
      guild: config.discord.guildId,
      bridge: `${config.bridge.host}:${config.bridge.port}`,
      ai: config.ai.baseUrl,
      mcbot: config.mcbot.enabled,
    },
    'starting smp-discord-bot',
  );

  // Register commands in code before publishing to Discord.
  for (const cmd of [
    help,
    link,
    unlinkCmd,
    profile,
    who,
    status,
    hostCmd,
    leaderboard,
    seen,
    broadcast,
    kick,
    ban,
    unban,
    mute,
    whitelist,
    consoleCmd,
    give,
    mail,
    pay,
    mcbotCmd,
    aiCmd,
    game,
    restart,
    suggest,
    ticket,
    stats,
    backup,
    serverip,
    discord,
  ]) {
    register(cmd);
  }
  logger.info({ count: commands.size }, 'commands registered');

  // Bring up the bridge server first so plugins can begin connecting.
  startBridgeServer();

  // Login Discord and wait for ready before touching channels.
  await login();
  await new Promise<void>((resolve) => client.once('ready', () => resolve()));

  await publishCommands();

  // Modules.
  initAuditLog(client);
  await initChatBridge(client);
  await initStatusPanel(client);
  initEventFeed(client);
  initVoiceStats(client);
  initWelcome(client);
  initDailyRecap(client);
  initAiMention(client);
  initPresenceRotation(client);
  initAlerts(client);
  initJoinDelivery();
  initRoleSync(client);
  initLinkHandler(client);
  initSocials(client);

  // In-game bot.
  mcbot.start();
  initBrain();

  logger.info('smp-discord-bot ready');
}

process.on('unhandledRejection', (reason) =>
  logger.error({ err: reason instanceof Error ? reason.message : String(reason) }, 'unhandled rejection'),
);
process.on('uncaughtException', (err) =>
  logger.fatal({ err: err.message, stack: err.stack }, 'uncaught exception'),
);

const shutdown = () => {
  logger.info('shutting down');
  mcbot.stop();
  client.destroy();
  process.exit(0);
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

main().catch((err) => {
  logger.fatal({ err: err instanceof Error ? err.message : String(err) }, 'boot failed');
  process.exit(1);
});
