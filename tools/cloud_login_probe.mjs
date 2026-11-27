#!/usr/bin/env node
// 格力云 2025-08 起现用协议验证（只读登录探针，不改设备）。
// 与 app/src/main/java/com/gree/remote/data/cloud/ 下的 Kotlin 实现逐行同构：
//   CloudSigner.psw / requestBody(datVc) / AES-128-ECB + Base64
//   CloudCryptoInterceptor 请求/响应加解密
// 用法：node tools/cloud_login_probe.mjs <账号> <密码>
// 预期：账号密码正确 → {r:200, uid, token}；错误 → {r:411}；签名错误 → {r:403}。
import crypto from "node:crypto";

const HOST = "https://grih.gree.com";
const APP_ID = "4920681951525131286";
const APP_HASH = "0fa513124aa97781d1f3f40d61ca1a89";
const AES_KEY = "#G$&^jgfujy6ujxt";
const GAE1 = "5ac2bdf935bcca70";

const md5 = (s) => crypto.createHash("md5").update(s, "utf8").digest("hex");
const aesEcb = (buf, mode) => {
  const c = crypto.createCipheriv("aes-128-ecb", Buffer.from(AES_KEY, "ascii"), null);
  return Buffer.concat([c.update(buf), c.final()]).toString("base64");
};
const aesDec = (b64) => {
  const d = crypto.createDecipheriv("aes-128-ecb", Buffer.from(AES_KEY, "ascii"), null);
  return Buffer.concat([d.update(Buffer.from(b64, "base64")), d.final()]).toString("utf8");
};

const [, , user, password] = process.argv;
if (!user || !password) {
  console.error("用法：node tools/cloud_login_probe.mjs <账号> <密码>");
  process.exit(2);
}

// 对应 CloudSigner.psw / requestBody
const t = new Date().toISOString().slice(0, 19).replace("T", " ");
const r = Math.floor(Date.now() / 1000);
const psw = md5(md5(md5(password) + password) + t);
const vc = md5(`${APP_ID}_${APP_HASH}_${t}_${r}`);
const datVc = md5([APP_HASH, user, psw, t].join("_"));
const payload = {
  api: { appId: APP_ID, r, t, vc },
  user, t, psw, datVc,
};
const plain = JSON.stringify(payload);
const enc = aesEcb(Buffer.from(plain, "utf8"), "encrypt");

console.log(`[probe] t=${t} r=${r}`);
console.log(`[probe] psw=${psw} vc=${vc} datVc=${datVc}`);
const resp = await fetch(`${HOST}/App/UserLoginV2`, {
  method: "POST",
  headers: {
    "Content-Type": "application/x-www-form-urlencoded",
    Gaen1: GAE1,
    Charset: "utf-8",
  },
  body: enc,
});
const raw = await resp.text();
console.log(`[probe] HTTP ${resp.status}, raw=${raw.slice(0, 160)}`);
let obj;
try {
  obj = JSON.parse(raw);
} catch (e) {
  console.error("响应非 JSON");
  process.exit(1);
}
if (obj.enRes) {
  const data = aesDec(obj.enRes);
  console.log(`[probe] decrypted: ${data}`);
  try {
    const j = JSON.parse(data);
    if (j.r === 200) {
      console.log(`✅ 登录成功：uid=${j.uid} token=${j.token.slice(0, 8)}…`);
    } else {
      console.log(`❌ r=${j.r} msg=${j.msg}`);
    }
  } catch (_) { /* printed above */ }
} else {
  console.log(`[probe] 非加密响应: ${raw.slice(0, 300)}`);
}
