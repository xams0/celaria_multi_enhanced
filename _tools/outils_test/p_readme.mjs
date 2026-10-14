import { patch } from "./wsrep.mjs";
const F = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/README.md";
patch(F, [
[`**Départ synchronisé façon Trackmania** (serveur Coupe)
- L'horloge du mod est synchronisée avec celle du serveur (échanges horodatés, échantillon au plus petit aller-retour).
- Le serveur programme le départ à un instant T ; le mod débloque le joueur exactement à T, quel que soit le ping.
- Grand compte à rebours « 3 – 2 – 1 – GO! » au centre de l'écran, calé sur T.
- Les autres joueurs sont affichés « au présent » : à chaque mise à jour réseau, leur position est avancée de
  vitesse × retard estimé (retard calculé par le serveur à partir des pings), puis l'extrapolation du jeu continue.`,
`**Départ synchronisé façon Trackmania** (serveur Coupe)
- L'horloge du mod est synchronisée avec celle du serveur (échanges horodatés, échantillon au plus petit aller-retour).
- Le serveur programme le départ (Go) à un instant T. Un seul compte à rebours : celui du jeu (3, 2, 1, Go!). Le mod le
  lance à T − 3 s et le garde calé sur l'horloge du serveur : le chrono passe à 0 à T pour tout le monde (mesuré : à une
  image près). Le compte à rebours est forcé à 3 s (le réglage « compte à rebours initial » de chaque joueur est rétabli
  après le départ). Seule la partie négative du chrono est ajustée, jamais le temps de course.
- Les autres joueurs sont affichés au même temps de course que le joueur local : le mod ajoute son temps de course à ses
  paquets de position, le serveur le transmet (estimé avec le ping pour les joueurs sans mod) et chaque position reçue est
  avancée de vitesse × (mon temps de course − son temps de course).
- Le jeu lisse l'affichage des autres joueurs de 8 % par pas (≈ 115 ms de retard visuel) : le mod les affiche à leur
  position réelle et ne lisse que les petites corrections réseau.`],
[`**Spectateur** (quand on ne court pas : en attente du départ, éliminé après un reset total, fin de manche)
- \`V\` : vue suivante (1re personne → 3e personne → caméra libre → désactivé)
- \`←\` / \`→\` : joueur suivi
- Caméra libre : \`Z/W\` \`Q/A\` \`S\` \`D\` (direction de la souris), \`Espace\` / \`C\` monter / descendre, \`Maj\` rapide`,
`**Spectateur** (quand on ne court pas : arrivée, reset total, vainqueur de la coupe, observateur, arrivé en cours de manche)
- Automatique : dès qu'on ne court plus et que d'autres joueurs courent, vue 3e personne sur un joueur au hasard
  (l'écran d'arrivée du jeu est fermé après 2,5 s). Quand le joueur suivi termine, un autre est choisi.
- Une nouvelle manche (départ synchronisé) quitte le mode spectateur.
- Nom du joueur suivi au-dessus du chrono ; le chrono affiche son temps de course (ou son temps d'arrivée).
- Le rendu (culling, niveau de détail, ombres, effets) est calculé depuis la caméra spectateur ; le modèle du joueur local
  n'est pas dessiné.
- \`V\` : vue suivante (1re personne → 3e personne → caméra libre → désactivé ; désactivé = plus de vue automatique jusqu'à
  la manche suivante)
- \`←\` / \`→\` : joueur suivi
- Caméra libre : \`Z/W\` \`Q/A\` \`S\` \`D\` (direction de la souris), \`Espace\` / \`C\` monter / descendre, \`Maj\` rapide

**Mode observateur (caster)** : bouton « Observer » / « Play » du menu Échap (ou \`/spec\` et \`/play\` dans le chat).
L'observateur ne court pas, n'est pas classé et regarde la coupe en spectateur.

**Interface Coupe**
- En haut à gauche : manche en cours sur la carte (« ROUND 2 / 5 »).
- En bas à droite : classement aux checkpoints de la manche (position, joueur, dernier CP, temps ou écart avec le meilleur
  au même CP ; arrivés en tête).
- Classement plus large ; badge rouge autour de « WINNER », jaune autour de « FINALIST ».`],
[`| client → serveur | TCP 206 | u32 : horloge du client (synchronisation) |`,
 `| client → serveur | TCP 206 | u32 : horloge du client (synchronisation) |
| client → serveur | TCP 207 | u32 : temps du checkpoint en ms (envoyé juste avant le paquet 202) |
| client → serveur | TCP 208 | u8 : mode observateur (1) / joueur (0) |
| client → serveur | UDP 1 + 5 octets | \`R\` + u32 temps de course (ms) de la position (\`N\` si inconnu) |
| serveur → client | UDP 1, après les données du jeu | [id u8, temps de course u32] × n, n u8, \`RX\` |
| serveur → client | chat \`<SOH>CUPROUND <manche> <manches> <en cours> <points> <départ>\` | manche en cours |
| serveur → client | chat \`<SOH>CUPST <id>:<état>[:<ms>] ...\` | état des joueurs (R course, F arrivé, O reset, W vainqueur, V observateur, N hors manche) |
| serveur → client | chat \`<SOH>CUPCP <id> <cp> <ms>\` | passage à un checkpoint |`],
[`| serveur → client | chat \`<SOH>CUPSTART <heure serveur> <ticks>\` | départ programmé de la manche |`,
 `| serveur → client | chat \`<SOH>CUPSTART <heure serveur> <ticks>\` | heure du Go de la manche (les vainqueurs et observateurs ne le reçoivent pas) |`],
]);
