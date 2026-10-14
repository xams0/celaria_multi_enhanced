# Celaria Lanppart3 (launcher)

Un seul fichier : `out/Celaria Lanppart3.exe` (~32 Mo). Il contient Java (runtime réduit), le launcher, le serveur
Coupe / Time Attack, le mod (DLL) et les maps de base. À copier sur chaque PC de la LAN, rien à installer.

## Utilisation

- **JOUER** : le jeu est détecté tout seul (bibliothèques Steam, sinon « Choisir Celaria.exe »). « JOUER » lance Celaria
  avec le mod. Le mod est chargé dans le processus du jeu au démarrage : **aucun fichier n'est copié dans le dossier du jeu**.
  Steam doit tourner (le launcher le démarre si besoin).
- **Serveurs LAN** : les serveurs Lanppart3 du réseau (LAN, Radmin VPN, Hamachi...) avec mode, map et joueurs.
  « REJOINDRE » (ou double-clic) lance le jeu et s'y connecte tout seul. Direct-IP pour une adresse précise.
- **SERVEUR** : mode Coupe / Time Attack et tous les réglages (server.conf), démarrer / arrêter. Serveur lancé :
  redémarrer, relancer la map, map suivante, changer de map, console (commandes, `say <texte>` pour écrire aux joueurs),
  « JOUER SUR MON SERVEUR ». Au premier lancement du serveur, Windows demande l'accès au réseau pour Java : **Autoriser**
  (sinon les autres PC ne peuvent pas rejoindre).
- **MAPS** : maps du serveur (rotation par ordre alphabétique), ajouter des `.cmap`, importer ses maps de l'éditeur
  (`%LOCALAPPDATA%\Celaria\custom_maps`), supprimer. Pris en compte au prochain changement de map.

Si l'ancienne version du mod (winExt.dll remplacé, `winExt_orig.dll` présent) est dans le dossier du jeu, le launcher
propose de la retirer (restaure le winExt.dll d'origine).

## Dossiers

- `%LOCALAPPDATA%\Lanppart3\app\runtime` : fichiers extraits de l'exe (une fois par version). Log du mod :
  `runtime\mod\celaria_mod.log`.
- `%APPDATA%\Lanppart3` : réglages du launcher, `server\` (server.conf, maps, records.json, ghosts des bots).

## Fonctionnement

- `stub/lanppart3.c` : l'exe natif (i686). Sans argument : extrait la charge utile (compressée LZMS, `payload.h`) puis
  lance `jre\bin\javaw.exe -jar launcher.jar`. `--inject <Celaria.exe> <dll>` : crée le jeu suspendu (avec
  `SteamAppId=1081650`, `-nosteamrestart`), charge la DLL par une APC du thread principal (avant tout code du jeu), reprend
  le jeu et vérifie que la DLL est chargée. `LP3_CONNECT=adresse:port` est transmis au mod (connexion directe).
- `java/src/lp3` : le launcher (Swing, thème Celaria). Le serveur est lancé avec `--managed` (console sur stdout,
  commandes sur stdin, `@@STATUS` / `@@MAPS` pour l'état).
- La DLL injectée est `CelariaMod/out/lanppart3.dll` (même code que le proxy, compilé avec `-DMOD_INJECTED`).

## Compiler

1. `CelariaMod/build.sh` (mod), `_tools/build_server.sh` (serveur)
2. `Lanppart3Launcher/build.sh` → `out/Celaria Lanppart3.exe`
