# Celaria Cup Mod (client)

Mod client pour Celaria (Steam, `Celaria.exe` sha256 `68ef6866…f269`), à utiliser avec le serveur Coupe (`Celaria-Server-main`).
Il se charge à la place de `winExt.dll` (l'original est renommé `winExt_orig.dll` et reçoit tous les appels).
Il vérifie la version de l'exécutable et se désactive tout seul s'il ne la reconnaît pas.

## Protection : actif uniquement en LAN

Le mod ne fait **rien** (le jeu fonctionne exactement comme sans mod) sauf en partie multijoueur sur un serveur du réseau
local. Au moment où le jeu se connecte à un serveur, le mod lit la table des connexions TCP de Windows : il ne s'active
que si la connexion du jeu vers le port du serveur va vers une adresse locale :
- une adresse privée (127.x, 10.x, 172.16–31.x, 192.168.x, 169.254.x, 100.64–127.x pour Tailscale, ::1, fe80::/10, fc00::/7) ;
- ou une adresse du sous-réseau d’une carte réseau active du PC : réseaux locaux virtuels comme **Radmin VPN** (26.x.x.x),
  Hamachi (25.x.x.x) ou ZeroTier. Le log indique alors « server on the subnet of the adapter Radmin VPN ».

Un serveur sur Internet reste refusé, même à travers un VPN classique (NordVPN…) : son adresse n’est sur aucun sous-réseau local. Serveur sur Internet, solo, menus, ou erreur de lecture → mod inactif :
aucun paquet ajouté (204/205/206), aucun affichage au millième, pas de spectateur, d'onglet Records ni de blocage Steam.
Il se désactive à la déconnexion.

Étiquette **« Lanppart3 Mod »** en bas à gauche : blanche = mod chargé mais inactif, verte = mod actif (serveur LAN).

## V2 (Celaria Lanppart3)

- **Launcher** `Celaria Lanppart3.exe` (dossier `Lanppart3Launcher/`) : le mod est chargé dans le jeu au lancement
  (injection), **rien n'est copié dans le dossier du jeu**. `install.bat` (proxy winExt.dll) reste possible mais n'est plus
  nécessaire ; le launcher propose de retirer cette ancienne installation.
- **Coupe et Time Attack** : toutes les fonctions marchent dans les deux modes (spectateur, observateur, classement aux
  checkpoints, écart au 1er, temps au millième, navigateur LAN...).
- **Vue 1re personne du spectateur** fluide : la caméra du joueur suivi est interpolée (positions horodatées par son temps
  de course, lecture ~55 ms dans le passé) et le serveur relaie les positions 60 fois par seconde.
- **Écart au 1er** au milieu de l'écran (comme Trackmania) à chaque checkpoint et à l'arrivée : position + temps, puis
  l'écart (rouge = en retard, bleu = en avance). Coupe : temps de la manche. Time Attack : meilleurs runs de la map
  (le sien compris : rythme du record). Aussi pour le joueur suivi en spectateur.
- **Respawn** (touche R) : retour au dernier checkpoint, ou au départ s'il n'y en a pas, **le chrono continue**
  (`scr_player_respawn_forcestart`). Plus de DNF (réglable : `cup_respawnAtStartDNF`). **Restart** (Retour arrière) =
  DNF en Coupe (`cup_forceRestartDNF`, paquet 213). Pas de DNF pendant le warmup.
- **Warmup** au début de chaque map : boîte jaune « WARMUP » à côté de « ROUND x / y » (Time Attack : temps restant).
- **Fin de manche** : plus de fondu blanc (`scr_render_white_overlay_screen` n'est plus dessiné), délais réduits côté serveur.
- **Connexion directe** : `LP3_CONNECT=adresse:port` (posé par le launcher) → après le menu principal, le mod fait
  « Multiplayer » puis la connexion Direct-IP.

## V2.1

- Les autres joueurs sont dessinés entre deux pas de simulation (facteur d'interpolation du jeu `get_render_delta`) :
  plus de saccade quand ils sont proches (le jeu ne les déplace qu'à 100 Hz, la caméra est interpolée à chaque image).
- Compte à rebours : le jeu n'affiche 3, 2, 1 que si son chrono vaut exactement -300, -200, -100 ; le recalage sur
  l'horloge du serveur ne saute plus ces valeurs.
- Écart avec le 1er à chaque checkpoint : affiché à la réception du message `CUPCP` du serveur (numéro du checkpoint).
- Spectateur : uniquement par le bouton Spectator du menu Échap (ou automatique après l'arrivée / un DNF en Coupe) ; V
  change de vue entre 1re personne, 3e personne et caméra libre ; « Waiting for server... » masqué en spectateur.
- Warmup au temps : boîte « WARMUP m:ss » en Coupe aussi.

## Installation

- `install.bat` (par défaut dans `D:\SteamLibrary\steamapps\common\Celaria`, sinon `install.bat "chemin\vers\Celaria"`)
- `uninstall.bat` pour revenir au jeu d'origine (ou « Vérifier l'intégrité des fichiers » dans Steam)
- Log : `celaria_mod.log` à côté de `Celaria.exe`
- Recompiler : `build.sh` (llvm-mingw i686, dans `_tools`)

## Fonctions

Toutes les fonctions ci-dessous ne sont actives que sur un serveur LAN (voir plus haut).

**Temps au millième (affichage)**
- Chrono en mm:ss:mmm. Le chrono en course défile au millième ; les temps de checkpoint et d'arrivée sont exacts :
  l'instant du franchissement est calculé entre deux pas de simulation (le jeu simule à 100 Hz) en réutilisant le test
  de collision du jeu.
- Le temps **officiel** du jeu n'est jamais modifié : records locaux, replays, classements Steam et paquet d'arrivée
  gardent la même valeur qu'un client sans mod (aucun avantage).
- Sur un serveur Coupe : le temps en ms est envoyé au serveur (paquet 204) et le classement affiche les ms de tous les joueurs.

**Départ synchronisé façon Trackmania** (serveur Coupe)
- L'horloge du mod est synchronisée avec celle du serveur (échanges horodatés, échantillon au plus petit aller-retour).
- Le serveur programme le départ (Go) à un instant T. Un seul compte à rebours : celui du jeu (3, 2, 1, Go!). Le mod le
  lance à T − 3 s et le garde calé sur l'horloge du serveur : le chrono passe à 0 à T pour tout le monde (mesuré : à une
  image près). Le compte à rebours est forcé à 3 s (le réglage « compte à rebours initial » de chaque joueur est rétabli
  après le départ). Seule la partie négative du chrono est ajustée, jamais le temps de course.
- Les autres joueurs sont affichés au même temps de course que le joueur local : le mod ajoute son temps de course à ses
  paquets de position, le serveur le transmet (estimé avec le ping pour les joueurs sans mod) et chaque position reçue est
  avancée de vitesse × (mon temps de course − son temps de course).
- Le jeu lisse l'affichage des autres joueurs de 8 % par pas (≈ 115 ms de retard visuel) : le mod les affiche à leur
  position réelle et ne lisse que les petites corrections réseau.

**Serveur Coupe** (le serveur répond à l'annonce du mod)
- Aucun envoi vers Steam pendant la session (scores de classement, XP multijoueur).
- Onglet « Records » (à la place de « Friends ») : records du serveur sur la carte, au millième (`records.json` du serveur).

**Spectateur** (quand on ne court pas : arrivée, reset total, vainqueur de la coupe, observateur, arrivé en cours de manche)
- Automatique : 5 s après qu'on ne court plus (arrivée…) et si d'autres joueurs courent, vue 1re personne sur un joueur
  au hasard (l'écran d'arrivée du jeu est fermé). Quand le joueur suivi termine, un autre est choisi.
- 1re personne : la caméra du joueur suivi (lacet et tangage envoyés par son mod ; sans mod, la direction de son corps).
- 3e personne : derrière le joueur suivi avec du recul, la souris tourne autour.
- Caméra libre : souris pour regarder, ZQSD/WASD pour se déplacer.
- La souris est lue par le mod (Raw Input de la fenêtre) : la caméra du jeu ne la suit plus quand on ne court pas.
- Le compteur de vitesse affiche la vitesse du joueur suivi.
- Une nouvelle manche (départ synchronisé) quitte le mode spectateur.
- Nom du joueur suivi au-dessus du chrono ; le chrono affiche son temps de course (ou son temps d'arrivée).
- Le rendu (culling, niveau de détail, ombres, effets) est calculé depuis la caméra spectateur ; le modèle du joueur local
  n'est pas dessiné.
- `V` : vue suivante (1re personne → 3e personne → caméra libre → désactivé ; désactivé = plus de vue automatique jusqu'à
  la manche suivante)
- `←` / `→` : joueur suivi
- Caméra libre : `Z/W` `Q/A` `S` `D` (direction de la souris), `Espace` / `C` monter / descendre, `Maj` rapide

**Mode observateur (caster)** : bouton « Spectator » / « Play » du menu Échap (ou `/spec` et `/play` dans le chat).
Le menu se ferme et « You are now a Spectator » s'affiche au centre de l'écran ; la vue spectateur démarre aussitôt.
L'observateur ne court pas, n'est pas classé (« SPECTATE » devant son nom, en bas du classement) et regarde la coupe en spectateur.

**Navigateur de serveurs LAN** (menu Multiplayer : « Server Browser Online » = le navigateur d'origine, « Server Browser LAN »)
- Le navigateur LAN ne contacte pas le serveur maître : le mod envoie une recherche (UDP 26510) sur l'adresse de diffusion
  de chaque réseau local du PC (box, Radmin VPN, Hamachi, ZeroTier…), sur 255.255.255.255 et sur 127.0.0.1.
  Les serveurs Coupe répondent ; la liste, la sélection et « Connect » sont celles du jeu. « Refresh » relance la recherche.
- Ne trouve pas les réseaux sans diffusion (Tailscale) : utiliser Direct-IP.

**Interface Coupe**
- En haut à gauche : manche en cours sur la carte (« ROUND 2 / 5 »).
- En bas à droite : classement aux checkpoints de la manche (position, joueur, dernier CP, temps ou écart avec le meilleur
  au même CP ; arrivés en tête).
- Classement plus large ; badge rouge autour de « WINNER », jaune autour de « FINALIST ».

## Protocole ajouté (ignoré par les serveurs officiels)

V2 : client → serveur TCP 212 (u8, le chrono du run est reparti : Time Attack), TCP 213 (u8, touche Restart = DNF) ;
serveur → client `<SOH>CUPWU <actif> <n°> <nombre> <ms restantes|-1>` (warmup), `<SOH>CUPPB <id> <arrivée> <cp1> ...`
(meilleur run Time Attack, `CUPPB CLEAR` = nouvelle map), `<SOH>CUPCP <id> 0 0` (run reparti, Time Attack).
Les entrées UDP `SX` existent dans les deux modes (temps de course `0xFFFFFFFF` = inconnu, la caméra reste valable).

| Sens | Paquet | Contenu |
| --- | --- | --- |
| client → serveur | TCP 204 | u32 : temps d'arrivée en ms (envoyé juste avant le paquet 203) |
| client → serveur | TCP 205 | u8 : version du mod |
| client → serveur | TCP 120 type 2 | demande des records du serveur (onglet Records) |
| client → serveur | TCP 206 | u32 : horloge du client (synchronisation) |
| client → serveur | TCP 207 | u32 : temps du checkpoint en ms (envoyé juste avant le paquet 202) |
| client → serveur | TCP 208 | u8 : mode observateur (1) / joueur (0) |
| client → serveurs du LAN | UDP 26510 (diffusion) `CCPD` | recherche des serveurs (navigateur LAN) |
| serveur → client | UDP `CSPD|port|joueurs|max|protocole|mot de passe|nom` | réponse à la recherche |
| client → serveur | TCP 209 | u8 : carte chargée et affichée (fondu terminé) : le serveur attend ce message avant le départ |
| client → serveur | UDP 1 + 9 octets | `R` + u32 temps de course (ms) de la position (`N` si inconnu) + u16 lacet + s16 tangage de la caméra |
| serveur → client | UDP 1, après les données du jeu | [id u8, temps de course u32, lacet u16, tangage s16] × n, n u8, `SX` |
| serveur → client | chat `<SOH>CUPROUND <manche> <manches> <en cours> <points> <départ>` | manche en cours |
| serveur → client | chat `<SOH>CUPST <id>:<état>[:<ms>] ...` | état des joueurs (R course, F arrivé, O reset, W vainqueur, V observateur, N hors manche) |
| serveur → client | chat `<SOH>CUPCP <id> <cp> <ms>` | passage à un checkpoint |
| serveur → client | chat `<SOH>CUPCLK <client> <réception> <envoi>` | réponse de synchronisation (heures serveur, ms) |
| serveur → client | chat `<SOH>CUPSTART <heure serveur> <ticks>` | heure du Go de la manche (les vainqueurs et observateurs ne le reçoivent pas) |
| serveur → client | chat `<SOH>CUPLAG <id>:<ms> ...` | retard estimé de la position de chaque adversaire |
| serveur → client | chat `<SOH>CUPMOD` | le serveur gère le mod (message masqué) |
| serveur → client | chat `<SOH>CUPMS <ticks> <ms> <nom>` | millièmes d'une entrée du classement (masqué) |

Les messages cachés commencent par le caractère SOH (0x01), noté `<SOH>`.
