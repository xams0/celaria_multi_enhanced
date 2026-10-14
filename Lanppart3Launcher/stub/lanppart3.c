// Celaria Lanppart3.exe
//
// 1) Without arguments: the files appended to the exe (Java runtime, launcher, server, mod DLL, default maps; see
//    payload.h) are extracted once to %LOCALAPPDATA%\Lanppart3\app\<build>, then the launcher (Java) is started.
// 2) --inject <Celaria.exe> <mod dll> [arguments of the game]: starts the game with the mod. The game is created
//    suspended, the DLL is loaded by an APC of its main thread (before any code of the game runs), then the game is
//    resumed. Nothing is copied in the folder of the game. Steam: the game is started with SteamAppId (the game uses the
//    Steam client which is running, like when Steam starts it). Exit code: 0 = started with the mod.
#include <windows.h>
#include <compressapi.h>
#include <shlobj.h>
#include <tlhelp32.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include "payload.h"

#define CELARIA_APP_ID L"1081650"

static void message(const wchar_t *text) {
	MessageBoxW(NULL, text, L"Celaria Lanppart3", MB_OK | MB_ICONERROR);
}

// ---------------------------------------------------------------- injection

static int has_module(DWORD pid, const wchar_t *dll) {
	const wchar_t *name = wcsrchr(dll, L'\\');
	name = name ? name + 1 : dll;
	HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
	if (snap == INVALID_HANDLE_VALUE) return 0;
	MODULEENTRY32W me = { .dwSize = sizeof me };
	int found = 0;
	for (BOOL ok = Module32FirstW(snap, &me); ok && found == 0; ok = Module32NextW(snap, &me))
		found = _wcsicmp(me.szModule, name) == 0;
	CloseHandle(snap);
	return found;
}

