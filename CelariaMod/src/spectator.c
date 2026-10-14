// Spectator mode: follow another player in first person / third person, or free camera. Cup HUD.
//
// Available when the local player isn't racing (waiting for the start, finished, eliminated after a complete reset, winner
// of the cup, observer, round over). When the local player stops racing while other players are still racing, the view
// switches automatically to the third person view of a random racing player. A new round (synchronised start) ends the
// spectator mode. Keys: V = next view (off -> first person -> third person -> free -> off), Left/Right = other player,
// free camera: Z/W Q/A S D to move (direction of the mouse view), Space/C up/down, Shift faster.
//
// While spectating, the camera of the game (obj_camera and its values of the previous step, used for the interpolation)
// is replaced by the spectator camera during the rendering of the scene: the culling, the level of detail, the shadows
// and the effects are computed from the spectator camera. The model of the local player isn't drawn.
#include <windows.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "hook.h"
#include "mod.h"

#define NATIVE_D3D_SET_PROJECTION_EXT 0x00cd17c0
#define GML_SCR_RENDER_SCENE 0x00469c90
#define GML_DRAW_PLAYER_MODEL_ALLOWED 0x004b7ae0
#define GML_SCR_RENDER_USERNAMES 0x4c5a00// draws the names of the other players (text draw state)
#define GML_SCR_DRAW_GUI 0x49fd80// Draw GUI event of obj_gui (every frame, menus and game)
#define GML_OBJ_TIMER_DRAW 0x00a71ea0// obj_timer user event 0: draws the timer of the run
#define GML_SCR_CREATE_BUTTON_LEFT_BAR 0x004cc220// (id, y, text, behaviour script, icon)
#define GML_BEV_SPEEDRUN_RESET_ASK 0x0053ccf0// behaviour of the "Reset Run" button of the pause menu
#define GML_SCR_CREATE_PAUSE_BUTTONS 0x007dcd40
#define GML_BEV_RESUME_GAME 0x0053a890// "Resume" button of the pause menu
#define SCRIPT_BEV_SPEEDRUN_RESET_ASK 337// script index (button behaviour)
#define SPRITE_RESET_ICON 162// icon of the "Reset Run" button
#define GML_SCR_DELETE_ALL_GUI_ELEMENTS 0x004da340
#define GML_SCR_MENU_LEADERBOARD_DESTROY_ALL 0x008e09e0
#define GML_GET_RENDER_W 0x469a50
#define GML_GET_RENDER_H 0x469830
#define SLOT_FN_DRAW_ENABLE_ALPHABLEND 0x00f386f4
#define CONST_LAST_ALPHABLEND 0x03199550// RValue passed by the last draw_enable_alphablend call of scr_draw_gui
#define YY_GET_INT32 ((int(__cdecl *)(RValue *))ADDR(0x00bb2600))// RValue -> instance id (as the compiled GML)
// drawing functions called directly by the compiled GML (same as scr_render_networkplayer_usernames)
#define DIRECT_DRAW_SET_FONT 0x00be9950// (int font)
#define DIRECT_DRAW_SET_HALIGN 0x00be9980// (int)
#define DIRECT_DRAW_SET_VALIGN 0x00be99b0// (int)
#define DIRECT_DRAW_SET_COLOUR 0x00be3140// (int colour)
#define DIRECT_DRAW_SET_ALPHA 0x00be3170// (float alpha)
#define DIRECT_DRAW_RECTANGLE 0x00be3dd0// (float x1, float y1, float x2, float y2, int outline)
#define FONT_HUD 26// font indices: order of the FONT resources (fnt_multiplayer_usernames)
#define MOD_LABEL "Lanppart3 Mod"
#define SLOT_FN_DRAW_TEXT 0x00f38814
#define SLOT_FN_DRAW_TEXT_TRANSFORMED 0x00f38d44

#define OBJ_CAMERA 0// obj_camera: xfrom..zup, preValues (instance with the values of the previous step)
#define OBJ_CONTROLLER 0x50
#define OBJ_NETWORK_DATA 0x59
#define OBJ_PLAYER 0x65
#define OBJ_NETWORK_PLAYER 0x69
#define VAR_DELAY_TIME_OVER 324
#define VAR_ROUND_OVER 1174
#define VAR_MAP_FINISHED 820
#define VAR_MULTIPLAYER 965
#define VAR_USERNAME 1604
#define VAR_NETWORK_PLAYER_ID 981
#define VAR_MODEL_X 924
#define VAR_MODEL_Y 925
#define VAR_MODEL_Z 926
#define VAR_MODEL_ROTATION_Z 918
#define VAR_PRE_VALUES 1057

#define COLOUR_WHITE 0xFFFFFF
#define COLOUR_BLACK 0x000000
#define COLOUR_YELLOW 0x00FFFF// GameMaker colours are BGR
#define COLOUR_GREEN 0x60FF60

enum { VIEW_OFF, VIEW_FIRST_PERSON, VIEW_THIRD_PERSON, VIEW_FREE, VIEW_COUNT };
static const char *VIEW_NAMES[] = { "", "first person", "third person", "free camera" };

typedef void(__cdecl *NativeFn)(RValue *result, void *self, void *other, int argc, RValue *args);
typedef void(__cdecl *GmlEvent)(void *self, void *other);
static NativeFn orig_projection;
static GmlScript orig_render_scene;
static GmlScript orig_draw_player_model_allowed;
static GmlScript orig_render_usernames;
static GmlScript orig_draw_gui;
static GmlEvent orig_timer_draw;
static GmlScript orig_create_button_left_bar;
static GmlScript orig_reset_ask;
static GmlScript orig_create_pause_buttons;
static double button_y_sound = -1, button_y_controls = -1;// positions of the buttons of the pause menu

static int view;
static int gap_finish_shown = -1;// followed player whose finish was shown
static void show_gap(int id, int checkpoint, long ms);
static void gap_clear(void);
static int target_id = -1;// networkPlayerID of the followed player
static int spectating;// view active and allowed this frame
static double target_lost_since;// the followed player stopped racing (local ms), 0 = racing
static int finish_screen_closed;
static char target_name[64];
int g_in_timer_draw;

// the local player watches another player (or the free camera)
int spectator_active(void) { return spectating; }
static unsigned timer_drawn_frame;// last frame where the game drew its timer

static double eye_height = 2.3;// eye of the local player above its position (measured), used for the other players
static double free_pos[3];
static int free_initialised;
static double cam_eye[3], cam_dir[3];// spectator camera: eye, unit direction
static double cam_up[3] = { 0, 0, 1 };// up vector (tilted in the wall runs of the followed player)
static double cam_yaw, cam_pitch;// orbit (third person) / free camera orientation, degrees
static int orbit_initialised;
static double not_racing_since;// the local player stopped racing (local ms), 0 = racing
static char big_message[64];// message in the middle of the screen
static double big_message_until;
#define AUTO_SPECTATE_DELAY_MS 5000

static double obj_var(int instOrObject, int varId) {
	RValue v = { 0 };
	YY_VAR_GET(instOrObject, varId, ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}

static void set_obj_var(int instOrObject, int varId, double value) {
	RValue v = { 0 };
	v.real = value;
	YY_VAR_SET(instOrObject, varId, ARRAY_NONE, &v);
}

static int is_multiplayer(void) { return obj_var(OBJ_CONTROLLER, VAR_MULTIPLAYER) > 0.5; }

// the local player races in the current round
static int local_racing(void) {
	if (cup_known()) return cup_state(cup_local_id()) == 'R' && obj_var(OBJ_CONTROLLER, VAR_MAP_FINISHED) < 0.5;
	return obj_var(OBJ_NETWORK_DATA, VAR_DELAY_TIME_OVER) > 0.5 && obj_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) < 0.5
		&& obj_var(OBJ_CONTROLLER, VAR_MAP_FINISHED) < 0.5;
}

