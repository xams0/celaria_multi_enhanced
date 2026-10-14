import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "cupstate.c", [[`// the local player starts the round (CUPSTART received)`, `// observer mode requested: shown at once (the server confirms with the next CUPST message)
void cup_set_local_state(char state) {
	local_override = state;
}

// the local player starts the round (CUPSTART received)`]]);
patch(M + "mod.h", [[`void cup_local_round_starts(void);`, `void cup_local_round_starts(void);
void cup_set_local_state(char state);`]]);
patch(M + "spectator.c", [
[`#define GML_SCR_CREATE_PAUSE_BUTTONS 0x007dcd40`, `#define GML_SCR_CREATE_PAUSE_BUTTONS 0x007dcd40
#define GML_BEV_RESUME_GAME 0x0053a890// "Resume" button of the pause menu`],
[`static double not_racing_since;// the local player stopped racing (local ms), 0 = racing`, `static double not_racing_since;// the local player stopped racing (local ms), 0 = racing
static char big_message[64];// message in the middle of the screen
static double big_message_until;`],
[`			yy_set_string(args[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Observer");
		}
	}
	return orig_create_button_left_bar(self, other, result, argc, args);`, `			yy_set_string(args[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Spectator");
		}
	}
	return orig_create_button_left_bar(self, other, result, argc, args);`],
[`		YY_CREATE_STRING(&a[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Observer");`, `		YY_CREATE_STRING(&a[2], cup_state(cup_local_id()) == 'V' ? "Play" : "Spectator");`],
[`		RValue value = { 0 };
		value.real = cup_state(cup_local_id()) == 'V' ? 0 : 1;
		net_send_tcp(self, other, 208, BUFFER_U8, &value);
		mod_log("observer mode requested: %.0f", value.real);
		yy_set_real(result, 0);
		return result;`, `		RValue value = { 0 };
		int observer = cup_state(cup_local_id()) != 'V';
		value.real = observer;
		net_send_tcp(self, other, 208, BUFFER_U8, &value);
		mod_log("observer mode requested: %d", observer);
		cup_set_local_state(observer ? 'V' : 'N');// the button shows "Play" / "Spectator" at once
		snprintf(big_message, sizeof big_message, "%s", observer ? "You are now a Spectator" : "You will race from the next round");
		big_message_until = clock_local_ms() + 3000;
		if (observer) {
			manual_off = 0;// spectator view at once
			not_racing_since = clock_local_ms() - AUTO_SPECTATE_DELAY_MS;
		}
		RValue r = { 0 };
		((GmlScript)ADDR(GML_BEV_RESUME_GAME))(self, other, &r, 0, NULL);// the pause menu is closed
		yy_clear(&r);
		yy_set_real(result, 0);
		return result;`],
[`// checkpoint ranking of the round (bottom right, above the speed)`, `// big message in the middle of the screen (observer mode)
static void draw_big_message(void *self, void *other, double w, double h) {
	if (big_message[0] == 0 || clock_local_ms() > big_message_until) return;
	float tw = text_width(self, other, big_message) * 3.0f, th = text_height(self, other) * 3.0f;
	float y = (float)(h * 0.3);
	hud_font(1, 1);
	hud_rect((float)(w / 2) - tw / 2 - 24, y - th / 2 - 12, (float)(w / 2) + tw / 2 + 24, y + th / 2 + 12, COLOUR_BLACK, 0.6f);
	hud_text(self, other, w / 2, y, big_message, 3.0, COLOUR_YELLOW);
}

// checkpoint ranking of the round (bottom right, above the speed)`],
[`		draw_checkpoint_ranking(self, other, w, h);
		draw_spectated_timer(self, other, w, h);`, `		draw_checkpoint_ranking(self, other, w, h);
		draw_spectated_timer(self, other, w, h);
		draw_big_message(self, other, w, h);`],
]);
