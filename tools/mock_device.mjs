#!/usr/bin/env node
// 格力空调 UDP 协议 mock 设备（仅本机回环测试用，不接触真实设备）。
// 模拟 2 台设备：DEV1（固件 V3.4，bind 走 V2/AES-GCM）、DEV2（固件 V1.2，bind 走 V1/AES-ECB）。
// 协议常量与 app/src/main/java/com/gree/remote/data/lan/CipherV1.kt、CipherV2.kt 一致。
import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const V1_KEY = "a3K8Bx%2r8Y7#xDh";           // V1 通用密钥
const V2_KEY = "{yxAHAY_Lm6pbC/<";            // V2 通用密钥
const V2_NONCE = Buffer.from("5440784449675a516c5e6313", "hex");
const V2_AAD = "qualcomm-test";

const DEVICES = [
  {
    mac: "C03937518DC8", name: "客厅空调", ver: "V3.4.M", bindVer: "V2", datStyle: "object",
    key: "AAAA1111BBBB2222", state: { Pow: 1, Mod: 1, SetTem: 26, TemSen: 65, WdSpd: 0, TemRec: 1, Tur: 0, Quiet: 0, SwUpDn: 0, SwingLfRig: 0, StHt: 0, Blo: 0, Health: 0, Air: 0, SwhSlp: 0, Lig: 0, SvSt: 0, HeatCoolType: 0, TemUn: 0 },
  },
  {
    mac: "AABBCCDDEEFF", name: "卧室空调", ver: "V1.2.6", bindVer: "V1", datStyle: "array",
    key: "CCCC3333DDDD4444", state: { Pow: 0, Mod: 0, SetTem: 27, TemSen: 0, WdSpd: 0, TemRec: 0, Tur: 0, Quiet: 0, SwUpDn: 0, SwingLfRig: 0, StHt: 0, Blo: 0, Health: 0, Air: 0, SwhSlp: 0, Lig: 0, SvSt: 0, HeatCoolType: 0, TemUn: 0 },
  },
];

// ---- 加解密 ----
const encV1 = (plain, key) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]);
};
const decV1 = (buf, key) => {
  const c = crypto.createDecipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(buf), c.final()]).toString("utf8");
};
const encV2 = (plain, key) => {
  // ⚠️ Node 与 JVM 行为不同：Node 的 final() 只输出密文，tag 需 getAuthTag() 单独取；
  // JVM Cipher.doFinal() 才是 密文||tag 拼接（Kotlin CipherV2 按此拆分）。
  const c = crypto.createCipheriv("aes-128-gcm", Buffer.from(key, "ascii"), V2_NONCE);
  c.setAAD(Buffer.from(V2_AAD, "ascii"));
  const pack = Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]);
  return { pack, tag: c.getAuthTag() };
};
const decV2 = (pack, tag, key) => {
  const c = crypto.createDecipheriv("aes-128-gcm", Buffer.from(key, "ascii"), V2_NONCE);
  c.setAAD(Buffer.from(V2_AAD, "ascii"));
  c.setAuthTag(tag);
  return Buffer.concat([c.update(pack), c.final()]).toString("utf8");
};

// ---- 报文处理 ----
// 请求解密：尝试通用密钥（scan/bind）+ 各设备专属 key（status/cmd）
function parseEnvelope(outer) {
  const packB64 = outer.pack;
  const tagB64 = outer.tag;
  const candidates = [
    ["V1", V1_KEY], ["V2", V2_KEY],
    ...DEVICES.map((d) => [d.bindVer, d.key]),
  ];
  for (const [ver, key] of candidates) {
    try {
      const pack = Buffer.from(packB64, "base64");
      if (ver === "V1") {
        return { ver: "V1", key, plain: decV1(pack, key) };
      }
      const tag = tagB64 ? Buffer.from(tagB64, "base64") : null;
      if (!tag) continue;
      return { ver: "V2", key, plain: decV2(pack, tag, key) };
    } catch (_) { /* try next */ }
  }
  throw new Error("decrypt failed");
}