static int spectating_allowed(void) { return is_multiplayer() && local_racing() == 0; }

// YYC "with" iterator (same calls as the compiled GML)
typedef int(__cdecl *FnWithNew)(void *iterator, void **pSelf, void **pOther, int object);
typedef char(__cdecl *FnWithNext)(void *iterator, void **pSelf, void **pOther);
typedef void(__cdecl *FnWithDelete)(void *iterator, void **pSelf, void **pOther);
#define YY_WITH_NEW ((FnWithNew)ADDR(0x00b9d720))
#define YY_WITH_NEXT ((FnWithNext)ADDR(0x00b8fd20))
#define YY_WITH_DELETE ((FnWithDelete)ADDR(0x00b8fd50))

typedef struct {
	void *inst;
	int id;
	double x, y, z, rotation;
	char name[64];
} NetPlayer;

#define MAX_PLAYERS 32
static NetPlayer players[MAX_PLAYERS];
static int player_count;

static void collect_players(void *self, void *other) {
	uint8_t iterator[1024];
	void *s = self, *o = other;
	int n = 0;
	if (YY_WITH_NEW(iterator, &s, &o, OBJ_NETWORK_PLAYER) > 0) {
		do {
			if (n < MAX_PLAYERS) {
				NetPlayer *p = &players[n];
				p->inst = s;
				p->id = (int)yy_to_real(yy_self_var(s, VAR_NETWORK_PLAYER_ID));
				p->x = yy_to_real(yy_self_var(s, VAR_MODEL_X));
				p->y = yy_to_real(yy_self_var(s, VAR_MODEL_Y));
				p->z = yy_to_real(yy_self_var(s, VAR_MODEL_Z));
				p->rotation = yy_to_real(yy_self_var(s, VAR_MODEL_ROTATION_Z));
				const char *name = yy_string(yy_self_var(s, VAR_USERNAME));
				snprintf(p->name, sizeof p->name, "%s", name ? name : "?");
				n++;
			}
		} while (YY_WITH_NEXT(iterator, &s, &o));
	}
	YY_WITH_DELETE(iterator, &s, &o);
	player_count = n;
}

static NetPlayer *find_player(int id) {
	for (int i = 0; i < player_count; i++)
		if (players[i].id == id) return &players[i];
	return NULL;
}

const char *player_name(int id) {
	if (id == cup_local_id()) return "You";
	NetPlayer *p = find_player(id);
	return p ? p->name : "?";
}

static int is_racing(int id) { return cup_known() ? cup_state(id) == 'R' : 1; }

// random racing player (or any player), -1 if none
static int pick_target(int exclude) {
	int candidates[MAX_PLAYERS], n = 0;
	for (int i = 0; i < player_count; i++)
		if (players[i].id != exclude && is_racing(players[i].id)) candidates[n++] = players[i].id;
	if (n == 0)
		for (int i = 0; i < player_count; i++) candidates[n++] = players[i].id;
	return n > 0 ? candidates[rand() % n] : -1;
}

// next/previous player in the list
static void cycle_target(int direction) {
	if (player_count == 0) return;
	int index = 0;
	for (int i = 0; i < player_count; i++)
		if (players[i].id == target_id) index = i;
	index = (index + direction + player_count) % player_count;
	target_id = players[index].id;
}

// a new round starts for the local player: back to the race
void spectator_round_starts(void) {
	cup_local_round_starts();
	gap_clear();
	view = VIEW_OFF;
	spectating = 0;
	target_lost_since = 0;
	not_racing_since = 0;
	free_initialised = 0;
	finish_screen_closed = 0;
}

// the screen of the game after the goal (leaderboard + menu) stays until the next round: closed for the spectator view
static void close_finish_screen(void *self, void *other) {
	RValue r = { 0 };
	((GmlScript)ADDR(GML_SCR_MENU_LEADERBOARD_DESTROY_ALL))(self, other, &r, 0, NULL);
	yy_clear(&r);
	((GmlScript)ADDR(GML_SCR_DELETE_ALL_GUI_ELEMENTS))(self, other, &r, 0, NULL);
	yy_clear(&r);
	mod_log("map finished screen closed for the spectator view");
}

static int key_pressed(int vk) {
	static SHORT previous[256];
	SHORT state = GetAsyncKeyState(vk);
	int pressed = (state & 0x8000) && !(previous[vk] & 0x8000);
	previous[vk] = state;
	return pressed;
}

static int key_down(int vk) { return (GetAsyncKeyState(vk) & 0x8000) != 0; }

static int game_has_focus(void) {
	DWORD pid = 0;
	GetWindowThreadProcessId(GetForegroundWindow(), &pid);
	return pid == GetCurrentProcessId();
}

static int racing_players(void) {
	int n = 0;
	for (int i = 0; i < player_count; i++)
		if (is_racing(players[i].id)) n++;
	return n;
}

// ---------------------------------------------------------------- mouse (raw input)

// The game reads the mouse with raw input (winExt.dll registers the window and accumulates the movements). Its camera
// doesn't follow the mouse when the player doesn't race: the mod reads the raw input of the window too.
static HWND game_window;
static WNDPROC previous_wndproc;
static volatile LONG mouse_dx, mouse_dy;

static LRESULT CALLBACK spectator_wndproc(HWND hwnd, UINT message, WPARAM wparam, LPARAM lparam) {
	if (message == WM_INPUT) {
		RAWINPUT raw;
		UINT size = sizeof raw;
		if (GetRawInputData((HRAWINPUT)lparam, RID_INPUT, &raw, &size, sizeof(RAWINPUTHEADER)) != (UINT)-1
			&& raw.header.dwType == RIM_TYPEMOUSE && (raw.data.mouse.usFlags & MOUSE_MOVE_ABSOLUTE) == 0) {
			InterlockedExchangeAdd(&mouse_dx, raw.data.mouse.lLastX);
			InterlockedExchangeAdd(&mouse_dy, raw.data.mouse.lLastY);
		}
	}
	return IsWindowUnicode(hwnd) ? CallWindowProcW(previous_wndproc, hwnd, message, wparam, lparam)
		: CallWindowProcA(previous_wndproc, hwnd, message, wparam, lparam);
}

static BOOL CALLBACK find_game_window(HWND hwnd, LPARAM result) {
	DWORD pid = 0;
	GetWindowThreadProcessId(hwnd, &pid);
	if (pid == GetCurrentProcessId() && IsWindowVisible(hwnd) && GetWindow(hwnd, GW_OWNER) == NULL) {
		*(HWND *)result = hwnd;
		return FALSE;
	}
	return TRUE;
}

static void install_mouse(void) {
	if (game_window) return;
	HWND hwnd = NULL;
	EnumWindows(find_game_window, (LPARAM)&hwnd);
	if (hwnd == NULL) return;
	game_window = hwnd;
	if (IsWindowUnicode(hwnd)) previous_wndproc = (WNDPROC)SetWindowLongPtrW(hwnd, GWLP_WNDPROC, (LONG_PTR)spectator_wndproc);
	else previous_wndproc = (WNDPROC)SetWindowLongPtrA(hwnd, GWLP_WNDPROC, (LONG_PTR)spectator_wndproc);
	mod_log("spectator mouse installed");
}

// ---------------------------------------------------------------- camera

