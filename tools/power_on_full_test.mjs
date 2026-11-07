#!/usr/bin/env node
// 遥控器式全量开机实测（用户授权验证"摆风板正常往下"修复方案）：
// 读当前全状态 -> cmd 全量字段 + Pow=1（opt/p 数组，V1 通用密钥）-> status 回读。
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const MAC = "c03937518dc8";
const COLS = ["Pow", "Mod", "SetTem", "WdSpd", "Air", "Blo", "Health", "SwhSlp",
  "Lig", "SwingLfRig", "SwUpDn", "Quiet", "Tur", "StHt", "TemUn",
  "HeatCoolType", "TemRec", "SvSt", "TemSen"];

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
    } catch (_) { /* ignore */ }
  };
  const cleanup = () => { clearTimeout(t); sock.removeListener("message", onMsg); };
  const t = setTimeout(() => { cleanup(); resolve(null); }, timeoutMs);
  sock.on("message", onMsg);
  sock.send(Buffer.from(payload), PORT, IP);
});

const status = async () => {
  const resp = await request(envelope(encV1(JSON.stringify({ cols: COLS, mac: MAC, t: "status" }), V1_KEY), MAC));
  const obj = JSON.parse(decV1(resp.pack, V1_KEY));
  return Array.isArray(obj.dat)
    ? Object.fromEntries((obj.cols ?? COLS).map((c, i) => [c, obj.dat[i]]))
    : obj.dat;
};

// ---- 1. bind ----
const bind = await request(envelope(encV1(JSON.stringify({ mac: MAC, t: "bind", uid: 0 }), V1_KEY), MAC));
if (!bind) { console.error("bind 无应答"); process.exit(1); }
const devKey = JSON.parse(decV1(bind.pack, V1_KEY)).key;
console.log(`bind OK（设备 key=${devKey.slice(0, 4)}****，后续用 V1 通用密钥）`);

// ---- 2. 读当前全状态 ----
const cur = await status();
console.log("当前状态:", JSON.stringify(cur));

// ---- 3. 全量开机：所有字段 + Pow=1（与遥控器一致）----
const full = { ...cur };
delete full.TemSen;              // TemSen 是只读上报，不下发
full.Pow = 1;                    // 唯一改变：开机；其余字段原样回写
const opts = COLS.filter((c) => c in full && c !== "TemSen");
const cmdPlain = JSON.stringify({ opt: opts, p: opts.map((c) => full[c]), t: "cmd", sub: false, mac: MAC, uid: 0 });
console.log("全量下发:", JSON.stringify(Object.fromEntries(opts.map((c) => [c, full[c]]))));
const resp = await request(envelope(encV1(cmdPlain, V1_KEY), MAC));
if (!resp) { console.error("cmd 无应答"); process.exit(1); }
console.log("cmd r =", JSON.parse(decV1(resp.pack, V1_KEY)).r);

// ---- 4. 回读验证 ----
await sleep(1200);
const after = await status();
console.log("\n===== 回读 =====");
console.log(`  Pow=${after.Pow}（${after.Pow === 1 ? "已开机 ✓" : "未开机 ✗"}） SwUpDn=${after.SwUpDn} SwingLfRig=${after.SwingLfRig} SetTem=${after.SetTem}℃ TemSen=${after.TemSen}→${after.TemSen - 40}℃`);
console.log("👉 请观察摆风板位置是否正常往下（与遥控器开机一致）");
process.exit(0);
