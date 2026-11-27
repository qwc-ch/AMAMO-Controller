#!/usr/bin/env node
// 只读探测：读取标准字段 + 扩展字段（防直吹等），定位子母导风板控制字段。
// 不发任何 cmd。用法：node tools/probe_extra_fields.mjs [IP]
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const MAC = "c03937518dc8";
const COLS = [
  "Pow", "Mod", "SetTem", "WdSpd", "SwUpDn", "SwingLfRig", "TemSen", "TemRec",
  // 已知扩展字段（HA Gree 集成 opt_list）
  "AntiDirectBlow", "SlpMod", "AssHt", "LigSen", "Add0.5", "Beeper", "Blo",
  // 猜测的双导风板候选字段（设备不认通常会返回 0 或缺省）
  "SwUpDn2", "SwUpDnB", "SwDn", "SwUpDnSub", "WdSpd2", "MainSwUpDn", "SubSwUpDn",
];

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

const bind = await request(envelope(encV1(JSON.stringify({ mac: MAC, t: "bind", uid: 0 }), V1_KEY), MAC));
if (!bind) { console.error("bind 无应答"); process.exit(1); }

const resp = await request(envelope(encV1(JSON.stringify({ cols: COLS, mac: MAC, t: "status" }), V1_KEY), MAC));
if (!resp) { console.error("status 无应答"); process.exit(1); }
const obj = JSON.parse(decV1(resp.pack, V1_KEY));
const state = Array.isArray(obj.dat)
  ? Object.fromEntries((obj.cols ?? COLS).map((c, i) => [c, obj.dat[i]]))
  : obj.dat;

console.log("===== 设备回读字段值（只读）=====");
console.log(`  Pow=${state.Pow}  SwUpDn=${state.SwUpDn}  SwingLfRig=${state.SwingLfRig}  WdSpd=${state.WdSpd}  TemSen=${state.TemSen}`);
console.log("\n----- 扩展字段 -----");
for (const f of ["AntiDirectBlow", "SlpMod", "AssHt", "LigSen", "Add0.5", "Beeper"]) {
  console.log(`  ${f.padEnd(16)} = ${state[f] !== undefined ? state[f] : "(设备未返回)"}`);
}
console.log("\n----- 双导风板候选字段 -----");
for (const f of ["SwUpDn2", "SwUpDnB", "SwDn", "SwUpDnSub", "MainSwUpDn", "SubSwUpDn", "WdSpd2"]) {
  console.log(`  ${f.padEnd(16)} = ${state[f] !== undefined ? state[f] : "(设备未返回)"}`);
}
console.log("\n----- 设备实际返回的所有cols -----");
console.log(" ", JSON.stringify(obj.cols ?? Object.keys(state)));
sock.close();
process.exit(0);
