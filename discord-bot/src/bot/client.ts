import {
  Client,
  GatewayIntentBits,
  Partials,
  ActivityType,
  Events,
  Collection,
} from 'discord.js';
import type { ChatInputCommandInteraction, SlashCommandBuilder } from 'discord.js';

import { config } from '../config.js';
import { child } from '../utils/logger.js';

const log = child({ mod: 'discord' });

export interface SlashCommand {
  data: SlashCommandBuilder | ReturnType<SlashCommandBuilder['toJSON']> | unknown;
  name: string;
  execute: (interaction: ChatInputCommandInteraction) => Promise<void>;
}

export const commands = new Collection<string, SlashCommand>();

export const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMembers,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
    GatewayIntentBits.GuildPresences,
    GatewayIntentBits.GuildVoiceStates,
    GatewayIntentBits.GuildMessageReactions,
    GatewayIntentBits.DirectMessages,
  ],
  partials: [Partials.Channel, Partials.Message, Partials.Reaction],
  allowedMentions: { parse: ['users', 'roles'], repliedUser: false },
  presence: {
    activities: [{ name: 'the SMP · /help', type: ActivityType.Watching }],
    status: 'online',
  },
});

client.on(Events.Error, (err) => log.error({ err: err.message }, 'discord client error'));
client.on(Events.Warn, (msg) => log.warn({ msg }, 'discord warn'));

export async function login(): Promise<void> {
  await client.login(config.discord.token);
}
