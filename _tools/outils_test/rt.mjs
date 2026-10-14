import fs from "node:fs";
const b = fs.readFileSync("C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria_exe/Celaria.exe");
const secs = [[0x1000, 0x400, 0x9fe600], [0xa00000, 0x9fea00, 0x11c400], [0xb1d000, 0xb1ae00, 0x227b600]];
const off = (v) => { v -= 0x400000; for (const s of secs) if (v >= s[0] && v < s[0] + s[2]) return s[1] + v - s[0]; return -1; };
const cstr = (v) => { const o = off(v); let e = o; while (b[e]) e++; return b.toString("latin1", o, e); };
const base = 0xF39414;
for (const a of process.argv.slice(2)) { const i = +a; const o = off(base + i * 8); console.log(i, cstr(b.readUInt32LE(o)), "0x" + b.readUInt32LE(o + 4).toString(16)); }
