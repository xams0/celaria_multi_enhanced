# Celaria — notes de reverse du client (pour le mode Coupe)

## Binaire
- `Celaria_exe/Celaria.exe` : PE x86 32 bits, GameMaker Studio 1.4 compilé en **YYC** (`options.ini` → `[LLVM-WINDOWS]`).
  Le `data.win` (chunk `FORM`, offset fichier `0xB3B6EC`) est embarqué ; pas de chunk `CODE` : le GML est du C++ natif.
- Analyse : REA 6.4.0 (serveur MCP) + Ghidra 12.1.4 + JDK 21 (portables dans `_tools/`).
  ~13 min d'auto-analyse. `_tools/rea_bridge.mjs` garde une session REA ouverte (HTTP local :7777),
  `_tools/dec.mjs <fonction>` décompile + renomme les éléments YYC (`yyc_pp.mjs`).

## Tables YYC (adresses virtuelles)
| Table | Adresse | Contenu |
| --- | --- | --- |
| Fonctions GML | `0xF39414` | 2171 paires `{char* nom, code*}` (`gml_Script_*`, `gml_Object_*`) — injectées comme seeds Ghidra |
| Fonctions intégrées | `0xF384B8` | 326 paires `{char* nom, RFunction*}` (slot rempli au démarrage, ex. `0xF385BC` = `buffer_tell`) |
| Variables | `0xF32938` | 1948 paires `{char* nom, id}` (ex. 596 = `gamemode`, 1174 = `roundOver`) |
| Lien runtime | `0xB7C870` | struct de liaison (compte de fonctions `0x87B`) |

Helpers runtime : `0xB8E4E0` lecture variable d'objet, `0xB8E7C0` écriture, `0xB9D0C0` appel de fonction intégrée,
`0x401220` FREE_RValue, `0xBB2310` conversion en réel.

## Réseau client (`gml_Script_scr_network_handler_async` @ `0x869EA0`)
Dispatch sur `tcp_current_packet_id` à `0x86D8AE` (arbre de comparaisons + tables de sauts `0xE2319C`, `0xE23188`).
Ghidra ne décompile pas cette fonction (trop grosse) : lecture via désassemblage capstone + traceur de flux.

| Paquet serveur | Effet côté client |
| --- | --- |
| 1 `SERVER_INFO` | octet « modifié » lu puis ignoré ; salt mot de passe ; taux UDP |
| 180 `NEW_MAPLOAD` | `roundOver=0`, `delayTimeOver=0`, UI et spectateur détruits, **`gamemode = u8` sans validation** |
| 183 `ROUND_START` | `roundTimeRemaining = u32` (ticks), `delayTimeOver=1` (mouvement autorisé), `session_time=0` |
| 184 `ROUND_END` | `roundOver=1`, caméra de fin, UI supprimée sauf classement. Rien ne remet `roundOver` à 0 hormis 180 |
| 20 `ROUNDTIME_EXTEND` | `roundTimeRemaining += u32*100` (non signé : impossible de raccourcir) |
| 120 `LEADERBOARD` | type 1 (fin de manche) : écran « N. Place » + temps de l'entrée perso + gain d'XP (`scr_get_experience_gain`, proportionnel à `session_time`) ; noms affichés tels quels |

Valeurs de `gamemode` : 0 = freeroam (`bev_select_freeplay`), 1 = contre-la-montre. Aucun autre mode n'existe dans le client.

