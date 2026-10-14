import { patch } from "./wsrep.mjs";
const F = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/README.md";
patch(F, [
[`- Synchronized start: the server waits until every player loaded the map (20 s max) and schedules the start after a 3-2-1
  countdown. Clients with the mod synchronise their clock with the server and start exactly at the scheduled time; the start
  message is sent first to the players with the highest ping (latency compensation) for the clients without the mod.
  Players' positions are relayed at 30 Hz in cup mode.`,
`- Synchronized start: the server waits until every player loaded the map (20 s max) and schedules the race start (Go).
  The ROUND_START message is sent so that every client gets it 3 seconds before the Go (latency compensation): the
  countdown of the game (3, 2, 1, Go!) ends at the Go. Clients with the mod synchronise their clock with the server, start
  the countdown themselves and keep it aligned on the server clock (default 3 s countdown forced).
  Players' positions are relayed at 30 Hz in cup mode, with the race time of every position for the clients with the mod.`],
[`- A player who reaches the points limit becomes **FINALIST**. A finalist who wins a round **WINS** the cup.`,
 `- A player who reaches the points limit becomes **FINALIST**. A finalist who wins a round **WINS** the cup. A winner
  doesn't race any more until the end of the cup (spectator): he can't take the points of the other players.
- Observer (caster): \`/spec\` (or the "Observer" button of the pause menu with the mod) watches the cup without racing and
  without being ranked; \`/play\` races again from the next round.`],
[`(\`42 pts | Name\`, \`FINALIST | Name\`, \`WINNER #1 | Name\`)`, `(\`42 pts | Name\`, \`FINALIST | Name\`, \`WINNER | Name\`, \`WINNER 2 | Name\`)`],
[`Chat commands: \`/cup\` (or \`/points\`) shows the cup ranking,`, `Chat commands: \`/cup\` (or \`/points\`) shows the cup ranking, \`/spec\` / \`/play\` observer mode,`],
]);
