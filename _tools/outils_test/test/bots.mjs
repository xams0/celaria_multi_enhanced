// Fake Celaria clients (protocol v4) used to test the cup mode end-to-end against the real Java server.
// usage: node bots.mjs <port> <durationSec>
import net from "node:net";
import dgram from "node:dgram";

const PORT = +(process.argv[2] || 6599);
const DURATION = +(process.argv[3] || 120);
const t0 = Date.now();
const ts = () => ((Date.now() - t0) / 1000).toFixed(2).padStart(7);

// per bot: list of [finishDelaySec, restartAfterSec|null] per round (cycled). null delay = doesn't finish
const MODE = process.env.BOTS_MODE || "cup";
const BOTS = MODE === "reconnect" ? [
  { name: "Alice", steam: 76561190000000001n, rounds: [[3.0, null]], chatOnEnd: "/cup" },
  { name: "Carol", steam: 76561190000000003n, rounds: [[4.0, null]], quitAfter: 16 },
  { name: "Carol", steam: 76561190000000003n, rounds: [[4.0, null]], startAfter: 18 },
] : [
  { name: "Alice", steam: 76561190000000001n, rounds: [[3.0, null], [3.0, null], [3.0, null]] },
  { name: "Bob", steam: 76561190000000002n, rounds: [[3.5, null], [3.2, 1.7], [2.5, null]] },
  { name: "Carol", steam: 76561190000000003n, rounds: [[4.0, null], [null, null], [4.0, null]] },
];

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
  let id = -1, key = -1, magic = 0, udpReady = false, round = 0, roundStartAt = 0, timers = [];
  let rx = Buffer.alloc(0);
  const send = (pid, payload) => sock.write(tcpPacket(pid, payload));
  const udpSend = (pid, payload = Buffer.alloc(0)) => {
    const h = Buffer.alloc(8); h.write("CCPU", 0, "ascii"); h.writeUInt8(id, 4); h.writeUInt8(pid, 5); h.writeUInt16LE(key, 6);
    udp.send(Buffer.concat([h, payload]), PORT, "127.0.0.1");
  };
  let upd = 0;
  const keepalive = setInterval(() => {
    if (id < 0) return;
    if (!udpReady) udpSend(10);
    const st = Buffer.alloc(2 + 12 + 8); st.writeUInt8(upd = (upd + 1) % 128, 0); st.writeUInt8(0, 1);
    udpSend(1, st);
  }, 200);
  const tcpAlive = setInterval(() => send(210), 3000);

  sock.on("connect", () => send(1, Buffer.concat([u16(4), u8(1)])));
  udp.on("message", (m) => {
    if (m.toString("ascii", 0, 4) !== "CSPU") return;
    if (m[4] === 10 && !udpReady) { udpReady = true; send(240); log("UDP ok"); }
  });
  sock.on("data", (d) => {
    rx = Buffer.concat([rx, d]);
    while (rx.length >= 7) {
      if (rx.toString("ascii", 0, 4) !== "CSPT") { log("bad header"); sock.destroy(); return; }
      const pid = rx[4], len = rx.readUInt16LE(5);
      if (rx.length < 7 + len) break;
      const p = rx.subarray(7, 7 + len); rx = rx.subarray(7 + len);
      handle(pid, p);
    }
  });
  function handle(pid, p) {
    switch (pid) {
      case 1: {// SERVER_INFO
        const name = Buffer.alloc(cfg.name.length * 2); for (let i = 0; i < cfg.name.length; i++) name.writeUInt16LE(cfg.name.charCodeAt(i), i * 2);
        const steam = Buffer.alloc(8); steam.writeBigUInt64LE(cfg.steam);
        send(3, Buffer.concat([u8(cfg.name.length), name, steam, Buffer.from([0, 200, 150, 120, 255, 255, 255, 0, 200, 255])]));
        break;
      }
      case 8: id = p[0]; key = p.readUInt16LE(1); log("joined id", id); send(4, Buffer.concat([u32(1000), u8(1)])); break;
      case 200: {// chat
        const n = p[3]; let s = ""; for (let i = 0; i < n; i++) s += String.fromCharCode(p.readUInt16LE(4 + i * 2));
        if (index === 0) console.log(ts(), "CHAT  ", s);
        break;
      }
      case 180: log("NEW_MAPLOAD gamemode", p[0]); clearTimers(); break;
      case 182: magic = p.readUInt16LE(0); send(199, u16(magic)); break;
      case 4: send(199, u16(magic)); break;// map packet -> request next
      case 5: send(6); break;// map loaded
      case 183: {
        const ticks = p.readUInt32LE(0); roundStartAt = Date.now();
        const [delay, restart] = cfg.rounds[round % cfg.rounds.length]; round++;
        log("ROUND_START remaining", ticks / 100, "s; plan", delay, restart ? "(restart at " + restart + ")" : "");
        if (restart) timers.push(setTimeout(() => { send(201); log("reset run"); }, restart * 1000));
        if (delay !== null) timers.push(setTimeout(() => {
          const clientTime = Math.round((delay - (restart || 0)) * 100);
          send(203, u32(clientTime)); log("goal, client time", clientTime / 100);
          // second finish: must be ignored by the cup
          timers.push(setTimeout(() => send(203, u32(50)), 300));
        }, delay * 1000));
        break;
      }
      case 184: log("ROUND_END"); clearTimers(); if (index === 0) send(120, Buffer.from([1, 0, 10, 1])); if (cfg.chatOnEnd) setTimeout(() => send(200, Buffer.from(cfg.chatOnEnd, "latin1")), 1500); break;
      case 120: {// leaderboard
        let o = 0; const type = p[o++], total = p[o++], cnt = p[o++], own = p[o++];
        const rows = [];
        for (let i = 0; i < cnt; i++) {
          const place = p[o++], pidd = p[o++], nl = p[o++]; const nm = p.toString("latin1", o, o + nl); o += nl; const badge = p[o++]; const set = p[o++];
          let tm = null, medal = null; if (set) { tm = p.readUInt32LE(o); o += 4; medal = p[o++]; }
          rows.push(`  ${place}. [${pidd}] ${nm}  ${tm === null ? "--:--:--" : (tm / 100).toFixed(2) + "s"}`);
        }
        console.log(ts(), "LEADERBOARD type", type, "total", total, "own", own); console.log(rows.join("\n"));
        break;
      }
      case 121: if (index === 0) send(120, Buffer.from([0, 0, 10, 1])); break;
      case 250: log("DISCONNECTED reason", p[0]); break;
    }
  }
  function clearTimers() { for (const t of timers) clearTimeout(t); timers = []; }
  sock.on("close", () => { log("socket closed"); clearInterval(keepalive); clearInterval(tcpAlive); udp.close(); });
  sock.on("error", (e) => log("err", e.message));
  return () => { send(20); sock.end(); };
}

const stops = [];
BOTS.forEach((b, i) => setTimeout(() => { const st = startBot(b, i); stops.push(st); if (b.quitAfter) setTimeout(() => { console.log(ts(), b.name, "QUITS"); st(); }, b.quitAfter * 1000); }, (b.startAfter || 0) * 1000));
setTimeout(() => { stops.forEach((s) => s()); setTimeout(() => process.exit(0), 500); }, DURATION * 1000);
