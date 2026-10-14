// usage: node varref.mjs <varName|id> : find code that passes this var id as immediate (mov [esp+4],id / mov [esp],id / push id)
import fs from "node:fs";
const b = fs.readFileSync("C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria_exe/Celaria.exe");
const syms = []; for (const l of fs.readFileSync(process.env.TEMP + "/cel_syms.tsv", "utf8").split("\n")) { const [a, n] = l.split("\t"); syms.push([parseInt(a), n]); }
syms.sort((x, y) => x[0] - y[0]);
const vars = new Map(); for (const l of fs.readFileSync(process.env.TEMP + "/cel_vars.tsv", "utf8").split("\n")) { const [a, n] = l.split("\t"); vars.set(n, +a); }
const owner = (va) => { let lo = 0, hi = syms.length - 1, r = null; while (lo <= hi) { const m = (lo + hi) >> 1; if (syms[m][0] <= va) { r = syms[m]; lo = m + 1; } else hi = m - 1; } return r ? r[1] : "?"; };
for (const arg of process.argv.slice(2)) {
  const id = /^\d+$/.test(arg) ? +arg : vars.get(arg);
  const imm = Buffer.alloc(4); imm.writeUInt32LE(id);
  const pats = [Buffer.concat([Buffer.from([0xc7, 0x44, 0x24, 0x04]), imm]), Buffer.concat([Buffer.from([0xc7, 0x04, 0x24]), imm])];
  const counts = new Map();
  for (const p of pats) { let i = 0x400; while ((i = b.indexOf(p, i + 1)) >= 0 && i < 0x9fea00) { const o = owner(i - 0x400 + 0x401000); counts.set(o, (counts.get(o) || 0) + 1); } }
  console.log("== " + arg + " (" + id + ")"); for (const [k, v] of [...counts].sort((a, c) => c[1] - a[1])) console.log("  " + v + "  " + k);
}
