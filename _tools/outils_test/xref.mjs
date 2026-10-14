// usage: node xref.mjs <symbol|0xaddr> ... : find direct call sites, report containing gml function
import fs from "node:fs";
const b = fs.readFileSync("C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria_exe/Celaria.exe");
const syms = []; const byName = new Map();
for (const l of fs.readFileSync(process.env.TEMP + "/cel_syms.tsv", "utf8").split("\n")) { const [a, n] = l.split("\t"); syms.push([parseInt(a), n]); byName.set(n, parseInt(a)); }
syms.sort((x, y) => x[0] - y[0]);
const owner = (va) => { let lo = 0, hi = syms.length - 1, r = null; while (lo <= hi) { const m = (lo + hi) >> 1; if (syms[m][0] <= va) { r = syms[m]; lo = m + 1; } else hi = m - 1; } return r ? r[1] + "+0x" + (va - r[0]).toString(16) : "?"; };
for (const arg of process.argv.slice(2)) {
  const t = arg.startsWith("0x") ? parseInt(arg) : byName.get(arg);
  console.log("== " + arg + " @0x" + t.toString(16));
  for (let i = 0x400; i < 0x400 + 0x9fe600 - 5; i++) {
    if (b[i] !== 0xe8) continue;
    const va = i - 0x400 + 0x401000; const tgt = (va + 5 + b.readInt32LE(i + 1)) >>> 0;
    if (tgt === t) console.log("  0x" + va.toString(16) + "  " + owner(va));
  }
}
