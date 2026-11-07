#!/usr/bin/env node
// 格力空调局域网扫描脚本 —— 按 AMAMO-Controller GreeLanClient.scan() 协议思路
// 用法: node tools/scan_device.mjs [目标IP]
// 不带参数 = 广播 255.255.255.255:7000; 带参数 = 单播到指定 IP

import dgram from "node:dgram";
import crypto from "node:crypto";

const PORT = 7000;
const BROADCAST_ADDR = "255.255.255.255";
const SCAN_TIMEOUT_MS = 1500;
const COMMON_KEY = "a3K8Bx%2r8Y7#xDh"; // V1 通用密钥 (AES-128-ECB)

const target = process.argv[2] ?? BROADCAST_ADDR;
const socket = dgram.createSocket("udp4");

socket.on("error", (err) => {
  console.error("[socket error]", err.message);
  socket.close();
  process.exit(1);
});

socket.bind(() => {
  socket.setBroadcast(true);
  // 对应 Kotlin: socket.soTimeout + send + receive
  const scan = JSON.stringify({ t: "scan" });
  const buf = Buffer.from(scan, "utf8");
  socket.send(buf, 0, buf.length, PORT, target, (err) => {
    if (err) {
      console.error("[send error]", err.message);
      socket.close();
      process.exit(1);
    }
    console.log(`[scan] 已发送到 ${target}:${PORT} payload=${scan}`);
  });
});

const timer = setTimeout(() => {
  console.error(`[scan] 超时: ${SCAN_TIMEOUT_MS}ms 内未收到响应`);
  socket.close();
  process.exit(1);
}, SCAN_TIMEOUT_MS);

socket.on("message", (msg, rinfo) => {
  clearTimeout(timer);
  console.log(`[resp] 收到 ${rinfo.address}:${rinfo.port} ${msg.length} 字节`);
  try {
    const outer = JSON.parse(msg.toString("utf8"));
    console.log("[resp] 外层字段:", Object.keys(outer).join(", "));

    const packB64 = outer.pack;
    if (!packB64) {
      console.error("[resp] 响应没有 pack 字段:", msg.toString("utf8"));
      socket.close();
      process.exit(1);
    }

    // AES-128-ECB 解密 pack,对应 Kotlin CipherV1.decrypt
    const key = Buffer.from(COMMON_KEY, "ascii");
    const cipher = crypto.createDecipheriv("aes-128-ecb", key, null);
    cipher.setAutoPadding(true);
    let plain = cipher.update(Buffer.from(packB64, "base64"));
    plain = Buffer.concat([plain, cipher.final()]);
    const inner = JSON.parse(plain.toString("utf8"));

    console.log("[resp] 解密后设备信息:");
    console.log("  type :", inner.t ?? "");
    console.log("  cid  :", inner.cid ?? "");
    console.log("  mac  :", inner.mac ?? "");
    console.log("  mid  :", inner.mid ?? "");
    console.log("  name :", inner.name ?? "");
    console.log("  brand:", inner.brand ?? "");
    console.log("  bc   :", inner.bc ?? "");
    console.log("  ver  :", inner.ver ?? "");
    console.log("  hid  :", inner.hid ?? "");
    console.log("  pv   :", inner.pv ?? 0);
    console.log("  sub  :", inner.sub ?? false);
    console.log("  uid  :", inner.uid ?? 0);
  } catch (e) {
    console.error("[resp] 解析失败:", e.message);
  } finally {
    socket.close();
    process.exit(0);
  }
});