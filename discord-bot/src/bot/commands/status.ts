import { SlashCommandBuilder } from 'discord.js';

import { hub } from '../../bridge/hub.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { humanDuration } from '../../utils/time.js';
import type { SlashCommand } from '../client.js';

const status: SlashCommand = {
  name: 'status',
  data: new SlashCommandBuilder().setName('status').setDescription('Statut instantané du réseau.'),
  async execute(ix) {
    const peers = hub.list();
    if (!peers.length) {
      await ix.reply({
        embeds: [baseEmbed({ description: 'Aucun serveur connecté au bridge.', color: COLOR.warn })],
      });
      return;
    }

    const sections = peers.map((p) => {
      const t = p.telemetry;
      const lines = t
        ? [
            `TPS \`${t.tps1m.toFixed(1)}/${t.tps5m.toFixed(1)}/${t.tps15m.toFixed(1)}\``,
            `MSPT \`${t.msptAvg.toFixed(1)}\` (p95 \`${t.msptP95.toFixed(1)}\`)`,
            `Joueurs \`${t.online}/${t.maxOnline}\``,
            `Chunks \`${t.loadedChunks}\` · Entités \`${t.entities}\``,
            `Mémoire \`${t.memUsedMb}/${t.memMaxMb} MB\``,
            `Uptime \`${humanDuration(t.uptimeSec)}\``,
          ]
        : ['*en attente de télémétrie*'];
      return {
        name: `${p.origin} · ${p.software} · ${p.mcVersion}`,
        value: lines.join('\n'),
      };
    });

    await ix.reply({
      embeds: [baseEmbed({ title: 'Statut réseau', sections, color: COLOR.accent, timestamp: true })],
    });
  },
};

export default status;
