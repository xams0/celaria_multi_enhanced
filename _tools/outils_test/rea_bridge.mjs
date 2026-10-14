// Minimal HTTP -> REA MCP (stdio) bridge, keeps one REA/Ghidra session alive.
// POST /call {"name": "<tool>", "arguments": {...}}  -> JSON result
// GET  /tools -> tools/list
import { spawn } from "node:child_process";
import http from "node:http";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const rea = join(here, "rea", "node_modules", "rea-agents", "scripts", "rea.mjs");
const child = spawn(process.execPath, [rea, "--mcp"], { stdio: ["pipe", "pipe", "inherit"], env: process.env });

let nextId = 1;
const pending = new Map();
let buf = "";
child.stdout.on("data", (d) => {
  buf += d.toString("utf8");
  let i;
  while ((i = buf.indexOf("\n")) >= 0) {
    const line = buf.slice(0, i).trim();
    buf = buf.slice(i + 1);
    if (!line) continue;
    let msg;
    try { msg = JSON.parse(line); } catch { console.error("bad line", line.slice(0, 200)); continue; }
    if (msg.id !== undefined && pending.has(msg.id)) {
      pending.get(msg.id)(msg);
      pending.delete(msg.id);
    }
  }
});
child.on("exit", (c) => { console.error("rea exited", c); process.exit(1); });

function rpc(method, params) {
  const id = nextId++;
  child.stdin.write(JSON.stringify({ jsonrpc: "2.0", id, method, params }) + "\n");
  return new Promise((res) => pending.set(id, res));
}

await rpc("initialize", { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "rea-bridge", version: "1" } });
child.stdin.write(JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }) + "\n");
console.error("REA MCP initialized");

http.createServer(async (req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", async () => {
    try {
      let out;
      if (req.url === "/tools") out = await rpc("tools/list", {});
      else out = await rpc("tools/call", JSON.parse(body));
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify(out));
    } catch (e) {
      res.writeHead(500);
      res.end(String(e));
    }
  });
}).listen(7777, "127.0.0.1", () => console.error("bridge on 7777"));
