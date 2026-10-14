// Leaderboard of a cup server: wider panel (the cup status is written in front of the names) and badges around the
// status of the winners (red) and of the finalists (yellow).
//
// The width of the leaderboard panel is a constant of scr_menu_leaderboard_create (500, GUI units): it is replaced in
// the code during the call, only on a cup server. The badges are drawn after the entry (obj_button_leaderboard_entry,
// user event 1): the name ("<status> | <player>") starts after the place column (width of "000000" in font 5).
#include <windows.h>
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

static void __cdecl hook_entry_draw(void *self, void *other) {
	orig_entry_draw(self, other);
	if (self == NULL || cup_server() == 0) return;
	const char *name = yy_string(yy_self_var(self, VAR_ENTRY_USERNAME));
	if (name == NULL) return;
	int colour;
	if (strncmp(name, "WINNER", 6) == 0) colour = COLOUR_RED;
	else if (strncmp(name, "FINALIST", 8) == 0) colour = COLOUR_YELLOW;
	else return;
	char status[64];
	const char *bar = strstr(name, " | ");
	size_t n = bar ? (size_t)(bar - name) : strlen(name);
	if (n >= sizeof status) n = sizeof status - 1;
	memcpy(status, name, n);
	status[n] = 0;
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_FONT))(FONT_LEADERBOARD);
	double x = builtin_var(self, SLOT_BUILTIN_VAR_A), y = builtin_var(self, SLOT_BUILTIN_VAR_B);
	double h = builtin_var(self, SLOT_BUILTIN_HEIGHT);
	double left = x + string_width(self, other, "000000");
	double width = string_width(self, other, status);
	frame((float)(left - 4), (float)(y + 2), (float)(left + width + 4), (float)(y + h - 2), colour);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(0xFFFFFF);
}

void leaderboard_install(void) {
	static const uint8_t width_code[] = { 0xC7, 0x84, 0x24, 0x84, 0x11, 0x00, 0x00, 0x00, 0x40, 0x7F, 0x40 };
	if (bytes_match(ADDR(WIDTH_IMMEDIATE - 7), width_code, sizeof width_code))
		orig_leaderboard_create = (GmlScript)hook_install(ADDR(GML_SCR_MENU_LEADERBOARD_CREATE), PROLOGUE, sizeof PROLOGUE, hook_leaderboard_create);
	orig_entry_draw = (GmlEvent)hook_install(ADDR(GML_OBJ_LEADERBOARD_ENTRY_DRAW), PROLOGUE, sizeof PROLOGUE, hook_entry_draw);
}
