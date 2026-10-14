import { patch } from "./wsrep.mjs";
const S = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria-Server-main/src/server/";
patch(S + "game/CupState.java", [
[`					RoundFinish f = findFinish(p.playerID);
				entries.add(new CupLeaderboardEntry(p.playerID, text + " | " + p.name, p.badge, f != null, f != null ? f.effectivePreciseTime : 0, f != null ? f.effectiveTime : 0));
			}
		}
		return entries;`,
 `					RoundFinish f = findFinish(p.playerID);
				entries.add(new CupLeaderboardEntry(p.playerID, text + " | " + p.name, p.badge, f != null, f != null ? f.effectivePreciseTime : 0, f != null ? f.effectiveTime : 0));
			}
		}
		// observers (casters): out of the competition, below the players
		List<CupPlayer> observers = new ArrayList<CupPlayer>();
		for (CupPlayer p : playersByID.values()) {
			if (p.observer) {
				observers.add(p);
			}
		}
		Collections.sort(observers, new Comparator<CupPlayer>() {
			@Override
			public int compare(CupPlayer a, CupPlayer b) {
				return a.name.compareTo(b.name);
			}
		});
		for (CupPlayer p : observers) {
			entries.add(new CupLeaderboardEntry(p.playerID, "SPECTATE | " + p.name, p.badge, false, 0, 0));
		}
		return entries;`],
]);
