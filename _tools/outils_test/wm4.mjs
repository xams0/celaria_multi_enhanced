import fs from "node:fs";
const f = "src/spectator.c"; let s = fs.readFileSync(f, "utf8");
const rep = (o, n) => { if (!s.includes(o)) { console.error("missing: " + o); process.exit(1); } s = s.replace(o, () => n); };
rep('#define GML_SCR_RENDER_GUI_ELEMENTS 0x48d4a0// menus and buttons, called by scr_draw_gui (Draw GUI of obj_gui) every frame\n',
`#define GML_SCR_DRAW_GUI 0x49fd80// Draw GUI event of obj_gui (every frame, menus and game)
#define SLOT_FN_DRAW_ENABLE_ALPHABLEND 0x00f386f4
#define CONST_LAST_ALPHABLEND 0x03199550// RValue passed by the last draw_enable_alphablend call of scr_draw_gui
`);
rep('static GmlScript orig_render_gui_elements;\nstatic void draw_mod_label(void *self, void *other);\n', 'static GmlScript orig_draw_gui;\n');
rep(`// Drawn after the menus (scr_render_gui_elements) and in game (after the names of the other players), once per frame.
static void draw_mod_label(void *self, void *other) {
	static double last_draw;
	double now = clock_local_ms();
	if (now - last_draw < 3) return;
	last_draw = now;
`, `// Drawn at the end of scr_draw_gui, on top of everything (menus and game).
static void draw_mod_label(void *self, void *other) {
`);
rep(`static RValue *__cdecl hook_render_gui_elements(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_render_gui_elements(self, other, result, argc, args);
	draw_mod_label(self, other);
	return result;
}`, `static void set_alphablend(void *self, void *other, RValue *value) {
	RValue r = { 0 };
	RValue *args[1] = { value };
	YY_CALL_BUILTIN(self, other, &r, 1, *(void **)ADDR(SLOT_FN_DRAW_ENABLE_ALPHABLEND), args);
	yy_clear(&r);
}

// scr_draw_gui ends with alpha blending disabled (text would be drawn as boxes): enabled for the label, then the state
// left by the game is restored
static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_draw_gui(self, other, result, argc, args);
	RValue on = { 0 };
	on.real = 1;
	set_alphablend(self, other, &on);
	draw_mod_label(self, other);
	set_alphablend(self, other, (RValue *)ADDR(CONST_LAST_ALPHABLEND));
	return result;
}`);
rep('	if (mod_active()) draw_spectator_text(self, other);\n	draw_mod_label(self, other);', '	if (mod_active()) draw_spectator_text(self, other);');
rep('orig_render_gui_elements = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_GUI_ELEMENTS), PROLOGUE, sizeof PROLOGUE, hook_render_gui_elements);', 'orig_draw_gui = (GmlScript)hook_install(ADDR(GML_SCR_DRAW_GUI), PROLOGUE, sizeof PROLOGUE, hook_draw_gui);');
fs.writeFileSync(f, s); console.log("ok");
