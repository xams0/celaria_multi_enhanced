import { patch } from "./wsrep.mjs";
const R = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/";
patch(R + "CelariaMod/README.md", [
[`**Interface Coupe**`, `**Navigateur de serveurs LAN** (menu Multiplayer : « Server Browser Online » = le navigateur d'origine, « Server Browser LAN »)
- Le navigateur LAN ne contacte pas le serveur maître : le mod envoie une recherche (UDP 26510) sur l'adresse de diffusion
  de chaque réseau local du PC (box, Radmin VPN, Hamachi, ZeroTier…), sur 255.255.255.255 et sur 127.0.0.1.
  Les serveurs Coupe répondent ; la liste, la sélection et « Connect » sont celles du jeu. « Refresh » relance la recherche.
- Ne trouve pas les réseaux sans diffusion (Tailscale) : utiliser Direct-IP.

**Interface Coupe**`],
[`| client → serveur | TCP 209 |`, `| client → serveurs du LAN | UDP 26510 (diffusion) \`CCPD\` | recherche des serveurs (navigateur LAN) |
| serveur → client | UDP \`CSPD|port|joueurs|max|protocole|mot de passe|nom\` | réponse à la recherche |
| client → serveur | TCP 209 |`],
]);
patch(R + "Celaria-Server-main/README.md", [[`Bots (cup mode):`, `LAN discovery: the server answers the "Server Browser LAN" of the cup mod on UDP port 26510 (all the local networks:
LAN, Radmin VPN, Hamachi...). Several servers can run on the same computer.

Bots (cup mode):`]]);
patch(R + "REVERSE_NOTES.md", [[`- Moi : \`obj_network_handler.player_id_server\` (1027).`, `- Navigateur de serveurs : \`menu_multiplayer\` → \`scr_create_button_icon_menu(x, y, w, h, texte, icône, script, id, échelle,
  échelle)\` (\`0x4d3920\`) ; « Server-Browser » = script 509 \`bev_multiplayer_serverbrowser\`, icône 155 ; « Direct-IP » =
  script 508, icône 156. Le navigateur crée \`obj_http_serverList\` (objet 160) : user event 0 = \`http_post_string\`
  (getServerList.php), événement HTTP (\`0xa8ea60\`) via \`cHttp_is_responding/get_response_status/get_response/
  message_is_ok\` ; JSON \`{"version", "totalServers", "servers": [[ip, nom, port, joueurs, max, version, mot de passe, ?]]}\`.
  Sélection → \`selected_ip/selected_port\` → \`bev_serverbrowser_connect\` (\`server_adress\`, \`server_port\`).
  Le bouton LAN utilise le script 850 \`bev_guiE_test\` (inutilisé) détourné par le mod.
- Moi : \`obj_network_handler.player_id_server\` (1027).`]]);
