// usage: node flow.mjs <asmfile> <startAddrHex> [stopAddrHex,...] [maxBlocks]
// Follows control flow from start in an annotated asm listing (from dis.mjs), prints a de-noised trace.
import fs from "node:fs";
const [,, file, startHex, stops = "", maxB = "400", minA = "0", maxA = "ffffffff"] = process.argv;
const lines = fs.readFileSync(file, "utf8").split("\n").filter(Boolean);
const idx = new Map(); const ins = lines.map((l, i) => { const a = parseInt(l.slice(0, l.indexOf(" ")), 16); idx.set(a, i); return { a, l }; });
const stopSet = new Set(stops.split(",").filter(Boolean).map((h) => parseInt(h, 16)));
const LM = process.env.LM || "0x1e38"; const lineMark = new RegExp("mov dword ptr \[esp \+ " + LM + "\], (0x[0-9a-f]+)");
const visited = new Set(); const order = [];
const queue = [parseInt(startHex, 16)];
while (queue.length && order.length < +maxB) {
  const s = queue.shift();
  if (visited.has(s) || stopSet.has(s) || !idx.has(s) || s < parseInt(minA,16) || s > parseInt(maxA,16)) continue;
  let i = idx.get(s); const blk = [];
  visited.add(s);
  for (; i < ins.length; i++) {
    const { a, l } = ins[i];
    if (a !== s && (visited.has(a) || stopSet.has(a))) { blk.push("   -> falls to " + a.toString(16)); if (!visited.has(a) && !stopSet.has(a)) queue.unshift(a); break; }
    blk.push(l);
    const m = l.match(/\s(j\w+) 0x([0-9a-f]+)/);
    if (m) {
      const t = parseInt(m[2], 16);
      if (m[1] === "jmp") { queue.unshift(t); break; }
      queue.push(t);
    }
    if (/\sret\b/.test(l) || /jmp dword ptr/.test(l)) break;
  }
  order.push([s, blk]);
}
for (const [s, blk] of order) {
  const out = [];
  let prev = "";
  for (const l of blk) {
    const lm = l.match(lineMark);
    if (lm) { out.push("      LINE " + parseInt(lm[1], 16)); continue; }
    if (/;|\scmp |\sj(?!mp)\w+ 0x|ucomisd|->/.test(l) && !/FREE_RValue/.test(l) && !l.includes(LM+"]")) out.push("   " + l);
  }
  if (out.length) { console.log("[" + s.toString(16) + "]"); console.log(out.join("\n")); }
}
