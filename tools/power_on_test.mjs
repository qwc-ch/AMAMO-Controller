#!/usr/bin/env node
// 真机开机实测（用户已授权）：scan -> bind(V1) -> cmd Pow=1 -> status 回读验证。
// 仅发一条 Pow=1（绝对值、幂等）；key 策略与 Kotlin GreeLanClient 一致（bind key 失败换 V1 通用密钥）。
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const MAC = "c03937518dc8";

const encV1 = (plain, key) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]).toString("base64");
};
const decV1 = (b64, key) => {
  const c = crypto.createDecipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(b64, "base64")), c.final()]).toString("utf8");
};
const envelope = (packB64, tcid) => JSON.stringify({
  cid: "app", i: 1, t: "pack", uid: 0, tcid, pack: packB64,
});

const sock = dgram.createSocket("udp4");
sock.bind(() => sock.setBroadcast(true));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const request = (payload, timeoutMs = 2500) => new Promise((resolve) => {
  const onMsg = (msg) => {
    try {
      const o = JSON.parse(msg.toString("utf8"));
      if (o.t === "pack") { cleanup(); resolve(o); }
    } catch (_) { /* ignore */ }
  };
  const cleanup = () => { clearTimeout(t); sock.removeListener("message", onMsg); };
  const t = setTimeout(() => { cleanup(); resolve(null); }, timeoutMs);
  sock.on("message", onMsg);
  const buf = Buffer.from(payload, "utf8");
  sock.send(buf, 0, buf.length, PORT, IP);
});

// ---- 1. scan（单播，bind 后设备可能忽略广播）----
console.log("[1] scan 单播…");
const scan = await request(JSON.stringify({ t: "scan" }), 2000);
if (!scan) { console.error("scan 无应答"); process.exit(1); }
const dev = JSON.parse(decV1(scan.pack, V1_KEY));
console.log(`  设备 ${dev.mac} ver=${dev.ver}`);

// ---- 2. bind（V1 通用密钥）----
console.log("[2] bind V1…");
const bind = await request(envelope(encV1(JSON.stringify({ mac: MAC, t: "bind", uid: 0 }), V1_KEY), MAC));
if (!bind) { console.error("bind 无应答"); process.exit(1); }
const devKey = JSON.parse(decV1(bind.pack, V1_KEY)).key;
console.log(`  bindok，设备 key=${devKey.slice(0, 4)}****`);

// ---- 3. cmd Pow=1（opt/p 数组形式，与 gree-remote 一致；先 bind key 后通用密钥；绝对值幂等）----
console.log("[3] cmd Pow=1 …");
const cmdPlain = JSON.stringify({ opt: ["Pow"], p: [1], t: "cmd", sub: false, mac: MAC, uid: 0 });
let r = null, viaKey = null;
for (const [label, key] of [["bind key", devKey], ["V1 通用密钥", V1_KEY]]) {
  const resp = await request(envelope(encV1(cmdPlain, key), MAC));
  if (resp) {
    const obj = JSON.parse(decV1(resp.pack, key));
    if (obj.r === 200) { r = obj; viaKey = label; break; }
  }
}
if (!r) { console.error("cmd 无应答/被拒"); process.exit(1); }
console.log(`  cmd r=200 ✓（生效密钥：${viaKey}）`);

// ---- 4. status 回读验证 ----
await sleep(1200);
console.log("[4] status 回读…");
const stPlain = JSON.stringify({
  cols: ["Pow", "Mod", "SetTem", "TemSen", "WdSpd"], mac: MAC, t: "status",
});
let state = null;
for (const key of [V1_KEY, devKey]) {
  const resp = await request(envelope(encV1(stPlain, key), MAC));
  if (resp) {
    const obj = JSON.parse(decV1(resp.pack, key));
    const dat = Array.isArray(obj.dat)
      ? Object.fromEntries((obj.cols ?? []).map((c, i) => [c, obj.dat[i]]))
      : obj.dat;
    state = dat; break;
  }
}
if (!state) { console.error("status 回读失败"); process.exit(1); }

console.log("\n===== 开机实测结果 =====");
console.log(`  Pow    = ${state.Pow}（${state.Pow === 1 ? "✓ 已开机" : "✗ 未开机"}）`);
console.log(`  Mod    = ${state.Mod}`);
console.log(`  SetTem = ${state.SetTem}℃`);
if (state.TemSen) console.log(`  TemSen = ${state.TemSen} → 室温 ${state.TemSen - 40}℃`);
console.log(`  WdSpd  = ${state.WdSpd}`);
process.exit(state.Pow === 1 ? 0 : 1);