function replyFor(dev, inner, respVer, respKey) {
  const t = inner.t;
  if (t === "scan") {
    const respInner = {
      t: "dev", cid: "dev", mac: dev.mac, mid: "11002", name: dev.name,
      brand: "gree", bc: "gree0000000000000000", ver: dev.ver, hid: "mock",
      pv: 0, sub: false, uid: 0,
    };
    return encryptResp(respInner, respVer, respKey);
  }
  if (t === "bind") {
    return encryptResp({ t: "bindok", mac: dev.mac, uid: 0, key: dev.key }, respVer, respKey);
  }
  if (t === "status") {
    // 两种真实设备形态双覆盖：DEV1 = 对象形式 dat；DEV2 = cols/dat 数组形式（旧固件常见）
    if (dev.datStyle === "object") {
      return encryptResp({ t: "dat", mac: dev.mac, r: 200, ...dev.state }, respVer, respKey);
    }
    const cols = Object.keys(dev.state);
    return encryptResp({ t: "dat", mac: dev.mac, r: 200, cols, dat: cols.map((c) => dev.state[c]) }, respVer, respKey);
  }
  if (t === "cmd") {
    const opts = Array.isArray(inner.opt) ? inner.opt : [inner.opt];
    const vals = Array.isArray(inner.p) ? inner.p : [inner.p];
    opts.forEach((o, i) => { if (o in dev.state) dev.state[o] = vals[i]; });
    console.log(`[mock] ${dev.mac} cmd`, JSON.stringify(Object.fromEntries(opts.map((o, i) => [o, vals[i]]))));
    return encryptResp({ t: "res", mac: dev.mac, r: 200 }, respVer, respKey);
  }
  return encryptResp({ t: "unknown", r: 400 }, respVer, respKey);
}

function encryptResp(innerObj, ver, key) {
  const plain = JSON.stringify(innerObj);
  if (ver === "V2") {
    const { pack, tag } = encV2(plain, key);
    return JSON.stringify({ cid: "app", i: 1, t: "pack", uid: 0, tcid: "dev", tag: tag.toString("base64"), pack: pack.toString("base64") });
  }
  return JSON.stringify({ cid: "app", i: 1, t: "pack", uid: 0, tcid: "dev", pack: encV1(plain, key).toString("base64") });
}

// ---- 启动 ----
const sock = dgram.createSocket("udp4");
sock.on("message", (msg, rinfo) => {
  try {
    const outer = JSON.parse(msg.toString("utf8"));
    // 真实协议：scan 请求是裸 JSON（无 pack 信封，见问题清单实测记录 payload={"t":"scan"}）
    if (outer.t === "scan") {
      DEVICES.forEach((dev) => {
        const resp = replyFor(dev, { t: "scan" }, "V1", V1_KEY); // scan 应答固定用 V1 通用密钥
        sock.send(Buffer.from(resp), rinfo.port, rinfo.address);
      });
      return;
    }
    if (outer.t !== "pack") return;
    const { ver, key: reqKey, plain } = parseEnvelope(outer);
    const inner = JSON.parse(plain);
    if (inner.t === "scan") {
      DEVICES.forEach((dev) => {
        const resp = replyFor(dev, inner, "V1", V1_KEY); // scan 应答固定用 V1 通用密钥
        sock.send(Buffer.from(resp), rinfo.port, rinfo.address);
      });
      return;
    }
    const dev = DEVICES.find((d) => d.mac === inner.mac);
    if (!dev) return;
    // status/cmd 应答：设备专属 key + 设备代际；bind 应答：请求代际 + 通用密钥
    const respVer = inner.t === "bind" ? ver : dev.bindVer;
    const respKey = inner.t === "bind" ? reqKey : dev.key;
    const resp = replyFor(dev, inner, respVer, respKey);
    sock.send(Buffer.from(resp), rinfo.port, rinfo.address);
  } catch (e) {
    console.error("[mock] parse error:", e.message);
  }
});
sock.bind(PORT, () => {
  console.log(`[mock] listening on 0.0.0.0:${PORT}, devices: ${DEVICES.map((d) => d.mac).join(", ")}`);
});
