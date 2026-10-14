#include <windows.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include "hook.h"

static FILE *log_file;
char g_log_path[520] = "celaria_mod.log";// injected by the launcher: next to the DLL

void mod_log(const char *fmt, ...) {
	if (log_file == NULL) {
		log_file = fopen(g_log_path, "w");
		if (log_file == NULL) return;
	}
	va_list args;
	va_start(args, fmt);
	vfprintf(log_file, fmt, args);
	va_end(args);
	fputc('\n', log_file);
	fflush(log_file);
}

int bytes_match(uintptr_t address, const uint8_t *expected, int len) {
	return memcmp((const void *)address, expected, len) == 0;
}

void *hook_install(uintptr_t target, const uint8_t *expected, int len, void *hook) {
	if (len < 5 || bytes_match(target, expected, len) == 0) {
		mod_log("hook 0x%08x: unexpected bytes, not patched", (unsigned)target);
		return NULL;
	}
	// trampoline: original bytes + jmp back
	uint8_t *tramp = VirtualAlloc(NULL, 64, MEM_COMMIT | MEM_RESERVE, PAGE_EXECUTE_READWRITE);
	if (tramp == NULL) return NULL;
	memcpy(tramp, (const void *)target, len);
	tramp[len] = 0xE9;
	*(int32_t *)(tramp + len + 1) = (int32_t)((target + len) - ((uintptr_t)tramp + len + 5));

	DWORD old;
	VirtualProtect((void *)target, len, PAGE_EXECUTE_READWRITE, &old);
	uint8_t *p = (uint8_t *)target;
	p[0] = 0xE9;
	*(int32_t *)(p + 1) = (int32_t)((uintptr_t)hook - (target + 5));
	for (int i = 5; i < len; i++) p[i] = 0x90;
	VirtualProtect((void *)target, len, old, &old);
	FlushInstructionCache(GetCurrentProcess(), (void *)target, len);
	mod_log("hook 0x%08x installed", (unsigned)target);
	return tramp;
}
