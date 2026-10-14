import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`static float text_width(const char *text) { return 7.7f * (float)strlen(text); }`,
`#define SLOT_FN_STRING_WIDTH 0x00f38804
#define SLOT_FN_STRING_HEIGHT 0x00f3880c

// size of a text in the HUD font (GUI units, scale 1)
static float text_size(void *self, void *other, const char *text, uintptr_t slot) {
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_FONT))(FONT_HUD);
	RValue a = { 0 }, r = { 0 };
	YY_CREATE_STRING(&a, text);
	RValue *args[1] = { &a };
	YY_CALL_BUILTIN(self, other, &r, 1, *(void **)ADDR(slot), args);
	float size = (float)yy_to_real(&r);
	yy_clear(&r);
	yy_clear(&a);
	return size;
}

static float text_width(void *self, void *other, const char *text) { return text_size(self, other, text, SLOT_FN_STRING_WIDTH); }
static float text_height(void *self, void *other) { return text_size(self, other, "A", SLOT_FN_STRING_HEIGHT); }`],
[`	hud_font(0, 1);
	hud_rect(10, 10, 26 + text_width(text) * 1.6f, 46, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 18, 28, text, 1.6, COLOUR_WHITE);`,
`	float th = text_height(self, other) * 1.6f;
	hud_font(0, 1);
	hud_rect(10, 10, 26 + text_width(self, other, text) * 1.6f, 18 + th, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 18, 14 + th / 2, text, 1.6, COLOUR_WHITE);`],
[`	hud_font(0, 1);
	hud_rect(10, 54, 22 + text_width(text), 80, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 16, 67, text, 1.0, COLOUR_WHITE);`,
`	float th = text_height(self, other);
	float top = 26 + th * 1.6f;// below the round
	hud_font(0, 1);
	hud_rect(10, top, 22 + text_width(self, other, text), top + th + 6, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 16, top + 3 + th / 2, text, 1.0, COLOUR_WHITE);`],
[`		float tw = text_width(target_name) * 1.6f;
		float y = (float)(h * 0.835);
		hud_rect((float)(w / 2) - tw / 2 - 12, y - 18, (float)(w / 2) + tw / 2 + 12, y + 18, COLOUR_BLACK, 0.6f);`,
`		float tw = text_width(self, other, target_name) * 1.6f, nh = th * 1.6f;
		float y = (float)(h * 0.835);
		hud_font(1, 1);
		hud_rect((float)(w / 2) - tw / 2 - 12, y - nh / 2 - 4, (float)(w / 2) + tw / 2 + 12, y + nh / 2 + 4, COLOUR_BLACK, 0.6f);`],
[`	const double scale = 1.4;
	float top = (float)h - 36;
	hud_font(0, 1);
	hud_rect(4, top, 20 + text_width(MOD_LABEL) * (float)scale, top + 30, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 12, top + 15, MOD_LABEL, scale, mod_active() ? COLOUR_GREEN : COLOUR_WHITE);`,
`	const double scale = 1.4;
	float th = text_height(self, other) * (float)scale;
	float top = (float)h - th - 12;
	hud_font(0, 1);
	hud_rect(4, top, 20 + text_width(self, other, MOD_LABEL) * (float)scale, top + th + 8, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 12, top + 4 + th / 2, MOD_LABEL, scale, mod_active() ? COLOUR_GREEN : COLOUR_WHITE);`],
]);
