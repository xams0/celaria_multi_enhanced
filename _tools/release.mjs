// Publishes the launcher on GitHub: release <tag> (created if needed) with the exe as asset "Celaria.Lanppart3.exe"
// (an existing asset with this name is replaced). The token comes from the git credential manager (never printed).
// usage: node _tools/release.mjs v2.0.1 "Lanppart3Launcher/out/Celaria Lanppart3.exe" Lanppart3Launcher/release_notes.md
import fs from "node:fs";
import { execSync } from "node:child_process";
const [tag, exe, notesFile] = process.argv.slice(2);
const repo = "xams0/celaria_multi_enhanced";
const cred = execSync("git credential fill", { input: "protocol=https\nhost=github.com\n\n", env: { ...process.env, GIT_TERMINAL_PROMPT: "0" } }).toString();
const token = /^password=(.*)$/m.exec(cred)[1];
const headers = { Authorization: "Bearer " + token, Accept: "application/vnd.github+json", "User-Agent": "lp3-release" };
let r = await fetch(`https://api.github.com/repos/${repo}/releases/tags/${tag}`, { headers });
let rel = r.status === 200 ? await r.json() : null;
if (!rel) {
  r = await fetch(`https://api.github.com/repos/${repo}/releases`, {
    method: "POST", headers,
    body: JSON.stringify({ tag_name: tag, target_commitish: "main", name: "Celaria Lanppart3 " + tag, body: fs.readFileSync(notesFile, "utf8"), draft: false, prerelease: false }),
  });
  rel = await r.json();
  if (!rel.id) { console.error("create failed:", rel.message); process.exit(1); }
}
console.log("release", rel.id, rel.html_url);
for (const a of rel.assets || []) {
  if (a.name === "Celaria.Lanppart3.exe") {
    await fetch(`https://api.github.com/repos/${repo}/releases/assets/${a.id}`, { method: "DELETE", headers });
    console.log("previous asset removed");
  }
}
const data = fs.readFileSync(exe);
r = await fetch(`https://uploads.github.com/repos/${repo}/releases/${rel.id}/assets?name=Celaria.Lanppart3.exe`, {
  method: "POST", headers: { ...headers, "Content-Type": "application/octet-stream" }, body: data,
});
const a = await r.json();
console.log(a.browser_download_url || ("upload failed: " + a.message));
