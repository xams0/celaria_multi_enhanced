// whitespace tolerant replacement helper: import { patch } from "./wsrep.mjs"; patch(file, [[old, new, all?], ...])
// The match includes the indentation of its first line: the replacement brings its own indentation.
import fs from "node:fs";
const esc = (t) => t.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
export function patch(file, edits) {
	let s = fs.readFileSync(file, "utf8");
	for (const [o, n, all] of edits) {
		const re = new RegExp("[ \\t]*" + o.trim().split(/\s+/).map(esc).join("\\s*"), "g");
		const m = s.match(re);
		if (!m || (!all && m.length !== 1)) { console.error(file + ": found " + (m ? m.length : 0) + "x: " + o.slice(0, 80)); process.exit(1); }
		s = s.replace(re, () => n);
	}
	fs.writeFileSync(file, s);
	console.log("patched " + file);
}
