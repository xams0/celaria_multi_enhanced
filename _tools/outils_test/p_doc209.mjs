import { patch } from "./wsrep.mjs";
const R = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/";
patch(R + "CelariaMod/README.md", [[`| client → serveur | TCP 208 | u8 : mode observateur (1) / joueur (0) |`, `| client → serveur | TCP 208 | u8 : mode observateur (1) / joueur (0) |
| client → serveur | TCP 209 | u8 : carte chargée et affichée (fondu terminé) : le serveur attend ce message avant le départ |`]]);
patch(R + "REVERSE_NOTES.md", [[`- Moi : \`obj_network_handler.player_id_server\` (1027).`, `- Compte à rebours visible seulement si \`obj 0x16.mload_complete\` (895) = 1 et \`obj_controller.map_load_alpha_overlay\`
  (823) = 0 (fondu après le chargement) : sinon \`step_timer\` saute tout le bloc (chiffres, sons). Le mod envoie 209 quand
  les deux sont vrais (le serveur attend ce signal) et termine le fondu au lancement du compte à rebours.
- Moi : \`obj_network_handler.player_id_server\` (1027).`]]);
