import fs from "node:fs";
const sub = (f, o, n) => { const s = fs.readFileSync(f, "utf8"); const c = s.split(o).length - 1; if (c !== 1) { console.error(f + ": found " + c + "x: " + o); process.exit(1); } fs.writeFileSync(f, s.replace(o, () => n)); };
const f = "spectator.c";
sub(f, '#define GML_SCR_RENDER_USERNAMES 0x4c5a00// draws the names of the other players (text draw state)\n',
`#define GML_SCR_RENDER_USERNAMES 0x4c5a00// draws the names of the other players (text draw state)
#define GML_SCR_DRAW_GUI 0x49fd80// called by the Draw GUI event of obj_gui (every frame, menus included)
`);
sub(f, '#define FONT_HUD 26// font indices: order of the FONT resources (fnt_multiplayer_usernames)\n',
`#define FONT_HUD 26// font indices: order of the FONT resources (fnt_multiplayer_usernames)
#define FONT_SMALL 15// fnt_chat
#define MOD_LABEL "Lanppart3 Mod"
`);
sub(f, 'static GmlScript orig_render_usernames;\n', 'static GmlScript orig_render_usernames;\nstatic GmlScript orig_draw_gui;\n');
sub(f, 'void spectator_install(void) {', `// "Lanppart3 Mod" in the bottom left corner: the mod is loaded (grey), active on a LAN server (green)
static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_draw_gui(self, other, result, argc, args);
	RValue rh = { 0 };
	((GmlScript)ADDR(0x469830))(self, other, &rh, 0, NULL);// get_render_h
	double h = yy_to_real(&rh);
	yy_clear(&rh);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_FONT))(FONT_SMALL);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_HALIGN))(0);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(2);
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(0.7f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(mod_active() ? 0x60FF60 : 0xC0C0C0);
	draw_text(self, other, 8, h - 6, MOD_LABEL);
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(0xFFFFFF);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(0);
	return result;
}

void spectator_install(void) {`);
sub(f, '	orig_render_usernames = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_USERNAMES), PROLOGUE, sizeof PROLOGUE, hook_render_usernames);\n',
`	orig_render_usernames = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_USERNAMES), PROLOGUE, sizeof PROLOGUE, hook_render_usernames);
	orig_draw_gui = (GmlScript)hook_install(ADDR(GML_SCR_DRAW_GUI), PROLOGUE, sizeof PROLOGUE, hook_draw_gui);
`);
console.log("ok");