// world directions: yaw in degrees (world yaw = - mouse_direction of the game), pitch in degrees (up > 0).
// Turning right (mouse right) increases the yaw: the vector on the right of the yaw a is (-sin a, cos a).
static void direction(double yaw, double pitch, double d[3]) {
	double a = yaw * M_PI / 180.0, p = pitch * M_PI / 180.0;
	d[0] = cos(a) * cos(p);
	d[1] = sin(a) * cos(p);
	d[2] = sin(p);
}

// camera of the followed player: sent by his mod, or his rotation (direction of his body, horizontal)
static void target_look(NetPlayer *t, double *yaw, double *pitch) {
	if (prediction_look(t->id, yaw, pitch)) return;
	*yaw = -t->rotation;
	*pitch = 0;
}

static void render_size(void *self, void *other, double *w, double *h);

#define VAR_PAUSE 1013// obj_controller.pause: pause menu (Escape) open

// mouse position in GUI coordinates (render size of the game), 0 if unknown
static int mouse_gui(void *self, void *other, double *x, double *y) {
	POINT p;
	RECT rc;
	if (game_window == NULL || GetCursorPos(&p) == 0 || ScreenToClient(game_window, &p) == 0 || GetClientRect(game_window, &rc) == 0) return 0;
	if (rc.right <= 0 || rc.bottom <= 0) return 0;
	double w, h;
	render_size(self, other, &w, &h);
	*x = p.x * w / rc.right;
	*y = p.y * h / rc.bottom;
	return 1;
}

#define MOUSE_SENSITIVITY 0.1// degrees per mouse count (as the default settings of the game)
#define THIRD_PERSON_DISTANCE 4.5

// once per frame (before the rendering of the scene): keys, mouse and camera
static void update(void *self, void *other) {
	clock_update(self, other);
	install_mouse();
	static LARGE_INTEGER freq, last;
	LARGE_INTEGER now;
	if (freq.QuadPart == 0) QueryPerformanceFrequency(&freq);
	QueryPerformanceCounter(&now);
	double dt = last.QuadPart ? (double)(now.QuadPart - last.QuadPart) / (double)freq.QuadPart : 0;
	last = now;
	if (dt > 0.1) dt = 0.1;
	double now_ms = (double)now.QuadPart * 1000.0 / (double)freq.QuadPart;
	double mx = (double)InterlockedExchange(&mouse_dx, 0), my = (double)InterlockedExchange(&mouse_dy, 0);

	collect_players(self, other);

	int allowed = spectating_allowed();
	if (allowed == 0) {
		view = VIEW_OFF;
		spectating = 0;
			not_racing_since = 0;
		return;
	}
	if (not_racing_since == 0) not_racing_since = now_ms;
	int focus = game_has_focus();
	int v_pressed = focus && key_pressed('V');
	int clicked = key_pressed(VK_LBUTTON) && focus;
	// leaderboard (Tab, end of the round) or pause menu open: the mouse is used by the menus, the camera doesn't move
	int leaderboard = leaderboard_panel_open();
	int ui_open = leaderboard || obj_var(OBJ_CONTROLLER, VAR_PAUSE) > 0.5;
	if (ui_open) mx = my = 0;
	static int ui_open_logged = -1;
	if (ui_open != ui_open_logged) {
		ui_open_logged = ui_open;
		mod_log("spectator: menu / leaderboard open %d (camera locked)", ui_open);
	}
	// click on a player of the leaderboard: followed by the spectator view
	if (clicked && leaderboard && view != VIEW_OFF) {
		double gx, gy;
		int id = mouse_gui(self, other, &gx, &gy) ? leaderboard_player_at(gx, gy) : -1;
		if (id >= 0 && id != cup_local_id() && find_player(id)) {
			target_id = id;
			if (view == VIEW_FREE) view = VIEW_FIRST_PERSON;
			orbit_initialised = 0;
			target_lost_since = 0;
			mod_log("spectator: player %d chosen in the leaderboard", id);
		}
	}
	if (focus) {
		// V: next view (first person -> third person -> free camera). Only while spectating: a player who races enters the
		// spectator mode with the Spectator button of the pause menu (or automatically once he can't race any more).
		if (v_pressed && view != VIEW_OFF) {
			view = view == VIEW_FREE ? VIEW_FIRST_PERSON : view + 1;
			free_initialised = 0;
			if (view != VIEW_FREE && find_player(target_id) == NULL) target_id = pick_target(-1);
			if (view != VIEW_FREE && target_id < 0) view = VIEW_FREE;// nobody to follow
			if (view == VIEW_THIRD_PERSON) orbit_initialised = 0;
			mod_log("spectator view: %d", view);
		}
		if (key_pressed(VK_RIGHT)) {
			cycle_target(1);
			orbit_initialised = 0;
		}
		if (key_pressed(VK_LEFT)) {
			cycle_target(-1);
			orbit_initialised = 0;
		}
	}
	// automatic first person view 5 seconds after the local player stopped racing, if other players are racing
	// (time attack: observers only, the players restart their runs)
	int observer = cup_known() && cup_state(cup_local_id()) == 'V';
	int wi, wc;
	double wr;
	int warmup = cup_warmup(&wi, &wc, &wr);// warmup: the players restart their runs, no automatic view
	if (view == VIEW_OFF && cup_known() && ((cup_mode() && cup_round_running() && warmup == 0 && racing_players() > 0) || observer)
		&& now_ms - not_racing_since > (observer ? 0 : AUTO_SPECTATE_DELAY_MS)) {
		target_id = pick_target(-1);
		view = target_id >= 0 ? VIEW_FIRST_PERSON : VIEW_FREE;// observer alone: free camera
		free_initialised = 0;
		target_lost_since = 0;
		mod_log("automatic spectator view %d: player %d", view, target_id);
	}
	// the followed player finished or left: another racing player after a moment
	if (view != VIEW_OFF && view != VIEW_FREE) {
		if (find_player(target_id) == NULL) {
			target_id = pick_target(-1);
			orbit_initialised = 0;
		} else if (is_racing(target_id) == 0 && racing_players() > 0) {
			if (target_lost_since == 0) target_lost_since = now_ms;
			if (now_ms - target_lost_since > 2500) {
				target_id = pick_target(target_id);
				target_lost_since = 0;
				orbit_initialised = 0;
			}
		} else {
			target_lost_since = 0;
		}
	}
	spectating = view != VIEW_OFF;
	if (spectating == 0) return;
	// goal reached: the map finished screen is closed (not at the end of the round: final leaderboard)
	if (obj_var(OBJ_CONTROLLER, VAR_MAP_FINISHED) > 0.5 && obj_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) < 0.5 && finish_screen_closed == 0) {
		finish_screen_closed = 1;
		close_finish_screen(self, other);
	}
	// mouse: orbit (third person) / orientation (free camera)
	if (focus) {
		cam_yaw += mx * MOUSE_SENSITIVITY;
		cam_pitch -= my * MOUSE_SENSITIVITY;
	}
	if (cam_pitch > 85) cam_pitch = 85;
	if (cam_pitch < -85) cam_pitch = -85;

	if (view == VIEW_FREE) {
		if (free_initialised == 0) {
			if (cam_eye[0] == 0 && cam_eye[1] == 0 && cam_eye[2] == 0) {
				// no view yet: starts from the camera of the game
				cam_eye[0] = obj_var(OBJ_CAMERA, 1708);
				cam_eye[1] = obj_var(OBJ_CAMERA, 1736);
				cam_eye[2] = obj_var(OBJ_CAMERA, 1785);
				cam_yaw = -obj_var(OBJ_CAMERA, 934);
				cam_pitch = obj_var(OBJ_CAMERA, 1751);
			}
			for (int k = 0; k < 3; k++) free_pos[k] = cam_eye[k];
			free_initialised = 1;
		}
		double d[3];
		direction(cam_yaw, cam_pitch, d);
		double a = cam_yaw * M_PI / 180.0;
		double rx = -sin(a), ry = cos(a);// right
		double speed = (key_down(VK_SHIFT) ? 36.0 : 12.0) * dt;
		if (focus && ui_open == 0) {
			double v[3] = { 0, 0, 0 };
			if (key_down('Z') || key_down('W')) for (int k = 0; k < 3; k++) v[k] += d[k];
			if (key_down('S')) for (int k = 0; k < 3; k++) v[k] -= d[k];
			if (key_down('D')) { v[0] += rx; v[1] += ry; }
			if (key_down('Q') || key_down('A')) { v[0] -= rx; v[1] -= ry; }
			if (key_down(VK_SPACE)) v[2] += 1;
			if (key_down('C')) v[2] -= 1;
			for (int k = 0; k < 3; k++) free_pos[k] += v[k] * speed;
		}
		for (int k = 0; k < 3; k++) {
			cam_eye[k] = free_pos[k];
			cam_dir[k] = d[k];
		}
		target_name[0] = 0;
		cam_up[0] = cam_up[1] = 0;
		cam_up[2] = 1;
		return;
	}

	NetPlayer *t = find_player(target_id);
	if (t == NULL) {
		target_id = pick_target(-1);
		t = find_player(target_id);
	}
	if (t == NULL) {// nobody to follow: free camera (never the frozen view of the local player)
		view = VIEW_FREE;
		free_initialised = 0;
		return;
	}
	snprintf(target_name, sizeof target_name, "%s", t->name);
	// finish of the followed player (cup): position and gap with the first one
	long finish = cup_finish_ms(target_id);
	if (finish >= 0 && gap_finish_shown != target_id) {
		gap_finish_shown = target_id;
		show_gap(target_id, 0, finish);
	} else if (finish < 0 && gap_finish_shown == target_id) {
		gap_finish_shown = -1;
	}
	double yaw, pitch;
	target_look(t, &yaw, &pitch);
	cam_up[0] = 0;
	cam_up[1] = 0;
	cam_up[2] = 1;
	if (view == VIEW_FIRST_PERSON) {
		cam_yaw = yaw;// the third person view starts behind the player
		cam_pitch = pitch;
		// the camera of the followed player as his game computes it (rolls, slides, wall runs): eye, direction, up vector
		if (prediction_camera(t->id, cam_eye, cam_dir, cam_up) == 0) {
			cam_eye[0] = t->x;
			cam_eye[1] = t->y;
			cam_eye[2] = t->z + eye_height;
			direction(yaw, pitch, cam_dir);
			cam_up[0] = cam_up[1] = 0;
			cam_up[2] = 1;
		}
	} else {
		if (orbit_initialised == 0) {
			cam_yaw = yaw;
			cam_pitch = -15;
			orbit_initialised = 1;
		}
		direction(cam_yaw, cam_pitch, cam_dir);
		double centre[3] = { t->x, t->y, t->z + eye_height * 0.75 };
		for (int k = 0; k < 3; k++) cam_eye[k] = centre[k] - cam_dir[k] * THIRD_PERSON_DISTANCE;
	}
	if (free_initialised == 0)
		for (int k = 0; k < 3; k++) free_pos[k] = cam_eye[k];// the free camera starts from the current view
}

