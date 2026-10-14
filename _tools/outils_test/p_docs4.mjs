import { patch } from "./wsrep.mjs";
const R = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/";
patch(R + "CelariaMod/README.md", [
[`**Mode observateur (caster)** : bouton « Observer » / « Play » du menu Échap (ou \`/spec\` et \`/play\` dans le chat).`,
 `**Mode observateur (caster)** : bouton « Spectator » / « Play » du menu Échap (ou \`/spec\` et \`/play\` dans le chat).
Le menu se ferme et « You are now a Spectator » s'affiche au centre de l'écran ; la vue spectateur démarre aussitôt.`],
[`- La souris est lue par le mod (Raw Input de la fenêtre) : la caméra du jeu ne la suit plus quand on ne court pas.`,
 `- La souris est lue par le mod (Raw Input de la fenêtre) : la caméra du jeu ne la suit plus quand on ne court pas.
- Le compteur de vitesse affiche la vitesse du joueur suivi.`],
]);
patch(R + "REVERSE_NOTES.md", [[`- Moi : \`obj_network_handler.player_id_server\` (1027).`, `- Compteur de vitesse : \`scr_draw_gui\` affiche \`string(obj_player._speed)\` (var 64 = √(_xspeed² + _yspeed²) par pas),
  masqué si \`obj_controller.gui_elements_map_end_created\` (624). Menu : « Resume » = script 330 \`bev_resume_game\` (\`0x53a890\`).
- Moi : \`obj_network_handler.player_id_server\` (1027).`]]);
