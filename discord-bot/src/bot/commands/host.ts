import { SlashCommandBuilder } from 'discord.js';

import { hostSnapshot } from '../../host/metrics.js';
import { COLOR, baseEmbed } from '../../utils/embeds.js';
import { bytesToHuman, pct } from '../../utils/format.js';
import { humanDuration } from '../../utils/time.js';
import type { SlashCommand } from '../client.js';

const host: SlashCommand = {
  name: 'host',
  data: new SlashCommandBuilder().setName('host').setDescription('Stats de la machine qui héberge le réseau.'),
  async execute(ix) {
    await ix.deferReply();
    const s = await hostSnapshot();
    await ix.editReply({
      embeds: [
        baseEmbed({
          title: 'Hôte',
          color: COLOR.accent,
          sections: [
            { name: 'OS', value: `${s.osName} ${s.osVersion}`, inline: false },
            { name: 'CPU', value: `${pct(s.cpuLoad)} · ${s.cpuCores} threads`, inline: true },
            {
              name: 'RAM',
              value: `${bytesToHuman(s.memUsed)}/${bytesToHuman(s.memTotal)} (${pct(s.memPct)})`,
              inline: true,
            },
            {
              name: 'Disque',
              value: `${bytesToHuman(s.diskUsed)}/${bytesToHuman(s.diskTotal)} (${pct(s.diskPct)})`,
              inline: true,
            },
            {
              name: 'Réseau',
              value: `↓ ${s.netRxKbps.toFixed(0)} kb/s · ↑ ${s.netTxKbps.toFixed(0)} kb/s`,
              inline: true,
            },
            { name: 'Uptime', value: humanDuration(s.uptimeSec), inline: true },
            {
              name: 'Température',
              value: typeof s.tempC === 'number' ? `${s.tempC.toFixed(1)} °C` : '*non exposée*',
              inline: true,
            },
          ],
          timestamp: true,
        }),
      ],
    });
  },
};

export default host;