// ---------------------------------------------------------------- gap with the first player (middle of the screen)

// Like Trackmania: at every checkpoint and at the finish, the position and the time, and below the gap with the best time
// (red: behind, blue: ahead of the second one / of the record). Cup: times of the round. Time attack: best runs of the map.
#define GAP_DISPLAY_MS 3000
static struct {
	double until;
	int rank;
	long ms, gap;
	int has_gap;
} gapbox;

static void gap_clear(void) { gapbox.until = 0; }

static void show_gap(int id, int checkpoint, long ms) {
	int rank = 1;
	long gap = 0;
	gapbox.has_gap = cup_compare(id, checkpoint, ms, &rank, &gap);
	gapbox.rank = rank;
	gapbox.ms = ms;
	gapbox.gap = gap;
	gapbox.until = clock_local_ms() + GAP_DISPLAY_MS;
}

void spectator_local_checkpoint(int index, long ms) {
	if (spectating == 0) show_gap(cup_local_id(), index, ms);
}

// checkpoint of a player (message of the server, with the index of the checkpoint): followed player while spectating,
// or the local player while racing
void spectator_player_checkpoint(int id, int index, long ms) {
	if (spectating && view != VIEW_FREE && id == target_id) show_gap(id, index, ms);
	else if (spectating == 0 && id == cup_local_id()) show_gap(id, index, ms);
}

// ---------------------------------------------------------------- rendering

// native d3d_set_projection_ext(xfrom, yfrom, zfrom, ...): eye height of the local player (first person views)
static void __cdecl hook_projection(RValue *result, void *self, void *other, int argc, RValue *args) {
	if (spectating == 0 && argc >= 9 && mod_active()) {
		double h = yy_to_real(&args[2]) - obj_var(OBJ_PLAYER, VAR_Z);
		if (h > 0.5 && h < 10) eye_height = h;
	}
	orig_projection(result, self, other, argc, args);
}

// camera of the game (obj_camera and its values of the previous step, for the interpolation): eye (xfrom, yfrom, zfrom),
// direction vector (xto, yto, zto), up vector, yaw (mouse_direction = - world yaw), pitch (z_angle), forward offset of
// the eye. While spectating they are replaced by the spectator camera during the rendering of the scene: the game computes
// the projection, the culling, the level of detail, the shadows and the effects from the spectator camera.
#define VAR_CAMERA_MOUSE_DIRECTION 934
#define VAR_CAMERA_Z_ANGLE 1751
#define VAR_CAMERA_FORWARD_OFFSET 570
static const int CAMERA_OVERRIDE_VARS[12] = { 1708, 1736, 1785, 1717, 1747, 1796, 1718, 1748, 1797,
	VAR_CAMERA_MOUSE_DIRECTION, VAR_CAMERA_Z_ANGLE, VAR_CAMERA_FORWARD_OFFSET };

static RValue *render_scene(void *self, void *other, RValue *result, int argc, RValue **args);

static RValue *__cdecl hook_render_scene(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_active() == 0) {
		spectating = 0;
		return orig_render_scene(self, other, result, argc, args);
	}
	prediction_interpolate_begin(self, other);// the other players between two steps (also for the spectator camera)
	render_scene(self, other, result, argc, args);
	prediction_interpolate_end();
	return result;
}

