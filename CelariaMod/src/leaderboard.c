// Leaderboard of a cup server: wider panel (the cup status is written in front of the names) and badges around the
// status of the winners (red) and of the finalists (yellow).
//
// The width of the leaderboard panel is a constant of scr_menu_leaderboard_create (500, GUI units): it is replaced in
// the code during the call, only on a cup server. The badges are drawn after the entry (obj_button_leaderboard_entry,
// user event 1): the name ("<status> | <player>") starts after the place column (width of "000000" in font 5).
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define GML_SCR_MENU_LEADERBOARD_CREATE 0x008d2200
#define GML_OBJ_LEADERBOARD_ENTRY_DRAW 0x00a01270
#define WIDTH_IMMEDIATE 0x008d287e// high dword of the double 500.0 (mov dword ptr [esp+0x1184], 0x407f4000)
#define WIDTH_DEFAULT 0x407f4000u// 500.0
#define WIDTH_CUP 0x40874000u// 744.0

#define SLOT_BUILTIN_HEIGHT 0x00f361e4// height of the entry (centre of the text: y + height / 2)
#define SLOT_FN_STRING_WIDTH 0x00f38804
#define VAR_ENTRY_USERNAME 534
#define FONT_LEADERBOARD 5
#define DIRECT_DRAW_SET_FONT 0x00be9950
#define DIRECT_DRAW_SET_COLOUR 0x00be3140
#define DIRECT_DRAW_SET_ALPHA 0x00be3170
#define DIRECT_DRAW_RECTANGLE 0x00be3dd0
#define COLOUR_RED 0x3030FF// BGR
#define COLOUR_YELLOW 0x00D8FF

#define OBJ_CONTROLLER 0x50
#define VAR_MULTIPLAYER 965

typedef void(__cdecl *GmlEvent)(void *self, void *other);
static GmlScript orig_leaderboard_create;
static GmlEvent orig_entry_draw;

static int cup_server(void) {
	if (g_server_supports_mod == 0 || mod_active() == 0) return 0;
	RValue v = { 0 };
	YY_VAR_GET(OBJ_CONTROLLER, VAR_MULTIPLAYER, ARRAY_NONE, &v);
	int mp = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	return mp;
}

static void write_code_u32(uintptr_t address, uint32_t value) {
	DWORD old;
	if (VirtualProtect((void *)address, 4, PAGE_EXECUTE_READWRITE, &old)) {
		memcpy((void *)address, &value, 4);
		VirtualProtect((void *)address, 4, old, &old);
		FlushInstructionCache(GetCurrentProcess(), (void *)address, 4);
	}
}

static RValue *__cdecl hook_leaderboard_create(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (cup_server() == 0) return orig_leaderboard_create(self, other, result, argc, args);
	write_code_u32(ADDR(WIDTH_IMMEDIATE), WIDTH_CUP);
	orig_leaderboard_create(self, other, result, argc, args);
	write_code_u32(ADDR(WIDTH_IMMEDIATE), WIDTH_DEFAULT);
	return result;
}

static double string_width(void *self, void *other, const char *text) {
	RValue a = { 0 }, r = { 0 };
	YY_CREATE_STRING(&a, text);
	RValue *args[1] = { &a };
	YY_CALL_BUILTIN(self, other, &r, 1, *(void **)ADDR(SLOT_FN_STRING_WIDTH), args);
	double w = yy_to_real(&r);
	yy_clear(&r);
	yy_clear(&a);
	return w;
}

