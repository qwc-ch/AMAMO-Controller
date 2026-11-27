#!/usr/bin/env node
// 聚焦验证：全量下发 SwUpDn=6（模拟遥控器报文），观察“母导风板（大板）”是否联动向下。
// 用法：node tools/full_swupdn_test.mjs [IP]
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
    } catch (_) {}
  };
  const cleanup = () => { clearTimeout(t); sock.removeListener("message", onMsg); };
  const t = setTimeout(() => { cleanup(); resolve(null); }, timeoutMs);
  sock.on("message", onMsg);
  sock.send(Buffer.from(payload), PORT, IP);
});
const fullCmd = async (state) => {
  const full = { ...state };
  delete full.TemSen;
  if (!full.SetTem || full.SetTem < 16) full.SetTem = 26;
  const opts = COLS.filter((c) => c in full && c !== "TemSen");
  const resp = await request(envelope(encV1(JSON.stringify({
    opt: opts, p: opts.map((c) => full[c]), t: "cmd", sub: false, mac: MAC, uid: 0,
  }), V1_KEY), MAC));
  return resp ? JSON.parse(decV1(resp.pack, V1_KEY)).r : null;
};
const status = async () => {
  const resp = await request(envelope(encV1(JSON.stringify({ cols: COLS, mac: MAC, t: "status" }), V1_KEY), MAC));
  if (!resp) return null;
  const obj = JSON.parse(decV1(resp.pack, V1_KEY));
  return Array.isArray(obj.dat)
    ? Object.fromEntries((obj.cols ?? COLS).map((c, i) => [c, obj.dat[i]]))
    : obj.dat;
};
const brief = (s) => s ? `Pow=${s.Pow} SwUpDn=${s.SwUpDn} WdSpd=${s.WdSpd} SetTem=${s.SetTem}` : "无应答";

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

console.log("\n全量下发 SwUpDn=6（所有字段原样，仅改摆风）…");
const next = { ...st, SwUpDn: 6 };
console.log("cmd r =", await fullCmd(next));
await sleep(2000);
const after = await status();
console.log("回读:", brief(after));

console.log("\n===== 请观察母导风板（大板）=====");
console.log("  刚才用“遥控器式全量报文”把 SwUpDn 设成 6（标准表最下）");
console.log("  母导风板（大板）是否从朝上变朝下了？");
sock.close();
process.exit(0);
