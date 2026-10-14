// Celaria cup mod: loaded by the game as winExt.dll (proxy, the original is renamed winExt_orig.dll).
#include <windows.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include "hook.h"
#include "yyc.h"
#include "mod.h"

// ---------------------------------------------------------------- time display in milliseconds
// Times stay in "ticks" (1/100 second) everywhere in the game (records, replays, network, Steam leaderboards).
// A tick value can now have a fractional part (1523.7 ticks = 15.237 s): the display shows milliseconds.
static GmlScript orig_time_format;

// The running timer advances by one engine step (10 ms). For the display of the running timer, the time elapsed since
// the last step is added (real time, at most one step) so that the milliseconds move smoothly.
// The value is computed once per frame (g_frame): the timer is drawn several times per frame (shadow, text) and the
// digits must be the same.
static double smooth_running_time(double ticks) {
	static unsigned cached_frame = (unsigned)-1;
	static double cached_ticks, cached_result;
	if (cached_frame == g_frame && cached_ticks == ticks) return cached_result;
	static double origin = -1;// wall clock (in ticks) when akt_time was 0
	static LARGE_INTEGER freq;
	if (freq.QuadPart == 0) QueryPerformanceFrequency(&freq);
	RValue v = { 0 };
	YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
	double steps = yy_to_real(&v);
	yy_clear(&v);
	YY_VAR_GET(OBJ_TIMER, VAR_TIME_RUNNING, ARRAY_NONE, &v);
	int running = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	if (running == 0 || ticks != steps) return ticks;// not the running timer
	LARGE_INTEGER now;
	QueryPerformanceCounter(&now);
	double wall = (double)now.QuadPart / (double)freq.QuadPart * 100.0;
	double t = wall - origin;
	if (origin < 0 || t < steps - 1.0 || t > steps + 2.0) {// (re)synchronise with the game timer
		origin = wall - steps;
		t = steps;
	}
		if (t < steps) t = steps;
	if (t > steps + 0.999) t = steps + 0.999;
	cached_frame = g_frame;
	cached_ticks = ticks;
	cached_result = t;
	return t;
}

// race time of the local player (ms): timer of the run with the fraction of the step; between the start and the first
// step or when the local player doesn't race (spectator), time since the synchronised race start. -1 if unknown.
double race_time_now_ms(void) {
	RValue v = { 0 };
	YY_VAR_GET(OBJ_TIMER, VAR_TIME_RUNNING, ARRAY_NONE, &v);
	int running = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	YY_VAR_GET(OBJ_TIMER, VAR_AKT_TIME, ARRAY_NONE, &v);
	double steps = yy_to_real(&v);
	yy_clear(&v);
	if (running && steps > 0) return smooth_running_time(steps) * 10.0;
	if (clock_race_start() > 0 && cup_round_running()) return clock_server_ms() - clock_race_start();
	return -1;
}

static RValue *__cdecl hook_time_format(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc < 1 || mod_active() == 0) return orig_time_format(self, other, result, argc, args);
	// display only: precise time of a checkpoint/goal of the current run, or the running timer smoothed
	double official = yy_to_real(args[0]);
	double ticks = ms_precise_ticks(official);
	if (ticks == official) ticks = smooth_running_time(official);
	double spectated = spectator_timer_ms();// timer of the followed player while spectating
	if (spectated >= 0) ticks = spectated / 10.0;
	if (ticks < 0) ticks = 0;
	long long ms = (long long)floor(ticks * 10.0 + 1e-6);
	char buf[32];
	snprintf(buf, sizeof buf, "%02lld:%02lld:%03lld", ms / 60000, (ms / 1000) % 60, ms % 1000);
	yy_set_string(result, buf);
	return result;
}

// ---------------------------------------------------------------- install

uintptr_t g_image_base;

// identifies the executable version: scr_time_format starts with mov [esp+0x1cc], "gml_Script_scr_time_format" (relocated pointer)
static int is_known_executable(void) {
	// SizeOfImage of Celaria.exe (0x30A3BB8 rounded): don't read anything in another process
	IMAGE_DOS_HEADER *dos = (IMAGE_DOS_HEADER *)g_image_base;
	IMAGE_NT_HEADERS32 *nt = (IMAGE_NT_HEADERS32 *)(g_image_base + dos->e_lfanew);
	if (nt->OptionalHeader.SizeOfImage != 0x030A4000) {
		mod_log("SizeOfImage 0x%08x", (unsigned)nt->OptionalHeader.SizeOfImage);
		return 0;
	}
	static const uint8_t opcode[] = { 0xC7, 0x84, 0x24, 0xCC, 0x01, 0x00, 0x00 };
	uintptr_t at = ADDR(0x00450c78);
	return bytes_match(at, opcode, sizeof opcode) && *(uint32_t *)(at + sizeof opcode) == ADDR(0x00e06f65);
}