static RValue *render_scene(void *self, void *other, RValue *result, int argc, RValue **args) {
	update(self, other);
	if (spectating == 0) return orig_render_scene(self, other, result, argc, args);

	RValue pre = { 0 };
	YY_VAR_GET(OBJ_CAMERA, VAR_PRE_VALUES, ARRAY_NONE, &pre);
	int pre_instance = YY_GET_INT32(&pre);
	yy_clear(&pre);
	double values[12] = { cam_eye[0], cam_eye[1], cam_eye[2], cam_dir[0], cam_dir[1], cam_dir[2], cam_up[0], cam_up[1], cam_up[2],
		-cam_yaw, asin(cam_dir[2] > 1 ? 1 : cam_dir[2] < -1 ? -1 : cam_dir[2]) * 180.0 / M_PI, 0 };
	double saved[2][12];
	for (int k = 0; k < 12; k++) {
		saved[0][k] = obj_var(OBJ_CAMERA, CAMERA_OVERRIDE_VARS[k]);
		saved[1][k] = obj_var(pre_instance, CAMERA_OVERRIDE_VARS[k]);
		set_obj_var(OBJ_CAMERA, CAMERA_OVERRIDE_VARS[k], values[k]);
		set_obj_var(pre_instance, CAMERA_OVERRIDE_VARS[k], values[k]);
	}
	// first person: the model of the followed player would be around the camera
	NetPlayer *t = view == VIEW_FIRST_PERSON ? find_player(target_id) : NULL;
	double hidden_z = 0;
	if (t && t->inst) {
		RValue *mz = yy_self_var(t->inst, VAR_MODEL_Z);
		hidden_z = yy_to_real(mz);
		yy_set_real(mz, hidden_z - 100000);
	}
	orig_render_scene(self, other, result, argc, args);
	if (t && t->inst) yy_set_real(yy_self_var(t->inst, VAR_MODEL_Z), hidden_z);
	for (int k = 0; k < 12; k++) {
		set_obj_var(OBJ_CAMERA, CAMERA_OVERRIDE_VARS[k], saved[0][k]);
		set_obj_var(pre_instance, CAMERA_OVERRIDE_VARS[k], saved[1][k]);
	}
	return result;
}

// the local player (waiting on the start line) isn't drawn while spectating
static RValue *__cdecl hook_draw_player_model_allowed(void *self, void *other, RValue *result, int argc, RValue **args) {
	orig_draw_player_model_allowed(self, other, result, argc, args);
	if (spectating) yy_set_real(result, 0);
	return result;
}

// ---------------------------------------------------------------- HUD

static void hud_font(int halign, int valign) {
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_FONT))(FONT_HUD);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_HALIGN))(halign);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(valign);
}

static void hud_colour(int colour, float alpha) {
	((void(__cdecl *)(float))ADDR(DIRECT_DRAW_SET_ALPHA))(alpha);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_COLOUR))(colour);
}

static void hud_rect(float x1, float y1, float x2, float y2, int colour, float alpha) {
	hud_colour(colour, alpha);
	((void(__cdecl *)(float, float, float, float, int))ADDR(DIRECT_DRAW_RECTANGLE))(x1, y1, x2, y2, 0);
}

static void hud_text(void *self, void *other, double x, double y, const char *text, double scale, int colour) {
	hud_colour(colour, 1.0f);
	RValue a[6] = { { { 0 } } }, r = { 0 };
	a[0].real = x;
	a[1].real = y;
	YY_CREATE_STRING(&a[2], text);
	a[3].real = scale;
	a[4].real = scale;
	a[5].real = 0;
	RValue *args[6] = { &a[0], &a[1], &a[2], &a[3], &a[4], &a[5] };
	YY_CALL_BUILTIN(self, other, &r, 6, *(void **)ADDR(SLOT_FN_DRAW_TEXT_TRANSFORMED), args);
	yy_clear(&r);
	yy_clear(&a[2]);
}

static void render_size(void *self, void *other, double *w, double *h) {
	RValue rw = { 0 }, rh = { 0 };
	((GmlScript)ADDR(GML_GET_RENDER_W))(self, other, &rw, 0, NULL);
	((GmlScript)ADDR(GML_GET_RENDER_H))(self, other, &rh, 0, NULL);
	*w = yy_to_real(&rw);
	*h = yy_to_real(&rh);
	yy_clear(&rw);
	yy_clear(&rh);
}

// approximate width of a text of the HUD font (pixels at scale 1)
#define SLOT_FN_STRING_WIDTH 0x00f38804
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
static float text_height(void *self, void *other) { return text_size(self, other, "A", SLOT_FN_STRING_HEIGHT); }

// top left: round of the map (cup) and warmup (yellow box next to it; time attack: remaining time of the warmup)
static void draw_round(void *self, void *other) {
	if (cup_known() == 0) return;
	float th = text_height(self, other) * 1.6f;
	float x = 10;
	hud_font(0, 1);
	if (cup_mode() && cup_rounds() > 0) {
		char text[64];
		int round = cup_round() > 0 ? cup_round() : 1;
		snprintf(text, sizeof text, "ROUND %d / %d", round, cup_rounds());
		float right = x + 16 + text_width(self, other, text) * 1.6f;
		hud_rect(x, 10, right, 18 + th, COLOUR_BLACK, 0.6f);
		hud_text(self, other, x + 8, 14 + th / 2, text, 1.6, COLOUR_WHITE);
		x = right + 8;
	}
	int index, count;
	double remaining;
	if (cup_warmup(&index, &count, &remaining)) {
		char text[64];
		if (remaining >= 0) {// warmup with a duration: remaining time
			long s = (long)(remaining / 1000.0 + 0.999);
			snprintf(text, sizeof text, "WARMUP %ld:%02ld", s / 60, s % 60);
		} else if (count > 1) snprintf(text, sizeof text, "WARMUP %d / %d", index, count);
		else snprintf(text, sizeof text, "WARMUP");
		float right = x + 16 + text_width(self, other, text) * 1.6f;
		hud_rect(x, 10, right, 18 + th, COLOUR_YELLOW, 0.85f);
		hud_text(self, other, x + 8, 14 + th / 2, text, 1.6, COLOUR_BLACK);
	}
}

static void draw_spectator_hint(void *self, void *other, double w, double h) {
	if (spectating_allowed() == 0) return;
	char text[160];
	if (spectating) {
		if (view == VIEW_FREE) snprintf(text, sizeof text, "SPECTATOR - free camera   [V] view  [Mouse] look  [ZQSD/WASD] move  [Space/C] up/down  [Shift] fast");
		else if (view == VIEW_THIRD_PERSON) snprintf(text, sizeof text, "SPECTATOR - third person   [V] view  [Mouse] turn around  [Left/Right] player");
		else snprintf(text, sizeof text, "SPECTATOR - %s   [V] view  [Left/Right] player", VIEW_NAMES[view]);
	} else {
		return;// a player who races: no hint (Spectator button of the pause menu)
	}
		float th = text_height(self, other);
	float top = 26 + th * 1.6f;// below the round
	hud_font(0, 1);
	hud_rect(10, top, 22 + text_width(self, other, text), top + th + 6, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 16, top + 3 + th / 2, text, 1.0, COLOUR_WHITE);
	// name of the followed player above the timer
	if (spectating && view != VIEW_FREE && target_name[0]) {
		hud_font(1, 1);
		float tw = text_width(self, other, target_name) * 1.6f, nh = th * 1.6f;
		float y = (float)(h * 0.835);
		hud_font(1, 1);
		hud_rect((float)(w / 2) - tw / 2 - 12, y - nh / 2 - 4, (float)(w / 2) + tw / 2 + 12, y + nh / 2 + 4, COLOUR_BLACK, 0.6f);
		hud_text(self, other, w / 2, y, target_name, 1.6, COLOUR_YELLOW);
	}
}

// timer of the followed player when the game doesn't draw its timer (local player finished)
static void draw_spectated_timer(void *self, void *other, double w, double h) {
	if (spectating == 0 || view == VIEW_FREE || g_frame - timer_drawn_frame < 3) return;
	g_in_timer_draw = 1;
	double ms = spectator_timer_ms();
	g_in_timer_draw = 0;
	if (ms < 0) return;
	long t = (long)ms;
	char text[32];
	snprintf(text, sizeof text, "%02ld:%02ld:%03ld", t / 60000, (t / 1000) % 60, t % 1000);
	hud_font(1, 1);
	hud_text(self, other, w / 2 + 2, h * 0.918 + 2, text, 2.6, COLOUR_BLACK);
	hud_text(self, other, w / 2, h * 0.918, text, 2.6, COLOUR_WHITE);
}