## Conséquences pour le mode Coupe
- Le mode est entièrement piloté par le serveur, le client reste en `gamemode = 1` → compatible avec le client officiel non modifié.
- Une manche = `NEW_MAPLOAD` + transfert de carte + `ROUND_START` (seul chemin qui sort de l'état `roundOver`).
- Les points / FINALIST / WINNER sont affichés dans le nom des entrées du classement (paquet 120).
- Le chrono client repart à zéro sur un restart : le serveur mesure le temps réel depuis `ROUND_START` (horodatage à la réception TCP)
  et ajoute le temps perdu (paquet 201 = reset de run, envoyé par `scr_player_step`).

## Compléments (départ synchronisé, resets, chrono)
- Paquet client 6 : envoyé dans `step_network_handler` (`0x78cfdd`) quand `map_loaded == 1`, à **chaque** chargement de carte
  → le serveur sait quand chaque joueur est prêt. Avant `ROUND_START` le joueur ne peut pas bouger (`delayTimeOver = 0`).
- Touche *respawn* (`ctrl_respawn`, `scr_player_step` lignes 59-76) : s'il y a des checkpoints (`current_times` non vide) →
  `scr_player_respawn` (retour au checkpoint, chrono inchangé) ; sinon → `bev_map_restart` + **paquet 201**.
- Touche *restart* (`ctrl_restart`, lignes 97-99) : `bev_map_restart` (reset total) **sans paquet 201**.
  Le serveur détecte donc aussi le reset total par UDP : `respawnNumber` incrémenté + retour sur la position de départ.
- `scr_player_respawn` incrémente `respawnNumber` (envoyé en UDP) et ne touche pas au chrono.
- Chrono : `scr_get_time` = `obj_timer.akt_time`, incrémenté de 1 par pas moteur (`step_timer`, appelé par `engine_update_loop`)
  → résolution du client = 1/100 s. Le millième n'existe pas côté client ; le serveur mesure le temps réel en ms.
- Classement : `scr_menu_leaderboard_refresh_entries` affiche le nom tel quel (`0x216`) et le temps via `scr_time_format` (`0x213`).

## Mod client (`CelariaMod/`) — points d'accroche
- **ASLR actif** (`DYNAMIC_BASE`) : toutes les adresses sont relatives à la base préférée `0x400000`.
- Lancement de test hors Steam : `steam_appid.txt` = `1081650` + argument `-nosteamrestart`.
- Prologue commun des scripts YYC : `55 89 E5 53 57` (5 octets, hook par `jmp`).
- Variables intégrées d'instance : `Builtin_Get/Set(inst, id, arrayIndex, RValue*)` en `0xb8dd30` / `0xb8dd90`
  (IDs dans les slots `0xf3612c`, `0xf36134`…). Variables d'instance : `inst+4` = tableau de RValue, sinon `vtable[1](id)` (thiscall).
- Itérateur `with(obj)` : `0xb9d720` (new), `0xb8fd20` (next), `0xb8fd50` (delete), signature `(it, &self, &other[, obj])`.
- Chaînes : `0xba0570(RValue*, const char*)` crée une chaîne ; RValue chaîne → `*(char**)ptr`.
- Fonctions natives directes : `draw_set_font(int)` `0xbe9950`, `draw_set_halign` `0xbe9980`, `draw_set_valign` `0xbe99b0`,
  `draw_set_colour(int)` `0xbe3140`, `draw_set_alpha(float)` `0xbe3170`, `draw_rectangle(f,f,f,f,int)` `0xbe3dd0`.
  Natives « Function_Add » (signature `(RValue* res, self, other, argc, RValue* args)`) : `d3d_set_projection_ext` `0xcd17c0`.
- Polices : index = ordre des noms `fnt_*` dans STRG (15 `fnt_chatmessage`, 26 `fnt_multiplayer_usernames`).
- Objets : 0x50 controller, 0x58 network handler (`tcp_out_buffer`, `server`), 0x59 données réseau (`delayTimeOver`, `roundOver`),
  0x65 joueur local, 0x69 `obj_networkPlayer` (`model_x/y/z`, `model_rotationZ`, `username`), 0x70 timer (`akt_time`, `time_running`).
- `scr_get_collided_checkpoint_marker(x, y, z, r)` (`0x6371e0`) : détection checkpoint/arrivée (position en arguments).
- Le texte en Draw GUI doit être dessiné juste après `scr_render_networkplayer_usernames` (`0x4c5a00`) : ailleurs l'état
  de rendu (shader) transforme les glyphes en blocs.
- Envois Steam : `scr_steamRequest_leaderboardUploadScore` `0x8cd3f0` (appelé aussi en multijoueur à l'arrivée), relance
  `0x8cdb00`, XP multijoueur `scr_steam_add_multiplayer_experience` `0x9bca50`.
- Onglets du classement : `scr_create_button` `0x4ceb00` (texte en argument), `bev_menu_leaderboard_setCurrent_multiplayer`
  `0x8e8b10`, `bev_menu_leaderboard_setFriends` `0x8ec6d0`, `scr_send_leaderboard_request_packet(type, offset, size, own)` `0x868890`,
  `scr_menu_leaderboard_addEntry` `0x8ceda0` (nom = « nom [id] », temps entier).

- Vitesse des joueurs dans les paquets UDP : longueur codée `octet / 255 × obj_player.maxfallrate` (= 3) dans les deux sens ;
  le serveur officiel relayait avec `len / 6` (vitesse divisée par deux). `step_networkPlayer` (100 Hz, appelé par
  `engine_update_loop`) fait `x += movement_x` (extrapolation) ; `scr_read_and_apply_network_coordinates` (`0x860c30`) applique
  les paquets (x/y via `0xb8e560`, setter de variable intégrée par ID d'instance).
- Départ : `ROUND_START` = `roundTimeRemaining` (1176), `delayTimeOver` (324) = 1, `session_time` (1250) = 0 sur l'objet 0x59 :
  le mod reproduit exactement ces trois écritures à l'heure programmée.

## Activation LAN uniquement + étiquette (mod client)

- `obj_network_handler` (0x58) : `server_adress` (1237), `server_port` (1239, écrit par `bev_accept_ip_address` via ObjVar_Set).
- `lan.c` : à l'envoi des métadonnées (connexion établie), GetExtendedTcpTable (IPv4/IPv6, PID du jeu, port du serveur,
  état ESTABLISHED) ; actif seulement si toutes les connexions trouvées vont vers une adresse privée. `mod_active()` =
  session LAN && `controller.multiplayer` ; chaque hook appelle l'original sinon.
- Étiquette : hook de `scr_draw_gui` (`0x49fd80`, Draw GUI de `obj_gui`, menus et jeu). Le script se termine avec
  `draw_enable_alphablend` (slot `0xf386f4`) désactivé → texte en blocs : le mod l'active, dessine, puis rappelle
  `draw_enable_alphablend` avec la constante du jeu (`0x3199550`). `scr_render_gui_elements` (`0x48d4a0`) ne convient
  pas : la barre latérale du menu est composée après.
- Réseaux virtuels (Radmin VPN 26.0.0.0/8, Hamachi…) : acceptés si l’adresse du serveur est dans le préfixe on-link
  (GetAdaptersAddresses, OnLinkPrefixLength ≥ 8 en IPv4 / ≥ 16 en IPv6) d’une carte active. Testé : connexion via
  26.149.186.83 → mod actif.

## Retours de test Radmin (compte à rebours, spectateur, synchro, interface)

- Compte à rebours du jeu : `scr_reset_timer` met `obj_timer.akt_time = start_time` (= − réglage « initial countdown »,
  `scr_apply_gameplay_settings`, var `start_time` 1384, `start_time_respawn` 1385) ; le chrono part du négatif, la course
  commence à 0 ; `step_timer` affiche 3, 2, 1, Go! (textures `counter_texture_number`, sons `sfx_countdown_A/B`).
  ROUND_START (ou `delayTimeOver` = 1) redémarre le joueur → le Go arrive « compte à rebours » après le message.
- Affichage des autres joueurs : `step_networkPlayer` fait `model_x = scr_merge_numbers(model_x, x, 0.08)`
  (`a + (b − a) × f`, constante RValue `0xf272f0` = 0.08) → ≈ 115 ms de retard visuel à vitesse constante.
- Paquet UDP de position du client (`step_network_handler`) : 29 octets dans un buffer de 32, envoyé par
  `network_send_udp_raw` avec `buffer_get_size`. Les natives sont appelées par `CallBuiltin` via un index : table
  `[0x31b3104]`, 0x50 octets par fonction, pointeur à +0x40, signature `(RValue* res, self, other, argc, RValue* args)`.
  `buffer_get_address` (slot `0xf3851c`) donne l'adresse des données.
- `scr_read_and_apply_network_coordinates(buffer, …)` lit un joueur ; le serveur termine par un octet 0 → les octets
  suivants sont ignorés par le jeu.
- Caméra : `obj_camera` (objet 0) `xfrom..zup` (1708, 1736, 1785, 1717, 1747, 1796, 1718, 1748, 1797) et `preValues`
  (id d'instance, valeurs du pas précédent pour l'interpolation, `YYGetInt32` `0xbb2600`). `scr_render_scene` (`0x469c90`)
  s'en sert pour le culling / LOD / ombres ; `draw_player_model_allowed` (`0x4b7ae0`) décide du dessin du joueur local.
- Chrono : `obj_timer` user event 0 (`0xa71ea0`) → `scr_time_format` + `draw_text_transformed_hud` (ombre + texte).
- Menu Échap : `scr_create_pause_buttons` (`0x7dcd40`) → `scr_create_button_left_bar(id, y, texte, script, icône)`
  (`0x4cc220`) ; « Reset Run » = script 337 `bev_speedrun_reset_ask`, icône 162 (absent en multijoueur).
  Index de script = index de la table des fonctions GML (`0xF39414`).
- Écran d'arrivée : `scr_gui_create_map_finished` (via `scr_pause_switch`), reste jusqu'à la manche suivante ;
  fermé par `scr_menu_leaderboard_destroy_all` (`0x8e09e0`) + `scr_delete_all_gui_elements` (`0x4da340`).
- Classement : largeur = constante 500 dans `scr_menu_leaderboard_create` (`mov [esp+0x1184], 0x407f4000` à `0x8d2877`) ;
  entrée `obj_button_leaderboard_entry` user event 1 (`0xa01270`) : police 5, nom à `x + string_width("000000")`,
  centré sur `y + h/2` (h = builtin `0xf361e4`), var `entry_username` 534. `#` = retour à la ligne dans les textes GM.
- Compte à rebours visible seulement si `obj 0x16.mload_complete` (895) = 1 et `obj_controller.map_load_alpha_overlay`
  (823) = 0 (fondu après le chargement) : sinon `step_timer` saute tout le bloc (chiffres, sons). Le mod envoie 209 quand
  les deux sont vrais (le serveur attend ce signal) et termine le fondu au lancement du compte à rebours.
- Caméra du jeu (obj 0) : `xfrom/yfrom/zfrom` = œil, `xto/yto/zto` = vecteur direction unitaire (pas un point),
  `mouse_direction` (934) = − lacet monde (degrés), `z_angle` (1751) = tangage (haut > 0), `forward_offset` (570).
  Souris vers la droite → lacet monde croissant ; vecteur à droite du lacet a = (−sin a, cos a). Rotation UDP du joueur =
  `obj_player.main_direction` (direction du corps, suit `mouse_direction` avec retard).
- winExt.dll : `registerMouse(hwnd)` enregistre le Raw Input (RIDEV_INPUTSINK) et sous-classe la fenêtre ; `update()`
  copie et remet à zéro les cumuls, `getRegisteredMouseMovementX/Y` les renvoie.
- Compteur de vitesse : `scr_draw_gui` affiche `string(obj_player._speed)` (var 64 = √(_xspeed² + _yspeed²) par pas),
  masqué si `obj_controller.gui_elements_map_end_created` (624). Menu : « Resume » = script 330 `bev_resume_game` (`0x53a890`).
- Navigateur de serveurs : `menu_multiplayer` → `scr_create_button_icon_menu(x, y, w, h, texte, icône, script, id, échelle,
  échelle)` (`0x4d3920`) ; « Server-Browser » = script 509 `bev_multiplayer_serverbrowser`, icône 155 ; « Direct-IP » =
  script 508, icône 156. Le navigateur crée `obj_http_serverList` (objet 160) : user event 0 = `http_post_string`
  (getServerList.php), événement HTTP (`0xa8ea60`) via `cHttp_is_responding/get_response_status/get_response/
  message_is_ok` ; JSON `{"version", "totalServers", "servers": [[ip, nom, port, joueurs, max, version, mot de passe, ?]]}`.
  Sélection → `selected_ip/selected_port` → `bev_serverbrowser_connect` (`server_adress`, `server_port`).
  Le bouton LAN utilise le script 850 `bev_guiE_test` (inutilisé) détourné par le mod.
- Moi : `obj_network_handler.player_id_server` (1027). Envoi CP : `scr_send_checkpoint_packet` (`0x866cf0`).

## V2 (launcher, warmup, respawn, écart au 1er)

- Fondu blanc de fin de manche : ROUND_END met `obj_controller.white_overlay_fadeIn` (1686) = 1, `white_overlay_alpha_value`
  (1684) / `_prev` (1685) = 0 ; dessiné par `scr_render_white_overlay_screen` (`0x5a42b0`, appelé par `scr_draw_gui`).
- Respawn (`scr_player_step`) : sans checkpoint (`ds_list_size(obj_controller.current_times)` = 0, var 300) →
  `bev_map_restart` (`0x5791f0`, retour `0x76b96a`) + `scr_send_reset_round_packet` (201). Touche restart → `bev_map_restart`
  (retour `0x76c0ac`). `scr_player_respawn_forcestart` (`0x62fd80`, 0 argument, self = joueur) : retour au départ sans
  toucher au chrono (utilisé par le speedrun). Contrôles : `%LOCALAPPDATA%\Celaria\config.ini` (respawn = R, restart = Retour arrière).
- Connexion sans passer par les menus : le menu principal charge l'aperçu « Daily Featured » (objets 161 et 190, map avec
  checkpoints) et met `obj_controller.levels` (764) à autre chose : connecter directement depuis le menu principal donne
  « d3d_transform_add_scaling argument 2 (undefined) » dans `obj_checkpoint_marker`. Il faut d'abord `bev_multiplayer`
  (`0x53a4b0` : `levels = "singleplayer"`, `scr_destroy_daily_workshop_preview_load`, transition vers `menu_multiplayer`
  `0x52cbf0`), puis comme `bev_accept_ip_address` : `server_adress` (1237), `server_port` (1239), `udp_port` (1530) de
  l'objet 0x58, `scr_destroy_maplockscreen`, `scr_delete_all_gui_elements`, `scr_start_multiplayer` (`0x65c7f0`), dans un
  événement step (`step_controller` `0x75b100`, appelé par le step de obj_engine).
- Menus : `menu_main` `0x4e0a20`. Maps de l'éditeur : `%LOCALAPPDATA%\Celaria\custom_maps`, format `celaria_map` version 3
  (même disposition que la version 2 pour le serveur).
- Injection (launcher) : processus créé suspendu + APC `LoadLibraryW` ; la table des fonctions natives (`0x31b3104`) n'existe
  pas encore à ce moment : les remplacements d'entrées (network_send_udp_raw, http_post_string) sont faits à la 1re image.
  Sans Steam lancé, le jeu se ferme tout de suite (SteamAPI) ; avec Steam, `SteamAppId` dans l'environnement suffit
  (pas de steam_appid.txt).
