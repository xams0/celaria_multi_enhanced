import { patch } from "./wsrep.mjs";
patch("ms_timing.c", [
[`static GmlScript orig_send_goal;`, `static GmlScript orig_send_goal;
static GmlScript orig_send_checkpoint;

#define GML_SCR_SEND_CHECKPOINT_PACKET 0x00866cf0`],
[`void ms_timing_install(void) {`, `// same for the checkpoint packet (202): packet 207 with the milliseconds (checkpoint ranking of the cup mod)
static RValue *__cdecl hook_send_checkpoint(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (argc >= 1 && g_server_supports_mod && mod_active()) {
		double ms = floor(ms_precise_ticks(yy_to_real(args[0])) * 10.0 + 1e-6);
		RValue value = { 0 };
		value.real = ms;
		net_send_tcp(self, other, 207, BUFFER_U32, &value);
	}
	return orig_send_checkpoint(self, other, result, argc, args);
}

void ms_timing_install(void) {
	orig_send_checkpoint = (GmlScript)hook_install(ADDR(GML_SCR_SEND_CHECKPOINT_PACKET), PROLOGUE, sizeof PROLOGUE, hook_send_checkpoint);`],
]);