// big message in the middle of the screen (observer mode)
static void draw_big_message(void *self, void *other, double w, double h) {
	if (big_message[0] == 0 || clock_local_ms() > big_message_until) return;
	float tw = text_width(self, other, big_message) * 3.0f, th = text_height(self, other) * 3.0f;
	float y = (float)(h * 0.3);
	hud_font(1, 1);
	hud_rect((float)(w / 2) - tw / 2 - 24, y - th / 2 - 12, (float)(w / 2) + tw / 2 + 24, y + th / 2 + 12, COLOUR_BLACK, 0.6f);
	hud_text(self, other, w / 2, y, big_message, 3.0, COLOUR_YELLOW);
}

static const char *ordinal_suffix(int n) {
	if (n % 100 >= 11 && n % 100 <= 13) return "th";
	switch (n % 10) {
	case 1: return "st";
	case 2: return "nd";
	case 3: return "rd";
	}
	return "th";
}

#define COLOUR_GAP_RED 0x3030D0// BGR
#define COLOUR_GAP_BLUE 0xD07020
#define COLOUR_DARK 0x302020

static void draw_gap(void *self, void *other, double w, double h) {
	if (gapbox.until == 0 || clock_local_ms() > gapbox.until) return;
	char line1[48], line2[32];
	long t = gapbox.ms;
	snprintf(line1, sizeof line1, "%d%s  %02ld:%02ld:%03ld", gapbox.rank, ordinal_suffix(gapbox.rank), t / 60000, (t / 1000) % 60, t % 1000);
	long g = gapbox.gap < 0 ? -gapbox.gap : gapbox.gap;
	snprintf(line2, sizeof line2, "%c%02ld:%02ld:%03ld", gapbox.gap <= 0 ? '-' : '+', g / 60000, (g / 1000) % 60, g % 1000);
	const float scale = 1.5f;
	float th = text_height(self, other) * scale;
	float w1 = text_width(self, other, line1) * scale, w2 = text_width(self, other, line2) * scale;
	float cx = (float)(w / 2), y = (float)(h * 0.26);
	hud_font(1, 1);
	hud_rect(cx - w1 / 2 - 14, y - th / 2 - 5, cx + w1 / 2 + 14, y + th / 2 + 5, COLOUR_DARK, 0.75f);
	hud_text(self, other, cx, y, line1, scale, COLOUR_WHITE);
	if (gapbox.has_gap) {
		float y2 = y + th + 10;
		hud_rect(cx - w2 / 2 - 14, y2 - th / 2 - 5, cx + w2 / 2 + 14, y2 + th / 2 + 5, gapbox.gap > 0 ? COLOUR_GAP_RED : COLOUR_GAP_BLUE, 0.85f);
		hud_text(self, other, cx, y2, line2, scale, COLOUR_WHITE);
	}
}

// checkpoint ranking of the round (bottom right, above the speed)
static void draw_checkpoint_ranking(void *self, void *other, double w, double h) {
	if (cup_known() == 0) return;
	CupRankLine lines[10];
	int highlight_id = spectating && view != VIEW_FREE ? target_id : cup_local_id();
	int count = cup_build_ranking(lines, 10, highlight_id);
	if (count == 0) return;
		const float fs = 1.1f;
	float line_h = text_height(self, other) * fs + 6;
	float col_name = text_width(self, other, "00 ") * fs;
	float col_where = col_name + text_width(self, other, "WWWWWWWWWWW") * fs;
	float col_time = col_where + text_width(self, other, "CP00 ") * fs;
	float width = col_time + text_width(self, other, "+00:00:000") * fs + 16;
	// top left, below the round / warmup boxes (and the hint of the spectator)
	float th1 = text_height(self, other);
	float top = 26 + th1 * 1.6f + (spectating ? th1 + 14 : 0);
	float left = 10, right = left + width, bottom = top + line_h * (float)count + 8;
	(void)w;
	(void)h;
	hud_rect(left, top, right, bottom, COLOUR_BLACK, 0.55f);
	hud_font(0, 1);
	for (int i = 0; i < count; i++) {
		float y = top + 4 + line_h * ((float)i + 0.5f);
		int colour = lines[i].id == highlight_id ? COLOUR_YELLOW : COLOUR_WHITE;
		char pos[8];
		snprintf(pos, sizeof pos, "%d", lines[i].place);
		char name[16];
		snprintf(name, sizeof name, "%.11s", player_name(lines[i].id));
		hud_text(self, other, left + 8, y, pos, fs, colour);
		hud_text(self, other, left + 8 + col_name, y, name, fs, colour);
		hud_text(self, other, left + 8 + col_where, y, lines[i].where, fs, colour);
		hud_text(self, other, left + 8 + col_time, y, lines[i].time, fs, colour);
	}
}

