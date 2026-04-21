# SMP Discord Bot

Companion bot for the SMP network. One process, many faces.

## What it does

- **Chat bridge** — webhook-rendered chat bidirectional between Discord and every Minecraft server of the network.
- **Live status** — a single pinned embed auto-updates every 10s with TPS, MSPT, player list, uptime, host CPU/RAM.
- **Host monitoring** — CPU / RAM / disk / network / temperature with alerts when thresholds are crossed.
- **Admin console** — kick, ban, mute, whitelist, op, gamemode, broadcast, and a modal-backed `/console` to run arbitrary commands.
- **Account linking** — `/link` issues a one-time code typed in-game. Linked users get a role, show in `/profile`, can use economy commands.
- **Event feed** — joins, leaves, deaths, advancements, first-time logins, milestone announcements, rare drops.
- **AI** — z.ai GLM with **function calling** wired into live server state. It can answer *"how many players online"*, *"who died today"*, *"give me the top miner"* by actually querying the bridge.
- **In-game bot** — a Mineflayer-powered fake player that joins the survival server, greets new players, can follow, pathfind, relay chat, and be commanded from Discord.
- **Games** — tic-tac-toe, connect-4, blackjack, higher/lower, trivia, all playable in-channel, optionally betting server currency.
- **Give / pay / mail** — Discord users with linked accounts can send items, coins, or offline messages to other players.
- **Role sync** — Discord roles drive in-game permission groups. Boosters get cosmetic trails and nicks.
- **Dynamic voice channels** — player count and TPS written into voice channel names, updated every minute.
- **Tickets & suggestions** — threads + voting.
- **Social feeds** — Twitch live pings, YouTube uploads, arbitrary RSS.
- **Daily recap** — every day at 23:59 the bot posts the day's biggest deaths, longest sessions, richest player, most blocks broken.

## Architecture

```
              ┌─────────────────────────┐
              │   Discord (users)       │
              └────────────┬────────────┘
                           │ slash cmds, chat
              ┌────────────▼────────────┐
              │       smp-discord-bot    │
              │   discord.js + WS hub    │
              └────┬──────────┬──────────┘
                   │          │
   WS in/out       │          │ WS in/out
                   │          │
          ┌────────▼───┐  ┌───▼──────────┐
          │ core-paper │  │ core-velocity│
          │  bridge    │  │   bridge     │
          └────────────┘  └──────────────┘
```

The bot hosts a WebSocket server. Each MC server plugin connects as a client
with a shared token and announces its role (`velocity`, `lobby`, `survival`).
Packets are JSON, versioned, typed, and routed by origin.

## Quick start

```bash
cd discord-bot
cp .env.example .env
# fill .env (see the file for guidance)
npm install
npm run build
npm start
```

For development: `npm run dev` (tsx watch).

## Layout

```
src/
├── bot/             Discord client, commands, events, feature modules
├── bridge/          WebSocket server, protocol, RPC
├── ai/              z.ai GLM client, tool definitions
├── db/              better-sqlite3, migrations, queries
├── host/            systeminformation, alert engine
├── mcbot/           Mineflayer wrapper and behavior
├── social/          Twitch / YouTube / RSS pollers
├── utils/           logger, embeds, perms, format
├── config.ts        zod-validated env
└── index.ts         entry
```

## Requirements

- Node.js 20.12+
- A Discord bot with `MessageContent`, `GuildMembers`, `GuildPresences` intents
- Paper plugin built with the `discordbridge` module (see `plugins/core-paper`)
- Velocity plugin built with the `discordbridge` module (see `plugins/core-velocity`)
