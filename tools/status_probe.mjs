#!/usr/bin/env node
// status 无应答排查矩阵（全部只读，不发 cmd）：不同 mac 大小写 × 加密代际 × 设备 key/通用 key。
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const IP = process.argv[2] ?? "192.168.10.7";
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const V2_KEY = "{yxAHAY_Lm6pbC/<";
const V2_NONCE = Buffer.from("5440784449675a516c5e6313", "hex");
const V2_AAD = "qualcomm-test";
const MAC_LOWER = "c03937518dc8";
const MAC_UPPER = "C03937518DC8";

const encV1 = (plain, key) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]).toString("base64");
};
const decV1 = (b64, key) => {
  const c = crypto.createDecipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(b64, "base64")), c.final()]).toString("utf8");
};
const encV2 = (plain, key) => {
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const sendRecv = (payload, timeoutMs, host = IP) => new Promise((resolve) => {
  const out = [];
  const onMsg = (msg, rinfo) => {
    try {
      const o = JSON.parse(msg.toString("utf8"));
      out.push({ o, rinfo });
      if (out.length >= 1) { clearTimeout(t); sock.removeListener("message", onMsg); resolve(out); }
    } catch (_) { /* ignore */ }
  };
  const t = setTimeout(() => { sock.removeListener("message", onMsg); resolve(out); }, timeoutMs);
  sock.on("message", onMsg);
  const buf = Buffer.from(payload, "utf8");
  sock.send(buf, 0, buf.length, PORT, host);
});

// bind（V1）拿设备 key
const bindPlain = JSON.stringify({ mac: MAC_LOWER, t: "bind", uid: 0 });
const bindResp = await sendRecv(envelope(encV1(bindPlain, V1_KEY), null, MAC_LOWER), 3000);
if (!bindResp.length) { console.error("bind 无应答"); process.exit(1); }
const devKey = JSON.parse(decV1(bindResp[0].o.pack, V1_KEY)).key;
console.log(`bind V1 OK，设备 key: ${devKey.slice(0, 4)}****\n`);

const COLS = ["Pow", "Mod", "SetTem", "WdSpd", "Air", "Blo", "Health", "SwhSlp",
  "Lig", "SwingLfRig", "SwUpDn", "Quiet", "Tur", "StHt", "TemUn",
  "HeatCoolType", "TemRec", "SvSt", "TemSen"];

const trials = [
  { name: "A: 小写mac + V1设备key", mac: MAC_LOWER, ver: "V1", key: devKey },
  { name: "B: 小写mac + V2设备key", mac: MAC_LOWER, ver: "V2", key: devKey },
  { name: "C: 大写mac + V1设备key", mac: MAC_UPPER, ver: "V1", key: devKey },
  { name: "D: 大写mac + V2设备key", mac: MAC_UPPER, ver: "V2", key: devKey },
  { name: "E: 大写mac + V1通用key", mac: MAC_UPPER, ver: "V1", key: V1_KEY },
  { name: "F: 小写mac + V2通用key", mac: MAC_LOWER, ver: "V2", key: V2_KEY },
];

for (const t of trials) {
  const plain = JSON.stringify({ cols: COLS, mac: t.mac, t: "status" });
  const payload = t.ver === "V2"
    ? envelope(...encV2(plain, t.key), t.mac)
    : envelope(encV1(plain, t.key), null, t.mac);
  const replies = await sendRecv(payload, 1800);
  if (!replies.length) {
    console.log(`✗ ${t.name} — 无应答`);
  } else {
    const { o } = replies[0];
    try {
      const dec = o.tag ? decV2(o.pack, o.tag, t.key) : decV1(o.pack, t.key);
      console.log(`✓ ${t.name} — 应答: ${dec.slice(0, 220)}`);
    } catch (e) {
      console.log(`? ${t.name} — 有应答但按 ${t.ver}/${t.key.slice(0, 4)} 解密失败: ${e.message}; outer.tag=${!!o.tag}`);
    }
  }
  await sleep(800);
}
process.exit(0);
