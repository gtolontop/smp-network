# CAHIER DES CHARGES MINECRAFT V3

Document de direction créative pour un serveur SMP custom premium, économie, progression, events, items, boss, teams et sécurité.  
Date de rédaction : 2026-05-04. Contexte local observé : réseau Velocity + Lobby Paper + Survival Paper, SMPCore maison, économie, saphirs, auctions, orders, bounties, teams, homes, TPA, wealth sinks, duels, custom enchants, spawners, logger, anti-cheat, Discord bridge, event worlds.

## 0. Méthode et règles de lecture

Le serveur doit rester un SMP Minecraft lisible. La bonne direction n'est pas "ajouter 100 systèmes RPG", mais créer une boucle simple : jouer, gagner, investir, se spécialiser, s'afficher, coopérer, rivaliser, revenir demain.  
Notation utilisée dans les tableaux : difficulté, exploit et lag sont notés sur 10, où 10 = très difficile ou très risqué. Priorités : `now` = coeur de V3, `later` = bon après base stable, `maybe` = à prototyper, `avoid` = séduisant mais dangereux.

Sources externes consultées et adaptées : PaperMC Anti-Xray et configuration, PaperMC profiling/spark, Purpur docs, CoreProtect, spark, Oraxen, ItemsAdder crops, MythicMobs, Quests, Citizens, ExcellentEnchants, PlaceholderAPI, GriefPrevention, Chunky, Grim AntiCheat, Matrix AntiCheat, Terralith, Minecraft Usage Guidelines, et exemples de SMP custom actuels comme PrimordialMC. Les liens sont regroupés en fin de document.

## 1. Executive Summary

### Vision

Créer un SMP custom francophone premium qui garde le coeur de Minecraft survival, mais donne une vraie raison de jouer longtemps : économie utile, progression claire, events réguliers, items custom pratiques, boss mémorables, teams légères, sécurité sérieuse et contenu facile à montrer en vidéo.

Le serveur doit être "vanilla+ intelligent" : assez custom pour être identifiable en 30 secondes, pas assez chargé pour perdre un nouveau joueur. Le joueur doit comprendre vite : je mine/farm, je vends, j'achète des upgrades, je rejoins une team, je fais des quêtes/events, je prépare des boss, je montre ma base et mes cosmétiques.

### Ce qui rend le serveur unique

- Une économie qui sert vraiment à quelque chose : upgrades, spawners, carburants, réparations, teams, black market, cosmétiques, boss tickets, taxes et locations.
- Une double monnaie : argent classique pour le quotidien, saphirs pour le rare, le prestige et les décisions importantes.
- Des outils custom pratiques mais limités par énergie, durabilité, cooldowns et coûts.
- Des events communautaires lancables proprement, plus des events aléatoires monde qui créent des moments vidéo.
- Des boss et world events avec récompenses par contribution, pas seulement "taper un mob avec beaucoup de vie".
- Un système de teams light : identité, banque, upgrades, rivalités, classements, mais peu de drama destructeur.
- Une couche anti-cheat/anti-find/logger assumée comme une feature de confiance.

### Expérience joueur visée

- Nouveau joueur : comprend en moins de 5 minutes comment gagner de l'argent, se téléporter, créer une base, vendre, rejoindre une team et suivre une quête.
- Joueur casual : peut progresser chaque jour sans obligation PvP.
- Grinder : a des objectifs économiques, spawners, collections, enchants, outils, crops, titres et classements.
- Joueur social : participe aux teams, events, duels, marchés, bounties, boss publics.
- Créateur de contenu : trouve des clips naturels : boss, météorites, marchés noirs, events, moments PvP, fin de saison.

### A garder absolument

Enchants customs, potions custom, argent utile, spawners, events, homes/TPA, combat log, items utiles, progression custom, features communautaires, saphirs, auctions/orders, bounties, duels, logger, anti-xray.

### A améliorer absolument

Onboarding, équilibre économie, anti-base finder, anti-cheat mouvement/combat, performance des entités/spawners/redstone, tutorial spawn, quêtes quotidiennes, teams, communication vidéo, roadmap de saisons.

### A éviter

5x5 permanent, fly payant, kits payants puissants, spawners infinis, casino lié à l'argent réel, mobs custom partout, boss sacs à PV, systèmes de pourriture trop punitifs, claims trop politiques, enchants PvP incontrôlables, features invisibles qui demandent beaucoup de maintenance.

## 2. Piliers du serveur

| Pilier | Description | Pourquoi c'est important | Features liées | Risques | Règles de design |
|---|---|---|---|---|---|
| Economie utile et permanente | L'argent sert à débloquer, réparer, accélérer, louer, taxer, miser et personnaliser. | Sans sink, le serveur meurt dès que les grinders sont riches. | Shop, AH, orders, wealth, spawners, repairs, team bank, black market, taxes. | Inflation, farms AFK, duplication, monopoles. | Chaque gain doit avoir un sink clair. Les gros achats doivent être visibles. |
| Progression vanilla+ | Ajouter des paliers sans remplacer Minecraft. | Les joueurs ont besoin d'objectifs mais pas d'un MMO illisible. | Quêtes, enchants, outils, crops, boss, saphirs. | Trop complexe, trop grind, power creep. | Simple à expliquer, profond à optimiser. |
| Communauté et événements | Le serveur doit vivre même hors grind solo. | Les meilleurs souvenirs viennent des moments partagés. | Events admin, events monde, boss publics, duels, teams, classements. | Toxicité, favoritisme, bugs live. | Events courts, récompenses plafonnées, logs et commandes admin propres. |
| Items utiles mais équilibrés | Les items custom doivent résoudre des besoins réels. | C'est le contenu le plus filmable et collectionnable. | Foreuse, tronçonneuse, grappin, scanner, voidstone, boussoles. | Cheat mining, lag, PvP cassé. | Cooldowns, énergie, durabilité, interdictions PvP si besoin. |
| Boss et endgame | Donner une vraie fin de progression de saison. | Un SMP sans endgame dépend trop des spawners. | Boss early/mid/end, boss publics, Nether/End rework, trophées. | Lag, one-shot, leeching, rewards trop forts. | Télégraphes lisibles, récompenses par contribution, arènes contrôlées. |
| Teams light | Créer des identités sans devenir factions toxiques. | Les groupes retiennent les joueurs. | Team bank, home, upgrades, alliances, quêtes team, classements. | Drama, grief, collusion, domination. | Pas de raid destructeur par défaut, rivalités soft et économiques. |
| Confiance et sécurité | Le joueur doit croire que son temps n'est pas volé par triche/exploit. | La confiance vaut plus qu'une feature. | Anti-xray, anti-cheat, anti-dupe, logs, rollback, anti-alt. | Faux positifs, surveillance lourde, lag. | Détecter, logger, alerter; punir progressivement. |
| Saisons et contenu visuel | Donner un arc narratif et marketing. | Les resets/chapitres créent une raison de revenir. | Lore, trailers, world events, fin de saison, spawn évolutif. | Trop de prod, promesses non tenues. | Une saison = 1 thème fort + 3 features fortes + 1 boss final. |

## 3. Ce qui a marché

| Système | Pourquoi ça a marché | Comment l'améliorer | A ne pas refaire | Nouvelles idées liées |
|---|---|---|---|---|
| Enchants customs | Les joueurs aiment optimiser leur stuff et distinguer leur build. | Limiter les stacks abusifs, créer des raretés, lier à l'économie. | Trop d'enchants PvP RNG qui décident les fights. | Livres rares, reroll saphir, forge d'enchants, enchants boss. |
| Potions custom | Effets temporaires faciles à comprendre. | Les mettre dans crafts, boss, events et crops. | Potions qui rendent invincible ou allongent trop le reach. | Potions de forage, anti-chute, respiration, focus boss. |
| Argent utile | Signal très fort : c'est l'ADN du serveur. | Ajouter plus de sinks visibles et progressifs. | Argent qui ne sert qu'à /baltop. | Wealth tree, team upgrades, taxes AH, carburants, shop rent. |
| Spawners | Objectif économique clair et long terme. | Rentabilité progressive, maintenance, limites anti-lag. | Spawners AFK infinis ou cheap. | Spawner GUI, upgrades, maintenance, taxes, spawners d'event. |
| Events | Génère des pics de joueurs et clips. | Système /event complet, horaires, récompenses standardisées. | Events improvisés sans logs ni anti-abus. | Color, Spleef, caravane, boss public, météorite, pêche flash. |
| Homes / TPA | UX vitale pour SMP public. | Monétiser en argent ingame, cooldowns propres, limites PvP. | Trop de TP qui annule l'exploration/PvP. | Homes achetables, /back PVE payant, team home bannière. |
| Combat log | Protège l'équité PvP. | Tag clair, restrictions TP, mort contrôlée si déco. | Punition opaque ou trop bug. | Scoreboard combat, logger kill, anti pearl logout. |
| Items custom utiles | Les items donnent une identité au serveur. | Système énergie/essence unifié. | Items purement gadget. | Grappin, boomerang, scanner, voidstone, casque lampe, foreuse 3x3. |
| Progression custom | Donne un chemin au-delà du vanilla. | Roadmap early/mid/late, quêtes et boss. | Tout débloquer trop vite. | Paliers de saphirs, tiers spawners, boss tokens, rangs de métiers. |
| Features communautaires | Les joueurs reviennent pour les gens. | Teams, classements, events, spotlight Discord. | Trop de permissions confuses. | Team quests, marché de spawn, contrats, tournois. |

## 4. Ce qui doit être amélioré

| Point | Problème probable | Solution recommandée | Features associées | Priorité |
|---|---|---|---|---|
| Anti-cheat | Un seul anti-cheat maison ne couvrira pas tout. | Garder maison pour règles serveur + ajouter Grim/Matrix selon tests. | Alerts staff, logs, exemptions custom items. | now |
| Anti-find / anti-base finder | Xray, freecam, seed cracking, chest ESP ne sont jamais 100% bloquables. | Paper anti-xray par monde, seed spoof si possible, logs rares, locator disabled, chest exposure design. | Suspicion score, fake honeytokens, audits staff. | now |
| Anti chest viewer / ESP | Les tile entities exposées restent difficiles à cacher. | Ne pas promettre l'impossible; réduire infos envoyées quand possible et détecter comportements. | Logs coffres, patterns d'ouverture, base visits. | later |
| Logger system | Déjà très bon signal dans le repo, à industrialiser. | Couvrir blocs, containers, trades, kills, spawners, économie, teams, rare items. | /co-like, rollback ciblé, relation graph. | now |
| Alliances / teams | Risque de système incomplet ou trop permissif. | Teams light avec upgrades, banque, home, alliances utiles mais pas claims lourds au départ. | Team bank, team quests, taxes soft. | now |
| Quêtes journalières | Retention quotidienne encore à formaliser. | 3 quêtes/jour + 1 hebdo + 1 team, anti-abus, reroll payant. | Quest GUI, streak, saphirs rares. | now |
| Duels | Excellente feature si anti-triche et kits propres. | 1v1 kit imposé d'abord, puis stuff perso avec pari plafonné. | Leaderboard, spectateurs, tournois. | now |
| Communication | Les features ne vivent pas si elles ne sont pas montrées. | Plan Shorts/TikTok, trailers, patch notes, teasers boss. | Discord bot, clips events. | now |
| Performance | Spawners, hoppers, mobs custom et datapacks peuvent tuer le TPS. | Budgets techniques par feature + spark hebdo + pregen Chunky. | Limits par chunk, virtualisation spawner, timings/spark. | now |
| Tutoriel | Un joueur perdu quitte. | Spawn en portes + tuto 3 minutes + récompense + NPC/GUI. | /tuto, guide économie, guide teams. | now |
| Spawn | Doit vendre le concept en 30 secondes. | Hub compact, lisible, portes systèmes, leaderboard, marché. | NPC, holograms sobres, zones shops. | now |
| Economie | Risque inflation si vente/farms trop généreuses. | Courbes early/mid/late, sinks, taxes, buy orders, price bands. | Worth tiers, AH tax, maintenance. | now |
| UX joueur | Beaucoup de commandes = friction. | Menus clairs, /menu central, feedback actionbar, docs courtes. | Menus GUI, guide Discord, tooltips. | now |

## 5. Economie complète

### Vision économie

L'économie doit être une machine à décisions. Le joueur gagne de l'argent avec plusieurs styles de jeu, puis choisit : confort, puissance limitée, rendement, prestige, team, boss, commerce ou cosmétiques. Le but n'est pas de vider les poches par punition, mais de donner envie de dépenser.

### Monnaies principales

- Argent normal : monnaie de circulation. Obtenu par vente, quêtes, jobs, events, commerce, bounties, duels plafonnés.
- Saphirs : monnaie rare de prestige/progression. Obtenue par playtime actif, boss, events, quêtes hebdo, top contribution, PvP anti-farm, fragments saison.
- Monnaies temporaires : tokens event, boss tokens, fragments de saison. Elles empêchent les anciens riches d'acheter tout le contenu de saison jour 1.

### Progression économique

| Phase | Objectif joueur | Revenus | Dépenses | Design |
|---|---|---|---|---|
| Early | Comprendre vendre, home, shop, quêtes. | Sell hand/all, quêtes simples, mobs, farms manuelles. | Homes, TPA, outils basiques, seeds custom. | Prix bas, feedback clair, pas de dette. |
| Mid | Automatiser et se spécialiser. | Crops, AH, orders, spawners T1/T2, events. | Spawners, upgrades, sell sticks, foreuse, team. | Gros sinks mais progression visible. |
| Late | Prestige, boss, domination économique. | Boss, marché, spawners optimisés, contracts, rare drops. | Boss tickets, maintenance spawners, cosmétiques, team upgrades. | Dépenser pour statut et contenu, pas pour one-shot. |

### Systèmes économiques

| Système | Utilité | Fonctionnement | Pourquoi payer | Pricing indicatif | Inflation/exploit | Priorité |
|---|---|---|---|---|---|---|
| Shop spawn | Prix planchers/plafonds. | Achats/ventes sélectionnés, pas tous les items. | Accès rapide aux bases. | Prix dynamiques ou tiers. | Trop de buyback = inflation. | now |
| Worth/sell | Revenu lisible. | Items vendables classés par catégories. | Gagner vite après farm. | Tiers early bas, late contrôlé. | Farms AFK, dupes. | now |
| Auction House | Commerce joueur. | Listing avec taxe + durée. | Vendre items rares. | Taxe 3-5%, frais fixe. | Manipulation prix. | now |
| Buy orders | Demande structurée. | Joueur poste commande d'achat. | Obtenir ressources sans spam chat. | Frais création 1-2%. | Orders fake pour transfert. | now |
| Marché joueur | Interaction sociale. | Parcelles louables au spawn. | Visibilité, prestige. | Loyer hebdo croissant. | Stockage abusif. | later |
| Marché noir | Rotation rare. | PNJ/event temporaire, prix saphirs/argent. | Items introuvables autrement. | Cher, stock limité. | Pay-to-win si items trop forts. | later |
| Loterie | Fun communautaire. | Tickets argent ingame, jackpot plafonné, aucune monnaie réelle. | Rêve de gain. | Ticket petit, taxe 20%. | Risque guidelines si présenté comme gambling. | maybe |
| Casino contrôlé | A traiter avec méfiance. | Mini-jeux gratuits/argent ingame, odds publiques, plafonds. | Divertissement. | Mise max faible. | Peut nuire image all-age. | maybe/avoid |
| Spawners | Investissement long terme. | Achat, stack, upgrades, maintenance. | Revenu/XP régulier. | ROI 5-10 jours actifs. | AFK, entités, inflation. | now |
| Pince à spawner | Mobilité spawner. | Item rare, chance de casse selon tier. | Réorganiser base. | Argent + saphirs. | Dupes/casse bugs. | later |
| Homes | Confort. | 3 slots base, 4/5 achetables. | Base/farms multiples. | 25k puis 100k. | Annule exploration si trop. | now |
| TPA | Confort social. | Cooldown + coût léger optionnel. | Jouer avec amis. | Gratuit ou 100-500. | Abuse PvP. | now |
| /back PVE | Récupération. | Seulement mort PVE, jamais combat tag, coût. | Sauver temps. | 2k-20k selon wealth. | Annule danger. | later |
| Enderchest | Stockage. | Pages ou slots bonus ingame. | QoL non P2W si limité. | Argent + saphirs. | Trop de stockage mobile. | later |
| Repairs | Sink stable. | Réparer items custom/vanilla, coût par tier. | Préserver gear. | 5-15% valeur item. | Trop cheap = mending inutile. | now |
| Crates | Rewards cadrées. | Clés vote/event/boss, odds publiques. | Suspense. | Pas vendues P2W. | Gambling perception. | maybe |
| Familiers | Utility faible + cosmétique. | Achat, skins, nourriture. | Style + micro QoL. | Argent/saphirs/cosmétiques. | Ramassage trop fort. | later |
| Titres/tags | Prestige. | Déblocage par argent, saphirs, events. | Affichage social. | 10k à 1M. | Aucun fort. | now |
| Team bank | Objectifs groupe. | Compte partagé, permissions internes. | Financer upgrades. | Dépôts libres, retraits logs. | Vol interne/drama. | now |
| Team upgrades | Progression sociale. | Slots, home, tag, banque, quêtes. | Grandir. | Courbe exponentielle. | Teams dominantes. | now |
| Contrats de chasse | Activité PvP/PVE. | Bounties, mobs rares, objets à livrer. | Gains ciblés. | Commission 5-10%. | Farm amis/alts. | now |
| Parcelles louables | Vie au spawn. | Shops physiques temporaires. | Visibilité. | Loyer hebdo. | Abandon. | later |

