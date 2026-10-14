import fs from "node:fs";
const sub = (f, o, n) => { const s = fs.readFileSync(f, "utf8"); const c = s.split(o).length - 1; if (c !== 1) { console.error(f + ": found " + c + "x: " + o); process.exit(1); } fs.writeFileSync(f, s.replace(o, () => n)); };
sub('mod.h','extern int g_records_tab;\n',`extern int g_records_tab;
// lan.c: the mod is only active in a multiplayer game on a server of the local network
extern int g_lan_session;
int mod_active(void);
void lan_check_session(void);
void lan_end_session(void);
`);
sub('lan.c','#define VAR_SERVER_PORT 1239\n','#define VAR_SERVER_PORT 1239\n#define OBJ_CONTROLLER 0x50\n#define VAR_MULTIPLAYER 965\n');
sub('lan.c','void lan_end_session(void) {',`// 1 = connected to a LAN server and in a multiplayer game: every feature of the mod may run
int mod_active(void) {
	if (g_lan_session == 0) return 0;
	RValue v = { 0 };
	YY_VAR_GET(OBJ_CONTROLLER, VAR_MULTIPLAYER, ARRAY_NONE, &v);
	int mp = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	return mp;
}

void lan_end_session(void) {`);
sub('mod.c','\tif (argc < 1) return orig_time_format(','\tif (argc < 1 || mod_active() == 0) return orig_time_format(');
sub('ms_timing.c',`	orig_collided(self, other, result, argc, args);
	if (argc < 4) return result;`,`	orig_collided(self, other, result, argc, args);
	if (argc < 4 || mod_active() == 0) return result;`);
sub('ms_timing.c','\tif (argc >= 1) {\n\t\tdouble ms','\tif (argc >= 1 && mod_active()) {\n\t\tdouble ms');
sub('net.c',`	orig_send_metadata(self, other, result, argc, args);
	RValue version`,`	orig_send_metadata(self, other, result, argc, args);
	lan_check_session();
	if (mod_active() == 0) return result;// server on the Internet: nothing is sent
	RValue version`);
sub('net.c',`static RValue *__cdecl hook_add_chat(void *self, void *other, RValue *result, int argc, RValue **args) {
	for`,`static RValue *__cdecl hook_add_chat(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_active() == 0) return orig_add_chat(self, other, result, argc, args);
	for`);
sub('net.c',`static RValue *__cdecl hook_init_mp(void *self, void *other, RValue *result, int argc, RValue **args) {
	g_server_supports_mod = 0;`,`static RValue *__cdecl hook_init_mp(void *self, void *other, RValue *result, int argc, RValue **args) {
	g_server_supports_mod = 0;
	lan_end_session();`);
sub('net.c',`static RValue *__cdecl hook_reset_mp(void *self, void *other, RValue *result, int argc, RValue **args) {
	g_server_supports_mod = 0;`,`static RValue *__cdecl hook_reset_mp(void *self, void *other, RValue *result, int argc, RValue **args) {
	g_server_supports_mod = 0;
	lan_end_session();`);
let s = fs.readFileSync('net.c','utf8'); s = s.replaceAll('\tif (g_server_supports_mod) ','\tif (g_server_supports_mod && mod_active()) '); fs.writeFileSync('net.c', s);
sub('clock.c','\tif (g_server_supports_mod == 0) return;','\tif (g_server_supports_mod == 0 || mod_active() == 0) return;');
sub('prediction.c','if (g_server_supports_mod == 0 || lag_known == 0)','if (g_server_supports_mod == 0 || lag_known == 0 || mod_active() == 0)');
sub('records_tab.c',`	if (g_server_supports_mod == 0) return 0;
	RValue v = { 0 };
	YY_VAR_GET(OBJ_CONTROLLER, VAR_MULTIPLAYER, ARRAY_NONE, &v);
	int mp = yy_to_real(&v) > 0.5;
	yy_clear(&v);
	return mp;`,`	return g_server_supports_mod && mod_active();`);
sub('spectator.c',`static RValue *__cdecl hook_set_projection_interpolated(void *self, void *other, RValue *result, int argc, RValue **args) {
	update(self, other);`,`static RValue *__cdecl hook_set_projection_interpolated(void *self, void *other, RValue *result, int argc, RValue **args) {
	if (mod_active() == 0) {
		spectating = 0;
		return orig_set_projection_interpolated(self, other, result, argc, args);
	}
	update(self, other);`);
sub('spectator.c',`	orig_render_usernames(self, other, result, argc, args);
	draw_spectator_text(self, other);`,`	orig_render_usernames(self, other, result, argc, args);
	if (mod_active()) draw_spectator_text(self, other);`);
console.log("ok");
