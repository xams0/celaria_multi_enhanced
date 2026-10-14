import fs from "node:fs";
const b = fs.readFileSync("C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria_exe/Celaria.exe");
const pe = b.readUInt32LE(0x3c), n = b.readUInt16LE(pe + 6), opt = b.readUInt16LE(pe + 20), sec = pe + 24 + opt;
const off = (va) => { const rva = va - 0x400000; for (let i = 0; i < n; i++) { const s = sec + i * 40, v = b.readUInt32LE(s + 12), sz = b.readUInt32LE(s + 8), raw = b.readUInt32LE(s + 20); if (rva >= v && rva < v + Math.max(sz, b.readUInt32LE(s + 16))) return rva - v + raw; } return -1; };
for (const a of process.argv.slice(2)) { const va = parseInt(a); const o = off(va); console.log(a, "off", o.toString(16), "double", b.readDoubleLE(o), "kind", b.readUInt32LE(o + 12), "hex", b.subarray(o, o + 16).toString("hex")); }
