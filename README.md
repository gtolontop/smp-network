# SMP Network

Serveur SMP Minecraft open-source avec architecture multi-serveurs, heberge en self-host.

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
        │    (Paper)    │        │    (Paper)       │
        │    :25566     │        │    :25567        │
        └───────────────┘        └─────────────────┘
```

| Composant | Software | Port | Role |
|-----------|----------|------|------|
| **Proxy** | Velocity | 25565 | Auth, routage, commandes reseau |
| **Lobby** | Paper | 25566 | Hub, menu serveurs, AFK zone |
| **Survival** | Paper | 25567 | Monde survie, bordure 10k, farms |

## Features

- **Velocity Proxy** - Modern forwarding, join/leave reseau, `/lobby`, `/survival`, `/glist`
- **Server Selector GUI** - Menu chest propre avec icones par serveur, clic droit compas
- **Paper latest** - MC 26.1.2 (resolved dynamically by `scripts/download.sh`)
- **Custom Generation** - Support datapacks (Terralith, Tectonic, etc.)
- **Optimise** - Configs Paper tunees, Alternate Current redstone, anti-xray
- **Chat format** - MiniMessage avec gradients
- **Spawn system** - `/spawn`, `/setspawn`

## Prerequis

- **Java 25+** ([Adoptium](https://adoptium.net/)) - inclus dans `java/`
- **Git**
- **Gradle** (inclus via wrapper dans les plugins)

## Installation rapide

### 1. Cloner le repo

```bash
git clone https://github.com/TON-USER/smp-network.git
cd smp-network
```

### 2. Telecharger les JARs

```bash
bash scripts/download.sh
```

Ce script telecharge Velocity et le dernier build Paper disponible pour `MC_VERSION`.

> Note : `26.1.2` est une branche experimentale sur Paper. Si les dimensions
> (Nether/End) ont un comportement instable, evite de re-pinner un vieux build
> experimental et prefere soit le dernier build disponible, soit une version
> stable de Paper si tu veux prioriser la fiabilite.

### 3. Compiler les plugins

```bash
# Plugin Velocity (proxy)
cd plugins/core-velocity
./gradlew shadowJar
cp build/libs/SMPCore-Velocity-1.0.0.jar ../../velocity/plugins/

# Plugin Paper (serveurs)
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
│   ├── paper.jar             # Paper (build telecharge dynamiquement)
│   ├── server.properties
│   ├── bukkit.yml
│   ├── spigot.yml
│   ├── config/
│   │   ├── paper-global.yml
│   │   └── paper-world-defaults.yml
│   └── plugins/
├── survival/                 # Serveur survie (Paper)
│   ├── paper.jar             # Paper (build telecharge dynamiquement)
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
│   └── core-paper/           # Plugin reseau cote serveurs (Paper API)
│       └── src/
├── java/
│   └── jdk-25.0.2+10/        # Java 25 bundle
├── scripts/
│   ├── download.sh           # Installe les JARs
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
| `/gsurvival` | `/survival`, `/surv`, `/s` | Rejoindre le survie |
| `/glist` | | Liste des joueurs en ligne |

### Serveurs (Paper)
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