static int inject(int argc, wchar_t **argv) {
	if (argc < 4) return 10;
	const wchar_t *exe = argv[2], *dll = argv[3];
	wchar_t dir[MAX_PATH];
	wcsncpy(dir, exe, MAX_PATH - 1);
	dir[MAX_PATH - 1] = 0;
	wchar_t *slash = wcsrchr(dir, L'\\');
	if (slash == NULL) return 11;
	*slash = 0;

	// command line: "Celaria.exe" -nosteamrestart <arguments>
	size_t size = wcslen(exe) + 64;
	for (int i = 4; i < argc; i++) size += wcslen(argv[i]) + 3;
	wchar_t *cmd = calloc(size, sizeof(wchar_t));
	_snwprintf(cmd, size, L"\"%ls\" -nosteamrestart", exe);
	for (int i = 4; i < argc; i++) {
		wcscat(cmd, L" \"");
		wcscat(cmd, argv[i]);
		wcscat(cmd, L"\"");
	}
	SetEnvironmentVariableW(L"SteamAppId", CELARIA_APP_ID);
	SetEnvironmentVariableW(L"SteamGameId", CELARIA_APP_ID);

	STARTUPINFOW si = { .cb = sizeof si };
	PROCESS_INFORMATION pi;
	if (!CreateProcessW(exe, cmd, NULL, NULL, FALSE, CREATE_SUSPENDED, NULL, dir, &si, &pi)) return 12;
	size_t bytes = (wcslen(dll) + 1) * sizeof(wchar_t);
	void *remote = VirtualAllocEx(pi.hProcess, NULL, bytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
	FARPROC load = GetProcAddress(GetModuleHandleW(L"kernel32.dll"), "LoadLibraryW");
	if (remote == NULL || load == NULL || !WriteProcessMemory(pi.hProcess, remote, dll, bytes, NULL)
		|| !QueueUserAPC((PAPCFUNC)(void *)load, pi.hThread, (ULONG_PTR)remote)) {
		TerminateProcess(pi.hProcess, 1);
		return 13;
	}
	ResumeThread(pi.hThread);
	// the APC runs when the main thread starts: check that the mod is loaded
	int result = 14;
	for (int i = 0; i < 100; i++) {
		if (WaitForSingleObject(pi.hProcess, 100) == WAIT_OBJECT_0) {
			result = 15;// the game stopped (Steam restart?)
			break;
		}
		if (has_module(pi.dwProcessId, dll)) {
			result = 0;
			break;
		}
	}
	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	return result;
}

// ---------------------------------------------------------------- payload

static HANDLE self_file;

static int read_at(uint64_t offset, void *buffer, DWORD size) {
	LARGE_INTEGER li;
	li.QuadPart = (LONGLONG)offset;
	DWORD read = 0;
	return SetFilePointerEx(self_file, li, NULL, FILE_BEGIN) && ReadFile(self_file, buffer, size, &read, NULL) && read == size;
}

static int make_dirs(wchar_t *path) {// creates the parent folders of a file path
	for (wchar_t *p = path + 3; *p; p++) {
		if (*p == L'\\') {
			*p = 0;
			CreateDirectoryW(path, NULL);
			*p = L'\\';
		}
	}
	return 1;
}

static void delete_tree(const wchar_t *dir) {
	wchar_t from[MAX_PATH + 2] = { 0 };
	wcsncpy(from, dir, MAX_PATH);
	SHFILEOPSTRUCTW op = { .wFunc = FO_DELETE, .pFrom = from, .fFlags = FOF_NO_UI };
	SHFileOperationW(&op);
}

// extracts the payload to <app>\runtime (once per version); returns 0 on success
static int extract(const wchar_t *self, wchar_t *target, size_t target_size) {
	self_file = CreateFileW(self, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
	if (self_file == INVALID_HANDLE_VALUE) return 1;
	LARGE_INTEGER file_size;
	GetFileSizeEx(self_file, &file_size);
	uint8_t footer[PAYLOAD_FOOTER_SIZE];
	if (!read_at(file_size.QuadPart - PAYLOAD_FOOTER_SIZE, footer, PAYLOAD_FOOTER_SIZE) || memcmp(footer + 12, PAYLOAD_MAGIC, 8) != 0) return 2;
	uint64_t index_offset;
	uint32_t index_size;
	memcpy(&index_offset, footer, 8);
	memcpy(&index_size, footer + 8, 4);
	uint8_t *index = malloc(index_size);
	if (!read_at(index_offset, index, index_size)) return 3;
	uint8_t *p = index;
	uint32_t count, build_length;
	memcpy(&count, p, 4);
	memcpy(&build_length, p + 4, 4);
	p += 8;
	char build[128] = { 0 };
	memcpy(build, p, build_length < 127 ? build_length : 127);
	p += build_length;

	wchar_t base[MAX_PATH], app[MAX_PATH], marker[MAX_PATH];
	if (SHGetFolderPathW(NULL, CSIDL_LOCAL_APPDATA | CSIDL_FLAG_CREATE, NULL, 0, base) != S_OK) return 4;
	_snwprintf(app, MAX_PATH, L"%ls\\Lanppart3\\app", base);
	// always the same folder: the rule of the Windows firewall for its java.exe (server) stays valid after an update.
	// .complete holds the build id of the extracted files.
	_snwprintf(target, target_size, L"%ls\\runtime", app);
	_snwprintf(marker, MAX_PATH, L"%ls\\.complete", target);
	char done[128] = { 0 };
	HANDLE mf = CreateFileW(marker, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, 0, NULL);
	if (mf != INVALID_HANDLE_VALUE) {
		DWORD n = 0;
		ReadFile(mf, done, sizeof done - 1, &n, NULL);
		CloseHandle(mf);
		if (strcmp(done, build) == 0) return 0;// this version is already extracted
	}

	// the runtime folder of another version is moved away (fails if the launcher or the server of this version still runs)
	if (GetFileAttributesW(target) != INVALID_FILE_ATTRIBUTES) {
		wchar_t old_runtime[MAX_PATH];
		_snwprintf(old_runtime, MAX_PATH, L"%ls\\old_%lu", app, (unsigned long)GetTickCount());
		if (!MoveFileW(target, old_runtime)) return 9;
	}
	// other versions are removed
	wchar_t pattern[MAX_PATH];
	_snwprintf(pattern, MAX_PATH, L"%ls\\*", app);
	WIN32_FIND_DATAW fd;
	HANDLE h = FindFirstFileW(pattern, &fd);
	if (h != INVALID_HANDLE_VALUE) {
		do {
			if (fd.cFileName[0] == L'.' || !(fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
			wchar_t old[MAX_PATH];
			_snwprintf(old, MAX_PATH, L"%ls\\%ls", app, fd.cFileName);
			delete_tree(old);
		} while (FindNextFileW(h, &fd));
		FindClose(h);
	}

	DECOMPRESSOR_HANDLE decompressor;
	if (!CreateDecompressor(COMPRESS_ALGORITHM_LZMS, NULL, &decompressor)) return 5;
	for (uint32_t i = 0; i < count; i++) {
		uint16_t length;
		memcpy(&length, p, 2);
		char path[1024] = { 0 };
		memcpy(path, p + 2, length < 1023 ? length : 1023);
		p += 2 + length;
		uint64_t offset, stored, size;
		uint32_t method;
		memcpy(&offset, p, 8);
		memcpy(&stored, p + 8, 8);
		memcpy(&size, p + 16, 8);
		memcpy(&method, p + 24, 4);
		p += 28;
		uint8_t *data = malloc(stored + 1);
		if (!read_at(offset, data, (DWORD)stored)) return 6;
		uint8_t *plain = data;
		if (method == METHOD_LZMS) {
			plain = malloc(size + 1);
			SIZE_T written = 0;
			if (!Decompress(decompressor, data, stored, plain, size, &written) || written != size) return 7;
		}
		wchar_t wpath[1024], full[MAX_PATH * 2];
		MultiByteToWideChar(CP_UTF8, 0, path, -1, wpath, 1024);
		for (wchar_t *c = wpath; *c; c++)
			if (*c == L'/') *c = L'\\';
		_snwprintf(full, MAX_PATH * 2, L"%ls\\%ls", target, wpath);
		make_dirs(full);
		HANDLE f = CreateFileW(full, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, 0, NULL);
		DWORD written = 0;
		if (f == INVALID_HANDLE_VALUE || (size > 0 && !WriteFile(f, plain, (DWORD)size, &written, NULL))) return 8;
		CloseHandle(f);
		if (plain != data) free(plain);
		free(data);
	}
	CloseDecompressor(decompressor);
	HANDLE m = CreateFileW(marker, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, 0, NULL);
	DWORD marker_written = 0;
	WriteFile(m, build, (DWORD)strlen(build), &marker_written, NULL);
	CloseHandle(m);
	CloseHandle(self_file);
	return 0;
}

// a small window while the files are extracted (first start of a new version)
static DWORD WINAPI splash_thread(LPVOID unused) {
	(void)unused;
	HWND w = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW, L"STATIC", L"  Celaria Lanppart3 : préparation...",
		WS_POPUP | WS_VISIBLE | WS_BORDER | SS_CENTERIMAGE, GetSystemMetrics(SM_CXSCREEN) / 2 - 180,
		GetSystemMetrics(SM_CYSCREEN) / 2 - 30, 360, 60, NULL, NULL, NULL, NULL);
	MSG msg;
	while (w && GetMessageW(&msg, NULL, 0, 0) > 0) DispatchMessageW(&msg);
	return 0;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR args, int show) {
	(void)instance;
	(void)previous;
	(void)args;
	(void)show;
	int argc = 0;
	wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
	if (argc >= 2 && wcscmp(argv[1], L"--inject") == 0) return inject(argc, argv);

	wchar_t self[MAX_PATH], target[MAX_PATH];
	GetModuleFileNameW(NULL, self, MAX_PATH);
	DWORD splash_id = 0;
	HANDLE splash = CreateThread(NULL, 0, splash_thread, NULL, 0, &splash_id);
	int error = extract(self, target, MAX_PATH);
	PostThreadMessageW(splash_id, WM_QUIT, 0, 0);
	if (splash) CloseHandle(splash);
	if (error) {
		wchar_t text[256];
		if (error == 9) _snwprintf(text, 256, L"Une autre version de Celaria Lanppart3 est encore ouverte (ou son serveur tourne).\nFerme-la puis relance.");
		else _snwprintf(text, 256, L"Impossible de préparer le launcher (erreur %d).\nEspace disque, antivirus ?", error);
		message(text);
		return 1;
	}
	wchar_t javaw[MAX_PATH], cmd[MAX_PATH * 4];
	_snwprintf(javaw, MAX_PATH, L"%ls\\jre\\bin\\javaw.exe", target);
	_snwprintf(cmd, MAX_PATH * 4, L"\"%ls\" -Dlp3.exe=\"%ls\" -Dlp3.app=\"%ls\" -Dfile.encoding=UTF-8 -jar \"%ls\\launcher.jar\"",
		javaw, self, target, target);
	STARTUPINFOW si = { .cb = sizeof si };
	PROCESS_INFORMATION pi;
	// this exe was started by the user (foreground): the launcher may take the foreground (else its window stays behind)
	AllowSetForegroundWindow(ASFW_ANY);
	if (!CreateProcessW(javaw, cmd, NULL, NULL, FALSE, 0, NULL, target, &si, &pi)) {
		message(L"Impossible de démarrer le launcher (Java).");
		return 1;
	}
	CloseHandle(pi.hThread);
	CloseHandle(pi.hProcess);
	return 0;
}
