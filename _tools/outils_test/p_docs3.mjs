import { patch } from "./wsrep.mjs";
const R = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/";
patch(R + "CelariaMod/README.md", [
[`- Automatique : dès qu'on ne court plus et que d'autres joueurs courent, vue 3e personne sur un joueur au hasard
  (l'écran d'arrivée du jeu est fermé après 2,5 s). Quand le joueur suivi termine, un autre est choisi.`,
`- Automatique : 5 s après qu'on ne court plus (arrivée…) et si d'autres joueurs courent, vue 1re personne sur un joueur
  au hasard (l'écran d'arrivée du jeu est fermé). Quand le joueur suivi termine, un autre est choisi.
- 1re personne : la caméra du joueur suivi (lacet et tangage envoyés par son mod ; sans mod, la direction de son corps).
- 3e personne : derrière le joueur suivi avec du recul, la souris tourne autour.
- Caméra libre : souris pour regarder, ZQSD/WASD pour se déplacer.
- La souris est lue par le mod (Raw Input de la fenêtre) : la caméra du jeu ne la suit plus quand on ne court pas.`],
[`L'observateur ne court pas, n'est pas classé et regarde la coupe en spectateur.`,
 `L'observateur ne court pas, n'est pas classé (« SPECTATE » devant son nom, en bas du classement) et regarde la coupe en spectateur.`],
[`| client → serveur | UDP 1 + 5 octets | \`R\` + u32 temps de course (ms) de la position (\`N\` si inconnu) |
| serveur → client | UDP 1, après les données du jeu | [id u8, temps de course u32] × n, n u8, \`RX\` |`,
 `| client → serveur | UDP 1 + 9 octets | \`R\` + u32 temps de course (ms) de la position (\`N\` si inconnu) + u16 lacet + s16 tangage de la caméra |
| serveur → client | UDP 1, après les données du jeu | [id u8, temps de course u32, lacet u16, tangage s16] × n, n u8, \`SX\` |`],
]);
patch(R + "Celaria-Server-main/README.md", [
[`Chat commands: \`/cup\` (or \`/points\`) shows the cup ranking, \`/spec\` / \`/play\` observer mode,`,
 `Bots (cup mode): the server records the runs of the players and keeps the best run of every map (\`ghosts/<map>.json\`).
\`/bots <count>\` (or \`cup_bots = <count>\` in \`server.conf\`) connects bots ("Bot 1", "Bot 2"...) which replay this run at
every round, a bit slower (1 %, 2.5 %, 4 %...): useful to test the spectator mode alone. Finish the map once first.

Chat commands: \`/cup\` (or \`/points\`) shows the cup ranking, \`/spec\` / \`/play\` observer mode, \`/bots <count>\` bots (0 = none),`],
[`| \`cup_roundTimeLimit\` | \`300\` | Maximum length of a round in seconds |`,
 `| \`cup_roundTimeLimit\` | \`300\` | Maximum length of a round in seconds |
| \`cup_bots\` | \`0\` | Bots connected when the server starts (they replay the best recorded run of the map) |`],
]);
patch(R + "REVERSE_NOTES.md", [[`- Moi : \`obj_network_handler.player_id_server\` (1027).`, `- Caméra du jeu (obj 0) : \`xfrom/yfrom/zfrom\` = œil, \`xto/yto/zto\` = vecteur direction unitaire (pas un point),
  \`mouse_direction\` (934) = − lacet monde (degrés), \`z_angle\` (1751) = tangage (haut > 0), \`forward_offset\` (570).
  Souris vers la droite → lacet monde croissant ; vecteur à droite du lacet a = (−sin a, cos a). Rotation UDP du joueur =
  \`obj_player.main_direction\` (direction du corps, suit \`mouse_direction\` avec retard).
- winExt.dll : \`registerMouse(hwnd)\` enregistre le Raw Input (RIDEV_INPUTSINK) et sous-classe la fenêtre ; \`update()\`
  copie et remet à zéro les cumuls, \`getRegisteredMouseMovementX/Y\` les renvoie.
- Moi : \`obj_network_handler.player_id_server\` (1027).`]]);