static double builtin_var(void *inst, uintptr_t slot) {
	RValue v = { 0 };
	YY_BUILTIN_GET(inst, *(int *)ADDR(slot), ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}

static void frame(float x1, float y1, float x2, float y2, int colour) {
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(colour);
	for (int i = 0; i < 2; i++)// 2 pixels
		((void(__cdecl *)(float, float, float, float, int))ADDR(DIRECT_DRAW_RECTANGLE))(x1 + i, y1 + i, x2 - i, y2 - i, 1);
}

#define ROWS_WIDTH 744.0// width of the panel on a cup server (WIDTH_CUP)

// geometry of the rows of the leaderboard drawn in the last frame (the cup table is drawn below the last used row)
static unsigned rows_frame;
static double rows_left, rows_bottom, rows_height;

#define VAR_ENTRY_PLACE_STRING 527

static int cup_table_shown(void) {
	const CupRankEntry *e;
	return cup_server() && cup_mode() && g_records_tab == 0 && cup_ranking(&e) > 0;
}

static void __cdecl hook_entry_draw(void *self, void *other) {
	if (self == NULL || cup_server() == 0) {
		orig_entry_draw(self, other);
		return;
	}
	const char *name = yy_string(yy_self_var(self, VAR_ENTRY_USERNAME));
	// cup server: the empty rows of the panel aren't drawn (the cup table of the mod uses this place)
	if (name == NULL || name[0] == 0) return;
	// own entry at the bottom of the panel (place "-", "Personal Best"): replaced by the cup table
	if (cup_table_shown()) {
		const char *place = yy_string(yy_self_var(self, VAR_ENTRY_PLACE_STRING));
		if (place && strcmp(place, "-") == 0) return;
	}
	// the client adds " [<player id>]" to the names: not shown (the name is restored after the drawing)
	char full[256], clean[256];
	snprintf(full, sizeof full, "%s", name);
	const char *bracket = strrchr(full, '[');
	size_t len = strlen(full);
	int strip = bracket && bracket > full && bracket[-1] == ' ' && full[len - 1] == ']';
	for (const char *c = bracket ? bracket + 1 : NULL; strip && c < full + len - 1; c++)
		if (*c < '0' || *c > '9') strip = 0;
	if (strip) {
		snprintf(clean, sizeof clean, "%.*s", (int)(bracket - full - 1), full);
		yy_set_string(yy_self_var(self, VAR_ENTRY_USERNAME), clean);
	}
	orig_entry_draw(self, other);
	if (strip) yy_set_string(yy_self_var(self, VAR_ENTRY_USERNAME), full);
	double y = builtin_var(self, SLOT_BUILTIN_VAR_B), h = builtin_var(self, SLOT_BUILTIN_HEIGHT);
	if (rows_frame != g_frame) {
		rows_frame = g_frame;
		rows_bottom = 0;
	}
	rows_left = builtin_var(self, SLOT_BUILTIN_VAR_A);
	rows_height = h;
	if (y + h > rows_bottom) rows_bottom = y + h;
}

// ---------------------------------------------------------------- cup table

#define SLOT_FN_DRAW_TEXT_TRANSFORMED 0x00f38d44
#define DIRECT_DRAW_SET_HALIGN 0x00be9980
#define DIRECT_DRAW_SET_VALIGN 0x00be99b0
#define COLOUR_ROW_A 0x1C1C1C
#define COLOUR_ROW_B 0x262626
#define COLOUR_HEAD 0x101010
#define COLOUR_TITLE 0x1FD2FF// yellow (BGR)
#define COLOUR_GREEN 0x60E060
#define COLOUR_GREY 0xB0B0B0

static void rect(float x1, float y1, float x2, float y2, int colour, float alpha) {
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(alpha);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(colour);
	((void(__cdecl *)(float, float, float, float, int))ADDR(DIRECT_DRAW_RECTANGLE))(x1, y1, x2, y2, 0);
}

// text vertically centred on y; halign 0 left, 1 centre, 2 right
static void text(void *self, void *other, double x, double y, const char *s, int halign, int colour, double scale) {
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_HALIGN))(halign);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(1);
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(colour);
	RValue a[6] = { { { 0 } } }, r = { 0 };
	a[0].real = x;
	a[1].real = y;
	YY_CREATE_STRING(&a[2], s);
	a[3].real = scale;
	a[4].real = scale;
	a[5].real = 0;
	RValue *args[6] = { &a[0], &a[1], &a[2], &a[3], &a[4], &a[5] };
	YY_CALL_BUILTIN(self, other, &r, 6, *(void **)ADDR(SLOT_FN_DRAW_TEXT_TRANSFORMED), args);
	yy_clear(&r);
	yy_clear(&a[2]);
}

#define VAR_GLOBAL_PERSONAL_BEST_HEADER 1016// global.personal_best_header_element (textbox "Personal Best")
#define INSTANCE_GLOBAL (-5)

typedef int(__cdecl *FnWithNew)(void *iterator, void **pSelf, void **pOther, int object);
typedef char(__cdecl *FnWithNext)(void *iterator, void **pSelf, void **pOther);
typedef void(__cdecl *FnWithDelete)(void *iterator, void **pSelf, void **pOther);
#define YY_WITH_NEW ((FnWithNew)ADDR(0x00b9d720))
#define YY_WITH_DELETE ((FnWithDelete)ADDR(0x00b8fd50))

static int number_of(const RValue *v, double *out) {
	switch (v->kind & 0xffffff) {
	case KIND_REAL: *out = v->real; return 1;
	case 7: *out = v->i32; return 1;
	case 10: *out = (double)v->i64; return 1;
	}
	return 0;
}

// top of the title "Personal Best" at the bottom of the panel (the own entry is below it), 0 if unknown
static double personal_best_top(void *self, void *other) {
	RValue v = { 0 };
	YY_VAR_GET(INSTANCE_GLOBAL, VAR_GLOBAL_PERSONAL_BEST_HEADER, ARRAY_NONE, &v);
	double id;
	int known = number_of(&v, &id);
	yy_clear(&v);
	if (known == 0 || id < 100000) return 0;// instance ids start at 100000
	uint8_t iterator[1024];
	void *s = self, *o = other;
	double top = 0;
	if (YY_WITH_NEW(iterator, &s, &o, (int)id) > 0) top = builtin_var(s, SLOT_BUILTIN_VAR_B);
	YY_WITH_DELETE(iterator, &s, &o);
	return top;
}

// text shortened with "..." to fit in the width (font of the leaderboard, scale applied)
static void fit_text(void *self, void *other, char *s, double width, double scale) {
	size_t n = strlen(s);
	while (n > 4 && string_width(self, other, s) * scale > width) {
		n--;
		strcpy(s + n - 3, "...");
	}
}

