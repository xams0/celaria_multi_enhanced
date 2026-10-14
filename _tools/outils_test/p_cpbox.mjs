import { patch } from "./wsrep.mjs";
const M = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/CelariaMod/src/";
patch(M + "spectator.c", [
[`	const float line_h = 22, width = 330;
	float right = (float)w - 12, bottom = (float)(h * 0.885);
	float top = bottom - line_h * (float)count - 8, left = right - width;`,
`	const float fs = 1.1f;
	float line_h = text_height(self, other) * fs + 6;
	float col_name = text_width(self, other, "00 ") * fs;
	float col_where = col_name + text_width(self, other, "WWWWWWWWWWW") * fs;
	float col_time = col_where + text_width(self, other, "CP00 ") * fs;
	float width = col_time + text_width(self, other, "+00:00:000") * fs + 16;
	float right = (float)w - 12, bottom = (float)(h * 0.885);
	float top = bottom - line_h * (float)count - 8, left = right - width;`],
[`		hud_text(self, other, left + 8, y, pos, 1.1, colour);
		hud_text(self, other, left + 34, y, player_name(lines[i].id), 1.1, colour);
		hud_text(self, other, left + 178, y, lines[i].where, 1.1, colour);
		hud_text(self, other, left + 222, y, lines[i].time, 1.1, colour);`,
`		char name[16];
		snprintf(name, sizeof name, "%.11s", player_name(lines[i].id));
		hud_text(self, other, left + 8, y, pos, fs, colour);
		hud_text(self, other, left + 8 + col_name, y, name, fs, colour);
		hud_text(self, other, left + 8 + col_where, y, lines[i].where, fs, colour);
		hud_text(self, other, left + 8 + col_time, y, lines[i].time, fs, colour);`],
]);