## 6. Système de monnaies

### Argent normal

Rôle : circulation quotidienne. L'argent doit pouvoir être gagné par mining, farming, chasse, pêche, quêtes, commerce, events et services entre joueurs. Il doit sortir via taxes, repairs, homes, upgrades, carburants, spawners, locations et cosmétiques.

### Saphirs rares

Rôle : rareté, prestige, limites anti-inflation. Les saphirs ne doivent pas remplacer l'argent, mais décider les achats importants.

| Obtention saphirs | Fonctionnement | Anti-farm | Notes |
|---|---|---|---|
| Playtime actif | Petite chance ou fragments toutes X minutes actives. | AFK detection, interactions requises. | Bon pour retention. |
| Quêtes hebdo | 1-5 saphirs selon difficulté. | Objectifs variés, reset. | Stable. |
| Boss | Drops par contribution. | Participation minimum, cooldown compte. | Très fort. |
| Events | Top ou participation. | Récompense plafonnée. | Social. |
| PvP | Petite prime sur kills valides. | Anti-alt, cooldown adversaire, différence IP, relation graph. | Risqué mais excitant. |
| Marché noir | Achat rare contre argent très cher. | Stock hebdo limité. | Sink late. |
| Fragments saison | Convertibles fin de saison. | Cap journalier. | Evite rush. |

Utilisations recommandées : pince à spawner, boss keys, reroll enchant, skin item, upgrades team late, fragments d'armes boss, cosmétiques premium, slots spéciaux, black market.  
Utilisations à éviter : vendre directement des dégâts PvP, kits d'armure, spawners late sans argent, fly, protection absolue.

### Monnaies d'event et boss tokens

