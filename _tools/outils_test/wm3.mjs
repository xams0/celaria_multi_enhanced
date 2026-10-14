import fs from "node:fs";
const f = "src/spectator.c"; let s = fs.readFileSync(f, "utf8");
const a = s.indexOf('// "Lanppart3 Mod" in the bottom left corner');
const b = s.indexOf('void spectator_install(void) {');
const label = `// "Lanppart3 Mod" in the bottom left corner: the mod is loaded (white), active on a LAN server (green).
// Drawn after the menus (scr_render_gui_elements) and in game (after the names of the other players), once per frame.
static void draw_mod_label(void *self, void *other) {
	static double last_draw;
	double now = clock_local_ms();
	if (now - last_draw < 3) return;
	last_draw = now;
	RValue rh = { 0 };
	((GmlScript)ADDR(0x469830))(self, other, &rh, 0, NULL);// get_render_h
	double h = yy_to_real(&rh);
	yy_clear(&rh);
	const double scale = 1.4;
	float top = (float)h - 36;
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_FONT))(FONT_HUD);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_HALIGN))(0);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(1);
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(0.6f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(0x000000);
	((void(__cdecl *)(float, float, float, float, int))ADDR(DIRECT_DRAW_RECTANGLE))(4, top, 20 + 7.7f * (float)scale * (float)strlen(MOD_LABEL), top + 30, 0);
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(mod_active() ? 0x60FF60 : 0xFFFFFF);// BGR: green when active
	RValue t[6] = { { { 0 } } }, r = { 0 };
	t[0].real = 12;
	t[1].real = top + 15;
	YY_CREATE_STRING(&t[2], MOD_LABEL);
	t[3].real = scale;
	t[4].real = scale;
	t[5].real = 0;
	RValue *args[6] = { &t[0], &t[1], &t[2], &t[3], &t[4], &t[5] };
	YY_CALL_BUILTIN(self, other, &r, 6, *(void **)ADDR(SLOT_FN_DRAW_TEXT_TRANSFORMED), args);
	yy_clear(&r);
	yy_clear(&t[2]);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(0xFFFFFF);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(0);
}

static RValue *__cdecl hook_render_gui_elements(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_render_gui_elements(self, other, result, argc, args);
	draw_mod_label(self, other);
	return result;
}

`;
s = s.slice(0, a) + label + s.slice(b);
s = s.replace('static GmlScript orig_draw_gui;', 'static GmlScript orig_render_gui_elements;');
s = s.replace('orig_draw_gui = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_GUI_ELEMENTS), PROLOGUE, sizeof PROLOGUE, hook_draw_gui);', 'orig_render_gui_elements = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_GUI_ELEMENTS), PROLOGUE, sizeof PROLOGUE, hook_render_gui_elements);');
const u = '	if (mod_active()) draw_spectator_text(self, other);';
if (!s.includes(u)) process.exit(2);
s = s.replace(u, u + '\n	draw_mod_label(self, other);');
// draw_mod_label is defined after hook_render_usernames: forward declaration
s = s.replace('static GmlScript orig_render_gui_elements;', 'static GmlScript orig_render_gui_elements;\nstatic void draw_mod_label(void *self, void *other);');
fs.writeFileSync(f, s); console.log("ok");
