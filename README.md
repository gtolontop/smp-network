# SMP Network

Serveur SMP Minecraft open-source avec architecture multi-serveurs, hébergé en self-host.

## Architecture

```
                    ┌─────────────────────┐
    Joueurs ──────► │   Velocity Proxy    │ :25565
                    │   (authentification) │
                    └────────┬────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
        ┌───────▼───────┐        ┌────────▼────────┐
        │    Lobby      │        │    Survival      │
        │    (Paper)    │        │    (Folia)       │
        │    :25566     │        │    :25567        │
        └───────────────┘        └─────────────────┘
```

| Composant | Software | Port | Role |
|-----------|----------|------|------|
| **Proxy** | Velocity | 25565 | Auth, routage, commandes réseau |
| **Lobby** | Paper | 25566 | Hub, menu serveurs, AFK zone |
| **Survival** | Folia | 25567 | Monde survie, bordure 10k, farms |

## Features

- **Velocity Proxy** - Modern forwarding, join/leave réseau, `/lobby`, `/survival`, `/glist`
- **Server Selector GUI** - Menu chest propre avec icones par serveur, clic droit compas
- **Folia Survival** - Multithreading régionalisé pour 30-60 joueurs avec grosses farms
- **Custom Generation** - Support datapacks (Terralith, Tectonic, etc.)
- **Optimisé** - Configs Paper/Folia tunées, Alternate Current redstone, anti-xray
- **Chat format** - MiniMessage avec gradients
- **Spawn system** - `/spawn`, `/setspawn`

## Prérequis

- **Java 21+** ([Adoptium](https://adoptium.net/))
- **Git**
- **Gradle** (inclus via wrapper dans les plugins)

## Installation rapide

### 1. Cloner le repo

```bash
git clone https://github.com/TON-USER/smp-network.git
cd smp-network
```

### 2. Telecharger les JARs serveur

```bash
bash scripts/download.sh
```

Ou manuellement :
- [Velocity](https://papermc.io/downloads/velocity) → `velocity/velocity.jar`
- [Paper](https://papermc.io/downloads/paper) → `lobby/paper.jar`
- [Folia](https://papermc.io/downloads/folia) → `survival/folia.jar`

### 3. Compiler les plugins

```bash
# Plugin Velocity (proxy)
cd plugins/core-velocity
./gradlew shadowJar
cp build/libs/SMPCore-Velocity-1.0.0.jar ../../velocity/plugins/

# Plugin Paper/Folia (serveurs)
cd ../core-paper
./gradlew shadowJar
cp build/libs/SMPCore-Paper-1.0.0.jar ../../lobby/plugins/
cp build/libs/SMPCore-Paper-1.0.0.jar ../../survival/plugins/
```

### 4. Configurer

- **Forwarding secret** : le fichier `velocity/forwarding.secret` doit matcher le secret dans `lobby/config/paper-global.yml` et `survival/config/paper-global.yml`
- **Lobby** : `lobby/plugins/SMPCore/config.yml` → `server-type: "lobby"`
- **Survival** : `survival/plugins/SMPCore/config.yml` → `server-type: "survival"`

### 5. Accepter l'EULA

```bash
echo "eula=true" > lobby/eula.txt
echo "eula=true" > survival/eula.txt
```

### 6. Lancer

**Windows :**
```cmd
scripts\start-all.bat
```

**Linux/macOS :**
```bash
bash scripts/start-all.sh
```

## Datapacks (generation custom)

Les datapacks vont dans `survival/world/datapacks/` apres le premier lancement du serveur.

Recommandes :
- [Terralith](https://modrinth.com/datapack/terralith) - Generation terrain
- [Tectonic](https://modrinth.com/datapack/tectonic) - Montagnes et terrain
- [Structory](https://modrinth.com/datapack/structory) - Structures custom
- [Incendium](https://modrinth.com/datapack/incendium) - Nether custom
- [Nullscape](https://modrinth.com/datapack/nullscape) - End custom

> **Important** : les datapacks de terrain doivent etre installes **avant** la generation du monde. Supprime le dossier `world/` si tu veux regenerer.

## Structure du projet

```
.
├── velocity/                 # Velocity proxy
│   ├── velocity.toml         # Config proxy
│   ├── forwarding.secret     # Secret partage avec les backends
│   └── plugins/              # Plugins Velocity
├── lobby/                    # Serveur lobby (Paper)
│   ├── server.properties
│   ├── bukkit.yml
│   ├── spigot.yml
│   ├── config/
│   │   ├── paper-global.yml
│   │   └── paper-world-defaults.yml
│   └── plugins/
├── survival/                 # Serveur survie (Folia)
│   ├── server.properties
│   ├── bukkit.yml
│   ├── spigot.yml
│   ├── config/
│   │   ├── paper-global.yml
│   │   └── paper-world-defaults.yml
│   ├── datapacks/            # Datapacks a installer
│   └── plugins/
├── plugins/
│   ├── core-velocity/        # Plugin reseau cote proxy
│   │   └── src/
│   └── core-paper/           # Plugin reseau cote serveurs
│       └── src/
├── scripts/
│   ├── download.sh           # Telecharge les JARs
│   ├── start-all.bat         # Demarre tout (Windows)
│   ├── start-all.sh          # Demarre tout (Linux)
│   └── stop-all.sh           # Arrete tout (Linux)
└── README.md
```

## Commandes

### Proxy (Velocity)
| Commande | Aliases | Description |
|----------|---------|-------------|
| `/globby` | `/lobby`, `/hub`, `/l` | Rejoindre le lobby |
| `/gsurvival` | `/survival`, `/surv`, `/s` | Rejoindre le servie |
| `/glist` | | Liste des joueurs en ligne |

### Serveurs (Paper/Folia)
| Commande | Aliases | Description |
|----------|---------|-------------|
| `/menu` | `/servers`, `/selector` | Ouvrir le menu serveurs |
| `/spawn` | | Teleportation au spawn |
| `/setspawn` | | Definir le spawn (admin) |

## Acces distant

### Cloudflare Tunnel (recommande)

```bash
# Installer cloudflared
# Creer un tunnel pour le port MC
cloudflared tunnel create smp
cloudflared tunnel route dns smp mc.ton-domaine.fr

# Config tunnel (config.yml cloudflared)
# tunnel: <tunnel-id>
# credentials-file: ~/.cloudflared/<tunnel-id>.json
# ingress:
#   - hostname: mc.ton-domaine.fr
#     service: tcp://localhost:25565
#   - hostname: panel.ton-domaine.fr
#     service: http://localhost:8080
#   - service: http_status:404
```

> Note : les tunnels Cloudflare TCP pour Minecraft necessitent que les joueurs utilisent `cloudflared access tcp` cote client, ou alors utiliser un SRV record + port forwarding classique pour le port MC.

### Panel Web

Options recommandees pour Windows :
- **[MCSManager](https://mcsmanager.com/)** - Panel web natif Windows
- **[Crafty Controller](https://craftycontrol.com/)** - Panel Python multi-plateforme

## Specs recommandees

Ce setup a ete concu pour tourner sur :
- **CPU** : Ryzen 7 7700 (8c/16t)
- **RAM** : 64 Go DDR5
- **Stockage** : NVMe SSD
- **Allocation** : Velocity 512MB / Lobby 2GB / Survival 16GB

## License

MIT - voir [LICENSE](LICENSE)
