import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import type { SlashCommand } from '../client.js';

const who: SlashCommand = {
  name: 'who',
  data: new SlashCommandBuilder().setName('who').setDescription('Joueurs connectés, regroupés par serveur.'),
  async execute(ix) {
    const roster = hub.allRoster();
    if (!roster.length) {
      await ix.reply({ embeds: [baseEmbed({ description: '*Le réseau est vide.*', color: COLOR.muted })] });
      return;
    }
    const byServer = new Map<string, string[]>();
    for (const p of roster) {
      const s = p.server ?? 'unknown';
      if (!byServer.has(s)) byServer.set(s, []);
      byServer.get(s)!.push(`\`${p.name}\` (${p.ping}ms)`);
    }
    const sections = [...byServer.entries()].map(([server, names]) => ({
      name: `${server} · ${names.length}`,
      value: names.join(' · '),
    }));
    await ix.reply({
      embeds: [
        baseEmbed({
          title: `Joueurs connectés · ${roster.length}`,
          sections,
          color: COLOR.accent,
        }),
      ],
    });
  },
};

export default who;