static void install(void) {
	g_image_base = (uintptr_t)GetModuleHandleA(NULL);
	mod_log("image base 0x%08x", (unsigned)g_image_base);
	if (is_known_executable() == 0) {
		mod_log("unknown Celaria.exe version: mod disabled");
		return;
	}
	orig_time_format = (GmlScript)hook_install(ADDR(GML_SCR_TIME_FORMAT), PROLOGUE, sizeof PROLOGUE, hook_time_format);
	ms_timing_install();
	net_install();
	spectator_install();
	records_tab_install();
	prediction_install();
	leaderboard_install();
	lanbrowser_install();
	respawn_install();
	autoconnect_install();
	mod_log("Celaria cup mod %d loaded", MOD_VERSION);
}

// ---------------------------------------------------------------- original winExt.dll

// every export of the game DLL is forwarded to winExt_orig.dll (the original winExt.dll renamed by install.bat). If it is
// missing (or is a copy of the mod), the game shows "Error defining an external function" forever: explain what to do.
static wchar_t orig_problem[160];

static int file_contains(const wchar_t *path, const char *text) {
	HANDLE f = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
	if (f == INVALID_HANDLE_VALUE) return 0;
	DWORD size = GetFileSize(f, NULL), read = 0;
	int found = 0;
	char *data = size != INVALID_FILE_SIZE && size < 16 * 1024 * 1024 ? malloc(size) : NULL;
	if (data && ReadFile(f, data, size, &read, NULL)) {
		size_t n = strlen(text);
		for (DWORD i = 0; i + n <= read && found == 0; i++) found = memcmp(data + i, text, n) == 0;
	}
	free(data);
	CloseHandle(f);
	return found;
}

static DWORD WINAPI show_orig_problem(LPVOID unused) {
	(void)unused;
	MessageBoxW(NULL, orig_problem, L"Lanppart3 Mod", MB_OK | MB_ICONERROR | MB_TOPMOST);
	return 0;
}

__attribute__((unused)) static void check_original_dll(HINSTANCE instance) {
	wchar_t path[MAX_PATH];
	DWORD n = GetModuleFileNameW(instance, path, MAX_PATH);
	wchar_t *slash = wcsrchr(path, 0x5c);
	if (n == 0 || n >= MAX_PATH || slash == NULL || (size_t)(slash - path) + 17 >= MAX_PATH) return;
	wcscpy(slash + 1, L"winExt_orig.dll");
	const wchar_t *what;
	if (GetFileAttributesW(path) == INVALID_FILE_ATTRIBUTES) what = L"winExt_orig.dll est introuvable";
	else if (file_contains(path, "winExt_orig.")) what = L"winExt_orig.dll est une copie du mod au lieu du fichier d'origine du jeu";
	else return;
	mod_log("winExt_orig.dll missing or invalid: the game can't load its functions");
	_snwprintf(orig_problem, sizeof orig_problem / sizeof orig_problem[0] - 1,
		L"%ls."
		L"\n\nRéparation : Steam > Celaria > Propriétés > Fichiers installés > Vérifier l'intégrité des fichiers, "
		L"puis relancer install.bat du mod.", what);
	HANDLE t = CreateThread(NULL, 0, show_orig_problem, NULL, 0, NULL);// not inside DllMain (loader lock)
	if (t) CloseHandle(t);
}

// path of a file in the folder of the mod DLL
static char dll_dir[MAX_PATH];
const char *mod_file_path(const char *name) {
	static char path[MAX_PATH + 64];
	snprintf(path, sizeof path, "%s%s", dll_dir, name);
	return path;
}

extern char g_log_path[520];

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
	(void)reserved;
	if (reason == DLL_PROCESS_ATTACH) {
		DWORD n = GetModuleFileNameA(instance, dll_dir, MAX_PATH);
		char *slash = n > 0 && n < MAX_PATH ? strrchr(dll_dir, 0x5c) : NULL;
		if (slash) slash[1] = 0;
		else dll_dir[0] = 0;
#ifdef MOD_INJECTED
		// loaded by the Celaria Lanppart3 launcher (nothing copied in the folder of the game): log next to the DLL
		snprintf(g_log_path, sizeof g_log_path, "%s", mod_file_path("celaria_mod.log"));
		mod_log("Lanppart3 launcher: mod injected");
#else
		check_original_dll(instance);
#endif
		install();
	}
	return TRUE;
}
