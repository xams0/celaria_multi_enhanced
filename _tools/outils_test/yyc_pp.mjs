// Post-process Ghidra pseudo-C of a Celaria YYC function: name builtin slots, variable ids, runtime helpers.
// usage: node yyc_pp.mjs <file>   (reads pseudo-code text, prints annotated text)
import fs from "node:fs";
const T = process.env.TEMP;
const fslots = new Map();
for (const l of fs.readFileSync(T + "/cel_funcslots.tsv", "utf8").split("\n")) { const [a, n] = l.split("\t"); fslots.set(parseInt(a), n.slice(2)); }
const vars = new Map();
for (const l of fs.readFileSync(T + "/cel_vars.tsv", "utf8").split("\n")) { const [a, n] = l.split("\t"); if (+a < 100000) vars.set(+a, n); }
const rt = {
  "00b8e4e0": "Variable_GetValue_Direct", "00b9d0c0": "CallBuiltin", "00401220": "FREE_RValue", "00bb2310": "REAL_RValue",
};
try { for (const l of fs.readFileSync(new URL("./yyc_rt.tsv", import.meta.url), "utf8").split("\n")) { if (!l.trim()) continue; const [a, n] = l.split("\t"); rt[a.replace(/^0x/, "").padStart(8, "0")] = n; } } catch {}
let s = fs.readFileSync(process.argv[2], "utf8");
s = s.replace(/DAT_00(f3[0-9a-f]{4})/g, (m, h) => { const n = fslots.get(parseInt(h, 16)); return n ? "BI_" + n : m; });
s = s.replace(/FUN_([0-9a-f]{8})/g, (m, h) => rt[h] ? rt[h] : m);
s = s.replace(/Variable_GetValue_Direct\(([^,]+),(0x[0-9a-f]+|\d+)/g, (m, a, id) => `Variable_GetValue_Direct(${a},/*${vars.get(parseInt(id)) ?? "?"}*/${id}`);
s = s.replace(/\(\*\*\(code \*\*\)\(\*([^)]+) \+ 4\)\)\((0x[0-9a-f]+|\d+)\)/g, (m, o, id) => `SELFVAR(${o}, ${vars.get(parseInt(id)) ?? id})`);
s = s.replace(/= 0x(40[0-9a-f]{6}|c0[0-9a-f]{6}|3ff[0-9a-f]{5}|bff[0-9a-f]{5});/g, (m, h) => { const b = Buffer.alloc(8); b.writeUInt32LE(parseInt(h, 16), 4); return m + " /*hi:" + b.readDoubleLE(0) + "*/"; });
process.stdout.write(s);
