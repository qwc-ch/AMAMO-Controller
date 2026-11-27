#!/usr/bin/env node
// 上下摆风档位验证（用户授权）：对比「单字段」与「全量组合」哪种能让 SwUpDn 生效。
// 流程：读状态 → 单发 SwUpDn=6 → 回读 → 全量组合(其余字段原样, SwUpDn=6) → 回读。
// 用法：node tools/swupdn_test.mjs [IP] [目标档位=6]
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const TARGET = Number(process.argv[3] ?? 6);
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
const brief = (s) => s ? `Pow=${s.Pow} SwUpDn=${s.SwUpDn} SwingLfRig=${s.SwingLfRig} WdSpd=${s.WdSpd}` : "无应答";

// 1. bind
const bind = await request(envelope(encV1(JSON.stringify({ mac: MAC, t: "bind", uid: 0 }), V1_KEY), MAC));
if (!bind) { console.error("bind 无应答"); process.exit(1); }
console.log("bind OK");

// 2. 初始状态；若关机则用已验证的全量命令开机
console.log("bind OK");
let before = await status();
console.log("初始:", brief(before));
if (!before || before.Pow !== 1) {
  console.log("\n[0] 空调关机 → 全量开机（Pow=1，其余原样）…");
  const full = { ...(before ?? Object.fromEntries(COLS.map((c) => [c, 0]))) };
  delete full.TemSen;
  if (!full.SetTem || full.SetTem < 16) full.SetTem = 26;
  full.Pow = 1;
  const opts = COLS.filter((c) => c in full && c !== "TemSen");
  const respP = await cmd(opts, opts.map((c) => full[c]));
  console.log("  开机 cmd r =", respP ? JSON.parse(decV1(respP.pack, V1_KEY)).r : "无应答");
  await sleep(1500);
  before = await status();
  console.log("  回读:", brief(before));
  if (!before || before.Pow !== 1) {
    console.error("⚠️ 全量开机后仍未开机，终止");
    sock.close();
    process.exit(1);
  }
}

// 3. 测 A：单字段 SwUpDn=TARGET
console.log(`\n[A] 单字段下发 SwUpDn=${TARGET} …`);
const respA = await cmd(["SwUpDn"], [TARGET]);
console.log("  cmd r =", respA ? JSON.parse(decV1(respA.pack, V1_KEY)).r : "无应答");
await sleep(1500);
const afterA = await status();
console.log("  回读:", brief(afterA), afterA?.SwUpDn === TARGET ? "✅ 生效" : "❌ 未生效");

// 4. 测 B：全量组合（其余原样，SwUpDn=TARGET）
console.log(`\n[B] 全量组合下发 SwUpDn=${TARGET} …`);
const full = { ...(afterA ?? before) };
delete full.TemSen;
full.SwUpDn = TARGET;
const opts = COLS.filter((c) => c in full && c !== "TemSen");
const respB = await cmd(opts, opts.map((c) => full[c]));
console.log("  cmd r =", respB ? JSON.parse(decV1(respB.pack, V1_KEY)).r : "无应答");
await sleep(1500);
const afterB = await status();
console.log("  回读:", brief(afterB), afterB?.SwUpDn === TARGET ? "✅ 生效" : "❌ 未生效");

console.log("\n===== 结论 =====");
console.log(`  单字段: ${afterA?.SwUpDn === TARGET ? "生效" : "不生效"}`);
console.log(`  全量组合: ${afterB?.SwUpDn === TARGET ? "生效" : "不生效"}`);
console.log("👉 请同时观察导风板是否真的移动到对应角度");
sock.close();
process.exit(0);
