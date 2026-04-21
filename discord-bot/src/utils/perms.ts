import type { ChatInputCommandInteraction, GuildMember } from 'discord.js';

import { config } from '../config.js';

export function isAdmin(member: GuildMember | null | undefined): boolean {
  if (!member) return false;
  if (member.permissions.has('Administrator')) return true;
  if (config.roles.admin && member.roles.cache.has(config.roles.admin)) return true;
  return false;
}

export function isMod(member: GuildMember | null | undefined): boolean {
  if (isAdmin(member)) return true;
  if (!member) return false;
  if (member.permissions.has('ModerateMembers')) return true;
  if (config.roles.mod && member.roles.cache.has(config.roles.mod)) return true;
  return false;
}

export async function assertAdmin(ix: ChatInputCommandInteraction): Promise<boolean> {
  const member = ix.member as GuildMember | null;
  if (!isAdmin(member)) {
    await ix.reply({ content: 'Commande réservée aux administrateurs.', ephemeral: true });
    return false;
  }
  return true;
}

export async function assertMod(ix: ChatInputCommandInteraction): Promise<boolean> {
  const member = ix.member as GuildMember | null;
  if (!isMod(member)) {
    await ix.reply({ content: 'Commande réservée aux modérateurs.', ephemeral: true });
    return false;
  }
  return true;
}
