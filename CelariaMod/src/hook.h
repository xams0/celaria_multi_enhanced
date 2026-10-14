// Minimal inline hooks for the YYC functions of Celaria.exe.
// The first bytes of a function are replaced by a jump to the hook. The replaced bytes are verified before patching
// (the mod refuses to patch an unknown executable) and copied into a trampoline which calls the original function.
#pragma once
#include <stdint.h>

// Installs a hook. expected: original bytes at the start of the function (len >= 5, must end on an instruction boundary).
// Returns a pointer to call the original function, or NULL if the bytes don't match.
void *hook_install(uintptr_t target, const uint8_t *expected, int len, void *hook);

// Checks that the bytes at an address match (used to identify the executable version).
int bytes_match(uintptr_t address, const uint8_t *expected, int len);

void mod_log(const char *fmt, ...);
