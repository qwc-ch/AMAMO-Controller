#!/usr/bin/env node
// 验证 AntiDirectBlow（防直吹）是否控制子母导风板的“母板”。
// 流程：开机 → 读 AntiDirectBlow 当前值 → 翻转(0↔1)全量下发 → 回读。
// 用法：node tools/anti_direct_blow_test.mjs [IP]
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const MAC = "c03937518dc8";
const COLS = ["Pow", "Mod", "SetTem", "WdSpd", "Air", "Blo", "Health", "SwhSlp",
  "Lig", "SwingLfRig", "SwUpDn", "Quiet", "Tur", "StHt", "TemUn", "HeatCoolType",
  "TemRec", "SvSt", "SlpMod", "AntiDirectBlow", "AssHt", "LigSen", "Add0.5"];

const encV1 = (plain, key) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]).toString("base64");
};
const decV1 = (b64, key) => {
  const c = crypto.createDecipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(b64, "base64")), c.final()]).toString("utf8");
};
const envelope = (packB64, tcid) => JSON.stringify({ cid: "app", i: 1, t: "pack", uid: 0, tcid, pack: packB64 });

const sock = dgram.createSocket("udp4");
sock.bind(() => sock.setBroadcast(true));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const request = (payload, timeoutMs = 2500) => new Promise((resolve) => {
  const onMsg = (msg) => {
    try {
      const o = JSON.parse(msg.toString("utf8"));
      if (o.t === "pack") { cleanup(); resolve(o); }
    } catch (_) {}
  };
  const cleanup = () => { clearTimeout(t); sock.removeListener("message", onMsg); };
  const t = setTimeout(() => { cleanup(); resolve(null); }, timeoutMs);
  sock.on("message", onMsg);
  sock.send(Buffer.from(payload), PORT, IP);
});
const cmd = (opts, values) => request(envelope(encV1(JSON.stringify({
  opt: opts, p: values, t: "cmd", sub: false, mac: MAC, uid: 0,
}), V1_KEY), MAC));
const status = async () => {
  const resp = await request(envelope(encV1(JSON.stringify({ cols: COLS, mac: MAC, t: "status" }), V1_KEY), MAC));
  if (!resp) return null;
  const obj = JSON.parse(decV1(resp.pack, V1_KEY));
  return Array.isArray(obj.dat)
    ? Object.fromEntries((obj.cols ?? COLS).map((c, i) => [c, obj.dat[i]]))
    : obj.dat;
};
const fullCmd = async (state) => {
  const full = { ...state };
  delete full.TemSen;
  if (!full.SetTem || full.SetTem < 16) full.SetTem = 26;
  const opts = Object.keys(full);
  const resp = await cmd(opts, opts.map((c) => full[c]));
  return resp ? JSON.parse(decV1(resp.pack, V1_KEY)).r : null;
};
const brief = (s) => s ? `Pow=${s.Pow} SwUpDn=${s.SwUpDn} AntiDirectBlow=${s.AntiDirectBlow ?? "未返回"}` : "无应答";

const bind = await request(envelope(encV1(JSON.stringify({ mac: MAC, t: "bind", uid: 0 }), V1_KEY), MAC));
if (!bind) { console.error("bind 无应答"); process.exit(1); }
console.log("bind OK");

let st = await status();
console.log("初始:", brief(st));
if (!st || st.Pow !== 1) {
  st = st ?? Object.fromEntries(COLS.map((c) => [c, 0]));
  st.Pow = 1;
  console.log("全量开机 cmd r =", await fullCmd(st));
  await sleep(1500);
  st = await status();
  console.log("开机后:", brief(st));
  if (!st || st.Pow !== 1) { console.error("开机失败"); process.exit(1); }
}

const cur = st.AntiDirectBlow ?? 0;
const target = cur === 1 ? 0 : 1;
console.log(`\n翻转 AntiDirectBlow: ${cur} → ${target}（全量下发）`);
const next = { ...st, AntiDirectBlow: target };
console.log("cmd r =", await fullCmd(next));
await sleep(1500);
const after = await status();
console.log("回读:", brief(after));

console.log("\n===== 请观察母导风板（大板）====");
console.log(`  刚把 AntiDirectBlow 从 ${cur} 改成 ${target}`);
console.log("  母导风板是否从朝上变成朝下（或相反）？");
sock.close();
process.exit(0);