Créer des tokens temporaires quand un contenu doit être isolé de l'économie principale. Exemple : `Jetons Lune Rouge`, `Eclats du Duc`, `Fragments d'Anomalie`. Ils servent à acheter des cosmétiques, trophées, crafts boss, pas à contourner toute la progression.

## 7. Enchants custom

Règle centrale : un enchant doit créer une décision, pas juste "plus fort". Les enchants de zone doivent être limités. 3x3 est acceptable avec cooldown/énergie; 4x4/5x5 permanent est trop dangereux.

### Enchants prioritaires

| Nom | Compatible | Rareté | Effet précis | Cooldown | Risque cheat | Equilibrage | Priorité |
|---|---|---|---|---|---|---|---|
| Saignement | Epée, hache | Rare | 8% d'appliquer 2 coeurs sur 4s en PvE, 1 coeur en PvP. | 12s par cible | Moyen | Ne stack pas. | now |
| Fureur | Epée | Epique | +1% dégâts par coeur manquant, cap 12%. | passif | Moyen | Désactivé sous Strength II en PvP. | later |
| Vol de vie faible | Epée | Légendaire | 4% de rendre 0,5 coeur sur hit joueur/mob. | 8s | Haut | PvP cap strict. | later |
| Foreuse | Pioche | Epique | Mine 3x3 si sneak + clic, consomme durabilité/énergie. | 6s | Haut | Pas en claims/events/PvP. | now |
| Aimant | Outils | Peu rare | Attire les drops à 4 blocs après break. | 1s | Bas | Pas les drops joueurs. | now |
| Auto-smelt | Pioche | Rare | Cuit minerais et donne XP réduite. | passif | Moyen | Respect Fortune avec coeff nerf. | now |
| Chance du fermier | Houe | Rare | 7% double récolte crops vanilla/custom. | passif | Moyen | Cap sur crops rares. | now |
| Moisson | Houe | Epique | Récolte 3x3 mature autour du bloc. | 5s | Moyen | Replant séparé. | now |
| Main verte | Houe | Rare | Replante si graine disponible. | passif | Bas | Ne duplique pas les graines. | now |
| Conservation | Tous outils | Rare | 6-18% de ne pas consommer durabilité. | passif | Bas | Incompatible avec certains outils énergie. | now |
| Pare-feu | Armure | Peu rare | -20% durée feu par niveau. | passif | Bas | Cap 60%. | now |
| Onde de choc | Plastron | Epique | Repousse mobs proches quand bas HP. | 25s | Moyen | PvP knockback réduit. | later |
| Dernier souffle | Armure | Légendaire | Bouclier absorption 2 coeurs sous 2 coeurs. | 90s | Haut | Visible, ne proc pas en duel ranked. | later |
| Bûcheron | Hache | Rare | Coupe jusqu'à 16 logs connectés. | 4s | Moyen | Consomme durabilité par log. | now |
| Mineur veine | Pioche | Rare | Mine minerais connectés cap 12. | 8s | Moyen | Pas ancient debris. | now |

### Enchants ajoutés

| Nom | Catégorie | Compatible | Rareté | Effet précis | Cooldown | Risque | Priorité |
|---|---|---|---|---|---|---|---|
| Brise-armure | PvP | Hache | Epique | +8% dégâts durabilité armure, pas dégâts HP. | 10s | Haut | later |
| Garde ferme | Défense | Bouclier | Rare | Réduit knockback de 25% en bloquant. | passif | Moyen | now |
| Frappe spectrale | PvE | Epée | Rare | +15% dégâts contre undead la nuit. | passif | Bas | now |
| Chasseur de boss | Boss | Arbalète | Légendaire | +6% dégâts contre boss uniquement. | passif | Moyen | later |
| Marque du chasseur | Teamplay | Arc | Epique | Marque une cible 6s, alliés voient particules. | 20s | Moyen | later |
| Ancrage | PvP | Bottes | Rare | Résiste au premier pull/knockback fort. | 35s | Moyen | later |
| Fouille | Mining | Pelle | Peu rare | Chance de trouver fragments/coins en gravel/sand. | passif | Moyen | now |
| Géologue | Mining | Pioche | Epique | 2% de fragment saphir sur minerais rares. | passif | Haut | later |
| Pas léger | Exploration | Bottes | Rare | Réduit dégâts chute 15%, stack avec Feather Falling cap. | passif | Bas | now |
| Course courte | Mobilité | Bottes | Rare | Speed I 4s après sprint 20 blocs. | 20s | Moyen | maybe |
| Récolte dorée | Farming | Houe | Epique | +petite chance de crop qualité rare. | passif | Moyen | later |
| Apiculteur | Farming | Armure | Peu rare | Abeilles n'attaquent pas, +miel rare. | passif | Bas | maybe |
| Marin | Exploration | Casque | Rare | Respiration + visibilité eau légère. | passif | Bas | now |
| Thermique | Nether | Armure | Epique | Réduit dégâts lave initial de 25%. | 30s | Moyen | later |
| Eclat prismarin | Trident | Rare | +10% dégâts sous pluie/eau. | passif | Bas | maybe |
| Ricochet | Arbalète | Légendaire | Flèche rebondit une fois sur mobs, pas joueurs. | 12s | Haut | later |
| Focus | Boss | Arc | Epique | Touches successives sur même boss +1% cap 8. | reset miss | Moyen | later |
| Secours | Teamplay | Plastron | Rare | Soigne 0,5 coeur un allié proche sous 30%. | 30s | Moyen | maybe |
| Coffre-fort | Utilitaire | Pioche | Rare | Chance que minerais cassés aillent dans inventaire. | passif | Bas | now |
| Expérience stable | Economie | Outils | Rare | +5% XP vanilla, cap avec spawners. | passif | Moyen | now |
| Rappel | Utilitaire | Bottes | Epique | Annule une chute fatale en téléportant 2 blocs haut. | 180s | Haut | maybe |
| Purge | PvE | Epée | Rare | +20% dégâts contre mobs invoqués par boss. | passif | Bas | later |
| Jardinier | Farming | Houe | Rare | Bonemeal en zone 2x2 avec coût. | 10s | Moyen | later |
| Endurance | Armure | Rare | Réduit faim consommée de 10%. | passif | Bas | now |
| Résonance | Mining | Pioche | Légendaire | Signale un minerai rare proche avec son. | 45s | Haut | later |

## 8. Potions custom

Potions = buffs courts, compréhensibles, craftables, utiles en clips. Ne pas vendre de supériorité PvP permanente.

| Potion | Effet | Durée | Obtention/craft | Coût | Danger | Equilibrage |
|---|---|---|---|---|---|---|
| Potion de Forage | Haste I + +10% durabilité outils. | 6 min | Glowstone + minerai compact. | Moyen | Mining trop rapide. | Incompatible beacon Haste II bonus. |
| Potion du Mineur stable | Vision nocturne + résistance explosion légère. | 8 min | Carotte dorée + tuff. | Bas | Rend mining trop safe. | Pas de Fire Resistance. |
| Potion anti-chute | Slow Falling court + absorption 1. | 90s | Plume + membrane. | Bas | Elytra abuse. | Cooldown consommation 60s. |
| Potion de Plongée | Water Breathing + Dolphin faible. | 5 min | Prismarine + kelp rare. | Bas | Exploration trop facile. | Pas de Speed fort. |
| Potion de Sang-froid | Réduit durée fire/poison/withering. | 3 min | Nether wart + menthe. | Moyen | Boss trivial. | Ne purge pas effets, réduit seulement. |
| Potion de Duel | Regen naturelle désactivée, saturation stable. | 2 min | Fournie arène. | Aucun | Exploit hors duel. | Item lié arène. |
| Potion de Récolte | +10% crops qualité rare. | 10 min | Lavande + miel. | Moyen | Farm abusif. | Cap quotidien par joueur. |
| Potion du Chasseur | Mobs rares visibles à 24 blocs. | 4 min | Oeil araignée + saphir fragment. | Haut | Radar. | Mobs seulement, jamais joueurs/coffres. |
| Potion de Focus boss | +5% dégâts boss, -10% dégâts joueurs. | 5 min | Boss token. | Haut | PvP abuse. | Flag boss arena uniquement. |
| Potion de Portée stable | +0,25 reach blocs uniquement. | 2 min | Event rare. | Haut | PvP impossible. | Interdit combat, blocs seulement. |
| Potion de Silence | Réduit aggro mobs non-boss. | 90s | Menthe + phantom membrane. | Moyen | Skip donjons. | Boss/miniboss immunisés. |
| Potion de Marchandage | -2% frais AH/shop rent. | 15 min | Event économie. | Bas | Rich get richer. | Cap et cooldown. |
| Potion Lune Rouge | +loot mobs event, mobs +dangereux. | event | Drop event. | Variable | Snowball. | Uniquement event. |
| Potion de Rappel | Après mort PVE, garde 10% XP bonus. | 10 min | Saphirs + XP bottle. | Haut | Annule risque. | Pas PvP, pas boss final. |

## 9. Items custom pratiques

Philosophie : un item custom doit avoir un rôle, une source, un coût, une limite, une upgrade et un risque maîtrisé.

| Item | Rôle | Gameplay exact | Obtention | Coût économie | Limite/énergie | Upgrade | Risque | Priorité |
|---|---|---|---|---|---|---|---|---|
| Mini-tronçonneuse | Bois early | Coupe 8 logs max. | Craft fer + essence. | Bas | Essence, dura. | Cap logs 12. | Déforestation. | now |
| Tronçonneuse | Bois mid | Coupe arbre entier cap 48 logs. | Shop/saphirs. | Moyen | Essence + cooldown. | Efficacité, réservoir. | Lag grands arbres. | now |
| Tronçonneuse lourde | Bois late | Zone ciblée, logs + feuilles. | Boss/black market. | Haut | Essence rare. | Cap 96. | Trop forte. | later |
| Foreuse 3x3 | Mining | 3x3 devant joueur. | Craft rare. | Haut | Batterie, cooldown. | Réservoir, vitesse. | Xray/lag. | now |
| Foreuse verticale | Puits | Mine colonne verticale contrôlée. | Upgrade foreuse. | Haut | Batterie forte. | Profondeur. | Chute/lava. | later |
| Foreuse horizontale | Tunnel | Mine 3x2 horizontal cap distance. | Upgrade. | Haut | Batterie. | Longueur. | Strip mine abusif. | later |
| Scanner géologique | Info | Son/GUI indique densité minerais dans chunk. | Saphirs + redstone. | Haut | Charges. | Précision. | Xray-like. | maybe |
| Pince à spawner | Spawners | Déplace spawner avec chance casse tier. | Marché noir/boss. | Très haut | Usage unique. | Pince renforcée. | Dupes. | later |
| Boomerang | Utility/PvE | Projectile revient, ramasse item ou hit mob. | Craft cuivre/bois. | Moyen | Cooldown. | Portée. | PvP poke. | later |
| Grappin | Mobilité | Tire joueur vers bloc, pas vers joueurs. | Craft rare. | Moyen | Charges. | Distance. | PvP fuite. | later |
| Piège à ours | Défense soft | Ralentit mob/joueur 2s, visible. | Craft fer. | Bas | Se casse. | Durée. | Toxicité. | maybe |
| Casque mineur | Exploration | Lampe client/particules légères. | Craft. | Moyen | Batterie lente. | Intensité. | Lag lumière si mal fait. | later |
| Armure plongeur | Exploration | Bonus respiration/nage, faibles stats combat. | Craft prismarine. | Moyen | Durabilité. | Profondeur. | PvP eau. | later |
| Bottes anti-fall | Sécurité | Réduit dégâts chute, charge consommée si fatal. | Quest. | Moyen | Charges. | Recharge. | Annule danger. | now |
| Leggings de vitesse | Mobilité | Speed léger hors combat. | Shop rare. | Haut | Désactivé combat. | Durée sprint. | PvP. | later |
| Bottes de saut | Mobilité | Jump Boost court activable. | Event. | Moyen | Cooldown. | Hauteur. | Parkour skip. | maybe |
| Boussole oeuf dragon | Objectif saison | Pointe vers zone approximative oeuf. | Craft saphirs. | Haut | Charges, rayon flou. | Précision. | Base finder. | now |
| Boussole boss | Boss | Pointe vers portail/boss actif. | Boss tokens. | Moyen | Event only. | Portée. | Leech. | now |
| Planeur | Exploration | Elytra low tier sans rockets ou durée courte. | Quest exploration. | Haut | Durabilité. | Stabilité. | Trop tôt. | later |
| Radar mobs | Chasse | Montre mobs rares/event proches. | Craft. | Moyen | Charges. | Rayon. | Lag scan. | later |
| Voidstone | Stockage | Sac à dos/void sélectif de matériaux. | Déjà cohérent repo. | Moyen | Slots limités. | Filtres. | Dupes stockage. | now |
| Marteau de build | Build | Place lignes/murs avec blocs inventaire. | Shop. | Moyen | Cooldown. | Modes. | Grief. | later |
| Clé de base | Défense | Ouvre porte team/zone sans redstone complexe. | Team upgrade. | Moyen | Permissions. | Logs. | Vol. | later |
| Totem d'alarme | Anti-raid | Alerte Discord/team si coffre/spawner touché. | Team shop. | Haut | Rayon, cooldown. | Sensibilité. | Spam. | later |
| Sceau anti-mob | Base | Empêche spawn hostile petit rayon. | Craft cher. | Haut | Rayon cap. | Durée. | Farms. | later |

## 10. Système d'énergie / essence / batterie

Le système doit limiter les outils sans devenir chiant. Recommandation : une seule logique, trois noms selon fantasy.

| Ressource | Pour | Obtention | Design |
|---|---|---|---|
| Essence | Tronçonneuses, moteurs bois. | Craft charbon + slime + huile de graines, shop. | Consommable fréquent, prix bas/moyen. |
| Batterie | Foreuses, casque, scanner. | Craft redstone + cuivre + améthyste/saphir. | Rechargeable en station. |
| Charge | Grappin, radar, boussoles. | Recharge station ou item charge. | Empêche spam mobilité/info. |
| Carburant rare | Outils late/boss. | Boss, marché noir, event monde. | Bloque power creep. |

Stations de recharge : bloc au spawn ou craft cher en base, consomme argent + matériaux. Upgrades : réservoir, efficacité, refroidissement, durabilité.  
Version MVP : stocker une `charge` dans PersistentDataContainer item, GUI simple `/charge`, station à placer plus tard.  
Risques : dupes au clic, items stackés avec NBT, perte en mort, charge non synchronisée. Priorité : `now` pour foreuse/tronçonneuse, `later` pour réseau de stations.

## 11. Déplacements custom

| Idée | Fun | Praticité | Coût | Risque PvP | Risque lag | Difficulté | Priorité |
|---|---:|---:|---|---|---|---:|---|
| Grappin bloc-only | 9 | 8 | Moyen | Haut | Bas | 6 | later |
| Planeur low tier | 8 | 7 | Haut | Moyen | Bas | 5 | later |
| Ballon dirigeable event | 8 | 5 | Haut | Bas | Moyen | 8 | maybe |
| Bateaux à coffre | 6 | 8 | Bas | Bas | Bas | 3 | now |
| Bateaux multi-joueurs | 7 | 6 | Bas | Bas | Moyen | 5 | later |
| Minecart foreur | 8 | 5 | Haut | Bas | Haut | 8 | maybe |
| Link minecarts | 6 | 6 | Moyen | Bas | Haut | 7 | maybe |
| Loup monture rare | 9 | 6 | Haut | Moyen | Moyen | 7 | later |
| Téléportation limitée | 6 | 9 | Haut | Haut | Bas | 4 | now |
| Routes rapides | 5 | 8 | Team cost | Bas | Bas | 3 | later |
| Portails d'event | 8 | 8 | Admin | Bas | Bas | 4 | now |
| Ascenseurs | 5 | 9 | Moyen | Bas | Bas | 3 | now |
| Tyroliennes | 8 | 6 | Moyen | Moyen | Moyen | 6 | later |
| Pads de saut | 7 | 6 | Bas | Moyen | Bas | 3 | now |

## 12. Agriculture custom

Objectif : donner une alternative économique aux spawners et une identité visuelle aux bases. Les crops doivent nourrir potions, nourriture, cosmétiques, argent et events.

| Culture | Obtention | Croissance | Utilité | Prix | Craft lié | Risque farm | Intérêt |
|---|---|---|---|---|---|---|---|
| Tomate | Shop seeds | Rapide | Pizza, sauce, vente early. | Bas | Pizza, burger. | Bas | Facile. |
| Maïs | Village/quest | Moyen | Tacos, pop-corn event. | Moyen | Tacos. | Moyen | Bon volume. |
| Riz | Marais/eau | Lent eau | Ramen, sushi. | Moyen | Ramen. | Moyen | Varie farms. |
| Orange | Arbre rare | Lent | Jus buff saturation. | Moyen | Boisson. | Bas | Décoratif. |
| Citron | Arbre rare | Lent | Potion sang-froid. | Moyen | Potion. | Bas | Alchimie. |
| Piment | Nether/market | Moyen | Food dégâts/haste court. | Haut | Tacos épicé. | Moyen | Fun. |
| Lavande | Prairies | Lent | Cosmétiques, potions calme. | Haut | Teintures, pets. | Bas | Prestige. |
| Menthe | Rivières | Moyen | Potions, nourriture fraîche. | Moyen | Sang-froid. | Bas | Utility. |
| Plante carnivore | Jungle rare | Lent | Défense décorative, loot fibres. | Haut | Pièges. | Haut | Vidéo. |
| Baies lumineuses+ | Caves | Moyen | Lumière, potions vision. | Moyen | Casque lampe. | Moyen | Exploration. |
| Coton | Plaine | Moyen | Cosmétiques, bandages PvE. | Bas | Capes? éviter vraie cape. | Bas | Craft. |
| Champignon azur | Deep caves | Lent | Potion boss focus. | Haut | Boss potions. | Haut | Rare. |
| Cactus bleu | Désert | Lent | Teinture, anti-chaleur. | Moyen | Armure désert. | Bas | Biome. |
| Ginseng | Montagne | Très lent | Potion regen faible. | Haut | Médecine. | Haut | Rare. |
| Fleur de saphir | Event | Très lent | Fragment saphir très rare. | Très haut | Saphirs. | Très haut | A caper. |
| Algue dorée | Océan | Lent | Plongée, argent. | Haut | Armure plongeur. | Moyen | Exploration. |
| Courge lunaire | Event nuit | Saison | Food Lune Rouge. | Haut | Event buffs. | Moyen | Saison. |
| Blé ancien | Ruines | Moyen | Pain premium. | Moyen | Nourriture buff. | Bas | Lore. |

Systèmes liés : arrosage simple avec arrosoir rechargeable; bonemeal améliorée craftée; serres comme zones décoratives qui accélèrent légèrement; irrigation 1 bloc d'eau amélioré par canal; compost simple pour convertir pourriture/crops bas en engrais.

## 13. Pourriture / nourriture / survie légère

Critique : la pourriture peut vite être une idée nulle si elle punit le joueur casual. Elle doit créer une économie de conservation, pas forcer tout le monde à gérer un frigo.

| Idée | Intérêt | Friction | Risque chiant | Version simple recommandée | Version avancée |
|---|---|---|---|---|---|
| Aliments périssables | Economie restaurants. | Moyen | Haut | Seulement foods custom premium périssent. | Tous foods custom avec timer. |
| Crue dangereuse | Logique survie. | Bas | Bas | Crue custom donne nausée légère. | Maladies multiples. |
| Maladie légère | Donne valeur aux remèdes. | Moyen | Haut | 1 debuff court, jamais mortel. | Système immunité. |
| Compost | Recycle surplus. | Bas | Bas | GUI ou composter vanilla boosté. | Qualité compost. |
| Sel | Conservation. | Bas | Bas | Craft sel + food = version conservée. | Mines de sel. |
| Fumoir | Métier cuisine. | Bas | Bas | Fumoir transforme viande en non périssable. | Recettes longues. |
| Glacière | Stockage. | Moyen | Moyen | Petit conteneur qui stoppe timer. | Energie/glace. |
| Restaurants joueurs | Social/éco. | Bas | Bas | Foods avec buffs légers vendables. | Licences de restaurant. |
| Buffs nourriture | Gameplay. | Bas | Moyen | Buff 3-10 min, faibles. | Combos repas. |

Verdict : `later`. MVP : nourritures custom avec buffs, compost, sel/fumoir. Pourriture globale : `avoid` au lancement.

## 14. Spawners

Objectif : rentables, désirables, mais jamais game-breaking. Un spawner est un investissement, pas une imprimante infinie.

| Feature | Fonctionnement | Design économique | Risque | Priorité |
|---|---|---|---|---|
| Spawners achetables | Shop tiers T1/T2/T3. | ROI 5-10 jours actifs. | Inflation. | now |
| Spawners saphirs | Types rares ou upgrade late. | Saphirs + argent. | Paywall progression. | later |
| Stack spawners | Stack virtuel par bloc/chunk. | Réduit entités. | GUI bugs. | now |
| Upgrade spawner | Vitesse, stockage, XP, loot. | Coût exponentiel. | Trop rentable. | now |
| Spawner loot GUI | Stock virtuel à collecter. | Anti-lag fort. | Perd feeling vanilla. | later |
| Spawner XP GUI | XP stockée plafonnée. | Sink via retrait/taxe. | XP inflation. | later |
| Limite chunk | Max spawners virtuels par chunk. | Performance. | Frustration. | now |
| Limite base/team | Cap selon team level. | Pousse team upgrades. | Alt teams. | later |
| Taxes/maintenance | Coût hebdo ou carburant. | Sink late. | Trop punitif. | later |
| Pince à spawner | Déplacement rare. | Sink saphir. | Dupes. | later |
| Risque casse par tier | T1 0%, T2 10%, T3 25% sans pince premium. | Décision. | Rage. | maybe |
| Spawners custom | Mobs custom avec drops. | Tokens/boss. | Balance. | later |
| Spawners event | Temporaires, 7 jours. | Event hype. | Stockpile. | later |

MVP recommandé : spawner achetable + stack virtuel + limites par chunk + GUI info + logs + coûts upgrade simples. Maintenance/taxes seulement après observation inflation.

## 15. Events admin

Commande cible : `/event launch <id>`, `/event schedule`, `/event stop`, `/event reward`, `/event leaderboard`.

| Event | Commande | Durée | Joueurs | Récompenses | Risque | Dev | Fun | Vidéo |
|---|---|---:|---|---|---|---:|---:|---:|
| Spleef | /event launch spleef | 5-8m | 4-40 | argent, tag | Map grief | 4 | 8 | 8 |
| TNT Run | /event launch tntrun | 5m | 6-40 | clés event | Lag blocs | 5 | 8 | 9 |
| Sumo | /event launch sumo | 3-8m | 2-32 | argent | Knockback cheat | 3 | 7 | 7 |
| Color Shuffle | /event launch color | 6m | 6-60 | tokens | Lag floor | 5 | 9 | 9 |
| Dé à coudre | /event launch deacoudre | 8m | 4-30 | titre | Map | 4 | 8 | 8 |
| Défense du Cube | /event launch cubedefense | 10m | 5-40 | loot mobs | Mob lag | 7 | 9 | 9 |
| Cache-cache | /event launch hide | 10m | 6-50 | cosmétiques | Staffing | 4 | 8 | 8 |
| Floor is Lava | /event launch lava | 6m | 6-40 | tokens | Death bugs | 5 | 8 | 9 |
| Parkour | /event launch parkour | 10m | 1-60 | argent top | Checkpoints | 4 | 7 | 6 |
| Anvil Rain | /event launch anvil | 4m | 6-50 | fun tags | Damage unfair | 4 | 8 | 9 |
| Build Battle | /event launch build | 15m | 4-30 | déco/titre | Vote biais | 5 | 7 | 8 |
| Chasse au trésor | /event launch treasure | 10m | 5-80 | saphir rare | Base finder | 5 | 8 | 9 |
| Pêche flash | /event launch fishing | 5m | 2-60 | loot table | AFK | 3 | 6 | 5 |
| Quiz chat | /event launch quiz | 5m | 2-100 | argent | Spam | 2 | 6 | 4 |
| Labyrinthe | /event launch maze | 10m | 4-40 | tokens | Triche map | 5 | 7 | 7 |
| Boss public | /event launch boss | 10-20m | 8-80 | boss tokens | Lag/leech | 8 | 10 | 10 |
| Capture Zone | /event launch koth | 10m | 6-60 | team points | Toxicité PvP | 6 | 8 | 9 |
| Livraison | /event launch delivery | 8m | 4-50 | argent | Alt abuse | 5 | 7 | 7 |
| Bataille de ponts | /event launch bridges | 10m | 4-32 | duel elo | PvP sweat | 6 | 8 | 9 |
| Marché fou | /event launch market | 10m | tous | prix réduits | Inflation | 3 | 6 | 6 |

## 16. Events aléatoires monde

| Event | Déclenchement | Annonce | Durée | Rewards | Risques | Anti-abus | Utilité éco | Priorité |
|---|---|---|---:|---|---|---|---|---|
| Lune de sang | 1-2 fois/semaine nuit | Chat + bossbar | 20m | mob drops, tokens | morts spawn | zones safe | mob loot sink | now |
| Boss public | Horaire + random | Coord approx | 15m | contribution | leech/lag | min contribution | saphirs/tokens | now |
| Invasion End | Quand End ouvert | global | 20m | fragments End | difficulté | cap rewards | endgame | later |
| Quiz chat | Random low pop | chat | 5m | argent | spam | cooldown | retention | now |
| Pêche 5 min | Random | chat | 5m | poissons rares | bots | anti-afk | food/cosmetic | now |
| Livraison colis | Random | coord start | 8m | argent | PvP grief | no TP item | social | now |
| Météorite | 1/semaine | bruit + coord vague | 15m | minerais, fragments | xray rush | zone event | sink tools | now |
| Marchand itinérant | Random spawn | rumeur | 15m | offres rares | camp | stock perso | money sink | later |
| Faille dimensionnelle | Random | portail visible | 10m | mobs rares | lag | player cap | boss tokens | later |
| Caravane à protéger | Route monde | annonce | 12m | argent/team | grief | instances | coop | later |
| Village attaqué | Village choisi | annonce | 15m | réputation | grief village | zone protégée | mats | later |
| Coffre maudit | Loot chest | chat local/global | 8m | rare loot | bait PvP | cooldown | black market | later |
| Tempête magique | Biome | météo | 10m | crops rares | confusion | clear effects | alchemy | maybe |
| Invasion pillards | Outpost/zone | global | 15m | banners, tokens | raid lag | mob cap | boss prep | now |
| Portail temporaire | Random | coord vague | 20m | mini-dungeon | chunk gen | prebuilt | exploration | later |
| Biome contaminé | Chunk cluster | map/coord | 30m | corrupted drops | grief | no spread real | season | later |
| Chasse aux reliques | 3 coords clues | chat | 20m | relics | base reveal | far from bases | content | later |
| Pluie de ressources | Zone | global | 5m | mats | inflation | low tier only | fun | maybe |
| Black market temporaire | Spawn/secret | rumeur | 10m | achats | unfair | stock/limits | sink | later |
| Trésor sous-marin | Océan | coord vague | 15m | prismarine, loot | drowning | no claims | exploration | later |
| Navire fantôme | Océan nuit | global | 15m | cosmetics | entity lag | one ship | video | later |
| Mini-donjon temporaire | Event world | portal | 20m | tokens | bugs | instance | retention | later |

## 17. Boss custom

Principe : un boss réussi a une arène, des télégraphes, 3-4 mécaniques, une récompense par contribution, des drops cosmétiques et un prérequis économique.

| Boss | Tier | Lore | Lieu | Invocation | Phases/attaques | Récompenses | Joueurs | Risques | Priorité |
|---|---|---|---|---|---|---|---|---|---|
| Capitaine Noyé | Early | Pirate noyé gardien d'un coffre. | Océan/arène | Carte au trésor + prismarine. | Vagues drowned, trident line, zones eau. | Tokens mer, casque plongeur fragment. | 2-5 | Eau/PvP | now |
| Gardien de la Mine | Early | Golem fissuré sous spawn ressource. | Mine event | 3 cristaux de cuivre. | Chute stalactites, slam, adds. | Foreuse parts, minerais. | 3-8 | Terrain grief | now |
| Roi des Pillards | Mid | Seigneur de raid avec bannières. | Fort pillard | Bannières rares + argent. | Armée, totems à détruire, charge ravager. | Arbalète, titre, saphirs. | 6-20 | Mob lag | now |
| Duc de Basalte | Mid | Noble du Nether volcanique. | Nether arena | Coeur magma + tokens. | Lave, explosions, plateformes froides. | Armure thermique, essence rare. | 6-20 | Fire chaos | later |
| La Reine Ruche | Mid | Boss agriculture/abeilles. | Plaine fleurie | Miel royal + lavande. | Essaims, zones pollen, dards. | Pet abeille, crops, potions. | 4-12 | Adds | later |
| L'Anomalie | Endgame | Boss glitché, instabilité saison. | Faille/event world | Fragments saison. | Cutscenes, clones, inversions, lasers. | Arme unique, cosmetic glitch. | 10-40 | Dev élevé | later |
| Dragon reforgé | Endgame | Rework End final. | End custom | Rituel oeuf dragon. | Cristaux actifs, zones void, adds End. | Fin de saison, trophée. | 15-60 | Très risqué | later |
| Le Banquier Maudit | Economie | Boss lié au marché noir. | Banque ruinée | Dette/contrat rare. | Vole argent temporaire, coffres piégés. | Coupons taxe, tags. | 5-15 | Rage si vol réel | maybe |
| Colosse de Givre | Saison neige | Ancien gardien gelé. | Biome neige | Cristaux glace. | Gel sol, stalactites, bouclier feu. | Skins hiver, arc givre. | 8-25 | Slow frustrant | later |
| Mère des Cavernes | Secret | Mob organique profond. | Deep cave | Relique + silence. | Obscurité, tentacules, spores. | Scanner parts, titre. | 4-10 | Horror/fairness | maybe |
| Héraut de la Lune Rouge | Event | Commandant des nuits sanglantes. | Overworld nuit | Event auto. | Mobs enrages, pluie sang, curse. | Tokens event, cosmétique. | public | Lag | now |
| Sculk Oracle | Endgame | Oracle qui prédit les mouvements. | Ancient city | Echo shards + saphirs. | Sons, aveuglement court, warden-like adds. | Enchant Résonance. | 6-15 | Trop dur | later |

Anti-leech : contribution mixte dégâts, support, objectifs, survie; seuil minimum; récompense personnelle; top rewards cosmétiques plutôt que puissance brute.

## 18. Mobs custom

| Mob | Comportement | Biome | Loot | Difficulté | Utilité | Lag | Priorité |
|---|---|---|---|---|---|---|---|
| Loup alpha | Pack, appelle 2 loups. | Taïga | Croc, pet fragment. | Moyen | Monture/pet | Bas | later |
| Loup monture | Apprivoisement long, rapide hors combat. | Neige/taïga | Aucun si tué. | Rare | Mobilité | Moyen | later |
| Mineur spectral | Apparaît en grotte, fuit avec minerai. | Caves | Poussière spectrale. | Moyen | Mining event | Bas | now |
| Rampant cristallin | Creeper qui drop cristaux, explosion réduite. | Amethyst caves | Améthyste. | Moyen | Saphirs/craft | Bas | now |
| Garde du coffre | Spawn quand coffre maudit ouvert. | Event | Loot coffre. | Moyen | Protection event | Bas | now |
| Sanglier sauvage | Charge, source viande custom. | Forêts | Viande, cuir. | Bas | Food | Bas | maybe |
| Cultiste pillard | Buff mobs proches. | Outposts | Totem shard. | Moyen | Boss prep | Bas | now |
| Magma Ducal | Split en petits slimes. | Nether | Essence chaude. | Moyen | Fuel | Moyen | later |
| Endermite instable | Téléporte court, drop fragment End. | End | Fragment. | Haut | End rework | Bas | later |
| Plante carnivore | Immobile, attrape mobs/joueurs. | Jungle/custom crop | Fibre. | Moyen | Farm/décor | Bas | later |
| Zombie gelé | Slow hit. | Saison neige | Glace bleue rare. | Bas | Saison | Bas | later |
| Corrompu | Infecte zone visuelle temporaire. | Saison corruption | Essence. | Moyen | Lore | Moyen | later |
| Pêcheur noyé rare | Lance filet. | Océan | Filet, poisson rare. | Moyen | Fishing | Bas | now |
| Gobelin marchand | Fuit, pas agressif. | Event | Coupon si capturé | Bas | Black market | Bas | maybe |

## 19. Teams / factions light

Le bon système : donner une identité et des objectifs de groupe sans créer une guerre permanente qui dégoûte les casuals.

| Feature | Fonctionnement | Utilité | Risque drama/exploit | Simplicité | Priorité |
|---|---|---|---|---|---|
| Création payante | Argent + nom/tag validé. | Sink + engagement. | Noms toxiques. | Haute | now |
| 2 membres base | Slot 3+ achetables. | Evite grosses teams jour 1. | Alts. | Haute | now |
| Grades internes | Owner, officer, member. | Gestion banque/home. | Vol. | Moyenne | now |
| Banque team | Dépôt/retrait loggé. | Objectifs communs. | Theft drama. | Moyenne | now |
| Home team | Via bannière posée en base. | Identité + QoL. | Base reveal si vol. | Moyenne | now |
| Bannière team | Bloc central, upgrade autour. | Visuel. | Grief. | Moyenne | now |
| Upgrades | Slots, home, coffre team, tag color, quest cap. | Progression. | Dominance. | Moyenne | now |
| Alliances | Chat allié, no friendly fire option. | Diplomatie. | Collusion. | Moyenne | later |
| Ennemis | Déclaration soft, bonus event. | Rivalités. | Toxicité. | Moyenne | later |
| Taxe team | Bonus/commission sur contrats, pas taxe forcée ennemie au départ. | Economie. | Abuse. | Faible | maybe |
| Leaderboards | Argent, puissance, events, boss. | Prestige. | Boost. | Haute | now |
| Quêtes team | Objectifs hebdo collectifs. | Retention groupe. | Farm alts. | Moyenne | now |
| Guerre soft | KOTH/outposts temporaires, pas raid base. | PvP contenu. | Toxicité. | Moyenne | later |

## 20. Quêtes journalières / hebdo / faction

| Type | Exemples | Récompenses | Anti-abus | Difficulté | Priorité |
|---|---|---|---|---|---|
| Quotidiennes solo | Miner 200 blocs, tuer 50 zombies, pêcher 15 poissons, vendre 5k. | Argent, XP, fragment saphir chance. | Objectifs random, caps. | 4 | now |
| Hebdo | Gagner 5 events, livrer 20 commandes, battre un boss. | Saphirs, titre temporaire. | Reset, pas reroll gratuit. | 5 | now |
| Team | Tuer 500 mobs, vendre 250k, gagner KOTH, craft 100 repas. | Team XP, argent banque. | Contributions perso min. | 6 | now |
| Saison | Collecter reliques, battre boss chapitre. | Fragments saison, cosmetics. | Non échangeable. | 7 | later |
| Economie | Remplir 10 buy orders, payer taxe shop, vendre crop qualité. | Coupons, argent. | Pas self orders. | 5 | now |
| Découverte | Visiter warp tuto, trouver biome, finir mini-donjon. | Argent + guide. | One-time. | 3 | now |
| PvP | Faire un duel, gagner 3 duels kit, bounty valide. | Elo, argent plafonné. | Anti-alt relation graph. | 6 | now |
| PvE | Boss, mobs rares, défense village. | Tokens. | Contribution. | 5 | now |
| Farming | Récolter tomates, faire compost, livrer nourriture. | Seeds rares. | Matures seulement. | 4 | later |
| Communautaire globale | Serveur mine 1M blocs, tue boss 20 fois. | Buff weekend/cosmetic. | Global cap. | 7 | later |
| Contrats chasse | Tuer mob rare, livrer item, bounty. | Argent/saphirs. | Cooldown cible. | 5 | now |

## 21. Duel / PvP propre

Système recommandé :
- Duel 1v1 kit imposé : priorité `now`, facile à équilibrer.
- Duel stuff perso : `later`, avec pari plafonné, snapshot inventaire, arène no drop.
- Classement Elo séparé par kit.
- Historique public : win/loss, streak, adversaire, date.
- Spectateurs en mode invisible, pas d'items, chat séparé.
- Anti-combat log : quitter = forfait.
- Quêtes liées : faire un duel, gagner un duel, assister à un tournoi.
- Tournois hebdo : bracket simple, récompenses cosmétiques + argent plafonné.

Risques : toxicité, accusation cheat, transfert argent via paris, kits mal équilibrés.  
Verdict : très fort pour contenu et retention si l'argent reste plafonné et si le staff peut review logs/replays simplifiés.

## 22. Spawn / tutoriel / onboarding

Objectif : un joueur comprend quoi faire sans staff.

Design spawn :
- Centre compact : portail Survival, NPC Guide, panneau argent, leaderboard, event board.
- Grandes portes par système : Economie, Teams, Spawners, Events, Boss, Duels, Saphirs, Marché.
- Tuto 3 minutes : 6 étapes seulement.
- Récompense fin tuto : 1 kit départ léger, 500-1000 argent, 1 seed custom, 1 quête journalière débloquée.

Onboarding recommandé :
1. Arrivée courte avec title + son + direction claire.
2. NPC "Guide" ouvre `/menu`.
3. Mini quêtes : vendre un item, créer un home, ouvrir AH, voir /quests, visiter marché.
4. Warp tuto toujours disponible.
5. Guide économie en 5 lignes max.
6. Guide sécurité : "ne montre pas ta base, active team, signale grief".

## 23. Cutscenes / animations / narration

| Animation | Intérêt | Difficulté | Plugin possible | Risque lourdeur | Priorité |
|---|---|---:|---|---|---|
| Intro serveur 10s | Première impression. | 5 | custom/Citizens/camera packets | Rejouée trop souvent | later |
| Cutscene boss | Rend boss premium. | 7 | MythicMobs + packets | Bugs live | later |
| Mort/killcam | Clip PvP. | 8 | custom complexe | Trop dur | maybe |
| Fin de saison | Moment YouTube. | 8 | custom + event world | Prod lourde | later |
| Ouverture portail | Hype event. | 5 | particles/sounds | Lag particules | now |
| Arrivée boss public | Signal visuel. | 5 | bossbar/particles | Spam | now |
| Achat important | Feedback dopamine. | 3 | title/sound/firework | Trop casino | now |
| Level-up/team créée | Social. | 3 | title/chat | Spam | now |

## 24. Saisons / lore / world events

| Saison | Thème | Changements monde | Events | Boss | Items | Economie | Trailer | Prod |
|---|---|---|---|---|---|---|---|---|
| Normale améliorée | SMP premium | Terralith/Tectonic, spawn propre. | Events admin. | Pillard/Mine. | Foreuse, crops. | Base stable. | Moyen | Faible |
| Apocalypse | Monde instable | Ruines, nuits dangereuses. | Lune rouge, coffres maudits. | Anomalie. | Corruption gear. | Ressources rares. | Très fort | Haute |
| Enneigée | Après fin de saison | Neige, glace, routes. | Tempêtes, colosse. | Colosse givre. | Skins hiver. | Food chaleur. | Fort | Moyenne |
| Corruption | Biomes contaminés | Zones temporaires. | Faille, mobs corrompus. | Sculk Oracle. | Essence sombre. | Tokens corruption. | Fort | Haute |
| Exploration | Monde ressources | Donjons, cartes, reliques. | Trésors, navire. | Capitaine. | Scanner. | Marché reliques. | Moyen | Moyenne |
| Boss/End | Rework final | End islands custom. | Invasions End. | Dragon reforgé. | End gear. | Boss tokens. | Très fort | Haute |
| Economie | Age des guildes | Marchés, parcelles. | Market crash, caravane. | Banquier maudit. | Titres riches. | Taxes/offres. | Moyen | Moyenne |
| Factions light | Rivalités | Outposts temporaires. | KOTH, livraison. | Roi pillards. | Bannières. | Team upgrades. | Fort | Moyenne |

Recommandation : Saison V3 de lancement = "SMP premium normal amélioré avec fissures d'apocalypse". Tu gardes l'accessibilité, mais tu teasers l'Anomalie et une fin de saison.

## 25. Génération / mondes / biomes

Analyse :
- Terralith/Tectonic sont très bons pour vendre l'exploration, mais doivent être installés avant génération.
- Nether/End rework est fort, mais pas prioritaire si la base économie/anti-cheat n'est pas stable.
- Resource world/mining world évite de reset le monde principal et protège les bases.

Propositions :
- Monde principal : pregen, worldborder, bases, économie.
- Monde ressources : reset mensuel, mining, bois, sable, structures simples.
- Monde event : déjà cohérent avec `eventworld`, instances temporaires.
- Nether : ouverture contrôlée, border, boss zones, anti-xray spécifique.
- End : ouverture événementielle, dragon custom, oeuf dragon objectif saison.
- Zones rares : cavernes saphir, ruines de boss, champs de météorites.
- Donjons légers : structures prebuilt instanciées ou protégées, pas command datapacks lourds.

## 26. Blocs custom / redstone custom

| Bloc | Fonctionnement | Craft | Utilité | Risque exploit/redstone | Priorité |
|---|---|---|---|---|---|
| Online detector | Signal si joueur/team online. | Redstone+saphir. | Bases sociales. | Stalking. | maybe |
| Geyser | Propulse entités/joueurs. | Prismarine. | Mobilité/base. | PvP traps. | later |
| TNT améliorée | Explosion contrôlée event/mining. | Gunpowder+saphir. | Events. | Grief. | avoid/later |
| TNT waterproof | Explose sous eau en event only. | TNT+prismarine. | Boss/arènes. | Raid bases. | avoid |
| Clicker redstone | Clic auto lent. | Redstone+quartz. | QoL. | Automation abuse. | maybe |
| Bloc de team | Coeur visuel team. | Bannière+saphir. | Home/upgrades. | Grief. | now |
| Bloc anti-mob | Empêche spawn petit rayon. | Améthyste+lanterne. | Base QoL. | Farms. | later |
| Stockage custom | Coffre compact limité. | Shulker+saphir. | QoL. | Dupes. | maybe |
| Station recharge | Recharge outils. | Cuivre+redstone. | Energie. | Dupes NBT. | now |
| Composteur avancé | Convertit crops en engrais. | Barrel+bone. | Agriculture. | Farm loop. | later |
| Shop display | Vitrine item prix. | Glass+emerald. | Marché. | Packet/dupe. | later |
| Portail limité | TP team/event. | Obsidian+saphirs. | Mobilité. | PvP escape. | maybe |

## 27. Logger system / anti-raid / protection

Ce qui est faisable proprement :
- Logs blocs, coffres, morts, kills, spawners, économie, shops, teams, trades, commands sensibles.
- Rollback ciblé par joueur/zone/temps/action.
- Alertes staff : casse spawner, gros transfert, coffre rare vidé, base visit suspecte.
- Détection alt/farm : IP, horaires, échanges répétés, kills répétés, mêmes devices si dispo.
- Precious item tracking : saphirs, spawners, boss items, dragon egg, foreuse.

Ce qui est réaliste mais imparfait :
- Base finder prevention par logs : détecter tunnels droits vers coffres/spawners, visites rapides de bases, pattern xray.
- Anti chest ESP : pas fiable à 100%; réduire exposition, logger comportements.
- Anti radar : difficile; limiter infos envoyées sur custom mobs si possible.

Ce qui est irréaliste à promettre :
- Bloquer totalement freecam.
- Cacher tous les coffres visibles/exposés.
- Garantir zéro seed cracking si seed fuit.
- Détecter tous les clients ghost sans faux positifs.

MVP protection : logger complet + Paper anti-xray + anti-cheat + alertes rares + rollback + politique staff claire.

## 28. Anti-cheat / anti-find / sécurité

| Sujet | Solution possible | Plugin/config | Limite | Priorité |
|---|---|---|---|---|
| Mouvement | Grim ou Matrix + exemptions custom. | Grim/Matrix. | Faux positifs grappin/planeur. | now |
| KillAura/Reach | Anti-cheat packet. | Grim/Matrix. | Ghost clients. | now |
| Anti-xray minerais | Paper anti-xray mode 2/3 par monde. | Paper. | Pas infallible, coût client/réseau. | now |
| Freecam | Détection indirecte. | Anti-cheat/logs. | Impossible à bloquer totalement. | later |
| Base finder | Seed spoof, logs patterns, no locator, careful maps. | custom/config. | Jamais parfait. | now |
| Coffres | Obfuscation limitée si enterrés, logs accès. | Paper/custom. | Tile entities exposées. | later |
| Seed cracking | Ne jamais leak seed, structure salt si possible. | config/process. | Les joueurs peuvent inférer. | now |
| Dupes | Patches Paper, logs, deny risky interactions. | Paper/custom. | Nouveaux dupes. | now |
| Crash items/books | Filtrer NBT, book limits. | custom/Paper. | Maintenance versions. | now |
| Permission leak | LuckPerms/manager strict, no op. | custom/LuckPerms. | Erreur humaine. | now |
| Alt abuse | Relation graph + cooldown rewards. | logger custom. | Familles/VPN. | now |
| Farm abuse | Cap rewards, AFK detection. | custom. | Contournement. | now |

## 29. Performance serveur

Checklist performance :
- Utiliser Paper/Purpur stable, éviter `/reload`.
- Pregen mondes avec Chunky avant ouverture.
- Spark hebdo et après chaque gros event.
- View distance/simulation distance raisonnables.
- Limiter entités par chunk, spawners virtuels/stackés.
- Hoppers : cooldowns, merge, limitations, alternatives GUI.
- Redstone : Alternate Current si déjà utilisé, pas de clickers rapides.
- Mobs custom : budget par event, despawn propre, bossbar update pas chaque tick si inutile.
- Holograms/scoreboards : update groupé, pas tous les ticks.
- Datapacks : éviter command functions tick lourdes; préférer plugins Java.
- Backups/logs : async, retention, partitions DB.
- Database : SQLite ok au début, prévoir MySQL/MariaDB si croissance.
- Events : instance/world séparé, cleanup automatique.
- Resource pack : pack compressé, versionné, fallback propre.

## 30. Communication / vidéos / contenu

Plan contenu :
- Trailer serveur 45-60s : spawn, économie, events, boss teaser, teams.
- Série "Feature en 30s" : saphirs, spawners, marché noir, boss, teams, foreuse.
- Shorts TikTok : météorite, boss phase, duel clutch, Lune rouge, jackpot loterie ingame, top base.
- Patch notes stylés : 5 lignes joueur + lien complet.
- Devlogs : "On a nerf la foreuse pour sauver l'économie", ça crée confiance.
- Teasers boss : silhouettes, sons, fragments.
- Classements hebdo : team riche, meilleur duelliste, farmer, chasseur.
- Spotlight team/base : récompense cosmétique.
- Guide nouveau joueur : 60s, pas 12 minutes.

Règle : si une feature n'est pas montrable en vidéo ou ressentie en jeu, elle doit être soit très utile techniquement, soit supprimée.

## 31. Grades / monétisation non P2W

Attention : les règles officielles Minecraft autorisent typiquement les cosmétiques et monnaies virtuelles sous conditions, mais tout avantage compétitif individuel est dangereux. Les serveurs doivent aussi rester adaptés à tous les âges; prudence avec casino/gambling.

### Offre recommandée

| Grade | Prix | Avantages safe | A éviter |
|---|---:|---|---|
| Saphir | 4,99€/mois | Préfixe/couleur, tag, emotes, particules lobby, son de kill cosmétique, 1 home bonus si aussi obtenable ingame, rôle Discord, skin pet. | Kit fort, fly survival, spawner, argent massif. |
| Saphir+ | 8,99€/mois | Tout Saphir + animations mort, chapeaux, cosmetics saison, file cosmétique, réduction purement cosmétique, vote cosmetic boost. | Réduction shop gameplay forte, XP/money boost privé. |

Avantages à privilégier : titres, tags, trails, sons, pets cosmétiques, chapeaux, death effects, cosmetic crates, accès bêta cosmétique, Discord.  
Avantages à encadrer : home bonus, /ec, réduction réparations; uniquement si obtenables ingame et faibles.  
Avantages à bannir : kits combat, saphirs achetables si utilisés pour puissance, spawners, enchant books puissants, boss tokens exclusifs, fly.

## 32. Système de familiers

| Pet | Type | Effet | Limite | Economie | Priorité |
|---|---|---|---|---|---|
| Luciole | Utility faible | Petite lumière/particules. | Pas vraie lumière tick si lag. | Cosmetic/saphirs. | later |
| Mini golem | Utility | Ramasse 1 item/sec rayon 2. | Pas drops joueurs. | Pet food. | later |
| Sacoche | Utility | 3 slots stockage. | Pas shulkers/spawners. | Upgrade cher. | maybe |
| Chien alerte | Utility | Son si mob hostile proche. | Cooldown. | Quest. | later |
| Abeille | Farming | +1% crops qualité dans rayon. | Cap, pas stack. | Lavande/miel. | later |
| Slime | Cosmetic | Rebondit, trails. | Aucun. | Boutique. | now |
| Dragonnet | Prestige | Cosmetic boss/end. | Aucun combat. | Boss final. | later |
| Chauve-souris | Exploration | Ping grotte rare. | 60s cooldown. | Saphirs. | maybe |

## 33. Cosmétiques

| Cosmétique | Obtention | Monnaie | Priorité |
|---|---|---|---|
| Tags de chat | Quêtes/events/shop | Argent/saphirs | now |
| Titres sous nom | Boss/classements | Tokens | now |
| Emotes | Grade/events | Boutique/cosmetic | later |
| Chapeaux | Crates cosmétiques | Argent/saphirs/grade | later |
| Skins elytra-like | Attention cape guidelines | Event only, pas cape officielle | maybe |
| Particules | Events/grade | Cosmetic | now |
| Sons de kill | Duels/grade | Cosmetic | later |
| Animations de mort | Duels/boss | Cosmetic | later |
| Trails | Parkour/events | Cosmetic | later |
| Pets cosmétiques | Shop/events | Cosmetic | later |
| Trophées boss | Boss contribution | Tokens | now |
| Décorations base | Events/saisons | Argent/saphirs | later |
| Cosmétiques saison | Pass gratuit + shop | Fragments | later |

## 34. Bank d'idées massive

310 idées, scores indicatifs. Les valeurs sont volontairement critiques pour aider à trier.

| # | Nom | Catégorie | Description courte | Fun | Utilité | Dev | Exploit | Lag | Priorité |
|---:|---|---|---|---:|---:|---:|---:|---:|---|
| 001 | Saignement | Enchants | Dot léger non stackable. | 7 | 6 | 4 | 5 | 1 | now |
| 002 | Foreuse | Enchants | 3x3 cooldown sur pioche. | 8 | 9 | 6 | 8 | 4 | now |
| 003 | Aimant | Enchants | Attire drops de minage. | 6 | 9 | 3 | 2 | 2 | now |
| 004 | Auto-smelt | Enchants | Cuit minerais automatiquement. | 6 | 8 | 4 | 5 | 1 | now |
| 005 | Moisson | Enchants | Récolte crops 3x3. | 7 | 8 | 4 | 4 | 2 | now |
| 006 | Main verte | Enchants | Replante avec graines. | 5 | 9 | 4 | 3 | 1 | now |
| 007 | Dernier souffle | Enchants | Absorption sous 2 coeurs. | 8 | 5 | 5 | 7 | 1 | later |
| 008 | Résonance | Enchants | Ping minerai rare proche. | 8 | 6 | 6 | 8 | 2 | maybe |
| 009 | Focus boss | Enchants | Bonus contre boss seulement. | 7 | 7 | 5 | 4 | 1 | later |
| 010 | Jardinier | Enchants | Bonemeal zone avec cooldown. | 6 | 7 | 4 | 5 | 2 | later |
| 011 | Forage | Potions | Haste léger mining. | 6 | 8 | 3 | 5 | 1 | now |
| 012 | Plongée | Potions | Exploration aquatique. | 6 | 7 | 3 | 2 | 1 | now |
| 013 | Anti-chute | Potions | Slow falling court. | 7 | 8 | 3 | 3 | 1 | now |
| 014 | Focus boss | Potions | Buff boss, nerf PvP. | 8 | 7 | 4 | 4 | 1 | later |
| 015 | Récolte | Potions | Boost crops qualité. | 6 | 7 | 4 | 6 | 1 | later |
| 016 | Sang-froid | Potions | Réduit feu/poison. | 6 | 6 | 4 | 4 | 1 | later |
| 017 | Marchandage | Potions | Frais AH réduits capés. | 5 | 6 | 3 | 5 | 1 | maybe |
| 018 | Silence | Potions | Moins d'aggro mobs. | 6 | 5 | 5 | 6 | 1 | maybe |
| 019 | Lune rouge | Potions | Loot event risqué. | 8 | 6 | 4 | 6 | 2 | later |
| 020 | Mini-tronçonneuse | Outils custom | Coupe 8 logs. | 7 | 8 | 5 | 4 | 3 | now |
| 021 | Tronçonneuse | Outils custom | Coupe arbre capé. | 8 | 9 | 6 | 6 | 4 | now |
| 022 | Foreuse 3x3 | Outils custom | Mining contrôlé énergie. | 9 | 9 | 7 | 8 | 5 | now |
| 023 | Foreuse verticale | Outils custom | Creuse puits sécurisé. | 7 | 7 | 7 | 6 | 4 | later |
| 024 | Scanner géologique | Outils custom | Densité minerais chunk. | 8 | 6 | 8 | 9 | 3 | maybe |
| 025 | Pince spawner | Outils custom | Déplace spawner. | 8 | 8 | 7 | 9 | 2 | later |
| 026 | Marteau build | Outils custom | Place murs/lignes. | 7 | 8 | 6 | 5 | 3 | later |
| 027 | Arrosoir | Outils custom | Accélère crops proches. | 6 | 7 | 5 | 5 | 2 | later |
| 028 | Station recharge | Outils custom | Recharge outils à coût. | 5 | 9 | 5 | 7 | 1 | now |
| 029 | Voidstone | Outils custom | Filtre/stockage limité. | 7 | 9 | 6 | 7 | 2 | now |
| 030 | Boomerang | Armes custom | Hit mob ou ramasse item. | 8 | 5 | 6 | 5 | 2 | later |
| 031 | Lance du Pillard | Armes custom | Dash court PvE. | 8 | 5 | 7 | 7 | 2 | maybe |
| 032 | Arbalète royale | Armes custom | Marque mobs/boss. | 7 | 6 | 6 | 5 | 1 | later |
| 033 | Marteau de Basalte | Armes custom | Slam AoE PvE. | 8 | 6 | 7 | 7 | 4 | later |
| 034 | Dague de duel | Armes custom | Kit duel rapide. | 7 | 4 | 4 | 5 | 1 | now |
| 035 | Arc météo | Armes custom | Bonus pluie/eau. | 6 | 4 | 5 | 4 | 1 | maybe |
| 036 | Trident filet | Armes custom | Ralentit mob marin. | 7 | 5 | 5 | 4 | 1 | later |
| 037 | Epée anomalie | Armes custom | Effet visuel glitch faible. | 9 | 5 | 7 | 7 | 2 | later |
| 038 | Bâton de soin | Armes custom | Heal allié PvE faible. | 7 | 6 | 7 | 6 | 2 | maybe |
| 039 | Casque mineur | Armures custom | Lumière/vision mine. | 7 | 8 | 6 | 3 | 5 | later |
| 040 | Armure plongeur | Armures custom | Nage et respiration. | 7 | 7 | 5 | 3 | 1 | later |
| 041 | Bottes anti-fall | Armures custom | Charge anti mort chute. | 7 | 8 | 5 | 4 | 1 | now |
| 042 | Bottes de saut | Armures custom | Jump court activable. | 7 | 5 | 5 | 5 | 1 | maybe |
| 043 | Leggings vitesse | Armures custom | Speed hors combat. | 7 | 6 | 5 | 7 | 1 | later |
| 044 | Plastron gardien | Armures custom | Réduit knockback mobs. | 6 | 5 | 5 | 4 | 1 | later |
| 045 | Armure thermique | Armures custom | Nether/lave légère. | 7 | 7 | 5 | 5 | 1 | later |
| 046 | Set ruche | Armures custom | Abeilles/farming. | 6 | 5 | 4 | 3 | 1 | maybe |
| 047 | Set givre | Armures custom | Saison neige. | 7 | 5 | 5 | 4 | 1 | later |
| 048 | Grappin | Mobilité | Pull vers blocs. | 9 | 8 | 7 | 8 | 2 | later |
| 049 | Planeur | Mobilité | Elytra low tier. | 8 | 7 | 6 | 6 | 2 | later |
| 050 | Bateau coffre | Mobilité | Transport maritime. | 5 | 8 | 3 | 2 | 1 | now |
| 051 | Bateau duo | Mobilité | Transport social. | 6 | 6 | 4 | 2 | 2 | later |
| 052 | Minecart foreur | Mobilité | Tunnel rail auto. | 8 | 5 | 8 | 8 | 7 | maybe |
| 053 | Loup monture | Mobilité | Monture rare. | 9 | 6 | 7 | 5 | 3 | later |
| 054 | Portail event | Mobilité | TP temporaire. | 8 | 9 | 4 | 3 | 1 | now |
| 055 | Pad de saut | Mobilité | Jump pad spawn/event. | 7 | 6 | 3 | 4 | 1 | now |
| 056 | Routes rapides | Mobilité | Speed sur chemins team. | 5 | 7 | 4 | 3 | 1 | later |
| 057 | Tomate | Agriculture | Crop food early. | 4 | 7 | 5 | 4 | 2 | now |
| 058 | Maïs | Agriculture | Crop tacos/popcorn. | 5 | 7 | 5 | 5 | 2 | now |
| 059 | Riz | Agriculture | Crop eau/ramen. | 5 | 7 | 5 | 5 | 2 | later |
| 060 | Lavande | Agriculture | Potion/cosmetic. | 6 | 6 | 5 | 4 | 1 | later |
| 061 | Menthe | Agriculture | Alchimie fraîcheur. | 5 | 6 | 5 | 3 | 1 | later |
| 062 | Piment | Agriculture | Food buff risqué. | 6 | 6 | 5 | 5 | 1 | later |
| 063 | Plante carnivore | Agriculture | Défense décorative. | 8 | 5 | 7 | 6 | 3 | maybe |
| 064 | Baies lumineuses+ | Agriculture | Lumière/potions. | 6 | 6 | 5 | 4 | 2 | later |
| 065 | Fleur saphir | Agriculture | Fragment rare capé. | 8 | 6 | 6 | 9 | 2 | maybe |
| 066 | Serre | Agriculture | Bonus croissance léger. | 6 | 8 | 6 | 6 | 3 | later |
| 067 | Pizza | Nourriture | Buff saturation. | 5 | 6 | 4 | 3 | 1 | now |
| 068 | Burger | Nourriture | Food dense. | 5 | 6 | 4 | 3 | 1 | now |
| 069 | Tacos épicés | Nourriture | Haste court + faim. | 6 | 6 | 4 | 5 | 1 | later |
| 070 | Ramen | Nourriture | Regen naturelle boost. | 6 | 6 | 4 | 4 | 1 | later |
| 071 | Jus citron | Nourriture | Anti feu léger. | 5 | 5 | 4 | 3 | 1 | later |
| 072 | Sushi | Nourriture | Respiration courte. | 5 | 5 | 4 | 3 | 1 | maybe |
| 073 | Popcorn event | Nourriture | Fun event spectateur. | 4 | 3 | 3 | 1 | 1 | maybe |
| 074 | Pain ancien | Nourriture | Buff XP faible. | 6 | 6 | 4 | 5 | 1 | later |
| 075 | Repas royal | Nourriture | Buff long non PvP. | 7 | 6 | 5 | 6 | 1 | later |
| 076 | Sel | Pourriture/survie | Conservation simple. | 4 | 7 | 4 | 2 | 1 | later |
| 077 | Fumoir avancé | Pourriture/survie | Food conservée. | 5 | 7 | 4 | 3 | 1 | later |
| 078 | Glacière | Pourriture/survie | Stop timer food custom. | 5 | 6 | 6 | 5 | 1 | maybe |
| 079 | Compost | Pourriture/survie | Recycle surplus. | 4 | 8 | 4 | 5 | 1 | now |
| 080 | Maladie légère | Pourriture/survie | Debuff court crue. | 3 | 4 | 4 | 3 | 1 | maybe |
| 081 | Restaurant joueur | Pourriture/survie | Shops food premium. | 7 | 6 | 5 | 3 | 1 | later |
| 082 | Qualité repas | Pourriture/survie | Normal/rare/royal. | 6 | 6 | 5 | 5 | 1 | later |
| 083 | Pourriture globale | Pourriture/survie | Tous aliments expirent. | 2 | 3 | 7 | 6 | 2 | avoid |
| 084 | Spawner stack | Spawners | Stack virtuel anti-lag. | 6 | 10 | 6 | 6 | 2 | now |
| 085 | Spawner upgrade | Spawners | Vitesse/loot/XP. | 7 | 9 | 7 | 7 | 3 | now |
| 086 | Maintenance spawner | Spawners | Carburant/taxe. | 5 | 8 | 6 | 5 | 1 | later |
| 087 | Spawner GUI loot | Spawners | Stock virtuel. | 6 | 8 | 8 | 6 | 1 | later |
| 088 | Spawner XP bank | Spawners | XP stockée plafonnée. | 6 | 7 | 7 | 7 | 1 | later |
| 089 | Pince risquée | Spawners | Chance casse. | 7 | 7 | 7 | 8 | 1 | later |
| 090 | Spawner event 7j | Spawners | Temporaire saison. | 8 | 6 | 6 | 6 | 2 | later |
| 091 | Limite par chunk | Spawners | Cap performance. | 3 | 10 | 3 | 2 | 1 | now |
| 092 | Spawner boss | Spawners | Mobs rares limités. | 8 | 6 | 8 | 8 | 4 | maybe |
| 093 | Wealth tree | Economie | Menu dépenses utiles. | 6 | 10 | 6 | 4 | 1 | now |
| 094 | Buy orders | Economie | Commandes d'achat. | 6 | 9 | 6 | 5 | 1 | now |
| 095 | Taxe AH | Economie | Sink vente joueur. | 3 | 9 | 3 | 2 | 1 | now |
| 096 | Parcelles shop | Economie | Loyer au spawn. | 7 | 7 | 6 | 4 | 1 | later |
| 097 | Contrats chasse | Economie | Bounties PvE/PvP. | 8 | 8 | 6 | 7 | 1 | now |
| 098 | Réparations chères | Economie | Sink gear. | 4 | 8 | 4 | 3 | 1 | now |
| 099 | Carburants outils | Economie | Sink consommable. | 5 | 9 | 5 | 4 | 1 | now |
| 100 | Team bank | Economie | Objectif groupe. | 6 | 8 | 6 | 6 | 1 | now |
| 101 | Black market | Economie | Offres rares temporaires. | 9 | 7 | 6 | 7 | 1 | later |
| 102 | Dynamic price bands | Economie | Prix shop ajustés. | 5 | 8 | 8 | 5 | 1 | later |
| 103 | Saphirs playtime actif | Saphirs | Fragments via activité. | 5 | 8 | 5 | 7 | 1 | now |
| 104 | Saphirs boss | Saphirs | Contribution boss. | 8 | 8 | 6 | 5 | 1 | now |
| 105 | Saphirs PvP valide | Saphirs | Kill reward anti-alt. | 8 | 5 | 7 | 9 | 1 | maybe |
| 106 | Reroll enchant saphir | Saphirs | Sink rare. | 7 | 8 | 6 | 6 | 1 | later |
| 107 | Boss keys saphir | Saphirs | Invoque boss. | 8 | 8 | 5 | 5 | 1 | later |
| 108 | Cosmetic saphir | Saphirs | Prestige non P2W. | 6 | 6 | 4 | 2 | 1 | now |
| 109 | Pince saphir | Saphirs | Déplacer spawner. | 7 | 7 | 6 | 7 | 1 | later |
| 110 | Fragment saison | Saphirs | Monnaie resetable. | 7 | 8 | 5 | 3 | 1 | later |
| 111 | Shop spawn | Shops | Marchands thématiques. | 5 | 9 | 5 | 4 | 1 | now |
| 112 | Shop rotatif | Shops | Stock changeant. | 7 | 7 | 6 | 5 | 1 | later |
| 113 | Shop crops | Shops | Agriculture market. | 5 | 7 | 5 | 5 | 1 | later |
| 114 | Shop boss tokens | Shops | Echange boss loot. | 7 | 7 | 5 | 4 | 1 | later |
| 115 | Shop cosmetics | Shops | Tags/titres. | 6 | 5 | 4 | 2 | 1 | now |
| 116 | Parcelle marché | Shops | Player shop physique. | 7 | 7 | 6 | 4 | 1 | later |
| 117 | Shop réputation | Shops | Débloqué par quêtes. | 7 | 7 | 6 | 4 | 1 | later |
| 118 | Shop event | Shops | Ouvert pendant events. | 7 | 6 | 5 | 4 | 1 | later |
| 119 | Marchand itinérant | Marché noir | PNJ offres rares. | 8 | 7 | 6 | 6 | 1 | later |
| 120 | Coffre illégal | Marché noir | Chest public risqué. | 8 | 5 | 5 | 6 | 1 | maybe |
| 121 | Vente aux enchères noire | Marché noir | Item secret chaque semaine. | 8 | 6 | 7 | 5 | 1 | later |
| 122 | Contrat interdit | Marché noir | Bounty PvE rare. | 7 | 6 | 6 | 5 | 1 | later |
| 123 | Tax dodge coupon | Marché noir | Réduit frais AH une fois. | 6 | 5 | 5 | 6 | 1 | maybe |
| 124 | Clé boss douteuse | Marché noir | Invoque boss plus dur. | 8 | 6 | 6 | 7 | 1 | later |
| 125 | Carte relique | Marché noir | Coord vague trésor. | 8 | 6 | 5 | 5 | 1 | later |
| 126 | Item maudit | Marché noir | Fort mais malus. | 8 | 5 | 7 | 7 | 1 | maybe |
| 127 | Loterie hebdo | Casino/loterie | Tickets argent capés. | 6 | 4 | 5 | 5 | 1 | maybe |
| 128 | Roue event gratuite | Casino/loterie | Spin participation. | 7 | 5 | 4 | 3 | 1 | now |
| 129 | Bingo serveur | Casino/loterie | Grille objectifs. | 8 | 7 | 6 | 3 | 1 | later |
| 130 | Scratch card ingame | Casino/loterie | Grattage argent faible. | 5 | 3 | 5 | 6 | 1 | avoid |
| 131 | Jackpot mobs | Casino/loterie | Mob rare drop pot. | 7 | 5 | 5 | 5 | 1 | maybe |
| 132 | Tombola cosmétique | Casino/loterie | Aucun gameplay. | 6 | 4 | 4 | 2 | 1 | now |
| 133 | Casino argent réel | Casino/loterie | Acheter chances. | 1 | 1 | 6 | 10 | 1 | avoid |
| 134 | Spleef | Events admin | Arène neige. | 8 | 5 | 4 | 3 | 2 | now |
| 135 | TNT Run | Events admin | Sol disparaît. | 9 | 4 | 5 | 4 | 4 | now |
| 136 | Color Shuffle | Events admin | Va sur couleur. | 9 | 4 | 5 | 3 | 3 | now |
| 137 | Défense cube | Events admin | Vagues coop. | 9 | 6 | 7 | 5 | 6 | later |
| 138 | Dé à coudre | Events admin | Saut eau. | 8 | 4 | 4 | 2 | 1 | now |
| 139 | Parkour chrono | Events admin | Checkpoints. | 7 | 4 | 4 | 2 | 1 | now |
| 140 | Build battle | Events admin | Build + vote. | 7 | 5 | 5 | 3 | 1 | later |
| 141 | Capture zone | Events admin | KOTH soft. | 8 | 6 | 6 | 6 | 2 | later |
| 142 | Livraison relais | Events admin | Porter colis. | 8 | 6 | 5 | 5 | 1 | now |
| 143 | Quiz chat | Events admin | Questions rapides. | 5 | 4 | 2 | 3 | 1 | now |
| 144 | Lune de sang | Events aléatoires | Nuit mobs boostés. | 9 | 7 | 6 | 6 | 5 | now |
| 145 | Météorite | Events aléatoires | Impact ressources. | 10 | 8 | 6 | 7 | 3 | now |
| 146 | Boss public | Events aléatoires | Boss contribution. | 10 | 8 | 8 | 6 | 6 | now |
| 147 | Marchand itinérant | Events aléatoires | Offres temporaires. | 8 | 7 | 6 | 5 | 1 | later |
| 148 | Faille dimensionnelle | Events aléatoires | Portal mini mobs. | 9 | 7 | 7 | 6 | 5 | later |
| 149 | Village attaqué | Events aléatoires | Défense coop. | 8 | 6 | 7 | 5 | 5 | later |
| 150 | Coffre maudit | Events aléatoires | Loot + gardien. | 8 | 6 | 5 | 5 | 2 | later |
| 151 | Trésor sous-marin | Events aléatoires | Exploration ocean. | 7 | 6 | 5 | 4 | 2 | later |
| 152 | Pluie ressources | Events aléatoires | Drops zone. | 7 | 4 | 4 | 7 | 4 | maybe |
| 153 | Navire fantôme | Events aléatoires | Donjon océan. | 9 | 5 | 8 | 5 | 5 | maybe |
| 154 | Roi des Pillards | Boss | Boss bannières. | 10 | 8 | 8 | 6 | 5 | now |
| 155 | Duc de Basalte | Boss | Boss Nether lave. | 9 | 7 | 8 | 6 | 6 | later |
| 156 | Anomalie | Boss | Boss glitch saison. | 10 | 8 | 9 | 7 | 5 | later |
| 157 | Gardien Mine | Boss | Boss early mining. | 8 | 7 | 6 | 4 | 3 | now |
| 158 | Capitaine Noyé | Boss | Boss océan. | 8 | 6 | 6 | 4 | 3 | now |
| 159 | Reine Ruche | Boss | Boss agriculture. | 8 | 6 | 7 | 5 | 4 | later |
| 160 | Dragon reforgé | Boss | Fin End custom. | 10 | 9 | 10 | 8 | 7 | later |
| 161 | Banquier Maudit | Boss | Boss économie. | 8 | 6 | 8 | 8 | 3 | maybe |
| 162 | Colosse Givre | Boss | Saison neige. | 9 | 7 | 8 | 5 | 5 | later |
| 163 | Sculk Oracle | Boss | Ancient city boss. | 9 | 7 | 8 | 6 | 5 | later |
| 164 | Mineur spectral | Mobs | Fuit avec minerais. | 7 | 6 | 5 | 4 | 2 | now |
| 165 | Rampant cristallin | Mobs | Creeper cristaux. | 7 | 6 | 5 | 5 | 2 | now |
| 166 | Garde coffre | Mobs | Protège coffre event. | 7 | 6 | 4 | 3 | 2 | now |
| 167 | Loup alpha | Mobs | Pack leader. | 7 | 5 | 5 | 4 | 2 | later |
| 168 | Loup monture | Mobs | Apprivoisement rare. | 9 | 6 | 7 | 5 | 3 | later |
| 169 | Cultiste pillard | Mobs | Buff raid mobs. | 7 | 6 | 5 | 4 | 2 | now |
| 170 | Magma ducal | Mobs | Fuel Nether. | 7 | 6 | 5 | 4 | 3 | later |
| 171 | Endermite instable | Mobs | End fragment. | 7 | 5 | 5 | 5 | 2 | later |
| 172 | Zombie gelé | Mobs | Saison slow. | 6 | 4 | 4 | 3 | 2 | later |
| 173 | Pêcheur noyé | Mobs | Filet océan. | 7 | 5 | 5 | 4 | 2 | now |
| 174 | Création team payante | Teams | Argent pour fonder. | 5 | 8 | 4 | 3 | 1 | now |
| 175 | Slots membres upgrade | Teams | Grandir par niveaux. | 6 | 8 | 5 | 5 | 1 | now |
| 176 | Banque team | Teams | Solde partagé loggé. | 6 | 8 | 6 | 6 | 1 | now |
| 177 | Bannière team home | Teams | Bloc identité/home. | 8 | 8 | 6 | 5 | 1 | now |
| 178 | Quêtes team | Teams | Objectifs collectifs. | 8 | 8 | 6 | 5 | 1 | now |
| 179 | Alliances | Teams | Chat/no FF option. | 6 | 6 | 5 | 6 | 1 | later |
| 180 | Rivalités soft | Teams | Bonus KOTH/contrats. | 7 | 6 | 6 | 7 | 1 | later |
| 181 | Leaderboard puissance | Teams | Score multi-critères. | 7 | 6 | 6 | 5 | 1 | now |
| 182 | Team shop | Teams | Dépenses groupe. | 6 | 7 | 5 | 4 | 1 | now |
| 183 | Quête quotidienne | Quêtes | 3 objectifs/jour. | 7 | 9 | 6 | 5 | 1 | now |
| 184 | Quête hebdo | Quêtes | Gros objectif. | 7 | 8 | 6 | 4 | 1 | now |
| 185 | Quête saison | Quêtes | Arc temporaire. | 8 | 8 | 7 | 4 | 1 | later |
| 186 | Quête découverte | Quêtes | Onboarding. | 5 | 9 | 4 | 2 | 1 | now |
| 187 | Contrat chasse mob | Quêtes | Tuer rare. | 7 | 7 | 5 | 5 | 1 | now |
| 188 | Contrat livraison | Quêtes | Porter item. | 8 | 6 | 5 | 6 | 1 | now |
| 189 | Quête économie | Quêtes | Vendre/trader. | 5 | 8 | 5 | 5 | 1 | now |
| 190 | Quête PvP duel | Quêtes | Faire/gagner duel. | 7 | 6 | 5 | 7 | 1 | now |
| 191 | Quête communauté | Quêtes | Objectif global. | 8 | 8 | 7 | 4 | 1 | later |
| 192 | Reroll quête payant | Quêtes | Sink argent. | 4 | 7 | 4 | 4 | 1 | now |
| 193 | Duel kit | Duels | Combat équilibré. | 8 | 7 | 6 | 4 | 1 | now |
| 194 | Duel stuff perso | Duels | Pari gear. | 8 | 5 | 7 | 8 | 1 | later |
| 195 | Pari argent plafonné | Duels | Mise contrôlée. | 7 | 5 | 5 | 8 | 1 | later |
| 196 | Duel spectateur | Duels | Voir fights. | 7 | 5 | 5 | 3 | 1 | later |
| 197 | Tournoi hebdo | Duels | Bracket event. | 9 | 7 | 7 | 6 | 1 | later |
| 198 | Elo par kit | Duels | Classement clair. | 7 | 6 | 6 | 5 | 1 | now |
| 199 | Historique duel | Duels | Transparence. | 5 | 7 | 5 | 3 | 1 | now |
| 200 | Anti-quit duel | Duels | Forfait propre. | 4 | 9 | 4 | 2 | 1 | now |
| 201 | Tuto 3 minutes | Spawn/tutoriel | Mini parcours utile. | 5 | 10 | 5 | 2 | 1 | now |
| 202 | Portes systèmes | Spawn/tutoriel | Zones lisibles. | 6 | 9 | 4 | 1 | 1 | now |
| 203 | NPC guide | Spawn/tutoriel | Menu central. | 5 | 9 | 5 | 2 | 1 | now |
| 204 | Warp tuto | Spawn/tutoriel | Revoir guides. | 4 | 8 | 3 | 1 | 1 | now |
| 205 | Board events | Spawn/tutoriel | Prochains events. | 6 | 8 | 5 | 2 | 1 | now |
| 206 | Market spawn | Spawn/tutoriel | Shops visibles. | 7 | 8 | 6 | 3 | 1 | later |
| 207 | Leaderboard podiums | Spawn/tutoriel | Prestige. | 7 | 6 | 5 | 2 | 1 | now |
| 208 | Guide vidéo QR/Discord | Spawn/tutoriel | Aide externe. | 4 | 8 | 3 | 1 | 1 | now |
| 209 | Intro camera | Cutscenes | 10s arrivée. | 7 | 5 | 7 | 2 | 2 | later |
| 210 | Boss entrance | Cutscenes | Arrivée boss. | 9 | 6 | 6 | 3 | 3 | now |
| 211 | Portal opening | Cutscenes | Portail event. | 8 | 7 | 5 | 2 | 2 | now |
| 212 | Level-up title | Cutscenes | Feedback progression. | 6 | 7 | 3 | 1 | 1 | now |
| 213 | Fin saison cinematic | Cutscenes | Grand final. | 10 | 7 | 9 | 4 | 5 | later |
| 214 | Killcam | Cutscenes | Revoir mort. | 8 | 4 | 10 | 5 | 4 | maybe |
| 215 | Achat majeur animation | Cutscenes | Firework/sound. | 6 | 6 | 3 | 1 | 1 | now |
| 216 | Saison apocalypse | Saisons | Nuits/events ruines. | 10 | 8 | 8 | 6 | 5 | later |
| 217 | Saison neige | Saisons | Monde gelé. | 8 | 7 | 7 | 4 | 3 | later |
| 218 | Saison corruption | Saisons | Biomes contaminés. | 9 | 8 | 8 | 6 | 5 | later |
| 219 | Saison économie | Saisons | Marchés et guildes. | 7 | 8 | 6 | 5 | 2 | later |
| 220 | Saison End | Saisons | Final dragon. | 10 | 9 | 9 | 7 | 6 | later |
| 221 | Saison exploration | Saisons | Reliques/donjons. | 8 | 8 | 7 | 5 | 4 | later |
| 222 | Saison factions light | Saisons | KOTH/outposts. | 8 | 7 | 7 | 7 | 3 | later |
| 223 | Saison normale+ | Saisons | Launch stable. | 7 | 10 | 5 | 3 | 2 | now |
| 224 | Nether boss zones | Nether/End rework | Arènes Nether. | 9 | 7 | 8 | 5 | 5 | later |
| 225 | End islands custom | Nether/End rework | Exploration End. | 9 | 8 | 8 | 5 | 5 | later |
| 226 | Dragon egg objective | Nether/End rework | Objet saison. | 10 | 8 | 7 | 7 | 2 | now |
| 227 | Nether ores economy | Nether/End rework | Ressources contrôlées. | 7 | 8 | 6 | 7 | 2 | later |
| 228 | End invasion | Nether/End rework | Event public. | 9 | 7 | 8 | 6 | 6 | later |
| 229 | Portail Nether taxé | Nether/End rework | Ouverture contrôlée. | 5 | 6 | 4 | 3 | 1 | maybe |
| 230 | Reset resource End | Nether/End rework | Ressources End reset. | 6 | 8 | 5 | 3 | 1 | later |
| 231 | Donjon bastion | Nether/End rework | Structure combat. | 8 | 7 | 8 | 5 | 5 | later |
| 232 | Station recharge | Blocs custom | Bloc énergie. | 5 | 9 | 5 | 7 | 1 | now |
| 233 | Bloc team | Blocs custom | Bannière/home. | 8 | 8 | 6 | 5 | 1 | now |
| 234 | Sceau anti-mob | Blocs custom | Anti spawn petit rayon. | 5 | 8 | 5 | 5 | 1 | later |
| 235 | Composteur avancé | Blocs custom | Engrais custom. | 5 | 7 | 5 | 5 | 1 | later |
| 236 | Shop display | Blocs custom | Vitrine prix. | 7 | 7 | 6 | 6 | 1 | later |
| 237 | Coffre compact | Blocs custom | Stockage limité. | 5 | 7 | 7 | 8 | 1 | maybe |
| 238 | Geyser | Blocs custom | Propulsion. | 7 | 5 | 5 | 5 | 2 | maybe |
| 239 | Totem alarme | Blocs custom | Alert team. | 7 | 7 | 6 | 4 | 1 | later |
| 240 | Portail limité | Blocs custom | TP coûteux. | 7 | 7 | 7 | 8 | 1 | maybe |
| 241 | Bloc trophée boss | Blocs custom | Décor prestige. | 8 | 5 | 4 | 1 | 1 | now |
| 242 | Online detector | Redstone custom | Signal online. | 5 | 4 | 6 | 8 | 1 | maybe |
| 243 | Clicker lent | Redstone custom | Auto clic capé. | 5 | 5 | 6 | 8 | 4 | maybe |
| 244 | Hopper filtrant | Redstone custom | Tri simple. | 5 | 8 | 6 | 6 | 3 | later |
| 245 | Capteur spawner | Redstone custom | Signal stockage plein. | 5 | 6 | 5 | 3 | 1 | later |
| 246 | Redstone lock team | Redstone custom | Permissions portes. | 6 | 6 | 5 | 5 | 1 | later |
| 247 | TNT waterproof | Redstone custom | Explo sous eau. | 7 | 3 | 5 | 10 | 3 | avoid |
| 248 | TNT event | Redstone custom | Explo arène only. | 8 | 4 | 5 | 5 | 4 | maybe |
| 249 | Pulse limiter | Redstone custom | Anti-clock rapide. | 3 | 9 | 4 | 2 | 1 | now |
| 250 | Paper anti-xray | Protection/anti-cheat | Obfuscation monde. | 3 | 10 | 3 | 3 | 3 | now |
| 251 | Grim/Matrix | Protection/anti-cheat | Anti-cheat mouvement/combat. | 4 | 10 | 5 | 4 | 2 | now |
| 252 | Logger coffres | Protection/anti-cheat | Historique containers. | 4 | 10 | 6 | 2 | 2 | now |
| 253 | Logger spawners | Protection/anti-cheat | Tracking spawners. | 4 | 10 | 5 | 2 | 1 | now |
| 254 | Relation graph alts | Protection/anti-cheat | Détecte farms amis/alts. | 6 | 8 | 8 | 5 | 1 | now |
| 255 | Rare resource tracker | Protection/anti-cheat | Minage suspect. | 5 | 8 | 7 | 4 | 1 | now |
| 256 | Seed spoof | Protection/anti-cheat | Réduit seed abuse. | 4 | 7 | 6 | 4 | 1 | now |
| 257 | Anti book crash | Protection/anti-cheat | Filtre NBT livres. | 2 | 9 | 4 | 2 | 1 | now |
| 258 | Anti-dupe audit | Protection/anti-cheat | Tests release. | 3 | 10 | 5 | 3 | 1 | now |
| 259 | Staff alert score | Protection/anti-cheat | Suspicion score. | 5 | 8 | 7 | 4 | 1 | later |
| 260 | Spark weekly | Performance | Profiling hebdo. | 2 | 10 | 2 | 1 | 1 | now |
| 261 | Chunky pregen | Performance | Pregen worlds. | 2 | 10 | 2 | 1 | 1 | now |
| 262 | Entity caps | Performance | Limite mobs/chunk. | 2 | 10 | 4 | 2 | 1 | now |
| 263 | Virtual spawners | Performance | Moins entités. | 4 | 9 | 8 | 5 | 1 | later |
| 264 | Hopper limits | Performance | Anti farms lag. | 2 | 9 | 4 | 3 | 1 | now |
| 265 | Scoreboard batching | Performance | Updates groupées. | 2 | 8 | 4 | 1 | 1 | now |
| 266 | Event cleanup | Performance | Nettoie worlds/mobs. | 3 | 9 | 5 | 2 | 1 | now |
| 267 | DB partitions | Performance | Logs scalables. | 2 | 8 | 6 | 2 | 1 | later |
| 268 | Trailer 60s | Communication/contenu | Vidéo lancement. | 9 | 8 | 4 | 1 | 1 | now |
| 269 | Shorts features | Communication/contenu | Clips 30s. | 8 | 8 | 3 | 1 | 1 | now |
| 270 | Patch notes stylés | Communication/contenu | Retention confiance. | 5 | 8 | 2 | 1 | 1 | now |
| 271 | Teaser boss | Communication/contenu | Hype. | 9 | 7 | 4 | 1 | 1 | later |
| 272 | Devlog economy | Communication/contenu | Transparence. | 6 | 7 | 3 | 1 | 1 | now |
| 273 | Spotlight team | Communication/contenu | Social proof. | 7 | 6 | 3 | 1 | 1 | later |
| 274 | Classement hebdo | Communication/contenu | Retour joueurs. | 6 | 7 | 4 | 2 | 1 | now |
| 275 | Guide nouveau | Communication/contenu | Onboarding externe. | 5 | 9 | 3 | 1 | 1 | now |
| 276 | Clip event auto | Communication/contenu | Réutiliser moments. | 8 | 5 | 7 | 2 | 1 | maybe |
| 277 | Tags | Cosmétiques | Suffix/prefix. | 5 | 6 | 3 | 1 | 1 | now |
| 278 | Titres | Cosmétiques | Sous-nom/prestige. | 6 | 6 | 4 | 1 | 1 | now |
| 279 | Particules | Cosmétiques | Trail/lobby. | 7 | 4 | 4 | 2 | 3 | now |
| 280 | Sons kill | Cosmétiques | Duel/PvP style. | 7 | 3 | 4 | 1 | 1 | later |
| 281 | Death effects | Cosmétiques | Animation mort. | 8 | 3 | 5 | 1 | 2 | later |
| 282 | Chapeaux | Cosmétiques | Armor stand/model. | 7 | 4 | 6 | 2 | 2 | later |
| 283 | Trophées boss | Cosmétiques | Décor base. | 9 | 5 | 4 | 1 | 1 | now |
| 284 | Pets cosmétiques | Cosmétiques | Compagnons. | 8 | 4 | 7 | 2 | 3 | later |
| 285 | Skins outils | Cosmétiques | Resource pack. | 8 | 5 | 6 | 2 | 1 | later |
| 286 | Décos saison | Cosmétiques | Blocs/furniture. | 7 | 4 | 6 | 2 | 2 | later |
| 287 | Grade Saphir | Grades | Cosmetic + QoL faible. | 6 | 7 | 4 | 3 | 1 | now |
| 288 | Grade Saphir+ | Grades | Cosmetics avancés. | 7 | 7 | 4 | 3 | 1 | later |
| 289 | Home bonus obtenable | Grades | QoL non exclusif. | 5 | 6 | 4 | 5 | 1 | maybe |
| 290 | Kit payant fort | Grades | Avantage combat. | 1 | 1 | 3 | 10 | 1 | avoid |
| 291 | Crate cosmétique | Grades | Odds non gameplay. | 6 | 5 | 4 | 2 | 1 | now |
| 292 | Rôle Discord | Grades | Social/status. | 4 | 5 | 2 | 1 | 1 | now |
| 293 | Pet luciole | Familiers | Lumière cosmétique. | 7 | 4 | 6 | 2 | 3 | later |
| 294 | Pet ramasseur | Familiers | Ramasse faible. | 7 | 6 | 7 | 6 | 3 | later |
| 295 | Pet sacoche | Familiers | 3 slots. | 6 | 5 | 7 | 7 | 1 | maybe |
| 296 | Pet alerte | Familiers | Alerte danger. | 6 | 5 | 6 | 4 | 2 | later |
| 297 | Pet abeille | Familiers | Farming +1% capé. | 7 | 5 | 6 | 5 | 2 | later |
| 298 | Pet dragonnet | Familiers | Boss prestige. | 9 | 4 | 7 | 2 | 3 | later |
| 299 | Pet nourriture | Familiers | Sink upkeep léger. | 4 | 6 | 4 | 3 | 1 | later |
| 300 | Skins de pet | Familiers | Monétisation safe. | 7 | 5 | 5 | 1 | 1 | later |
| 301 | Assurance item rare | Nouvelles | Protège 1 boss item PVE. | 6 | 6 | 7 | 7 | 1 | maybe |
| 302 | Musée serveur | Nouvelles | Expose boss/reliques. | 8 | 6 | 5 | 2 | 1 | later |
| 303 | Almanach mobs | Nouvelles | Collection mobs tués. | 7 | 6 | 6 | 3 | 1 | later |
| 304 | Cartes au trésor craft | Nouvelles | Exploration guidée. | 8 | 7 | 5 | 5 | 1 | later |
| 305 | Réputation PNJ | Nouvelles | Débloque shops. | 7 | 7 | 7 | 4 | 1 | later |
| 306 | Streak daily | Nouvelles | Bonus retour quotidien. | 6 | 8 | 5 | 5 | 1 | now |
| 307 | Contrats staff auto | Nouvelles | Staff lance objectifs serveur. | 7 | 8 | 6 | 3 | 1 | later |
| 308 | Coffres communautaires | Nouvelles | Objectif donation item. | 6 | 6 | 5 | 5 | 1 | maybe |
| 309 | Reliques de base | Nouvelles | Décos qui racontent saison. | 8 | 5 | 5 | 2 | 1 | later |
| 310 | Calendrier saisonnier | Nouvelles | Events planifiés visibles. | 6 | 9 | 5 | 2 | 1 | now |

## 35. Top 50 idées les plus fortes

| Rang | Idée | Pitch | Pourquoi c'est fort | Impact éco/rétention | Dev | Risques | MVP | Avancé | Verdict |
|---:|---|---|---|---|---:|---|---|---|---|
| 1 | Economie utile / Wealth tree | Menu clair de dépenses permanentes. | Transforme l'argent en progression. | Très haut/haut | 6 | Inflation si prix mauvais. | Homes, repairs, slots. | Arbre complet. | A faire |
| 2 | Quêtes quotidiennes | 3 objectifs/jour. | Raison de revenir. | Moyen/très haut | 6 | Farm répétitif. | 20 quêtes random. | Streaks/team. | A faire |
| 3 | Spawners équilibrés | Stack + upgrades + limites. | Déjà apprécié, gros objectif. | Très haut/haut | 7 | Lag/inflation. | Tiers + cap chunk. | Maintenance GUI. | A faire |
| 4 | Events admin commandés | /event propre. | Anime communauté. | Moyen/très haut | 6 | Bugs live. | Spleef/color/parkour. | Scheduler/leaderboards. | A faire |
| 5 | Météorite | Event monde ressources. | Clip instantané. | Haut/haut | 6 | Rush/PvP. | Coord vague + chest. | Cratère + mobs. | A faire |
| 6 | Boss public contribution | Combat public juste. | Endgame social. | Haut/très haut | 8 | Lag/leech. | 1 boss simple. | Plusieurs tiers. | A faire |
| 7 | Teams light | Banque, home, upgrades. | Retient les groupes. | Haut/haut | 6 | Drama. | 2 slots+bank+home. | Alliances/KOTH. | A faire |
| 8 | Logger complet | Confiance staff. | Protège temps joueur. | Indirect/très haut | 7 | DB lourde. | Blocs/coffres/kills. | Relation graph. | A faire |
| 9 | Anti-xray sérieux | Paper config + logs. | Stoppe triche visible. | Indirect/haut | 3 | Faux sentiment sécurité. | Config par monde. | suspicion score. | A faire |
| 10 | Tuto 3 minutes | Onboarding court. | Réduit drop nouveau. | Haut/très haut | 5 | Trop verbeux. | 6 étapes. | Cinématique. | A faire |
| 11 | Foreuse 3x3 énergie | Outil signature. | Très vendable en vidéo. | Haut/haut | 7 | Mining abusif. | Cooldown+charge. | Modes. | A faire |
| 12 | Tronçonneuse | Bois rapide contrôlé. | Utility immédiate. | Moyen/haut | 6 | Lag arbres. | Cap logs. | Tiers. | A faire |
| 13 | Saphirs rares | Monnaie prestige. | Structure late game. | Très haut/haut | 5 | Pay-to-win si mal utilisé. | Boss+quêtes. | Fragments saison. | A faire |
| 14 | Buy orders | Economie joueur avancée. | Crée interactions. | Haut/moyen | 6 | Transferts alts. | Orders simples. | Filtres/fulfill web. | A faire |
| 15 | Marché noir | Sink rare temporisé. | Hype hebdo. | Très haut/haut | 6 | Items trop forts. | PNJ spawn. | Marchand secret. | Later fort |
| 16 | Duel kit Elo | PvP propre. | Contenu sans casser SMP. | Moyen/haut | 6 | Cheat/toxicité. | 1 kit. | Tournois. | A faire |
| 17 | Bounties anti-abus | Contrats chasse. | Interaction PvP/social. | Moyen/haut | 6 | Farm amis. | Cap/cooldown. | Relation graph. | A faire |
| 18 | Dragon egg objective | Objectif serveur. | Narratif/vidéo. | Moyen/haut | 7 | Base finder. | Boussole vague. | Rituel final. | A faire |
| 19 | Boss Roi Pillards | Mid boss accessible. | Minecraft feeling fort. | Haut/haut | 8 | Adds lag. | Arène + phases. | Bannières objectives. | A faire |
| 20 | Lune de sang | Event nuit. | Simple, mémorable. | Moyen/haut | 6 | Morts injustes. | Mob buffs capés. | Boss final nuit. | A faire |
| 21 | Spawn portes systèmes | UX serveur. | Vend la vision. | Moyen/haut | 4 | Trop grand. | Portes + NPC. | Districts. | A faire |
| 22 | Parcelles shop | Economie physique. | Spawn vivant. | Haut/moyen | 6 | Abandon. | Loyer hebdo. | Vitrines custom. | Later |
| 23 | Crops custom utiles | Alternative farming. | Contenu calme + éco. | Haut/moyen | 6 | Farm abuse. | 4 crops. | Saisons/serres. | Later |
| 24 | Nourriture buffs légers | Donne valeur crops. | Simple à comprendre. | Moyen/moyen | 4 | Buff stack. | 5 foods. | Restaurants. | Later |
| 25 | Station recharge | Limite items. | Système propre. | Haut/moyen | 5 | Dupes. | /charge + block. | Upgrades. | A faire |
| 26 | Trophées boss | Prestige non P2W. | Monétisable/collectible. | Bas/haut | 4 | Aucun. | Items décor. | Musée. | A faire |
| 27 | Calendrier events | Prévisibilité. | Les joueurs planifient. | Moyen/haut | 5 | Promesses ratées. | GUI/Discord. | Auto scheduler. | A faire |
| 28 | Spark routine | Santé serveur. | Evite catastrophe. | Indirect/haut | 2 | Négligé. | Checklist hebdo. | Dashboards. | A faire |
| 29 | Resource world | Protège monde principal. | Longévité. | Moyen/haut | 5 | Confusion. | Reset mensuel. | Events ressources. | A faire |
| 30 | Cap spawners chunk | Performance. | Nécessaire stabilité. | Indirect/haut | 3 | Frustration. | Hard cap. | Team upgrades. | A faire |
| 31 | Streak daily | Rétention légère. | Retour quotidien. | Moyen/haut | 5 | FOMO. | 7 jours cap. | Cosmétiques. | A faire |
| 32 | Event livraison | Social/PvP soft. | Clips et commerce. | Moyen/moyen | 5 | Abuse TP. | No TP item. | Caravane. | A faire |
| 33 | Coffre maudit | Micro-event. | Facile à produire. | Moyen/moyen | 5 | Camp. | Chest+guardian. | Malédictions. | Later |
| 34 | Capitaine Noyé | Boss early. | Océan sous-utilisé. | Moyen/moyen | 6 | Eau frustrante. | Arène simple. | Navire. | A faire |
| 35 | Gardien Mine | Boss early mining. | Lie mining/items. | Haut/moyen | 6 | Terrain. | Arène prebuilt. | Stalactites. | A faire |
| 36 | Grappin bloc-only | Mobilité fun. | Clipable. | Moyen/haut | 7 | PvP fuite. | Disabled combat. | Upgrades. | Later |
| 37 | Pets cosmétiques | Monétisation safe. | Affectif. | Bas/haut | 7 | Lag. | 3 pets. | Skins. | Later |
| 38 | Titres/tags | Prestige simple. | Récompense partout. | Bas/moyen | 3 | Chat illisible. | Tags sobres. | Collections. | A faire |
| 39 | Black market rotating cosmetics | Sink sans P2W. | Hype + revenu. | Moyen/haut | 5 | Trop cher. | 5 offres/semaine. | Secret merchant. | Later |
| 40 | Team quests | Group retention. | Teams reviennent. | Haut/haut | 6 | Alts. | Contributions min. | Boss team. | A faire |
| 41 | Classements hebdo | Boucle sociale. | Donne objectifs. | Moyen/haut | 4 | Boost. | 5 classements. | Rewards tags. | A faire |
| 42 | Patch notes + Shorts | Communication. | Rend les features réelles. | Indirect/haut | 3 | Temps prod. | Template. | Devlogs. | A faire |
| 43 | Anti-alt rewards | Sécurité économie. | Protège PvP/saphirs. | Indirect/haut | 8 | Faux positifs. | Cooldown relation. | Graph score. | A faire |
| 44 | Boss tokens | Sépare économies. | Contrôle endgame. | Haut/moyen | 5 | Trop de monnaies. | 1 token/boss. | Token shop. | Later |
| 45 | Mini-donjons event world | Contenu contrôlé. | Pas de grief monde. | Moyen/haut | 8 | Prod maps. | 1 donjon. | Rotation. | Later |
| 46 | Rework End final | Grande promesse. | But de saison. | Très haut/très haut | 10 | Trop lourd. | Oeuf + boss. | End complet. | Later |
| 47 | Sceau anti-mob | QoL base. | Les builders aiment. | Moyen/moyen | 5 | Farms. | Petit rayon. | Upgrades. | Later |
| 48 | Museum serveur | Mémoire saison. | Premium feel. | Bas/moyen | 5 | Maintenance. | Salle trophées. | Histoire interactive. | Later |
| 49 | Marché physique | Interaction. | Rend spawn vivant. | Haut/moyen | 6 | Economie locale. | 8 stands. | Quartier marchand. | Later |
| 50 | Saison "fissures d'apocalypse" | Direction créative launch. | Tease sans surproduire. | Haut/très haut | 7 | Scope creep. | 3 events + boss. | Fin saison. | Verdict fort |

## 36. Roadmap

### Phase 0 - Nettoyage / base

Objectif : serveur fiable avant contenu.  
Features : performance, anti-cheat, anti-xray, permissions, bugs, logger, économie de base, tutoriel skeleton, configs monde.  
Difficulté : moyenne. Durée estimée : 1-3 semaines. Dépendances : Paper stable, build plugins, DB saine.  
Critères de réussite : 20 TPS en test, aucun bug duplication connu, nouveaux joueurs peuvent vendre/sethome/tpa, staff peut rollback/auditer.

### Phase 1 - MVP serveur propre

Objectif : un SMP jouable et reteneur sans gros contenu custom.  
Features : argent utile, shops, worth/sell, homes/TPA, /back PVE si prêt, spawners T1/T2, quêtes simples, events simples, spawn tuto, AH/orders, bounties.  
Difficulté : moyenne. Durée : 2-4 semaines.  
Critères : 10-20 joueurs peuvent jouer une semaine sans inflation absurde; 3 events fonctionnent; économie a au moins 8 sinks.

### Phase 2 - Custom content

Objectif : identité unique.  
Features : enchants custom équilibrés, potions, foreuse/tronçonneuse/voidstone, énergie, agriculture initiale, foods, cosmétiques/titres.  
Difficulté : moyenne/haute. Durée : 3-6 semaines.  
Critères : aucun item ne casse PvP/mining; chaque item a coût, limite, log et config.

### Phase 3 - Communautaire

Objectif : faire vivre les groupes.  
Features : teams complètes, team bank, team home, quests team, duels Elo, classements, events réguliers, marché noir MVP.  
Difficulté : moyenne. Durée : 2-5 semaines.  
Critères : teams actives, au moins 1 event planifié/semaine, logs team suffisants.

### Phase 4 - Endgame

Objectif : objectifs long terme.  
Features : boss early/mid, boss public, donjons légers, saison, Nether/End rework progressif, dragon egg objective.  
Difficulté : haute. Durée : 4-10 semaines.  
Critères : boss stable à 20+ joueurs, contribution anti-leech, rewards non inflationnistes.

### Phase 5 - Polish / contenu

Objectif : serveur premium visible.  
Features : cutscenes, trailer, lore, cosmétiques avancés, pets, museum, patch notes, teasers saison, event final.  
Difficulté : variable. Durée : continu.  
Critères : chaque update a un clip, un changelog, un objectif joueur.

## 37. Idées à éviter / red flags

| Idée | Pourquoi c'est dangereux | Verdict |
|---|---|---|
| Foreuse 5x5 permanente | Détruit mining, lag, inflation minerais. | avoid |
| Fly survival payant | Pay-to-win et casse exploration/PvP. | avoid |
| Kits payants puissants | P2W direct. | avoid |
| Casino avec argent réel ou clés vendues | Risque guidelines, image all-age, confiance. | avoid |
| Spawners sans limite | Lag + inflation + AFK server. | avoid |
| Pourriture globale | Friction casual énorme. | avoid launch |
| Boss sac à PV | Ennui, pas premium. | avoid |
| Mobs custom partout | Lag, confusion, perd feeling vanilla. | avoid |
| Claims/factions full raid | Drama, grief, casuals quittent. | avoid sauf serveur PvP assumé |
| Enchants PvP RNG forts | Fights injustes. | avoid |
| Black market power items | Riches deviennent imbattables. | avoid |
| Trop de monnaies permanentes | UX illisible. | avoid |
| Datapacks command tick lourds | Performance catastrophique. | avoid |
| Anti-cheat trop agressif sans test | Faux positifs, joueurs legit punis. | avoid |
| Réductions grade fortes shop/repair | P2W subtil. | avoid |

## 38. Version finale du cahier des charges

Le serveur V3 doit être un SMP vanilla+ premium centré sur une économie utile, une progression quotidienne, des items custom pratiques, des spawners contrôlés, des teams light et des events réguliers.  
Priorité absolue : stabilité, anti-triche, logs, onboarding et économie. Le contenu custom vient après, avec limites techniques dès le design.

Systèmes principaux :
- Economie : argent normal + saphirs + tokens temporaires, avec sinks forts.
- Progression : quêtes daily/weekly/team, enchants, outils, crops, boss.
- Events : admin commandés + aléatoires monde + calendrier.
- Teams : création payante, 2 slots base, banque, home bannière, upgrades.
- Items : foreuse/tronçonneuse/voidstone/scanners limités par énergie.
- Boss : early/mid/end, contribution rewards, trophées, tokens.
- Agriculture : crops utiles pour food/potions/économie, pas gadget.
- Sécurité : Paper anti-xray, anti-cheat, logger, rollback, relation graph.
- Performance : budgets entités, spawners virtuels/limits, spark, pregen.
- Monétisation : cosmétiques et QoL faible non exclusive, jamais P2W.

Priorités de décision :
1. Finir Phase 0/1 avant boss lourds.
2. Lancer avec 3-5 features signature, pas 50 incomplètes.
3. Rendre chaque système visible via `/menu`, spawn, Discord et vidéo.
4. Tout item puissant a coût, cooldown, durabilité/énergie, logs et config.

## 39. Questions restantes

- Thème de lancement : normal premium, apocalypse légère, neige, corruption ?
- Niveau PvP : casual avec duels/KOTH, ou rivalités plus fortes ?
- Niveau factions : teams light sans claims, claims soft, ou guerre ?
- Niveau RP/lore : simple teaser saison ou narration poussée ?
- Difficulté économie : casual, grind moyen, hardcore ?
- Importance des boss : contenu central ou endgame secondaire ?
- Place des saphirs : rareté prestige seulement ou progression majeure ?
- Monétisation : 1 grade Saphir ou 2 grades ?
- Plugin custom vs plugin existant : quoi garder maison, quoi déléguer ?
- Reset monde : garder monde actuel, reset total, resource world seulement ?
- Style spawn : village marchand, hub cristallin, cité ruinée, forteresse ?
- Intensité custom : vanilla+ discret ou visuels resource pack forts ?
- Bedrock/Geyser prévu ou Java only ? Impact custom models/anti-cheat.
- Durée saison : 2, 3, 4 ou 6 mois ?
- Tolérance casino/loterie : fun ingame très limité ou suppression totale ?

## 40. Ton critique final

Les meilleures idées de tes notes sont clairement : argent utile, saphirs, spawners, enchants, events, teams, quêtes, boss publics, logger/anti-find et items custom pratiques. C'est cohérent : tu ne construis pas un RPG, tu construis un SMP avec une économie et des moments.

Les idées à nerf dès maintenant : foreuses trop grandes, spawners trop rentables, grades avec avantages économiques, potions reach, casino, pourriture lourde, boss trop ambitieux avant stabilité.

La V3 gagnante n'est pas la plus grosse liste de features en production simultanée. C'est une base ultra propre + 5 signatures fortes : économie utile, quêtes quotidiennes, spawners contrôlés, events monde, foreuse/tronçonneuse énergie. Ensuite seulement : boss, agriculture avancée, teams avancées, saisons et cutscenes.

## Annexe technique - matrice des gros systèmes

| Système | Difficulté dev | Dépendance plugin/custom | Risque bug | Risque lag | Risque exploit | Impact économie | Impact PvP | Impact progression | Priorité | MVP possible |
|---|---:|---|---:|---:|---:|---|---|---|---|---|
| Economie + wealth sinks | 6 | SMPCore custom + Vault optionnel | 5 | 1 | 6 | Très fort | Faible | Très fort | now | Menu wealth avec 8 achats et logs |
| Saphirs | 5 | SMPCore custom | 4 | 1 | 7 | Fort | Moyen si PvP rewards | Fort | now | Saphirs via quêtes/boss, pas PvP au début |
| Spawners stack/upgrades | 7 | Custom ou plugin spécialisé | 7 | 7 sans virtualisation | 8 | Très fort | Faible | Fort | now | Stack + cap chunk + achat GUI |
| Enchants custom | 6 | Custom existant SMPCore ou ExcellentEnchants | 6 | 2 | 7 | Moyen | Fort | Fort | now | 10 enchants utilitaires/PvE, PvP limité |
| Potions custom | 4 | Custom recipes/effects | 4 | 1 | 5 | Moyen | Moyen | Moyen | later | 6 potions utilitaires sans reach PvP |
| Outils énergie | 7 | Custom PDC + GUI | 7 | 4 | 8 | Fort | Moyen | Fort | now | Foreuse + tronçonneuse + charge |
| Agriculture custom | 6 | ItemsAdder/Oraxen ou custom blocks | 6 | 4 | 6 | Moyen/fort | Faible | Moyen | later | 4 crops + 5 foods |
| Events admin | 6 | Custom eventworld + arenas | 6 | 4 | 4 | Moyen | Moyen | Fort retention | now | Color, Spleef, Parkour |
| Events monde | 7 | Custom scheduler + world hooks | 7 | 6 | 7 | Fort | Moyen/fort | Fort | now/later | Météorite + Lune rouge |
| Boss custom | 8 | MythicMobs ou custom | 8 | 7 | 6 | Fort | Moyen | Très fort | later | 1 boss public contribution |
| Mobs custom | 6 | MythicMobs/ModelEngine optionnel | 6 | 6 | 5 | Moyen | Moyen | Moyen | later | 5 mobs sans models lourds |
| Teams light | 6 | SMPCore custom | 6 | 1 | 7 | Fort | Moyen | Fort | now | Create, bank, home, slots |
| Quêtes | 6 | Custom ou Quests/BetonQuest | 5 | 2 | 6 | Moyen | Moyen | Très fort | now | Daily/weekly/team basiques |
| Duels | 6 | SMPCore custom déjà présent | 6 | 2 | 7 | Moyen | Très fort | Moyen | now | Kit duel + Elo + forfait |
| Spawn/tuto | 5 | Custom GUI/NPC ou Citizens | 3 | 2 | 2 | Moyen | Faible | Très fort | now | Tuto 3 minutes + NPC guide |
| Logger/rollback | 8 | SMPLogger custom ou CoreProtect | 7 | 5 | 3 | Indirect | Indirect | Très fort confiance | now | Blocs/coffres/kills/éco |
| Anti-cheat | 5 | Grim/Matrix + custom rules | 5 | 3 | 4 | Indirect | Très fort | Fort confiance | now | Anti-cheat externe + alertes |
| Anti-find | 7 | Paper config + custom heuristics | 6 | 3 | 5 | Indirect | Moyen | Très fort confiance | now | Anti-xray + seed policy + rare tracker |
| Performance process | 3 | spark + Chunky + config | 2 | - | 1 | Indirect | Indirect | Très fort | now | Checklist + spark hebdo |
| Monétisation cosmetic | 4 | Tebex/Discord/resource pack | 4 | 2 | 5 | Moyen | Risque P2W si mal cadré | Moyen | later | 1 grade Saphir cosmétique |

## Références utilisées

- [PaperMC - Anti-Xray](https://docs.papermc.io/paper/anti-xray/)
- [PaperMC - Configuration](https://docs.papermc.io/paper/reference/configuration)
- [PaperMC - Commands and spark note](https://docs.papermc.io/paper/reference/commands/)
- [PurpurMC Documentation](https://purpurmc.org/docs/purpur/)
- [CoreProtect Documentation](https://docs.coreprotect.net/)
- [spark profiler](https://spark.lucko.me/)
- [Oraxen custom items docs](https://docs.oraxen.com/creating-content/items)
- [ItemsAdder crops docs](https://itemsadder.devs.beer/plugin-usage/adding-content/crops)
- [MythicMobs mechanics wiki](https://git.lumine.io/mythiccraft/MythicMobs/-/wikis/Skills/Mechanics)
- [Quests plugin](https://modrinth.com/plugin/quests)
- [Citizens NPC plugin](https://www.citizensnpcs.co/)
- [ExcellentEnchants](https://www.spigotmc.org/resources/excellentenchants-%E2%AD%90-75-vanilla-like-enchantments.61693/)
- [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi)
- [GriefPrevention docs](https://docs.griefprevention.com/)
- [Chunky pregeneration](https://modrinth.com/plugin/chunky/)
- [Grim AntiCheat](https://grim.ac/)
- [Matrix AntiCheat docs](https://matrix.rip/docs/)
- [Terralith](https://modrinth.com/datapack/terralith/)
- [Minecraft Usage Guidelines](https://www.minecraft.net/usage-guidelines)
- [PrimordialMC example SMP custom](https://www.primordialmc.com/)
