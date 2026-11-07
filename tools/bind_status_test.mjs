#!/usr/bin/env node
// 真实空调只读实测：scan -> bind -> status（不发 cmd 控制命令）。
// 报文流程与 app/src/main/java/com/gree/remote/data/lan/GreeLanClient.kt 完全同构：
//   scan  广播 {"t":"scan"}（裸 JSON，V1 通用密钥应答）
//   bind  pack 信封（V2 带 tag / V1 不带），通用密钥加密 {mac, t:"bind", uid:0} → bindok + 设备 key
//   status 设备 key 加密 {cols, mac, t:"status"} → dat（对象或数组形式），解码验证 TemSen+40 偏移
// 用法：node tools/bind_status_test.mjs [目标IP]
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const BROADCAST = "255.255.255.255";
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const V2_KEY = "{yxAHAY_Lm6pbC/<";
const V2_NONCE = Buffer.from("5440784449675a516c5e6313", "hex");
const V2_AAD = "qualcomm-test";
const SCAN_WINDOW_MS = 2500;

const encV1 = (plain, key) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]).toString("base64");
};
const decV1 = (b64, key) => {
  const c = crypto.createDecipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(b64, "base64")), c.final()]).toString("utf8");
};
const encV2 = (plain, key) => {
  // Node：final() 只输出密文，tag 由 getAuthTag() 单独取（JVM doFinal 才是密文||tag 拼接）
  const c = crypto.createCipheriv("aes-128-gcm", Buffer.from(key, "ascii"), V2_NONCE);
  c.setAAD(Buffer.from(V2_AAD, "ascii"));
  const pack = Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]);
  return [pack.toString("base64"), c.getAuthTag().toString("base64")];
};
const decV2 = (packB64, tagB64, key) => {
  const c = crypto.createDecipheriv("aes-128-gcm", Buffer.from(key, "ascii"), V2_NONCE);
  c.setAAD(Buffer.from(V2_AAD, "ascii"));
  c.setAuthTag(Buffer.from(tagB64, "base64"));
  return Buffer.concat([c.update(Buffer.from(packB64, "base64")), c.final()]).toString("utf8");
};

const envelope = (packB64, tagB64, tcid) => JSON.stringify({
  cid: "app", i: 1, t: "pack", uid: 0, tcid,
  ...(tagB64 ? { tag: tagB64 } : {}), pack: packB64,
});

const sock = dgram.createSocket("udp4");
sock.bind(() => sock.setBroadcast(true));

const sendRecv = (payload, timeoutMs, opts = {}) => new Promise((resolve) => {
  const { collectAll = false, host, debug = false } = opts;
  const out = [];
  const onMsg = (msg, rinfo) => {
    try {
      if (debug) console.log(`  [raw] ${rinfo.address}:${rinfo.port} ${msg.length}B ${msg.toString("utf8").slice(0, 160)}`);
      const o = JSON.parse(msg.toString("utf8"));
      if (o.t === "pack") out.push({ outer: o, ip: rinfo.address });
      if (!collectAll) { cleanup(); resolve(out[0]); }
    } catch (_) { /* ignore */ }
  };
  const cleanup = () => { clearTimeout(timer); sock.removeListener("message", onMsg); };
  const timer = setTimeout(() => { cleanup(); resolve(collectAll ? out : null); }, timeoutMs);
  sock.on("message", onMsg);
  const buf = Buffer.from(payload, "utf8");
  sock.send(buf, 0, buf.length, PORT, host ?? BROADCAST);
});

let target = process.argv[2] ?? "";

// ---- 1. scan（广播收集多设备；空结果且指定了 IP 时单播兜底）----
console.log(`[1] scan 广播 ${BROADCAST}:${PORT}（窗口 ${SCAN_WINDOW_MS}ms）…`);
let scanReplies = await sendRecv(JSON.stringify({ t: "scan" }), SCAN_WINDOW_MS, { collectAll: true });
if (!scanReplies?.length && target) {
  console.log(`广播无应答，改单播 ${target}:${PORT} …`);
  scanReplies = await sendRecv(JSON.stringify({ t: "scan" }), 2000, { collectAll: true, host: target });
}
if (!scanReplies?.length) {
  console.error("未收到任何 scan 应答——确认手机/本机与空调在同一 WiFi（bind 后设备可能短暂忽略广播，可指定 IP 参数单播）。");
  process.exit(1);
}
const devices = new Map();
for (const { outer, ip } of scanReplies) {
  try {
    const inner = JSON.parse(decV1(outer.pack, V1_KEY));
    if (inner.mac) devices.set(inner.mac, { ...inner, ip });
  } catch (_) { /* 非设备应答 */ }
}
console.log(`发现 ${devices.size} 台设备：`);
for (const d of devices.values()) {
  console.log(`  mac=${d.mac} name=${d.name || "-"} ver=${d.ver} mid=${d.mid} ip=${d.ip}`);
}

// 筛选目标（参数指定 mac/ip，否则取第一台）
const pick = [...devices.values()].find((d) =>
  !target || d.mac === target.toUpperCase().replace(/:/g, "") || d.ip === target);
