# Celaria Server

This is the source code of the standalone multiplayer server for the Game "Celaria". (https://www.celaria.com)
of modding the server sided multiplayer functionality of the game. 

This server source code was released for the purposes of modding the server sided multiplayer functionality of the game.

### Note

This code was one of my first attempts at writing a multithreaded codebase and was refactored
multiple times (partially and/or fully) each time with different approaches during development. Hence it's a mix of different codingstyles
with multiple different solutions to the same issue (like thread locking) and thus probably an example how to (not) write a game server.

Nowadays i would've approached the coding structure differently (due to more experience compared to when this was written).

If you intend on modding and hosting your modified server, make sure to set the "MODIFIED" variable in the "ServerCore.java" file to "true".
This way your server will recieve a special icon in the serverbrowser as well as indicate the game client that the server may have altered behaviour.
```
public static final boolean MODIFIED = true;
```
-----------


### Dependencies
There are sections of the source code which are dependend on "Gson" so you will have to link the project against a build of this library:
https://github.com/google/gson

-----------

### Cup mode

This modified server adds a **Cup** game mode with the Trackmania cup rules. It works with the unmodified game client.

Enable it in `server.conf` with `gameMode = cup` (`gameMode = timeattack` restores the original mode).

Rules:
- Synchronized start: the server waits until every player loaded the map (20 s max) and schedules the race start (Go).
  The ROUND_START message is sent so that every client gets it 3 seconds before the Go (latency compensation): the
  countdown of the game (3, 2, 1, Go!) ends at the Go. Clients with the mod synchronise their clock with the server, start
  the countdown themselves and keep it aligned on the server clock (default 3 s countdown forced).
  Players' positions are relayed at 30 Hz in cup mode, with the race time of every position for the clients with the mod.
- Respawning at the last checkpoint is allowed. A complete reset of the run eliminates the player for the round: he becomes
  spectator (his map is reloaded without start signal, he can't move but sees the race) until the next round.
  Players who weren't ready at the start, or joined during a round, are spectators until the next round too.
- When the first player finishes, the others have a limited time to finish (finish timeout).
- Points are given by rank (`10,6,4,3,2,1` by default; lower ranks get the last value, players who don't finish get 0).
- A player who reaches the points limit becomes **FINALIST**. A finalist who wins a round **WINS** the cup. A winner
  doesn't race any more until the end of the cup (spectator): he can't take the points of the other players.
- Observer (caster): `/spec` (or the "Observer" button of the pause menu with the mod) watches the cup without racing and
  without being ranked; `/play` races again from the next round.
- The cup ends when the number of winners is reached; a new cup starts on the next map.
- Several rounds are played on each map (the map is reloaded between rounds), then the server switches to the next map.
- Times are computed in milliseconds and displayed as `minutes:seconds:milliseconds` in the chat (example: `00:15:237`).
  The game client measures its run time in 1/100 second (one engine step); in cup mode the server measures the real
  elapsed time since the synchronized start to the millisecond (time not counted by the client and tie breaks).
  The in-game leaderboard of the client still displays 1/100 second.

The in-game leaderboard shows the cup status in front of the player names (`42 pts | Name`, `FINALIST | Name`, `WINNER | Name`, `WINNER 2 | Name`)
and the points won in the last round (`52 pts (+10) | Name`). Results and announcements are sent in the chat.

| Setting | Default | Description |
| --- | --- | --- |
| `cup_pointsLimit` | `100` | Points needed to become finalist |
| `cup_roundsPerMap` | `5` | Rounds played on a map before the next map |
| `cup_nbWinners` | `1` | Number of winners before the cup ends |
| `cup_pointsRepartition` | `10,6,4,3,2,1` | Points per rank |
| `cup_finishTimeout` | `-1` | Seconds left after the first finisher (`-1`: 5 s + platinum medal time / 6) |
| `cup_roundTimeLimit` | `300` | Maximum length of a round in seconds |
| `cup_bots` | `0` | Bots connected when the server starts (they replay the best recorded run of the map) |
| `cup_warmupRounds` | `1` | Warmup rounds at the start of every map: no points, no DNF (0 = no warmup) |
| `cup_respawnAtStartDNF` | `false` | Respawn key without checkpoint (back to the start) = DNF. With the mod the timer continues; without the mod the server adds the lost time |
| `cup_forceRestartDNF` | `true` | Restart key (complete reset of the run) = DNF for the round |
| `cup_endRoundDelay` | `5` | Seconds the leaderboard is shown between two rounds (was 10) |
| `cup_endMatchDelay` | `10` | Seconds the leaderboard is shown at the end of a cup (was 15) |
| `cup_allFinishedDelay` | `1` | Seconds before the end of the round once every player finished (was 3) |
| `cup_startDelay` | `1.5` | Minimum seconds between the map load and the countdown (was 3) |

Time attack settings (V2):

| Setting | Default | Description |
| --- | --- | --- |
| `timeattack_warmup` | `60` | Warmup at the start of every map (seconds): times don't count, then the map restarts (0 = none) |
| `timeattack_endRoundDelay` | `10` | Seconds the leaderboard is shown at the end of a round (was 15) |
| `timeattack_startDelay` | `5` | Seconds before the start of a round (was 10) |
| `udpRelayRate` | `60` | Positions relayed per second (both modes; was 30 in cup, 10 in time attack) |

V2: the features of the mod work in both modes. Time attack: observers (`/spec`, `/play`, Spectator button), checkpoint
ranking of the current runs, gap with the best run at every checkpoint, spectator view and timer of the followed player.
Maps of the current editor (format 3) are accepted. The list of maps is read again at every map change.

Console without window (used by the Celaria Lanppart3 launcher): `java -jar CelariaServer.jar --headless --config server.conf`
(`--managed`: the server stops when its standard input closes). Commands on the standard input: `nextmap`, `restartmap`,
`map <file>`, `maps`, `status`, `say <text>`, `players`, `cupreset`, `bots <n>`, `stop`.
LAN discovery: the launcher sends `CCPI` and gets `CSPI|port|players|max|protocol|password|mode|map|name`.

LAN discovery: the server answers the "Server Browser LAN" of the cup mod on UDP port 26510 (all the local networks:
LAN, Radmin VPN, Hamachi...). Several servers can run on the same computer.

Bots (cup mode): the server records the runs of the players and keeps the best run of every map (`ghosts/<map>.json`).
`/bots <count>` (or `cup_bots = <count>` in `server.conf`) connects bots ("Bot 1", "Bot 2"...) which replay this run at
every round, a bit slower (1 %, 2.5 %, 4 %...): useful to test the spectator mode alone. Finish the map once first.

Chat commands: `/cup` (or `/points`) shows the cup ranking, `/spec` / `/play` observer mode, `/bots <count>` bots (0 = none), `/records` shows the server records of the map, `/cupreset` (admin) restarts the cup, `/nextmap` (admin) skips the remaining rounds of the map.

### Fix of the original server

The original server relayed the velocity of the players divided by two (`len/6` instead of `len/3`, the client decodes it
with `maxfallrate` = 3): the other players were extrapolated too slowly by every client. Fixed in all modes.

### Server records

The best time of every player on every map is stored in `records.json` (server folder), to the millisecond for players using the client mod (`CelariaMod`).
New server records are announced in the chat. Ranking: official time (1/100 second) first, then milliseconds, so players without the mod are not disadvantaged.

### Client mod

The optional client mod (`CelariaMod`) adds millisecond timing, a spectator mode and a "Records" tab. With the mod, nothing is sent to Steam while playing on this server.

Build: put `gson-*.jar` in `lib/`, run `build.bat` (needs a JDK) and start the server with `java -jar CelariaServer.jar`.
