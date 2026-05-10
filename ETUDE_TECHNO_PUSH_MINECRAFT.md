# ETUDE TECHNO - JUSQU'OU POUSSER MINECRAFT JAVA/PAPER

Date : 2026-05-05.  
Objectif : comprendre jusqu'ou pousser boss custom, models 3D, ecrans immersifs, UI, audio, cinematiques et techno premium sur un serveur SMP Paper/Purpur, sans perdre la stabilite.

## 1. Verdict rapide

On peut pousser tres loin sans mods client, mais pas tout. Le vrai plafond depend de la contrainte "Java vanilla avec resource pack obligatoire" ou "client modded impose".

| Niveau | Ce qu'on peut faire | Experience joueur | Limite dure |
|---|---|---|---|
| Vanilla sans pack | Boss avec mobs vanilla, particles, titles, maps simples, GUI coffres. | Accessible a tous. | Visuellement banal. |
| Paper + resource pack obligatoire | Items 3D, armes, outils, furniture, blocs custom simules, sons custom, glyph UI, textures, models via CustomModelData/item model definitions. | Tres premium si bien art-direction. | Pas de vrais nouveaux blocs/items avec ID natif, UI client limitee. |
| Paper + display entities + interactions | Holograms modernes, objets 3D poses, panneaux 3D, faux boutons dans le monde, boss arenas scenarisees, cinematiques legeres. | Impression "moddee" sans mod client. | Performance/FPS si trop d'entites; interactions moins naturelles qu'une vraie UI. |
| MythicMobs + Model Engine + resource pack | Boss/mobs 3D animes, skills, phases, hitboxes, animations Blockbench. | Le plus proche d'un serveur RPG premium sans mods. | Production artistique + debug + perf; hitboxes restent approximatives. |
| Maps + navigateur rendu sur cartes | Ecrans plats dans le monde, images, GIFs, videos/web selon plugin. | Possible de faire des bornes, panneaux, "TV", dashboards. | Resolution map, latence, charge CPU, interaction pas aussi fluide qu'un navigateur natif. |
| Client mod Fabric/Forge/NeoForge | Vraies UI modernes, HUD custom, rendu 3D client, shaders propres, nouveaux blocs/items/entities natifs, animations GeckoLib. | Vraiment "comme un modpack". | Tous les joueurs doivent installer un client/launcher; barriere d'entree enorme. |

Ma recommandation pour ton serveur : rester Java vanilla + pack obligatoire pour le lancement, puis ajouter une option "client premium" plus tard si tu veux vraiment des UI/HUD/mods. Le meilleur ratio wow/stabilite est : Paper + resource pack + Display Entities + Oraxen/ItemsAdder + MythicMobs/Model Engine + maps pour les ecrans.

## 2. Le socle moderne : Display Entities

Depuis Minecraft 1.19.4, Java a des display entities : `item_display`, `block_display`, `text_display`. Paper expose une API propre pour les manipuler, avec transformations, rotation, scale, interpolation et billboard.

Ce que ca debloque :
- Hologrammes propres sans armor stands.
- Objets 3D poses dans le monde.
- Decorations/furniture avec item models.
- Panneaux 3D, textes flottants, leaderboards.
- Animations simples : rotation, scale, levitation, apparition.
- Faux boutons via `Interaction` entities.
- Menus dans le monde : le joueur regarde/click un bouton 3D.

Ce que ca ne fait pas :
- Pas de vraie physique custom.
- Pas de hitbox complexe native pour chaque morceau.
- Pas une vraie interface 2D client.
- Trop d'entites affichage = FPS client qui descend vite.

Usage fort pour ton serveur :
- Spawn premium avec panneaux vivants.
- Tableau event 3D.
- Boss telegraphs : zones rouges au sol, lasers, cristaux, portails.
- Borne de marche noir.
- Musee de saison.
- Outils/furniture poses en base.

## 3. Models 3D sans client mod

### Items 3D

