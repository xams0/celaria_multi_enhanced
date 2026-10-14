// Fake Celaria clients for the synchronized start / complete reset tests.
// usage: node bots2.mjs <port> <durationSec>
import net from "node:net";
import dgram from "node:dgram";

const PORT = +(process.argv[2] || 6599);
const DURATION = +(process.argv[3] || 60);
const t0 = Date.now();
const ts = () => ((Date.now() - t0) / 1000).toFixed(2).padStart(7);

// events per round: [timeSec, action]; actions: move(x), cp(x) (checkpoint respawn at x), reset (UDP reset to spawn), reset201, goal(clientTimeSec)
const BOTS = [ { name: "Runner", steam: 9n, loadDelay: 0, plan: [], circle: true } ];

function tcpPacket(id, payload = Buffer.alloc(0)) {
  const h = Buffer.alloc(7); h.write("CCPT", 0, "ascii"); h.writeUInt8(id, 4); h.writeUInt16LE(payload.length, 5);
  return Buffer.concat([h, payload]);
}
const u8 = (v) => Buffer.from([v & 255]);
const u16 = (v) => { const b = Buffer.alloc(2); b.writeUInt16LE(v); return b; };
const u32 = (v) => { const b = Buffer.alloc(4); b.writeUInt32LE(v >>> 0); return b; };

function startBot(cfg, index) {
  const log = (...a) => console.log(ts(), cfg.name.padEnd(6), ...a);
  const sock = net.connect(PORT, "127.0.0.1");
  const udp = dgram.createSocket("udp4");
  let id = -1, key = -1, magic = 0, udpReady = false, timers = [];
  let rot = 0, pos = [0, 0, 0], respawn = 0, upd = 0, sentLoaded = false;
  let rx = Buffer.alloc(0);
  const send = (pid, payload) => sock.writable && sock.write(tcpPacket(pid, payload));
  const udpSend = (pid, payload = Buffer.alloc(0)) => {
    const h = Buffer.alloc(8); h.write("CCPU", 0, "ascii"); h.writeUInt8(id, 4); h.writeUInt8(pid, 5); h.writeUInt16LE(key, 6);
    udp.send(Buffer.concat([h, payload]), PORT, "127.0.0.1");
  };
  const sendState = () => {
    const st = Buffer.alloc(2 + 12 + 8); upd = (upd + 1) % 256; st.writeUInt8(upd, 0); st.writeUInt8(respawn, 1);
    if (cfg.circle) { const t = (Date.now() - t0) / 1000; pos = [Math.cos(t * 0.8) * 15, Math.sin(t * 0.8) * 15, 10]; rot = ((-(t * 0.8 * 180 / Math.PI) - 90) % 360 + 360) % 360; }
    st.writeFloatLE(pos[0], 2); st.writeFloatLE(pos[1], 6); st.writeFloatLE(pos[2], 10); { const t = (Date.now() - t0) / 1000; const tx = -Math.sin(t * 0.8), ty = Math.cos(t * 0.8); st.writeUInt8(Math.round((tx + 1) / 2 * 255), 14); st.writeUInt8(Math.round((ty + 1) / 2 * 255), 15); st.writeUInt8(128, 16); st.writeUInt8(Math.round(0.12 / 3 * 255), 17); } st.writeUInt8(Math.round(rot / 360 * 255), 18); st.writeUInt8(1, 19);
    udpSend(1, st);
  };
  const keepalive = setInterval(() => { if (id < 0) return; if (!udpReady) udpSend(10); sendState(); }, 100);
  const tcpAlive = setInterval(() => send(210), 3000);
  const at = (sec, fn) => timers.push(setTimeout(fn, sec * 1000));

  sock.on("connect", () => send(1, Buffer.concat([u16(4), u8(1)])));
  udp.on("message", (m) => { if (m.toString("ascii", 0, 4) === "CSPU" && m[4] === 10 && !udpReady) { udpReady = true; send(240); } });
  sock.on("data", (d) => {
    rx = Buffer.concat([rx, d]);
    while (rx.length >= 7) {
      const pid = rx[4], len = rx.readUInt16LE(5);
      if (rx.length < 7 + len) break;
      const p = rx.subarray(7, 7 + len); rx = rx.subarray(7 + len);
      handle(pid, p);
    }
  });
  function handle(pid, p) {
    switch (pid) {
      case 1: {
        const name = Buffer.alloc(cfg.name.length * 2); for (let i = 0; i < cfg.name.length; i++) name.writeUInt16LE(cfg.name.charCodeAt(i), i * 2);
        const steam = Buffer.alloc(8); steam.writeBigUInt64LE(76561190000000000n + cfg.steam);
        send(3, Buffer.concat([u8(cfg.name.length), name, steam, Buffer.from([0, 200, 150, 120, 255, 255, 255, 0, 200, 255])]));
        break;
      }
      case 8: id = p[0]; key = p.readUInt16LE(1); break;
      case 200: { const n = p[3]; let s = ""; for (let i = 0; i < n; i++) s += String.fromCharCode(p.readUInt16LE(4 + i * 2)); if (index === 0 || /You |Complete/.test(s)) log("CHAT", s); break; }
      case 180: log("NEW_MAPLOAD"); clearTimers(); pos = [0, 0, 0]; break;
      case 182: magic = p.readUInt16LE(0); send(199, u16(magic)); break;
      case 4: send(199, u16(magic)); break;
      case 5: { if (cfg.loadDelay) log("loading slowly..."); const first = !sentLoaded; sentLoaded = true; if (first) setTimeout(() => send(6), cfg.loadDelay * 1000); break; } // like the real client: packet 6 only for the first map
      case 183: {
        log("ROUND_START (exact ms " + Date.now() % 100000 + ")");
        for (const [t, act, arg] of cfg.plan) at(t, () => {
          if (act === "move") pos = [arg, 0, 0];
          if (act === "cp") { respawn++; pos = [arg, 0, 0]; log("respawn at checkpoint"); }
          if (act === "reset") { respawn++; pos = [0, 0, 0]; log("COMPLETE RESET (back to spawn)"); }
          if (act === "reset201") { respawn++; pos = [0, 0, 0]; send(201); log("COMPLETE RESET (packet 201)"); }
          if (act === "goal") { send(203, u32(Math.round(arg * 100))); log("goal", arg); }
        });
        break;
      }
      case 184: log("ROUND_END"); clearTimers(); if (index === 0) send(120, Buffer.from([1, 0, 10, 1])); break;
      case 120: {
        let o = 0; const type = p[o++], total = p[o++], cnt = p[o++]; o++;
        const rows = [];
        for (let i = 0; i < cnt; i++) {
          const place = p[o++]; o++; const nl = p[o++]; const nm = p.toString("latin1", o, o + nl); o += nl; o++; const set = p[o++];
          let tm = null; if (set) { tm = p.readUInt32LE(o); o += 5; }
          rows.push(`  ${place}. ${nm}  ${tm === null ? "--:--:--" : (tm / 100).toFixed(2) + "s"}`);
        }
        log("LEADERBOARD type " + type); console.log(rows.join("\n"));
        break;
      }
    }
  }
  function clearTimers() { for (const t of timers) clearTimeout(t); timers = []; }
  sock.on("close", () => { clearInterval(keepalive); clearInterval(tcpAlive); udp.close(); });
  return () => { send(20); sock.end(); };
}

const stops = BOTS.map((b, i) => startBot(b, i));
setTimeout(() => { stops.forEach((s) => s()); setTimeout(() => process.exit(0), 500); }, DURATION * 1000);
