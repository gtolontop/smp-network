import si from 'systeminformation';

export interface HostSnapshot {
  cpuLoad: number;
  cpuCores: number;
  memUsed: number;
  memTotal: number;
  memPct: number;
  diskPct: number;
  diskUsed: number;
  diskTotal: number;
  uptimeSec: number;
  netRxKbps: number;
  netTxKbps: number;
  tempC?: number;
  osName: string;
  osVersion: string;
}

/** One-shot snapshot. Safe to call often; systeminformation caches. */
export async function hostSnapshot(): Promise<HostSnapshot> {
  const [load, mem, fs, net, temp, os] = await Promise.all([
    si.currentLoad(),
    si.mem(),
    si.fsSize(),
    si.networkStats().catch(() => []),
    si.cpuTemperature().catch(() => ({ main: undefined })),
    si.osInfo(),
  ]);

  const biggest = fs.reduce<si.Systeminformation.FsSizeData | null>(
    (acc, d) => (!acc || d.size > acc.size ? d : acc),
    null,
  );
  const totalDisk = biggest?.size ?? 0;
  const usedDisk = biggest?.used ?? 0;
  const diskPct = totalDisk ? (usedDisk / totalDisk) * 100 : 0;

  const primaryNet = net[0] ?? { rx_sec: 0, tx_sec: 0 };

  return {
    cpuLoad: load.currentLoad,
    cpuCores: load.cpus.length,
    memUsed: mem.active,
    memTotal: mem.total,
    memPct: (mem.active / mem.total) * 100,
    diskPct,
    diskUsed: usedDisk,
    diskTotal: totalDisk,
    uptimeSec: (si as unknown as { time: () => { uptime: number } }).time().uptime,
    netRxKbps: (primaryNet.rx_sec ?? 0) / 1024,
    netTxKbps: (primaryNet.tx_sec ?? 0) / 1024,
    tempC: typeof temp.main === 'number' ? temp.main : undefined,
    osName: os.distro,
    osVersion: os.release,
  };
}