Avec un resource pack, on peut donner a des items des models 3D Blockbench : foreuse, tronconneuse, grappin, armes boss, cle de donjon, boussoles, trophies. En 1.21.4+, Mojang a bouge vers les item model definitions et les data components, donc il faut prevoir un pipeline propre par version.

Possibilites :
- Model 3D en main.
- Model 3D pose au sol via item display.
- Textures animees `.mcmeta`.
- Variantes selon donnees item/model.
- Custom sounds attaches aux actions.
- Custom durability bar visuelle si on la mappe bien.

Limites :
- Un item custom reste un item vanilla avec skin. Pas de nouveau vrai ID natif.
- Les animations 3D d'item sont limitees sans plugin/mod.
- Les tridents/projectiles lances peuvent redevenir vanilla si pas gere custom.

### Furniture / objets poses

Oraxen et ItemsAdder peuvent utiliser display entities, item frames ou autres mecaniques pour poser des meubles 3D. Oraxen documente notamment le furniture via Display Entities + Interaction Entities.

Possibilites :
- Chaises, tables, lampes, statues, machines, stations de recharge.
- Hitbox interaction custom approximative.
- Rotation libre.
- Brightness simulee.
- Animation simple par interpolation.

Limites :
- Mieux pour decorations et blocs interactifs rares que pour blocs de construction massifs.
- Beaucoup de furniture entity-based peut baisser les FPS.
- Les vrais blocs a miner en masse doivent rester des blocs custom plus simples.

## 4. Boss custom : jusqu'ou aller

### Stack recommandee

| Besoin | Solution |
|---|---|
| Comportements boss, phases, skills | MythicMobs ou custom Java |
| Models 3D animes | Model Engine 4 + Blockbench |
| Items boss | Oraxen/ItemsAdder ou SMPCore custom items |
| Arena logic | Plugin custom SMPCore |
| Contribution rewards | Plugin custom |
| Cinematiques legeres | Display entities + camera packets/titles/sounds |
| Audio boss | Resource pack sounds ou OpenAudioMc |

### Ce qu'on peut faire en boss "premium"

- Boss geant 3D anime avec idle/walk/attack/death.
- Plusieurs phases avec changement de model/couleur/texture.
- Attaques telegraphiees au sol.
- Adds custom.
- Objectifs dans l'arene : cristaux, banniere, pylones, leviers.
- Cutscene d'entree courte.
- Bossbar custom, dialogues, sons, musique.
- Loot par contribution.
- Trophes 3D poses au spawn.
- Armes/armures boss avec skins 3D.

### Limites reelles

- Plus le model est grand/complexe, plus les clients faibles souffrent.
- Les hitboxes Minecraft restent simples. Model Engine peut gerer des bones/sub-hitboxes, mais il faut rester lisible.
- Les boss avec trop d'entites/particles tuent TPS/FPS.
- Les combats "bullet hell" en Minecraft deviennent vite illisibles.
- Si tu fais 10 boss trop ambitieux, tu vas passer plus de temps a debug qu'a ouvrir le serveur.

### Direction recommandee

Faire 3 boss vraiment propres au lieu de 15 moyens :
- Early : Gardien de la Mine, model compact, 3 attaques.
- Mid : Roi des Pillards, armee + objectifs banniere.
- Endgame : Anomalie, model glitch, arene instanciee, cutscene courte.

## 5. Vrais ecrans 3D / UI immersive

Il y a 4 familles.

### A. GUI Minecraft classique, mais maquillee

Chest GUI + resource pack + custom font/glyphs + items invisibles.  
C'est ce que beaucoup de serveurs font pour avoir des menus beaux.

Avantages :
- Stable.
- Compatible vanilla.
- Bon pour shops, quetes, settings.

Inconvenients :
- Ca reste une GUI Minecraft.
- Tooltips/cases/slots a contourner.
- Pas un vrai ecran 3D.

### B. UI dans le monde avec display entities

Tu places un "ecran" en 3D dans le monde : textes, icons, boutons, panels, animations. Les clicks passent par Interaction entities ou raytrace plugin.

