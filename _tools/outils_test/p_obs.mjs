import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`#define GML_BEV_SPEEDRUN_RESET_ASK 0x0053ccf0// behaviour of the "Reset Run" button of the pause menu`,
 `#define GML_BEV_SPEEDRUN_RESET_ASK 0x0053ccf0// behaviour of the "Reset Run" button of the pause menu
#define GML_SCR_CREATE_PAUSE_BUTTONS 0x007dcd40
#define SCRIPT_BEV_SPEEDRUN_RESET_ASK 337// script index (button behaviour)
#define SPRITE_RESET_ICON 162// icon of the "Reset Run" button`],
[`static GmlScript orig_reset_ask;`, `static GmlScript orig_reset_ask;
static GmlScript orig_create_pause_buttons;
static double button_y_sound = -1, button_y_controls = -1;// positions of the buttons of the pause menu`],
[`// the "Reset Run" button (a complete reset eliminates the player in cup mode) becomes "Observer" / "Play"
static RValue *__cdecl hook_create_button_left_bar(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc >= 3 && cup_menu_active()) {
		const char *text = yy_string(args[2]);
		if (text && strcmp(text, "Reset Run") == 0) {
			yy_set_string(args[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Observer");
		}
	}
	return orig_create_button_left_bar(self, other, result, argc, args);
}`, `// pause menu of a cup server: button "Observer" (watch the cup without racing) / "Play", between "Resume" and "Sound"
// (place of the "Reset Run" button of the single player mode). Its behaviour is the "Reset Run" script, hooked below.
static RValue *__cdecl hook_create_button_left_bar(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc >= 3) {
		const char *text = yy_string(args[2]);
		if (text && strcmp(text, "Sound") == 0) button_y_sound = yy_to_real(args[1]);
		if (text && strcmp(text, "Controls") == 0) button_y_controls = yy_to_real(args[1]);
		if (text && strcmp(text, "Reset Run") == 0 && cup_menu_active()) {
			yy_set_string(args[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Observer");
		}
	}
	return orig_create_button_left_bar(self, other, result, argc, args);
}

static RValue *__cdecl hook_create_pause_buttons(void *self, void *other, RValue *result, int argc, RValue **args) {
	button_y_sound = button_y_controls = -1;
	orig_create_pause_buttons(self, other, result, argc, args);
	if (cup_menu_active() && button_y_sound > 0 && button_y_controls > button_y_sound) {
		RValue a[5] = { { { 0 } } }, r = { 0 };
		YY_CREATE_STRING(&a[0], "cup_observer");
		a[1].real = button_y_sound - (button_y_controls - button_y_sound) * 1.5;
		YY_CREATE_STRING(&a[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Observer");
		a[3].real = SCRIPT_BEV_SPEEDRUN_RESET_ASK;
		a[4].real = SPRITE_RESET_ICON;
		RValue *pa[5] = { &a[0], &a[1], &a[2], &a[3], &a[4] };
		orig_create_button_left_bar(self, other, &r, 5, pa);
		yy_clear(&r);
		yy_clear(&a[0]);
		yy_clear(&a[2]);
	}
	return result;
}`],
[`	orig_reset_ask = (GmlScript)hook_install(ADDR(GML_BEV_SPEEDRUN_RESET_ASK), PROLOGUE, sizeof PROLOGUE, hook_reset_ask);`,
 `	orig_reset_ask = (GmlScript)hook_install(ADDR(GML_BEV_SPEEDRUN_RESET_ASK), PROLOGUE, sizeof PROLOGUE, hook_reset_ask);
	orig_create_pause_buttons = (GmlScript)hook_install(ADDR(GML_SCR_CREATE_PAUSE_BUTTONS), PROLOGUE, sizeof PROLOGUE, hook_create_pause_buttons);`],
]);
