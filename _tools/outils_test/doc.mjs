import fs from "node:fs";
const sub = (f, o, n) => { const s = fs.readFileSync(f, "utf8"); if (!s.includes(o)) { console.error(f + " missing " + o); process.exit(1); } fs.writeFileSync(f, s.replace(o, () => n)); };
{ const f="CelariaMod/README.md"; fs.writeFileSync(f, fs.readFileSync(f,"utf8").split(String.fromCharCode(1)).join("<SOH>")); }
const notes = fs.readFileSync("REVERSE_NOTES.md", "utf8") + `
## Activation LAN uniquement + étiquette (mod client)

- \`obj_network_handler\` (0x58) : \`server_adress\` (1237), \`server_port\` (1239, écrit par \`bev_accept_ip_address\` via ObjVar_Set).
- \`lan.c\` : à l'envoi des métadonnées (connexion établie), GetExtendedTcpTable (IPv4/IPv6, PID du jeu, port du serveur,
  état ESTABLISHED) ; actif seulement si toutes les connexions trouvées vont vers une adresse privée. \`mod_active()\` =
  session LAN && \`controller.multiplayer\` ; chaque hook appelle l'original sinon.
- Étiquette : hook de \`scr_draw_gui\` (\`0x49fd80\`, Draw GUI de \`obj_gui\`, menus et jeu). Le script se termine avec
  \`draw_enable_alphablend\` (slot \`0xf386f4\`) désactivé → texte en blocs : le mod l'active, dessine, puis rappelle
  \`draw_enable_alphablend\` avec la constante du jeu (\`0x3199550\`). \`scr_render_gui_elements\` (\`0x48d4a0\`) ne convient
  pas : la barre latérale du menu est composée après.
`;
fs.writeFileSync("REVERSE_NOTES.md", notes);
console.log("ok");