// drawn right after the names of the other players: same draw state as the game's own text
static RValue *__cdecl hook_render_usernames(void *self, void *other, RValue *result, int argc, RValue **args) {
	prediction_interpolate_begin(self, other);// names at the displayed position of the players
	orig_render_usernames(self, other, result, argc, args);
	prediction_interpolate_end();
		if (mod_active() && g_server_supports_mod && obj_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) < 0.5) {
		double w, h;
		render_size(self, other, &w, &h);
		draw_round(self, other);
		draw_spectator_hint(self, other, w, h);
		draw_checkpoint_ranking(self, other, w, h);
		draw_spectated_timer(self, other, w, h);
		draw_big_message(self, other, w, h);
		draw_gap(self, other, w, h);
		hud_colour(COLOUR_WHITE, 1.0f);
		((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_HALIGN))(0);
		((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(0);
	}
	return result;
}

// time shown by the timer while spectating: race time of the followed player (or his finish time), -1 = no change
double spectator_timer_ms(void) {
	if (spectating == 0 || view == VIEW_FREE || g_in_timer_draw == 0) return -1;
	if (cup_mode() == 0) return prediction_race_time(target_id);// time attack: timer of the run of the followed player
	long finish = cup_finish_ms(target_id);
	if (finish >= 0) return (double)finish;
	if (cup_state(target_id) == 'R' && clock_race_start() > 0) {
		double t = clock_server_ms() - clock_race_start();
		return t > 0 ? t : 0;
	}
	return -1;
}

static void __cdecl hook_timer_draw(void *self, void *other) {
	timer_drawn_frame = g_frame;
	g_in_timer_draw = 1;
	orig_timer_draw(self, other);
	g_in_timer_draw = 0;
}

// ---------------------------------------------------------------- pause menu: observer mode

static int cup_menu_active(void) { return mod_active() && g_server_supports_mod && cup_known() && is_multiplayer(); }

// pause menu of a cup server: button "Observer" (watch the cup without racing) / "Play", between "Resume" and "Sound"
// (place of the "Reset Run" button of the single player mode). Its behaviour is the "Reset Run" script, hooked below.
static RValue *__cdecl hook_create_button_left_bar(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc >= 3) {
		const char *text = yy_string(args[2]);
		if (text && strcmp(text, "Sound") == 0) button_y_sound = yy_to_real(args[1]);
		if (text && strcmp(text, "Controls") == 0) button_y_controls = yy_to_real(args[1]);
		// cup: the time of the round is validated, the player waits for the others: no "Restart Map" (= DNF); the button
		// is created out of the screen (the game uses the instance it returns)
		if (text && strcmp(text, "Restart Map") == 0 && cup_menu_active() && cup_mode() && (args[1]->kind & 0xffffff) == KIND_REAL)
			args[1]->real = -100000;
		if (text && strcmp(text, "Reset Run") == 0 && cup_menu_active()) {
			yy_set_string(args[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Spectator");
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
		YY_CREATE_STRING(&a[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Spectator");
		a[3].real = SCRIPT_BEV_SPEEDRUN_RESET_ASK;
		a[4].real = SPRITE_RESET_ICON;
		RValue *pa[5] = { &a[0], &a[1], &a[2], &a[3], &a[4] };
		orig_create_button_left_bar(self, other, &r, 5, pa);
		yy_clear(&r);
		yy_clear(&a[0]);
		yy_clear(&a[2]);
	}
	return result;
}

static RValue *__cdecl hook_reset_ask(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (cup_menu_active()) {
		RValue value = { 0 };
		int observer = cup_state(cup_local_id()) != 'V';
		value.real = observer;
		net_send_tcp(self, other, 208, BUFFER_U8, &value);
		mod_log("observer mode requested: %d", observer);
		cup_set_local_state(observer ? 'V' : 'N');// the button shows "Play" / "Spectator" at once
		snprintf(big_message, sizeof big_message, "%s", observer ? "You are now a Spectator" : "You will race from the next round");
		big_message_until = clock_local_ms() + 3000;
		if (observer) {
			not_racing_since = clock_local_ms() - AUTO_SPECTATE_DELAY_MS;// spectator view at once
		} else {
			view = VIEW_OFF;// back to the race (next round)
			spectating = 0;
		}
		RValue r = { 0 };
		((GmlScript)ADDR(GML_BEV_RESUME_GAME))(self, other, &r, 0, NULL);// the pause menu is closed
		yy_clear(&r);
		yy_set_real(result, 0);
		return result;
	}
	return orig_reset_ask(self, other, result, argc, args);
}

// ---------------------------------------------------------------- mod label

// "Lanppart3 Mod" in the bottom left corner: the mod is loaded (white), active on a LAN server (green).
// Drawn at the end of scr_draw_gui, on top of everything (menus and game).
static void draw_mod_label(void *self, void *other) {
	double w, h;
	render_size(self, other, &w, &h);
		const double scale = 1.4;
	float th = text_height(self, other) * (float)scale;
	float top = (float)h - th - 12;
	hud_font(0, 1);
	hud_rect(4, top, 20 + text_width(self, other, MOD_LABEL) * (float)scale, top + th + 8, COLOUR_BLACK, 0.6f);
	hud_text(self, other, 12, top + 4 + th / 2, MOD_LABEL, scale, mod_active() ? COLOUR_GREEN : COLOUR_WHITE);
	hud_colour(COLOUR_WHITE, 1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(0);
}

// spectators (observers) in the bottom left corner, above the mod label, while the leaderboard is shown:
// an eye and the name
static void draw_eye(float cx, float cy, float size) {
	float w = size * 0.5f, hh = size * 0.32f;
	for (float dy = -hh; dy <= hh; dy += 1.0f) {// almond: white
		float k = dy / hh, half = w * (1.0f - k * k);
		hud_rect(cx - half, cy + dy, cx + half, cy + dy + 1.0f, COLOUR_WHITE, 1.0f);
	}
	float r = size * 0.2f;
	for (float dy = -r; dy <= r; dy += 1.0f) {// iris
		float half = (float)sqrt(r * r - dy * dy);
		hud_rect(cx - half, cy + dy, cx + half, cy + dy + 1.0f, 0xC08020, 1.0f);
	}
	float p = size * 0.09f;
	for (float dy = -p; dy <= p; dy += 1.0f) {// pupil
		float half = (float)sqrt(p * p - dy * dy);
		hud_rect(cx - half, cy + dy, cx + half, cy + dy + 1.0f, COLOUR_BLACK, 1.0f);
	}
}

static void draw_spectator_list(void *self, void *other) {
	if (mod_active() == 0 || g_server_supports_mod == 0 || is_multiplayer() == 0 || leaderboard_panel_open() == 0) return;
	const char *names[16];
	int count = cup_spectator_list(names, 16);
	if (count == 0) return;
	double w, h;
	render_size(self, other, &w, &h);
	const double scale = 1.2;
	hud_font(0, 1);
	float th = text_height(self, other) * (float)scale, line = th + 10;
	float bottom = (float)h - text_height(self, other) * 1.4f - 24;// above the mod label
	for (int i = 0; i < count; i++) {
		float y = bottom - line * (float)(count - i);
		char text[96];
		snprintf(text, sizeof text, "-  %s", names[i]);
		float icon = th * 1.3f;
		hud_rect(4, y, 20 + icon + 6 + text_width(self, other, text) * (float)scale, y + line - 2, COLOUR_BLACK, 0.6f);
		draw_eye(12 + icon / 2, y + (line - 2) / 2, icon);
		hud_font(0, 1);
		hud_text(self, other, 12 + icon + 6, y + (line - 2) / 2, text, scale, COLOUR_WHITE);
	}
	hud_colour(COLOUR_WHITE, 1.0f);
	((void(__cdecl *)(int))ADDR(DIRECT_DRAW_SET_VALIGN))(0);
}

// end of the map / of the round (cup server): the black bar of the left menu covers only its buttons (the checkpoint
// ranking stays visible above it). The buttons draw themselves after the bar: top of the highest one in the last frame.
#define GML_OBJ_BLACK_BAR_DEFAULT_DRAW 0x009fa160// obj_black_bar_default user event 0: full height bar
#define GML_OBJ_BUTTON_LEFT_BAR_DRAW 0x009eb370// obj_button_left_bar user event 1: draws the button
#define VAR_GLOBAL_LEFTBAR_X 0x2f7
#define VAR_GLOBAL_LEFTBAR_W 0x2f5
#define VAR_GLOBAL_LEFTBAR_BUTTON_H 0x2f4
static GmlEvent orig_black_bar_draw, orig_button_left_bar_draw;

static double builtin_var_of(void *inst, uintptr_t slot) {
	RValue v = { 0 };
	YY_BUILTIN_GET(inst, *(int *)ADDR(slot), ARRAY_NONE, &v);
	double d = yy_to_real(&v);
	yy_clear(&v);
	return d;
}
static double buttons_top, buttons_top_frame_value;
static unsigned buttons_frame;

static void __cdecl hook_button_left_bar_draw(void *self, void *other) {
	orig_button_left_bar_draw(self, other);
	if (self == NULL) return;
	double y = builtin_var_of(self, SLOT_BUILTIN_VAR_B);
	if (buttons_frame != g_frame) {// new frame: the value of the last one is kept for the bar
		buttons_top_frame_value = buttons_top;
		buttons_frame = g_frame;
		buttons_top = 1e9;
	}
	if (y > -1000 && y < buttons_top) buttons_top = y;
}

// variables of the left bar: object 77 (as obj_black_bar_default reads them)
#define OBJ_LEFTBAR_VARS 77
static double global_real(int var) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_LEFTBAR_VARS, var, ARRAY_NONE, &v);
	int kind = v.kind & 0xffffff;
	double d = kind == KIND_REAL ? v.real : kind == 7 ? v.i32 : kind == 10 ? (double)v.i64 : 0;
	yy_clear(&v);
	return d;
}

static void __cdecl hook_black_bar_draw(void *self, void *other) {
	int finish = obj_var(OBJ_CONTROLLER, VAR_MAP_FINISHED) > 0.5 || obj_var(OBJ_NETWORK_DATA, VAR_ROUND_OVER) > 0.5;
	double top_button = g_frame - buttons_frame <= 2 ? (buttons_frame == g_frame ? buttons_top_frame_value : buttons_top) : 1e9;
	if (finish == 0 || top_button >= 1e9 || cup_menu_active() == 0 || obj_var(OBJ_CONTROLLER, VAR_PAUSE) > 0.5) {
		orig_black_bar_draw(self, other);
		return;
	}
	double w, h;
	render_size(self, other, &w, &h);
	double x = global_real(VAR_GLOBAL_LEFTBAR_X), bw = global_real(VAR_GLOBAL_LEFTBAR_W), bh = global_real(VAR_GLOBAL_LEFTBAR_BUTTON_H);
	static int logged;
	if (logged == 0) {
		logged = 1;
		mod_log("finish menu: left bar x %.0f w %.0f button h %.0f, top button %.0f", x, bw, bh, top_button);
	}
	if (bw <= 0) {
		orig_black_bar_draw(self, other);
		return;
	}
	double top = top_button - (bh > 0 ? bh * 0.5 : 20);
	if (top < 0) top = 0;
	hud_rect((float)x, (float)top, (float)(x + bw), (float)h, COLOUR_BLACK, 0.8f);
	hud_colour(COLOUR_WHITE, 1.0f);
}

static void set_alphablend(void *self, void *other, RValue *value) {
	RValue r = { 0 };
	RValue *args[1] = { value };
	YY_CALL_BUILTIN(self, other, &r, 1, *(void **)ADDR(SLOT_FN_DRAW_ENABLE_ALPHABLEND), args);
	yy_clear(&r);
}

// scr_draw_gui ends with alpha blending disabled (text would be drawn as boxes): enabled for the label, then the state
// left by the game is restored
unsigned g_frame;

#define VAR_SPEED 64// obj_player._speed: horizontal speed (per step) shown by the speedometer
#define VAR_MOVEMENT_X 960
#define VAR_MOVEMENT_Y 961
#define VAR_GUI_MAP_END_CREATED 624// obj_controller: the map finished screen was created (no speedometer)

// end of a round: the game fades the screen to white (white_overlay_alpha_value of obj_controller, ROUND_END): not drawn
#define GML_SCR_RENDER_WHITE_OVERLAY_SCREEN 0x005a42b0
static GmlScript orig_white_overlay;
static RValue *__cdecl hook_white_overlay(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_active() && g_server_supports_mod) {
		yy_set_real(result, 0);
		return result;
	}
	return orig_white_overlay(self, other, result, argc, args);
}

static RValue *__cdecl hook_draw_gui(void *self, void *other, RValue *result, int argc, RValue **args) {
	static int natives_wrapped;
	if (natives_wrapped == 0) {// the table of the native functions exists once the game runs
		natives_wrapped = 1;
		prediction_wrap_natives();
		lanbrowser_wrap_natives();
	}
	respawn_update(self, other);
	autoconnect_update(self, other);
	// spectator: the speedometer shows the speed of the followed player (velocity of the network player per step)
	NetPlayer *t = spectating && view != VIEW_FREE ? find_player(target_id) : NULL;
	double saved_speed = 0, saved_map_end = 0;
	if (t && t->inst) {
		double mx = yy_to_real(yy_self_var(t->inst, VAR_MOVEMENT_X)), my = yy_to_real(yy_self_var(t->inst, VAR_MOVEMENT_Y));
		saved_speed = obj_var(OBJ_PLAYER, VAR_SPEED);
		saved_map_end = obj_var(OBJ_CONTROLLER, VAR_GUI_MAP_END_CREATED);
		set_obj_var(OBJ_PLAYER, VAR_SPEED, sqrt(mx * mx + my * my));
		set_obj_var(OBJ_CONTROLLER, VAR_GUI_MAP_END_CREATED, 0);
	}
	// spectator without start signal (observer): the game writes "Waiting for server..." while delayTimeOver = 0
	int hide_waiting = spectating && obj_var(OBJ_NETWORK_DATA, VAR_DELAY_TIME_OVER) < 0.5;
	if (hide_waiting) set_obj_var(OBJ_NETWORK_DATA, VAR_DELAY_TIME_OVER, 1);
	orig_draw_gui(self, other, result, argc, args);
	if (hide_waiting) set_obj_var(OBJ_NETWORK_DATA, VAR_DELAY_TIME_OVER, 0);
	lanbrowser_update(self, other);
	if (t && t->inst) {
		set_obj_var(OBJ_PLAYER, VAR_SPEED, saved_speed);
		set_obj_var(OBJ_CONTROLLER, VAR_GUI_MAP_END_CREATED, saved_map_end);
	}
	g_frame++;
	RValue on = { 0 };
	on.real = 1;
	set_alphablend(self, other, &on);
	leaderboard_draw_cup(self, other);
	draw_spectator_list(self, other);
	draw_mod_label(self, other);
	set_alphablend(self, other, (RValue *)ADDR(CONST_LAST_ALPHABLEND));
	return result;
}

void spectator_install(void) {
	static const uint8_t native_prologue[] = { 0x56, 0x8B, 0x74, 0x24, 0x18 };// push esi; mov esi,[esp+18h]
	orig_projection = (NativeFn)hook_install(ADDR(NATIVE_D3D_SET_PROJECTION_EXT), native_prologue, sizeof native_prologue, hook_projection);
	orig_render_scene = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_SCENE), PROLOGUE, sizeof PROLOGUE, hook_render_scene);
	orig_draw_player_model_allowed = (GmlScript)hook_install(ADDR(GML_DRAW_PLAYER_MODEL_ALLOWED), PROLOGUE, sizeof PROLOGUE, hook_draw_player_model_allowed);
	orig_render_usernames = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_USERNAMES), PROLOGUE, sizeof PROLOGUE, hook_render_usernames);
	orig_draw_gui = (GmlScript)hook_install(ADDR(GML_SCR_DRAW_GUI), PROLOGUE, sizeof PROLOGUE, hook_draw_gui);
	orig_timer_draw = (GmlEvent)hook_install(ADDR(GML_OBJ_TIMER_DRAW), PROLOGUE, sizeof PROLOGUE, hook_timer_draw);
	orig_create_button_left_bar = (GmlScript)hook_install(ADDR(GML_SCR_CREATE_BUTTON_LEFT_BAR), PROLOGUE, sizeof PROLOGUE, hook_create_button_left_bar);
		orig_reset_ask = (GmlScript)hook_install(ADDR(GML_BEV_SPEEDRUN_RESET_ASK), PROLOGUE, sizeof PROLOGUE, hook_reset_ask);
	orig_create_pause_buttons = (GmlScript)hook_install(ADDR(GML_SCR_CREATE_PAUSE_BUTTONS), PROLOGUE, sizeof PROLOGUE, hook_create_pause_buttons);
	orig_white_overlay = (GmlScript)hook_install(ADDR(GML_SCR_RENDER_WHITE_OVERLAY_SCREEN), PROLOGUE, sizeof PROLOGUE, hook_white_overlay);
	orig_button_left_bar_draw = (GmlEvent)hook_install(ADDR(GML_OBJ_BUTTON_LEFT_BAR_DRAW), PROLOGUE, sizeof PROLOGUE, hook_button_left_bar_draw);
	orig_black_bar_draw = (GmlEvent)hook_install(ADDR(GML_OBJ_BLACK_BAR_DEFAULT_DRAW), PROLOGUE, sizeof PROLOGUE, hook_black_bar_draw);
}
