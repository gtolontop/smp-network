# Deploying the SMP Discord Bot

Seven steps. Do them once, and the bot plus both plugin bridges come up
together on every reboot.

## 1. Discord app

1. https://discord.com/developers/applications → **New Application**.
2. Bot tab: enable **Message Content**, **Server Members** and
   **Presence** intents.
3. Copy the bot token, client id (OAuth2 → Client info), and your guild id.
4. Invite the bot with `applications.commands` and `bot` scopes plus at
   least `Manage Webhooks`, `Send Messages`, `Manage Channels`,
   `Manage Threads`, `Add Reactions`, `View Audit Log`.

## 2. Channels & roles

Create (or pick existing) text channels for chat bridge, status, events,
alerts, audit, recap, socials, tickets, suggestions, welcome. Create two
voice channels if you want live player-count / TPS display. Note every
id. Copy the admin / mod / linked / booster role ids.

## 3. Install the bot

```bash
cd discord-bot
cp .env.example .env          # then fill every field that matters
npm install
npm run build
```

Node.js **22.5+** required (built-in `node:sqlite`).

## 4. Paper plugin

Edit both lobby and survival `plugins/SMPCore/config.yml`:

```yaml
discord-bridge:
  enabled: true
  url: "ws://127.0.0.1:8787"       # or wherever the bot runs
  token: "SAME-AS-BRIDGE_TOKEN-IN-BOT-ENV"
  telemetry-interval-ticks: 40
```

Build the plugin:

```bash
cd plugins/core-paper
./gradlew shadowJar
cp build/libs/SMPCore-Paper-1.0.0.jar ../../lobby/plugins/
cp build/libs/SMPCore-Paper-1.0.0.jar ../../survival/plugins/
```

## 5. Velocity plugin

After the first run the plugin writes
`velocity/plugins/smp-core/discord-bridge.properties`. Open it and set:

```
enabled=true
url=ws://127.0.0.1:8787
token=SAME-AS-BRIDGE_TOKEN-IN-BOT-ENV
```

Build it:

```bash
cd plugins/core-velocity
./gradlew shadowJar
cp build/libs/SMPCore-Velocity-1.0.0.jar ../../velocity/plugins/
```

## 6. Run order

The bot must be up first so plugin clients can connect.

```bash
# terminal 1
cd discord-bot && npm start

# terminal 2
bash scripts/start-all.sh
```

On Windows a pair of `.bat` shortcuts does the same. If you want the
bot to autostart you can wrap it in `nssm` or a scheduled task that
runs `node dist/index.js` as a service.

## 7. First-run checks

- `/status` in Discord should show lobby, survival, velocity with TPS
  and online counts.
- `/host` should return a snapshot of the machine the bot runs on.
- Someone typing in-game should appear in `#chat` with their face.
- Discord messages in `#chat` should surface in every backend.
- `/link` → type `/link <code>` in game → role added, `/profile`
  returns live stats.

## Troubleshooting

- **Bridge won't connect**: make sure the Paper backend and the bot
  share the exact same `BRIDGE_TOKEN`, and that Windows Firewall
  allows the chosen port on loopback.
- **Chat bridge silent**: `CHANNEL_CHAT` must be a text channel the bot
  can see; the bot creates its webhook on first use and reuses it.
- **AI replies "IA non configurée"**: set `AI_API_KEY` (z.ai key) in
  `.env` and restart.
- **Mineflayer bot keeps reconnecting**: some online-mode proxies
  reject stable offline names. Set `MCBOT_AUTH=microsoft` plus the
  relevant account, or whitelist the bot's username.
