// usage: node dec.mjs <addr|name> [outfile] : decompile via REA bridge, post-process YYC names
import http from "node:http"; import fs from "node:fs"; import { execFileSync } from "node:child_process"; import { fileURLToPath } from "node:url";
const [,, proc, out] = process.argv;
const body = JSON.stringify({ name: "procedure_pseudo_code", arguments: { procedure: proc } });
const req = http.request({ host: "127.0.0.1", port: 7777, path: "/call", method: "POST" }, (res) => {
  let s = ""; res.setEncoding("utf8"); res.on("data", (c) => (s += c)); res.on("end", () => {
    const j = JSON.parse(s); const r = j.result;
    const t = (r.content || []).map((c) => c.text).join("\n");
    let code; try { code = JSON.parse(t).normalized_result; } catch { console.log(t.slice(0, 2000)); process.exit(1); }
    const tmp = (out || "dec_out.c") + ".raw"; fs.writeFileSync(tmp, code.replace(/\r/g, ""));
    const pp = execFileSync(process.execPath, [fileURLToPath(new URL("./yyc_pp.mjs", import.meta.url)), tmp], { maxBuffer: 1 << 28 }).toString();
    if (out) { fs.writeFileSync(out, pp); console.log(out + ": " + pp.split("\n").length + " lines"); } else process.stdout.write(pp);
  });
});
req.setTimeout(0); req.end(body);