if (!pick) { console.error("未找到目标设备"); process.exit(1); }
console.log(`\n选定：${pick.name || pick.mac}（${pick.ip}，固件 ${pick.ver}）`);

// ---- 2. bind（实测 2026-11：V3.4.M 走 V1；固定 V1 优先、V2 兜底）----
console.log("[2] bind（代际顺序 V1 → V2）…");

const bindOnce = async (ver) => {
  const plain = JSON.stringify({ mac: pick.mac, t: "bind", uid: 0 });
  const payload = ver === "V2"
    ? envelope(...encV2(plain, V2_KEY), pick.mac)
    : envelope(encV1(plain, V1_KEY), null, pick.mac);
  const resp = await sendRecv(payload, 3500);
  if (!resp) throw new Error("bind 无应答");
  const dec = resp.outer.tag ? decV2(resp.outer.pack, resp.outer.tag, V2_KEY) : decV1(resp.outer.pack, V1_KEY);
  const obj = JSON.parse(dec);
  if (obj.t !== "bindok") throw new Error(`bind 返回非 bindok: ${dec}`);
  return obj.key;
};

let bindVer = null, devKey = null;
for (const ver of ["V1", "V2"]) {
  try {
    devKey = await bindOnce(ver);
    bindVer = ver;
    console.log(`bind 成功：代际 ${ver}，key=${devKey.slice(0, 4)}****`);
    break;
  } catch (e) {
    console.log(`  ${ver} 失败：${e.message}，试下一代…`);
  }
}
if (!devKey) { console.error("bind 全部失败"); process.exit(1); }

// ---- 3. status（设备 key；验证对象/数组 dat + TemSen+40 解码）----
console.log("\n[3] status 查询…");
const COLS = ["Pow", "Mod", "SetTem", "WdSpd", "Air", "Blo", "Health", "SwhSlp",
  "Lig", "SwingLfRig", "SwUpDn", "Quiet", "Tur", "StHt", "TemUn",
  "HeatCoolType", "TemRec", "SvSt", "TemSen"];
const statusOnce = async () => {
  const plain = JSON.stringify({ cols: COLS, mac: pick.mac, t: "status" });
  const mk = (key, ver) => ver === "V2"
    ? envelope(...encV2(plain, key), pick.mac)
    : envelope(encV1(plain, key), null, pick.mac);
  // 与 Kotlin GreeLanClient 一致：先 bind key，超时换 V1 通用密钥兜底
  const tryKeys = [
    { key: devKey, ver: bindVer, label: "bind key" },
    { key: V1_KEY, ver: "V1", label: "V1 通用密钥" },
  ];
  for (const t of tryKeys) {
    const resp = await sendRecv(mk(t.key, t.ver), 2000, { host: pick.ip });
    if (!resp) continue;
    const dec = t.ver === "V2"
      ? decV2(resp.outer.pack, resp.outer.tag, t.key)
      : decV1(resp.outer.pack, t.key);
    console.log(`  （status 生效密钥：${t.label}）`);
    return JSON.parse(dec);
  }
  throw new Error("status 无应答（bind key 与通用密钥均无应答）");
};

let obj = null;
for (let i = 1; i <= 3; i++) {
  try {
    obj = await statusOnce();
    break;
  } catch (e) {
    console.log(`  第 ${i} 次失败：${e.message}`);
    await new Promise((r) => setTimeout(r, 400));
  }
}
if (!obj) { console.error("status 三次重试均失败"); process.exit(1); }

let state = {};
if (obj.dat && typeof obj.dat === "object" && !Array.isArray(obj.dat)) {
  console.log("dat 形态：对象");
  state = obj.dat;
} else if (Array.isArray(obj.dat)) {
  console.log("dat 形态：cols/dat 数组（旧固件）");
  const cols = obj.cols ?? COLS;
  cols.forEach((c, i) => { state[c] = obj.dat[i]; });
} else {
  console.error("未知 dat 形态:", dec.slice(0, 200));
  process.exit(1);
}

console.log("\n===== 状态解码（只读，未发任何控制命令）=====");
const modes = { 0: "自动", 1: "制冷", 2: "除湿", 3: "送风", 4: "制热" };
console.log(`  电源     Pow      = ${state.Pow}（${state.Pow === 1 ? "开" : "关"}）`);
console.log(`  模式     Mod      = ${state.Mod}（${modes[state.Mod] ?? "?"}）`);
console.log(`  设定温度 SetTem   = ${state.SetTem}℃（SetTem 即摄氏值）`);
if (state.TemRec === 1) console.log(`  半度位   TemRec   = 1（设定温度实际 ${state.SetTem + 0.5}℃）`);
if (state.TemSen != null && state.TemSen > 0) {
  console.log(`  室温     TemSen   = ${state.TemSen}（原始）→ 实际 ${state.TemSen - 40}℃（+40 偏移解码 ✓）`);
} else {
  console.log(`  室温     TemSen   = ${state.TemSen}（未上报）`);
}
console.log(`  风速     WdSpd    = ${state.WdSpd}`);
console.log(`  强劲     Tur      = ${state.Tur}  静音 Quiet = ${state.Quiet}  扫风 SwUpDn = ${state.SwUpDn}`);
console.log(`  加密代际：${bindVer}`);
process.exit(0);
