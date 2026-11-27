#!/usr/bin/env node
// 上下摆风档位真机监测（只读，不发任何 cmd）。
// bind 一次 → 高频轮询 status，捕捉 SwUpDn / SwingLfRig / Pow 的每一次变化。
// 用法：node tools/swing_monitor.mjs [IP] [持续秒数，默认150]
// 操作时请按顺序用遥控器调：关机→开机→最下→中下→居中→中上→最上→扫风→合拢，每档停 3~5 秒。
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const DURATION_MS = (Number(process.argv[3]) || 150) * 1000;
const INTERVAL_MS = 900;
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
const envelope = (packB64, tcid) => JSON.stringify({
  cid: "app", i: 1, t: "pack", uid: 0, tcid, pack: packB64,
});

const sock = dgram.createSocket("udp4");
let pending = new Map();
sock.on("message", (msg) => {
  try {
    const o = JSON.parse(msg.toString("utf8"));
    if (o.t === "pack" && pending.size) {
      const first = pending.keys().next().value;
      const cb = pending.get(first);
      pending.delete(first);
      cb(o);
    }
  } catch (_) { /* ignore */ }
});
sock.bind(() => sock.setBroadcast(true));

const sendRecv = (payload, timeoutMs) => new Promise((resolve) => {
  const id = Symbol("req");
  const timer = setTimeout(() => { pending.delete(id); resolve(null); }, timeoutMs);
  pending.set(id, (o) => { clearTimeout(timer); resolve(o); });
  const buf = Buffer.from(payload, "utf8");
  sock.send(buf, 0, buf.length, PORT, IP);
});

// bind（V1）— 仅用于确认设备，status 实测用 V1 通用密钥
const bindPlain = JSON.stringify({ mac: MAC, t: "bind", uid: 0 });
const bindResp = await sendRecv(envelope(encV1(bindPlain, V1_KEY), MAC), 3000);
if (!bindResp) { console.error("bind 无应答，退出"); process.exit(1); }
console.log(`[monitor] bind OK（${IP}），开始轮询 ${DURATION_MS / 1000}s，间隔 ${INTERVAL_MS}ms`);
console.log("[monitor] 同时记录 风速WdSpd / 上下摆风SwUpDn / 左右摆风SwingLfRig");
console.log("[monitor] 请先用遥控器调风速：自动→低→中→高，每档停 3~5 秒；再调风向各档\n");

const observed = new Map();   // SwUpDn -> 首次出现时间
const observedWind = new Map(); // WdSpd -> 首次出现时间
let last = null;
const start = Date.now();
let n = 0;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

while (Date.now() - start < DURATION_MS) {
  const plain = JSON.stringify({ cols: COLS, mac: MAC, t: "status" });
  const resp = await sendRecv(envelope(encV1(plain, V1_KEY), MAC), 1500);
  if (resp) {
    try {
      const dec = JSON.parse(decV1(resp.pack, V1_KEY));
      let st = {};
      if (dec.dat && typeof dec.dat === "object" && !Array.isArray(dec.dat)) st = dec.dat;
      else if (Array.isArray(dec.dat)) (dec.cols ?? COLS).forEach((c, i) => { st[c] = dec.dat[i]; });
      const cur = { Pow: st.Pow, WdSpd: st.WdSpd, SwUpDn: st.SwUpDn, SwingLfRig: st.SwingLfRig };
      const changed = !last || cur.Pow !== last.Pow || cur.WdSpd !== last.WdSpd ||
        cur.SwUpDn !== last.SwUpDn || cur.SwingLfRig !== last.SwingLfRig;
      const ts = ((Date.now() - start) / 1000).toFixed(1).padStart(6);
      if (st.SwUpDn != null && !observed.has(st.SwUpDn)) observed.set(st.SwUpDn, `${ts}s`);
      if (st.WdSpd != null && !observedWind.has(st.WdSpd)) observedWind.set(st.WdSpd, `${ts}s`);
      const mark = changed ? " ◀── 变化" : "";
      if (changed || n % 10 === 0) {
        console.log(`[${ts}s] Pow=${cur.Pow} WdSpd=${cur.WdSpd} SwUpDn=${cur.SwUpDn} SwingLfRig=${cur.SwingLfRig}${mark}`);
      }
      last = cur;
    } catch (e) {
      if (n % 10 === 0) console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] 解密失败: ${e.message}`);
    }
  } else if (n % 10 === 0) {
    console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] status 无应答`);
  }
  n++;
  await sleep(INTERVAL_MS);
}

console.log("\n===== 监测汇总：风速 WdSpd（首次出现时间）=====");
[...observedWind.entries()].sort((a, b) => a[0] - b[0]).forEach(([v, t]) => {
  console.log(`  WdSpd=${v}  首次 ${t}`);
});
console.log("\n===== 监测汇总：上下摆风 SwUpDn（首次出现时间）=====");
[...observed.entries()].sort((a, b) => a[0] - b[0]).forEach(([v, t]) => {
  console.log(`  SwUpDn=${v}  首次 ${t}`);
});
sock.close();
process.exit(0);
