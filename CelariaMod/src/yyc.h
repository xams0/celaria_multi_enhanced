// Bindings to the GameMaker YYC runtime of Celaria.exe (sha256 68ef6866eb8c5c1ca1c90231fbaec18a9d826790f97e893e3bad87544de7f269).
// Every address comes from the reverse engineering notes (REVERSE_NOTES.md).
#pragma once
#include <stdint.h>
#include <stdlib.h>

// Celaria.exe is built with ASLR: addresses below are relative to the preferred image base 0x400000.
extern uintptr_t g_image_base;
#define ADDR(a) ((uintptr_t)(a) - 0x00400000u + g_image_base)

typedef struct RValue {
	union {
		double real;
		int32_t i32;
		int64_t i64;
		void *ptr;
	};
	int32_t flags;
	int32_t kind;
} RValue;

enum { KIND_REAL = 0, KIND_STRING = 1, KIND_UNDEFINED = 5 };

// gml_Script_* / gml_Object_* functions: RValue* f(CInstance* self, CInstance* other, RValue* result, int argc, RValue** args)
typedef RValue *(__cdecl *GmlScript)(void *self, void *other, RValue *result, int argc, RValue **args);

// runtime helpers
typedef void(__cdecl *FnVarGet)(int instOrObject, int varId, int arrayIndex, RValue *out);
typedef void(__cdecl *FnVarSet)(int instOrObject, int varId, int arrayIndex, RValue *value);
typedef void(__cdecl *FnFreeRValue)(RValue *v);
typedef double(__cdecl *FnRealRValue)(RValue *v);
typedef void(__cdecl *FnCreateString)(RValue *out, const char *s);
typedef RValue *(__cdecl *FnCallBuiltin)(void *self, void *other, RValue *result, int argc, void *function, RValue **args);

#define YY_VAR_GET ((FnVarGet)ADDR(0x00b8e4e0))
#define YY_VAR_SET ((FnVarSet)ADDR(0x00b8e7c0))
#define YY_FREE ((FnFreeRValue)ADDR(0x00401220))
#define YY_REAL ((FnRealRValue)ADDR(0x00bb2310))
#define YY_CREATE_STRING ((FnCreateString)ADDR(0x00ba0570))
#define YY_CALL_BUILTIN ((FnCallBuiltin)ADDR(0x00b9d0c0))

typedef void(__cdecl *FnBuiltinVar)(void *instance, int builtinVarId, int arrayIndex, RValue *value);
typedef int(__cdecl *FnBufferWrite)(int buffer, int type, RValue *value);
#define YY_BUILTIN_GET ((FnBuiltinVar)ADDR(0x00b8dd30))
#define YY_BUILTIN_SET ((FnBuiltinVar)ADDR(0x00b8dd90))
#define YY_BUFFER_WRITE ((FnBufferWrite)ADDR(0x00bf5cd0))

// slots filled by the runtime with builtin variable ids / builtin function pointers
#define SLOT_BUILTIN_VAR_A 0x00f3612c// x or y (the code using them is symmetric)
#define SLOT_BUILTIN_VAR_B 0x00f36134
#define SLOT_FN_BUFFER_TELL 0x00f385bc
#define SLOT_FN_NETWORK_SEND_RAW 0x00f38b6c

// objects / variables
#define OBJ_TIMER 0x70
#define OBJ_NETWORK 0x58
#define VAR_AKT_TIME 0x4d
#define VAR_TIME_RUNNING 1479
#define VAR_Z 0x6d5
#define VAR_TCP_OUT_BUFFER 0x598
#define VAR_SERVER 0x4d3
#define BUFFER_U8 1
#define BUFFER_U32 5

#define ARRAY_NONE ((int)0x80000000)

// instance variables (CInstance: vtable at +0, variable array at +4)
typedef RValue *(__attribute__((thiscall)) * FnGetVarRef)(void *instance, int varId);
static inline RValue *yy_self_var(void *instance, int varId) {
	RValue *vars = *(RValue **)((uint8_t *)instance + 4);
	if (vars != 0) return vars + varId;
	FnGetVarRef getter = (*(FnGetVarRef **)instance)[1];
	return getter(instance, varId);
}

// GML scripts
#define GML_SCR_TIME_FORMAT 0x00450c60
#define GML_SCR_GET_TIME 0x004533d0
#define GML_SCR_SET_TIMER 0x00452d10
#define GML_SCR_GET_COLLIDED_CHECKPOINT_MARKER 0x006371e0
#define GML_SCR_SEND_GOAL_PACKET 0x00867660
#define GML_SCR_SEND_PLAYER_METADATA 0x00883d50
#define GML_SCR_MULTIPLAYER_ADD_CHAT_MESSAGE 0x008b0c00
#define GML_SCR_MENU_LEADERBOARD_ADD_ENTRY 0x008ceda0
#define GML_SCR_INIT_MULTIPLAYER_MODE 0x00865e90
#define GML_SCR_RESET_MULTIPLAYER 0x0085e6b0
#define GML_SCR_STEAM_LEADERBOARD_UPLOAD 0x008cd3f0
#define GML_SCR_STEAM_LEADERBOARD_UPLOAD_RETRY 0x008cdb00
#define GML_SCR_STEAM_ADD_MP_EXPERIENCE 0x009bca50
#define GML_BUFFER_RESET 0x00858530
#define GML_BUFFER_WRITE_CLIENT_HEADER_TCP 0x0085dce0

// string RValue: pointer to a reference counted string whose first field is the C string
static inline const char *yy_string(const RValue *v) {
	if ((v->kind & 0xffffff) != KIND_STRING || v->ptr == 0) return 0;
	return *(const char **)v->ptr;
}

static inline int yy_needs_free(const RValue *v) { return ((v->kind + 0xffffff) & 0xfffffc) == 0; }

static inline void yy_clear(RValue *v) {
	if (yy_needs_free(v)) YY_FREE(v);
	v->kind = KIND_REAL;
	v->flags = 0;
	v->real = 0;
}

// numbers only (int32 7, int64 10, bool 13): REAL on an unset/undefined value is a fatal error of the game
static inline double yy_to_real(RValue *v) {
	int kind = v->kind & 0xffffff;
	if (kind == KIND_REAL) return v->real;
	if (kind == 7 || kind == 10 || kind == 13) return YY_REAL(v);
	if (kind == KIND_STRING) {
		const char *s = yy_string(v);
		return s ? strtod(s, NULL) : 0;
	}
	return 0;
}

static inline void yy_set_real(RValue *v, double d) {
	yy_clear(v);
	v->real = d;
}

static inline void yy_set_string(RValue *v, const char *s) {
	yy_clear(v);
	YY_CREATE_STRING(v, s);
}
