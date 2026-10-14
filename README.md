# celaria_multi_enhanced

Updated Celaria Multiplayer Mode (Cup, Time Attack, Launcher, Spectator Mode and more) — made for the Lanppart3 LAN parties.

**Download: [Celaria Lanppart3.exe](https://github.com/xams0/celaria_multi_enhanced/releases/latest)** (one file, nothing to install; the launcher updates itself from the releases of this repository).

## Français

Mode multijoueur amélioré pour [Celaria](https://store.steampowered.com/app/1081650/Celaria/) en LAN (et réseaux virtuels Radmin VPN / Hamachi) :

- **Launcher « Celaria Lanppart3 »** : lance le jeu avec le mod sans rien copier dans le dossier du jeu, navigateur de serveurs LAN
  (rejoindre en un clic), serveur Coupe / Time Attack avec tous les réglages et sa gestion (redémarrer, relancer / changer de map,
  console), gestion des maps, français / anglais, patch notes, mises à jour automatiques.
- **Mode Coupe** (règles Trackmania) : points, finalistes, vainqueurs, warmup, départ synchronisé, respawn sans remise à zéro du
  chrono, restart = DNF.
- **Mod client « Lanppart3 Mod »** (actif uniquement sur un serveur du réseau local) : temps au millième, écart avec le 1er à chaque
  checkpoint, spectateur (1re / 3e personne, caméra libre), observateur, classement aux checkpoints, records du serveur, bots.

Le jeu officiel (Steam) est nécessaire. Les joueurs sans le mod peuvent rejoindre un serveur Lanppart3 (sans les ajouts d'affichage).

## English

Enhanced multiplayer for [Celaria](https://store.steampowered.com/app/1081650/Celaria/) on a LAN (and virtual LANs like Radmin VPN / Hamachi):

- **"Celaria Lanppart3" launcher**: starts the game with the mod without copying anything into the game folder, LAN server browser
  (one click join), Cup / Time Attack server with every setting and its management (restart, restart / change map, console),
  maps management, French / English, patch notes, automatic updates.
- **Cup mode** (Trackmania rules): points, finalists, winners, warmup, synchronized start, respawn without timer reset, restart = DNF.
- **"Lanppart3 Mod" client mod** (only active on a server of the local network): millisecond times, gap with the first player at every
  checkpoint, spectator (first / third person, free camera), observer, checkpoint ranking, server records, bots.

The official game (Steam) is required. Players without the mod can join a Lanppart3 server (without the display additions).

## Projects

| Folder | Content |
| --- | --- |
| `Lanppart3Launcher/` | Launcher (Java / Swing) and native stub (C: extraction, injection of the mod) — see its README |
| `CelariaMod/` | Client mod (C, 32-bit DLL hooking the GameMaker YYC code of `Celaria.exe`) — see its README |
| `Celaria-Server-main/` | Modified Celaria server (Java): Cup mode, time attack additions, LAN discovery, headless console |
| `REVERSE_NOTES.md` | Reverse engineering notes of the game (addresses, variables, packets) |
| `_tools/` | Build script of the server, test scripts |

## Build and release

1. `CelariaMod/build.sh` (llvm-mingw i686), `_tools/build_server.sh` (JDK 21)
2. Set the version in `Lanppart3Launcher/VERSION` (e.g. `2.0.1`), add the changes in `Lanppart3Launcher/java/src/lp3/PatchNotesPage.java`
3. `Lanppart3Launcher/build.sh` → `Lanppart3Launcher/out/Celaria Lanppart3.exe`
4. GitHub release with the tag `v2.0.1` and the exe as asset: the launchers of the players propose the update at their next start.