// Second table under the leaderboard of the game (cup server, tab "Server"): the cup ranking (place, player, status /
// points, points won in the last round), the observers in its title bar. Same width as the leaderboard; the rows are
// made smaller if the table doesn't fit above the own entry ("Personal Best") at the bottom of the panel.
void leaderboard_draw_cup(void *self, void *other) {
	if (cup_table_shown() == 0 || rows_height <= 0) return;
	// the game recreates the rows of the panel when the leaderboard changes (a player finishes): the last geometry is
	// kept while the panel is open (title "Personal Best" present)
	double pb_top = personal_best_top(self, other);
	if (g_frame - rows_frame > 2 && (pb_top <= 0 || g_frame - rows_frame > 120)) return;
	const CupRankEntry *e;
	int count = cup_ranking(&e);
	if (count == 0) return;
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_FONT))(FONT_LEADERBOARD);
	float x = (float)rows_left, w = (float)ROWS_WIDTH, h = (float)rows_height;
	float y = (float)rows_bottom + h * 0.4f;
	// title, observers on the right
	rect(x, y, x + w, y + h * 1.1f, COLOUR_HEAD, 1.0f);
	rect(x, y, x + 6, y + h * 1.1f, COLOUR_TITLE, 1.0f);
	text(self, other, x + 16, y + h * 0.55f, "CUP RANKING", 0, COLOUR_TITLE, 1.1);
	const char *spec = cup_spectators();
	if (spec[0]) {
		char line[300];
		snprintf(line, sizeof line, "Spectators: %s", spec);
		fit_text(self, other, line, w - 28 - string_width(self, other, "CUP RANKING") * 1.1 - 40, 0.9);
		text(self, other, x + w - 12, y + h * 0.55f, line, 2, COLOUR_GREY, 0.9);
	} else {
		text(self, other, x + w - 12, y + h * 0.55f, "Points", 2, COLOUR_GREY, 0.9);
	}
	y += h * 1.1f;
	// row height: everything above the title "Personal Best"
	double limit = pb_top - 2;
	if (limit > y && y + count * h > limit) {
		h = (float)((limit - y) / count);
		if (h < rows_height * 0.6) h = (float)(rows_height * 0.6);
	}
	double scale = h < rows_height * 0.95 ? h / rows_height + 0.05 : 1.0;
	int local = cup_local_id();
	double place_w = string_width(self, other, "000000");
	for (int i = 0; i < count; i++, y += h) {
		const CupRankEntry *r = &e[i];
		rect(x, y - 1, x + w, y + h + 1, i % 2 ? COLOUR_ROW_B : COLOUR_ROW_A, 1.0f);
		int colour = r->id == local ? COLOUR_TITLE : 0xFFFFFF;
		char buf[64];
		snprintf(buf, sizeof buf, "%d.", r->place);
		text(self, other, x + place_w * 0.5, y + h / 2, buf, 1, colour, scale);
		text(self, other, x + place_w, y + h / 2, r->name, 0, colour, scale);
		// status: WINNER (red frame), FINALIST (yellow frame) or points
		const char *status;
		char pts[32];
		int frame_colour = -1;
		if (r->winner > 0) {
			if (r->winner > 1) snprintf(pts, sizeof pts, "WINNER %d", r->winner);
			else snprintf(pts, sizeof pts, "WINNER");
			status = pts;
			frame_colour = COLOUR_RED;
		} else if (r->finalist) {
			status = "FINALIST";
			frame_colour = COLOUR_YELLOW;
		} else {
			snprintf(pts, sizeof pts, "%d pts", r->points);
			status = pts;
		}
		double right = x + w - 12;
		if (r->last_points > 0) {
			snprintf(buf, sizeof buf, "+%d", r->last_points);
			text(self, other, right, y + h / 2, buf, 2, COLOUR_GREEN, scale);
			right -= (string_width(self, other, "+00 ") + 8) * scale;
		}
		text(self, other, right, y + h / 2, status, 2, 0xFFFFFF, scale);
		if (frame_colour >= 0) {
			double sw = string_width(self, other, status) * scale;
			frame((float)(right - sw - 4), y + 2, (float)(right + 4), y + h - 2, frame_colour);
		}
	}
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_HALIGN))(0);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(0);
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(0xFFFFFF);
}

void leaderboard_install(void) {
	static const uint8_t width_code[] = { 0xC7, 0x84, 0x24, 0x84, 0x11, 0x00, 0x00, 0x00, 0x40, 0x7F, 0x40 };
	if (bytes_match(ADDR(WIDTH_IMMEDIATE - 7), width_code, sizeof width_code))
		orig_leaderboard_create = (GmlScript)hook_install(ADDR(GML_SCR_MENU_LEADERBOARD_CREATE), PROLOGUE, sizeof PROLOGUE, hook_leaderboard_create);
	orig_entry_draw = (GmlEvent)hook_install(ADDR(GML_OBJ_LEADERBOARD_ENTRY_DRAW), PROLOGUE, sizeof PROLOGUE, hook_entry_draw);
}
