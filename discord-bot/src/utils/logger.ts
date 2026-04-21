import { pino } from 'pino';

import { config } from '../config.js';

export const logger = pino({
  level: config.meta.logLevel,
  base: { app: 'smp-bot' },
  timestamp: pino.stdTimeFunctions.isoTime,
  transport:
    process.env.NODE_ENV === 'production'
      ? undefined
      : {
          target: 'pino-pretty',
          options: {
            colorize: true,
            translateTime: 'HH:MM:ss.l',
            ignore: 'pid,hostname,app',
            messageFormat: '{msg}',
          },
        },
});

export type Logger = typeof logger;

export const child = (bindings: Record<string, unknown>) => logger.child(bindings);
