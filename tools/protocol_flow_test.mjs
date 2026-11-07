#!/usr/bin/env node
// AMAMO Controller 局域网协议全流程验证（本机回环，不接触真实空调）。
// 复刻 app GreeLanClient 的报文流程：scan(广播/多设备收集) -> bind(V1/V2 协商) -> status(对象/数组 dat) -> cmd(单/多 opt)。
// 用法：先启动 mock（node tools/mock_device.mjs），再 node tools/protocol_flow_test.mjs
import dgram from "node:dgram";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

const PORT = 7000;
const V1_KEY = "a3K8Bx%2r8Y7#xDh";
const V2_KEY = "{yxAHAY_Lm6pbC/<";
const V2_NONCE = Buffer.from("5440784449675a516c5e6313", "hex");
const V2_AAD = "qualcomm-test";

const encV1 = (plain, key) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(plain, "utf8")), c.final()]).toString("base64");
};
const decV1 = (b64, key) => {
  const c = crypto.createDecipheriv("aes-128-ecb", Buffer.from(key, "ascii"), null);
  return Buffer.concat([c.update(Buffer.from(b64, "base64")), c.final()]).toString("utf8");
};
const encV2 = (plain, key) => {
  // Node：final() 只输出密文，tag 由 getAuthTag() 单独取（JVM 才是密文||tag 拼接）
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

// envelope 与 Kotlin buildEnvelope() 一致（V2 带 tag，V1 不带）
const envelope = (packB64, tagB64, tcid) => JSON.stringify({
  cid: "app", i: 1, t: "pack", uid: 0, tcid,
  ...(tagB64 ? { tag: tagB64 } : {}), pack: packB64,
});

let passed = 0, failed = 0;
const check = (name, cond, extra = "") => {
  if (cond) { passed++; console.log(`  PASS  ${name}${extra ? " — " + extra : ""}`); }
  else { failed++; console.error(`  FAIL  ${name}${extra ? " — " + extra : ""}`); }
};

const sock = dgram.createSocket("udp4");
const request = (payload, timeoutMs = 3000) => new Promise((resolve, reject) => {
  const buf = Buffer.from(payload, "utf8");
  const timer = setTimeout(() => reject(new Error(`timeout: ${payload.slice(0, 60)}`)), timeoutMs);
  const onMsg = (msg) => { clearTimeout(timer); sock.removeListener("message", onMsg); resolve(JSON.parse(msg.toString("utf8"))); };
  sock.on("message", onMsg);
  sock.send(buf, PORT, "127.0.0.1", (err) => { if (err) { clearTimeout(timer); reject(err); } });
});

const roundtrip = async (innerObj, key, ver, tcid) => {
  const plain = JSON.stringify(innerObj);
  let payload;
  if (ver === "V2") {
    const [pack, tag] = encV2(plain, key);
    payload = envelope(pack, tag, tcid);
  } else {
    payload = envelope(encV1(plain, key), null, tcid);
  }
  const outer = await request(payload);
  if (outer.t !== "pack") throw new Error("not a pack envelope");
  const respPlain = outer.tag
    ? decV2(outer.pack, outer.tag, key)
    : decV1(outer.pack, key);
  return JSON.parse(respPlain);
};

// ==== main ====
const mock = spawn("node", [new URL("./mock_device.mjs", import.meta.url).pathname], { stdio: ["ignore", "pipe", "pipe"] });
mock.stdout.on("data", (d) => process.stdout.write(`  [mock] ${d}`));
mock.stderr.on("data", (d) => process.stderr.write(`  [mock!] ${d}`));
await sleep(500);

try {
  sock.bind(() => {
    sock.setBroadcast(true);
    sock.unref?.();
  });
  await sleep(200);

  // ---- 1. scan：单请求 → 两台设备各应答一次（对应 Kotlin scanAll 去重收集）----
  console.log("\n[1] scan 多设备收集");
  const scanReplies = [];
  await new Promise((resolve) => {
    const onMsg = (msg) => scanReplies.push(JSON.parse(msg.toString("utf8")));
    sock.on("message", onMsg);
    sock.send(Buffer.from(JSON.stringify({ t: "scan" })), PORT, "127.0.0.1", () => {});
    setTimeout(() => { sock.removeListener("message", onMsg); resolve(); }, 1200);
  });
  const macs = [...new Set(scanReplies.map((r) => {
    const inner = JSON.parse(decV1(r.pack, V1_KEY));
    return inner.mac;
  }))];
  check("scan 收到两台设备", macs.length === 2, `macs=${macs.join(",")}`);
  check("设备信息含固件版本", scanReplies.some((r) => {
    const inner = JSON.parse(decV1(r.pack, V1_KEY));
    return inner.ver === "V3.4.M";
  }));

  // ---- 2. bind V2 设备（客厅空调）----
  console.log("\n[2] bind V2（AES-GCM，tag 分离）");
  const bind2 = await roundtrip({ mac: "C03937518DC8", t: "bind", uid: 0 }, V2_KEY, "V2", "C03937518DC8");
  check("bindok + 设备专属 key", bind2.t === "bindok" && bind2.key === "AAAA1111BBBB2222");

  // ---- 3. status 对象形式 dat（V2 设备）----
  console.log("\n[3] status（对象形式 dat）");
  const st2 = await roundtrip({ cols: ["Pow", "SetTem", "TemSen", "TemRec"], mac: "C03937518DC8", t: "status" }, bind2.key, "V2", "C03937518DC8");
  check("status r=200", st2.r === 200);
  check("SetTem=26", st2.SetTem === 26);
  check("TemSen=65（→ 实际 25℃，+40 偏移待 App 端解码）", st2.TemSen === 65);
  check("TemRec=1（半度位）", st2.TemRec === 1);

  // ---- 4. cmd 多 opt（SetTem + TemRec 组合，对应 RemoteViewModel.setTemp 半度）----
  console.log("\n[4] cmd 多 opt（SetTem + TemRec）");
  const cmd2 = await roundtrip({ opt: ["SetTem", "TemRec"], p: [26, 0], t: "cmd", sub: false, mac: "C03937518DC8", uid: 0 }, bind2.key, "V2", "C03937518DC8");
  check("cmd r=200", cmd2.r === 200);

  // ---- 5. bind V1 设备（卧室空调，旧固件）----
  console.log("\n[5] bind V1（AES-ECB）");
  const bind1 = await roundtrip({ mac: "AABBCCDDEEFF", t: "bind", uid: 0 }, V1_KEY, "V1", "AABBCCDDEEFF");
  check("bindok + 设备专属 key", bind1.t === "bindok" && bind1.key === "CCCC3333DDDD4444");

  // ---- 6. status 数组形式 dat（旧固件 cols+dat）----
  console.log("\n[6] status（数组形式 dat，旧固件形态）");
  const st1 = await roundtrip({ cols: ["Pow", "SetTem"], mac: "AABBCCDDEEFF", t: "status" }, bind1.key, "V1", "AABBCCDDEEFF");
  check("cols 与 dat 等长", Array.isArray(st1.cols) && Array.isArray(st1.dat) && st1.cols.length === st1.dat.length);
  const map1 = Object.fromEntries(st1.cols.map((c, i) => [c, st1.dat[i]]));
  check("数组解码 SetTem=27", map1.SetTem === 27);

  // ---- 7. 单 opt cmd（对应普通开关命令）----
  console.log("\n[7] cmd 单 opt（Pow）");
  const cmd1 = await roundtrip({ opt: "Pow", p: 1, t: "cmd", sub: false, mac: "AABBCCDDEEFF", uid: 0 }, bind1.key, "V1", "AABBCCDDEEFF");
  check("cmd r=200", cmd1.r === 200);

  // ---- 8. 状态回读验证 cmd 生效 ----
  console.log("\n[8] cmd 后状态回读");
  const st1b = await roundtrip({ cols: ["Pow"], mac: "AABBCCDDEEFF", t: "status" }, bind1.key, "V1", "AABBCCDDEEFF");
  const map1b = Object.fromEntries(st1b.cols.map((c, i) => [c, st1b.dat[i]]));
  check("Pow 已置 1", map1b.Pow === 1);

  console.log(`\n===== 结果：${passed} passed, ${failed} failed =====`);
  process.exitCode = failed > 0 ? 1 : 0;
} catch (e) {
  console.error("[test] 异常:", e.message);
  process.exitCode = 1;
} finally {
  sock.close();
  mock.kill();
}