Possible :
- Terminal de marche noir.
- Tableau quetes dans le spawn.
- Menu boss dans une salle.
- Leaderboard 3D.
- Carte de saison avec points cliquables.
- Boutique physique.

Tres fort pour l'immersion, mais :
- C'est une UI spatiale, pas une vraie UI client.
- Il faut gerer distance, angle, clicks, hover.
- Pas pratique pour acheter 200 items; excellent pour 5-20 actions stylisees.

### C. Ecrans maps / GIF / web browser sur cartes

Avec les APIs de maps Bukkit/Paper, on peut rendre des images sur des cartes. Des plugins comme ImageOnMap/Emage/ImageCanvas font images/GIFs; MapBrowser va plus loin avec un navigateur Chromium rendu sur maps.

Possible :
- Panneaux video/GIF.
- Ecran live d'events.
- Dashboard economie.
- Web page simplifiee affichee en jeu.
- Bornes interactives "ordinateur".

Limites :
- Resolution map faible : 128x128 par map.
- Un grand ecran = grille de maps/item frames.
- Animation/video = bande passante/CPU a surveiller.
- Interaction possible mais pas aussi naturelle qu'un vrai navigateur.

Usage recommande :
- 1 ou 2 grands ecrans au spawn, pas partout.
- Ecran event, bande-annonce, carte saison, pub patch notes.
- Pas de videos longues en boucle pour tous les joueurs.

### D. Vraie UI client modded

Avec Fabric/Forge/NeoForge, tu peux creer des vrais screens, HUDs, overlays, render 3D, panels modernes, effets post-process, minimap custom, codec reseau client/serveur.

Possible :
- Ecran full-screen premium hors Minecraft GUI.
- HUD custom argent/saphirs/quetes.
- Boss health UI animee.
- Carte 3D interactive.
- Bestiaire/quest journal moderne.
- Effects/shaders client.
- Nouveaux blocs/items/entities natifs avec GeckoLib.

Limite :
- Les joueurs doivent installer un mod/client.
- Tu perds une grosse partie de l'accessibilite SMP public.
- Il faut maintenir le client a chaque version.

Verdict : excellent pour un mode "premium client optionnel", mauvais pour MVP public.

## 6. Audio premium

Trois niveaux :

| Niveau | Possibilites | Limite |
|---|---|---|
| Resource pack sounds | Sons custom `.ogg`, musiques, boss voice lines, SFX items. | Le client doit charger le pack, pas de streaming live. |
| Paper Adventure Sound API | Jouer sons custom ou vanilla par joueur/position. | Depend du pack pour sons custom. |
| OpenAudioMc | Musiques/ambiances/voice via client web, spatial audio sans mod client. | Le joueur doit ouvrir une page web. |

Usage fort :
- Boss music.
- SFX foreuse/tronconneuse.
- Ambiance spawn/black market.
- Voix courte boss.
- Sons de saison.

Eviter :
- Musique trop forte non optionnelle.
- Audio web obligatoire pour jouer.

## 7. Cutscenes et camera

Sans mod client, les cutscenes sont faisables mais doivent rester courtes.

Possible :
- Teleporter/lock le joueur dans une position.
- Spectator camera controlee.
- Titles, subtitles, sounds.
- Display entities animees.
- Boss entrance.
- Portail qui s'ouvre.
- Camera path via plugin cinematic.

Limites :
- Les longues cutscenes frustrent.
- Les camera packets/cinematiques cassent facilement entre versions.
- Si le joueur a lag ou pack absent, l'effet tombe.

Regle : 5-12 secondes pour intro/boss, 20-40 secondes max pour final saison.

## 8. Blocs custom : vrai vs faux

| Methode | Pour | Contre |
|---|---|---|
| Note block/mushroom/chorus mechanics | Plus performant, bon pour blocs simples. | Nombre de variantes limite, contraintes textures/states. |
| Furniture display entity | Models complexes, rotation libre. | Entity-based, pas bon en masse. |
| BlockDisplay | Visuel 3D simple. | Pas vrai bloc naturel. |
| Client mod | Vrais blocs avec ID, blockstates, hitboxes, rendu custom. | Installation client obligatoire. |

