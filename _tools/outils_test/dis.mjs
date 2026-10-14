// usage: node dis.mjs <va|symbol> [maxInsns]
import { Capstone, Const, loadCapstone } from "capstone-wasm";
import fs from "node:fs";
await loadCapstone();
const EXE = "C:/Users/maxim/Desktop/Celaria Server Test V2 Claude Reverse Engi/Celaria_exe/Celaria.exe";
const b = fs.readFileSync(EXE);
const secs = [[0x1000, 0x400, 0x9fe600], [0xa00000, 0x9fea00, 0x11c400], [0xb1d000, 0xb1ae00, 0x227b600]];
const va2off = (v) => { v -= 0x400000; for (const s of secs) if (v >= s[0] && v < s[0] + s[2]) return s[1] + v - s[0]; return -1; };
const syms = new Map(); const byName = new Map();
for (const l of fs.readFileSync(process.env.TEMP + "/cel_syms.tsv", "utf8").split("\n")) { const [a, n] = l.split("\t"); syms.set(parseInt(a), n); byName.set(n, parseInt(a)); }
try { for (const l of fs.readFileSync(new URL("./extra_syms.tsv", import.meta.url), "utf8").split("\n")) { if (!l.trim()) continue; const [a, n] = l.split("\t"); syms.set(parseInt(a), n); byName.set(n, parseInt(a)); } } catch {}
const fslots=new Map();for(const l of fs.readFileSync(process.env.TEMP+"/cel_funcslots.tsv","utf8").split("\n")){const [a,n]=l.split("\t");fslots.set(parseInt(a),n);}
const vars=new Map();for(const l of fs.readFileSync(process.env.TEMP+"/cel_vars.tsv","utf8").split("\n")){const [a,n]=l.split("\t");if(+a<100000)vars.set(+a,n);}
const rt=new Map([[0xb8e4e0,"Variable_GetValue_Direct(inst,id,idx,out)"],[0xb9d0c0,"CallBuiltin(self,other,res,argc,fn,args)"],[0x401220,"FREE_RValue"],[0xbb2310,"REAL_RValue"],[0xb8e7c0,"ObjVar_Set"],[0xbb2600,"GetGlobalInstance"],[0xbf5cd0,"buffer_write_direct"],[0xb8dd30,"SelfVar_Get?"],[0xb8dd90,"SelfVar_Set?"]]);
const sorted = [...syms.keys()].sort((a, c) => a - c);
const cstr = (v) => { const o = va2off(v); if (o < 0) return null; let e = o; while (e < b.length && b[e] >= 32 && b[e] < 127 && e - o < 120) e++; if (b[e] !== 0 || e - o < 2) return null; return b.toString("latin1", o, e); };
const dbl = (v) => { const o = va2off(v); if (o < 0 || v < 0xa00000) return null; const d = b.readDoubleLE(o); return Number.isFinite(d) && Math.abs(d) < 1e9 && (d === Math.round(d * 1000) / 1000) ? d : null; };
let arg = process.argv[2]; let start = arg.startsWith("0x") ? parseInt(arg) : byName.get(arg);
const max = +(process.argv[3] || 3000);
const next = sorted.find((a) => a > start) || start + 0x10000;
const end = Math.min(next, start + 0x40000);
const cs = new Capstone(Const.CS_ARCH_X86, Const.CS_MODE_32);
const o = va2off(start);
const insns = []; { let a = start; while (a < end) { let chunk; try { chunk = cs.disasm(b.subarray(va2off(a), va2off(a) + Math.min(0x1000, end - a)), { address: a }); } catch { chunk = []; } if (!chunk.length) { a += 1; continue; } const last = chunk[chunk.length - 1]; const keep = (last.address + last.size >= end || chunk.length < 2) ? chunk : chunk.slice(0, -1); for (const x of keep) insns.push(x); const l2 = keep[keep.length - 1]; a = l2.address + l2.size; } }
let n = 0;
for (const i of insns) {
  let note = "";
  for (const m of (i.opStr.match(/0x[0-9a-f]{5,8}/g) || [])) {
    const v = parseInt(m);
    if (rt.has(v)) note += " <"+rt.get(v)+">"; else if (fslots.has(v)) note += " <"+fslots.get(v)+">"; else if (syms.has(v)) note += " <" + syms.get(v) + ">";
    else { const s = cstr(v); if (s) note += ' "' + s + '"'; else if (i.opStr.includes("qword ptr [" + m)) { const d = dbl(v); if (d !== null) note += " =" + d; } }
  }
  {const m2=i.opStr.match(/^dword ptr \[esp \+ 4\], (0x[0-9a-f]+|\d+)$/);if(i.mnemonic==="mov"&&m2&&vars.has(parseInt(m2[1])))note+=" var:"+vars.get(parseInt(m2[1]));
const m3=i.opStr.match(/, 0x(40[0-9a-f]{6}|c0[0-9a-f]{6}|3ff[0-9a-f]{5}|bff[0-9a-f]{5})$/);if(i.mnemonic==="mov"&&m3&&i.opStr.startsWith("dword ptr")){const bb=Buffer.alloc(8);bb.writeUInt32LE(parseInt(m3[1],16),4);note+=" hi=>"+bb.readDoubleLE(0);}}
  console.log(i.address.toString(16) + "  " + i.mnemonic + " " + i.opStr + (note ? "   ;" + note : ""));
  if (++n >= max) break;
}
