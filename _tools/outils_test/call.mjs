// usage: node call.mjs <tool> '<json args>' [maxChars]
import http from "node:http";
const [,, name, args = "{}", max = "200000"] = process.argv;
const body = JSON.stringify({ name, arguments: JSON.parse(args) });
const req = http.request({ host: "127.0.0.1", port: 7777, path: "/call", method: "POST", timeout: 0 }, (res) => {
  let s = ""; res.setEncoding("utf8"); res.on("data", (c) => (s += c)); res.on("end", () => {
    const j = JSON.parse(s);
    if (j.error) { console.log(JSON.stringify(j.error)); process.exit(1); }
    const r = j.result;
    let text = (r.content || []).map((c) => c.text ?? JSON.stringify(c)).join("\n");
    if (!text && r.structuredContent) text = JSON.stringify(r.structuredContent);
    console.log(text.slice(0, +max));
  });
});
req.setTimeout(0);
req.end(body);