Pour ton serveur :
- Ores/blocs farmes souvent : methode bloc custom simple ou vanilla texture.
- Machines/stations/decos : display/furniture.
- Ne jamais faire 10 000 blocs display dans des bases.

## 9. NPCs, dialogues, IA

Possible sans mod :
- NPC packet-based avec skins.
- Dialogues par chat, titles, menus, display text.
- NPC qui marche par waypoints.
- Quetes et shops.
- Dialog system moderne selon version/plugins.

Possible avance :
- NPC "vivant" avec LLM via Discord/web/API, mais a encadrer.
- NPC par joueur, etat different par quete.
- Cinematiques NPC.

Risques :
- Pathfinding NPC custom = bugs.
- IA LLM = cout, moderation, latence.
- Trop d'NPCs au spawn = charge visuelle.

## 10. Vehicules et montures custom

Sans mod client :
- Montures basees sur entites invisibles + model display.
- Bateaux/coffres custom.
- Minecarts custom.
- Loups/mobs montures avec model.
- Ballon dirigeable visuel, controle simplifie.

Limites :
- Physique pas aussi propre qu'un mod.
- Hitbox/collision approximatives.
- Anti-cheat doit connaitre les mouvements.

Recommandation :
- D'abord loup monture et grappin.
- Eviter vehicules trop libres avant anti-cheat/exemptions.

## 11. Resource pack pipeline

Si tu veux pousser fort, il faut un pipeline propre :

1. Choisir version cible stricte.
2. Pack obligatoire sur Survival.
3. Naming namespace propre : `smp:item/foreuse`, `smp:boss/anomaly`.
4. Blockbench comme source models.
5. Git LFS ou stockage propre pour gros assets.
6. CI qui zip le resource pack et calcule SHA-1.
7. Systeme de version pack : `pack-v001.zip`, `pack-v002.zip`.
8. Fallback si pack refuse : kick propre ou lobby sans pack.
9. Budget modele : poly/cubes/textures par asset.
10. Tests client faible.

## 12. Stack techno proposee pour toi

### MVP premium sans mod client

- Paper/Purpur stable.
- SMPCore custom pour economie/quests/items/evenements.
- Resource pack obligatoire.
- Oraxen ou ItemsAdder pour items/furniture/blocs, ou pipeline custom si tu veux tout controler.
- MythicMobs + Model Engine pour 2-3 boss/mobs premium.
- Display entities custom pour spawn/events/telegraphs.
- Map image plugin pour affiches/ecrans statiques.
- OpenAudioMc optionnel, jamais obligatoire.
- spark + profiler + budgets assets.

### Stack "on pousse plus fort"

- MapBrowser pour 1 ecran web experimental au spawn.
- PacketEvents/ProtocolLib pour UI monde per-player, NPCs, overlays limites.
- Cinematic plugin ou custom camera path.
- Boss arena instanciee.
- Pack assets versionne et genere automatiquement.

### Stack "full modded"

- Fabric client optionnel ou launcher maison.
- GeckoLib pour animations natives.
- HUD/Screens custom.
- Vrais menus 2D modernes.
- Vraies entites/blocs/items natifs.
- Plugin/server mod handshake.

Verdict : ne pas demarrer par full modded. Faire d'abord vanilla+ premium. Si le serveur grossit, proposer ensuite un "client optionnel" qui ajoute HUD/menus/optimisations, sans rendre le serveur injouable vanilla.

## 13. Idees concretes tres poussees mais realistes

