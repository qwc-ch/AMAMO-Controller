# AMAMO Controller

替代官方「格力+」的**安卓原生 App**。空调内置 WiFi 模块（UDP 7000 + 格力云），无需蓝牙/红外，家里不添置任何硬件。

技术方案见 [`格力空调遥控App技术方案.md`](格力空调遥控App技术方案.md)。

## 特性

- **双通道**：优先局域网 UDP 直连（低延迟、不耗云配额），跨网络可用；云端设备列表同步（可选登录）。云端「控制命令」端点待抓包定案。
- **V1/V2 加密自动协商**：bind 按固件版本预判代际（>=1.21 优先 V2/AES-GCM），失败自动兜底另一代，适配新旧固件。
- **多设备并行**：首页每台设备一个分页，每设备独立 UDP 客户端（`LanClientPool`），分页切走自动暂停轮询。
- **Material 3 Expressive 设计**：HCT 生成的品牌蓝紫配色（回退方案）、表达力字阶/形状、spring 弹性动效（温度弹跳、扫描旋转）、Material You 动态取色（设置页可关）。
- **免登录启动**：应用直达首页（局域网扫描 + 设备分页），云端登录改为设置页内的可选弹窗。
- **云端签名**：按公开逆向实现注入 `api/datVc`，支持 402 自动重登。
- **构建走云端 CI**（GitHub Actions / CNB），本地不装 Android 环境，push 即出 debug APK。

## 目录结构

```text
app/src/main/java/com/gree/remote/
├── GreeRemoteApp.kt / MainActivity.kt / AppContainer.kt   # 入口 + 手动 DI
├── data/
│   ├── model/     # AcState（室温+40偏移/TemRec半度解码）、AcDevice、ScannedDevice、CipherVersion
│   ├── lan/       # GreeLanClient（scan/bind/status/cmd）、LanClientPool（每设备一实例）、CipherV1/V2
│   ├── cloud/     # CloudSigner、GreeCloudApi（Retrofit）、GreeCloudClient（402 重登）
│   ├── repo/      # AcRepository（双通道策略、scanLan 纯扫描）
│   └── storage/   # PrefsStore（DataStore，动态取色偏好）、SecureStorage（Keystore）
└── ui/
    ├── GreeRemoteRoot.kt  # 底部导航（首页/设置）
    ├── home/      # 首页：局域网扫描 + 每设备一个分页（TabRow）内嵌遥控面板
    ├── remote/    # 遥控面板组件 + 每设备 ViewModel
    ├── settings/  # 设置页：通用（动态取色）/ 云端账号 / 关于 三个子分页
    └── theme/     # MD3E 主题（Color/Type/Shape/Motion）
```

测试辅助脚本（本机回环，不接触真实空调）：`tools/mock_device.mjs`（模拟 V1/V2 两台设备）、
`tools/protocol_flow_test.mjs`（协议全流程 13 项断言）、`tools/scan_device.mjs`（真机只读扫描）。

## 构建

本地无需 Android SDK/Java 环境，构建全部由云端 CI 完成。

### GitHub Actions（仓库放 GitHub）

`.github/workflows/build.yml`：push 到 `main` 自动出包，产物在 Actions 页面下载。

### CNB（仓库放 cnb.cool）

`.cnb.yml`：国内直连快，构建产物在流水线详情页获取。

### 关于 Gradle Wrapper

本地无环境无法生成二进制的 `gradle-wrapper.jar`，因此**未随仓库提交**。两份 CI 配置都带
`bootstrap-gradle-wrapper` 步骤：在 CI 上先下载 Gradle 发行版、执行 `gradle wrapper` 生成完整
wrapper，再走 `./gradlew assembleDebug`。若你在有 Gradle 的机器上执行一次
`gradle wrapper`，即可把 wrapper jar 提交进仓库，之后 CI 会直接跳过 bootstrap。

## 云端「控制命令」端点是唯一硬风险

本仓库对下发控制命令采用「最可能形态」的占位实现（`GreeCloudApi.devControl`），
**必须先按技术方案 §8 抓包定案**：确认是 HTTP 端点（如 `DevControl`）还是云端走 TCP/MQTT 长连接。
定案后改 `GreeCloudClient.devControl` 与 `GreeCloudApi` 即可。

## 局域网协议要点（已与 gree-remote / gree-hvac-client 对拍）

- 扫描：广播 `255.255.255.255:7000` 发 `{"t":"scan"}`，响应 `pack` 用通用密钥解密。
- 绑定：`{mac, t:"bind", uid:0}`，响应 `bindok` 带设备专属 `key`。
- 状态/命令：用设备 key 加密；status 带 `cols`，cmd 带 `opt` + `p`。
- V1：`AES-128-ECB + PKCS7`，通用密钥 `a3K8Bx%2r8Y7#xDh`。
- V2：`AES-128-GCM`，通用密钥 `{yxAHAY_Lm6pbC/<`，nonce `5440784449675a516c5e6313`，AAD `qualcomm-test`。

参考实现：
- https://github.com/tomikaa87/gree-remote
- https://github.com/inwaar/gree-hvac-client
- https://github.com/xuqiu/hass （云端签名 `gree/__init__.py`）

## 权限与安全

- 需 `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_MULTICAST_STATE`（UDP 广播）。
- 云端账号密码用 **Android Keystore** 加密后落盘；release 关闭 OkHttp 日志，避免泄漏签名与 token。
- 本项目为个人自用，不商用、不公开分发含厂商密钥的构建产物。

## 里程碑

- [x] M0：局域网协议层 + 加密对拍单测
- [x] M1：局域网直连版（scan/bind/status/cmd + 遥控 UI）
- [x] M2：云端登录/设备列表/跨网络控制 + 双通道
- [ ] M3：按 §8 抓包定案云端控制端点、状态轮询打磨、多设备、异常提示