| Idee | Tech | Faisabilite | Risque |
|---|---|---:|---:|
| Boss Anomalie 3D glitch | MythicMobs + Model Engine + sounds | 8/10 | 7/10 |
| Tableau events 3D interactif | Display + Interaction + GUI fallback | 9/10 | 3/10 |
| Ecran trailer au spawn | Maps/GIF ou MapBrowser | 7/10 | 6/10 |
| Marche noir terminal 3D | Display UI + NPC + shop custom | 8/10 | 4/10 |
| Forge d'enchants animee | Furniture + particles + GUI | 9/10 | 4/10 |
| Station recharge physique | Furniture + custom item charge | 9/10 | 5/10 |
| Boss telegraphs au sol | BlockDisplay/particles | 9/10 | 4/10 |
| Musee trophes 3D | ItemDisplay/furniture | 10/10 | 2/10 |
| Cinematique portail saison | Display + sounds + titles | 8/10 | 4/10 |
| HUD argent/saphirs natif | Client mod | 4/10 sans mod, 9/10 avec mod | 7/10 |
| Livre de quetes moderne | GUI pack ou client mod | 6/10 vanilla, 10/10 mod | 5/10 |
| Ecran web interactif banque | MapBrowser | 6/10 | 8/10 |
| Mobs animaux 3D | Model Engine | 8/10 | 5/10 |
| Vehicule dirigeable | Display mount custom | 5/10 | 8/10 |
| Cutscene fin saison | Custom cinematic | 7/10 | 7/10 |

## 14. Red flags techno

- Trop de Model Engine mobs en permanence.
- Trop d'item displays/furniture dans les bases.
- Videos/GIFs sur maps visibles par tout le serveur 24/7.
- UI monde obligatoire pour actions courantes sans fallback commande/GUI.
- Client mod obligatoire des le lancement.
- Pack resource trop lourd, non versionne, sans SHA propre.
- Boss avec 50 skills avant d'avoir teste 20 joueurs.
- Particles chaque tick sur beaucoup d'entites.
- ProtocolLib/NMS trop central sans tests version.
- Ecrans web en jeu pour features vitales.

## 15. Decision claire

Si le but est "wow premium mais accessible" :
- Oui aux boss 3D animes.
- Oui aux models 3D items/furniture.
- Oui aux ecrans 3D dans le monde pour spawn/events/boss.
- Oui aux maps/GIFs ponctuels.
- Oui aux sons custom.
- Non a une vraie UI client moderne sans mod.
- Non au full modded obligatoire au lancement.

La meilleure strategie est hybride : faire croire au joueur qu'il est sur un serveur presque modde, tout en restant compatible Java vanilla avec pack. C'est exactement la zone ou la techno actuelle est la plus forte.

## Sources

- PaperMC Display Entities : https://docs.papermc.io/paper/dev/display-entities/
- PaperMC Resource Packs : https://docs.papermc.io/adventure/resource-pack
- PaperMC Data Components : https://docs.papermc.io/paper/dev/data-component-api/
- PaperMC Sound API : https://docs.papermc.io/adventure/sound/
- Oraxen Display Entity Furniture : https://docs.oraxen.com/mechanics/furniture-mechanic/display_entity_furniture
- Oraxen Furniture : https://docs.oraxen.com/creating-content/furniture
- Oraxen Blocks : https://docs.oraxen.com/creating-content/blocks
- Model Engine 4 Creating a Model : https://git.mythiccraft.io/mythiccraft/model-engine-4/-/wikis/Modeling/Creating-a-Model
- MythicMobs mechanics : https://git.lumine.io/mythiccraft/MythicMobs/-/wikis/Skills/Mechanics
- Spigot MapRenderer API : https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/map/MapRenderer.html
- ImageOnMap : https://dev.curseforge.com/projects/imageonmap
- Emage : https://modrinth.com/plugin/emage
- MapBrowser : https://modrinth.com/plugin/mapbrowser
- ProtocolLib : https://protocollib.org/
- Fabric Custom Screens : https://docs.fabricmc.net/develop/rendering/gui/custom-screens
- Forge Screens : https://docs.minecraftforge.net/en/1.19.x/gui/screens/
- GeckoLib Wiki : https://wiki.geckolib.com/
- OpenAudioMc : https://docs.openaudiomc.net/
