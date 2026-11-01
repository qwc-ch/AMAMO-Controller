# 格力空调遥控 App · 技术方案

> 目标：做一个替代官方「格力+」的**安卓原生 App**。
> 前提：空调不支持蓝牙控制、手机没有红外——不需要，我们走的是空调内置 **WiFi 模块** 的网络通道。
> 已确认的决策：安卓 APP（原生）、跨网络控制采用**直连格力云**、家里不添置任何硬件；UI 采用 **Material 3 Expressive**；构建走**云端 CI（GitHub Actions / CNB），本地不搭 Android 环境**。

---

## 1. 可行性结论

空调能被「格力+」控制 ⇔ 空调内置 WiFi 模块（常见型号 WB01 / WB05 等）。该模块连上家里路由器后，会同时与格力云服务器保持长连接。因此控制通道有两条，**都已被社区完整逆向并有开源实现**：

| 通道 | 链路 | 成熟度 |
|---|---|---|
| 局域网直连 | 手机 → UDP 7000 → 空调 WiFi 模块 | ★★★★★ 协议完全公开，多语言开源实现 |
| 云端中转 | 手机 → 格力云（grih.gree.com）→ 空调 | ★★★☆☆ 登录/设备列表/场景已公开；**控制命令端点需一次抓包确认**（见 §8） |

结论：**可做**。唯一需要动手验证的硬前提是云端「下发控制命令」的具体端点与报文，其余全部有现成参考。

### 1.1 目标机型档案（Reference Unit）

| 项 | 值 | 说明 |
|---|---|---|
| 完整型号 | `KFR-35GW/(35572)FNhAa-B1(WIFI)` | 1.5 匹 · 变频（FN）· 冷暖（h）· 壁挂（GW）· 内置 WiFi |
| 机型条码（SN） | `4W68944029040` | 机身铭牌条码 |
| MAC 地址 | `c0:39:37:51:8d:c8` | bind 与「一机两 MAC」问题的基准值 |
| 型号 ID | `11002` / `7e000035` | 格力+ 内部型号 ID，云端能力匹配用 |
| 插件版本 | `V21.9` | 格力+ App 显示的设备插件版本 |

**由型号可判定**：
- 标准壁挂内机（非中央空调/风管机）→ 走社区通用的 UDP 7000 协议，是开源支持面最广、资料最全的一类设备；
- 能力集按挂机预置：`Pow` / `Mod` / `SetTem` / `WdSpd` / `SwUpDn`（上下扫风）/ `Lig` / `Tur` / `Quiet` / `Blo`（干燥防霉）/ `StHt`（电辅热）/ `SvSt`（节能）等；`SwingLfRig`（左右扫风）、`Health`（负离子）、`Air`（新风）大概率无此机型，UI 初版先不显示，实测后增删。

**无需人工预先判定**：
- 模块固件代际（V1 ECB / V2 GCM）——实现层已定案「都适配 + 运行时自动协商」（§2.1），不依赖型号猜测；局域网实测只用于**记录这台空调的实际代际**和确认连通性。

**待第一次局域网实测确定**：
- 手机/电脑与空调的 UDP 连通性（AP 隔离、防火墙等环境问题，代码解决不了，只能实测）；
- 是否支持 0.5℃ 步进；
- MAC 前缀 `c0:39:37` 对应的 WiFi 模块厂商（OUI 查询），仅作记录。

---

## 2. 控制原理

### 2.1 局域网直连（UDP 7000）

空调 WiFi 模块在局域网内监听 UDP 7000 端口，报文为 JSON，敏感字段用 AES-128 加密后 Base64。

**交互流程：`scan`（发现）→ `bind`（拿设备密钥）→ `status`（读状态）/ `cmd`（下发命令）**

| 请求 | 响应 | 说明 |
|---|---|---|
| `scan` | `dev` | 明文 `{"t":"scan"}`，广播或单播到 7000 端口 |
| `bind` | `bindok` | 用**通用密钥**加密；响应携带**设备专属密钥 key** |
| `status` | `dat` | 用设备密钥加密；按 `cols` 列名查询状态 |
| `cmd` | `res` | 用设备密钥加密；`opt`+`p` 设置参数 |

外层信封（`pack` 为加密后 Base64 的内层 JSON）：

```json
{"cid":"app","i":0,"pack":"<base64密文>","t":"pack","tcid":"<空调MAC>","uid":0}
```

**加密分两代**（按固件版本，扫描响应的 `ver`/`hid` 字段可判断）：

| 代 | 算法 | 通用密钥（16 字节） | 备注 |
|---|---|---|---|
| V1 | AES-128-**ECB** + PKCS7 | `a3K8Bx%2r8Y7#xDh` | 旧固件（<1.21） |
| V2 | AES-128-**GCM** | `{yxAHAY_Lm6pbC/<` | 固件 ≥1.21；固定 12 字节 nonce `54 40 78 44 49 67 5A 51 6C 5E 63 13`，AAD `qualcomm-test`，密文尾部 16 字节为 GCM tag，放外层 `tag` 字段 |

V2 外层信封多一个 `tag` 字段。**代际自动协商策略（定案做法，无需人工判定 V1/V2）**：

1. `scan` 响应的 `ver` / `hid` 字段预判代际（如 hid `U-WB05RT13V1.21.bin` ≥1.21 走 V2）；
2. `bind` 先用 V1（ECB）发，超时无响应自动换 V2（GCM）重发；
3. 协商成功后**记住该设备的代际**，后续 status/cmd 直接用，不再重复试探；
4. ⚠️ 不能只信 `tag` 字段：实测有的设备 scan 响应不带 `tag`，但 bind 却必须 V2（gree-remote issue #63）——所以 bind 兜底是必须的，scan 信号只用于加速。

**常用控制字段（`opt` / `cols`）**：

| 字段 | 含义 | 常见取值 |
|---|---|---|
| `Pow` | 开关机 | 0/1 |
| `Mod` | 模式 | 0 自动 / 1 制冷 / 2 除湿 / 3 送风 / 4 制热 |
| `SetTem` | 设定温度 | 16~30（0.5℃ 步进与单位处理见 gree-remote） |
| `WdSpd` | 风速 | 0 自动 ~ 5 高 |
| `SwUpDn` | 上下扫风 | 0 默认，其余为摆动/定点档位 |
| `SwingLfRig` | 左右扫风 | 同上 |
| `Lig` | 显示屏/指示灯 | 0/1 |
| `Tur` | 强劲 | 0/1 |
| `Quiet` | 静音 | 0/1/2/3 |
| `Blo` | 干燥防霉 X-Fan | 0/1 |
| `Health` | 健康负离子 | 0/1 |
| `SvSt` | 节能 | 0/1 |
| `StHt` | 电辅热 | 0/1 |
| `TemUn` / `HeatCoolType` / `TemRec` / `Air` / `SwhSlp` | 温度单位/冷暖类型/余温回收/新风/睡眠 | 0/1 等 |

完整状态查询可一次带全部 cols，响应 `dat` 按列序返回。

**两个坑：**
1. 设备可能在列表中出现**两个 MAC**（以 `00` 结尾和不以 `00` 结尾），通常只有一个响应命令 → UI 要允许手动切换。
2. 部分路由器开了 **AP 隔离** 会拦截 UDP → 支持手动填 IP 直连。

### 2.2 云端中转（国内「格力+」→ grih.gree.com）

**已公开可复现的端点**（HTTPS POST，JSON 请求体；套路来自 2019 年的公开逆向实现——2025-08 的 [luc10/gree-api-client](https://github.com/luc10/gree-api-client) 仍以同一接口族跑通登录/取设备，证明接口族在线，但**常量与端点最终以你自己的抓包为准**）：

| 端点 | 作用 |
|---|---|
| `https://grih.gree.com/App/UserLoginV2` | 账号登录 → 返回 `uid`、`token` |
| `https://grih.gree.com/App/GetDevsOfUserHomes` | 家庭 + 设备列表（含 MAC、名称、在线状态） |
| `https://grih.gree.com/App/GetScenes` | 场景列表 |
| `https://grih.gree.com/App/StartOrCancelScene` | 启动/取消场景 |

**签名规则**（请求体固定套路，可参考 §11 中公开的 Python 逆向实现）：

- 内置常量：`GREE_KEY = d15cb842b7fd704ebcf8276f34cbd771`，`appId = 5686063144437916735`
- 每个请求体带 `api` 对象：`{appId, r, t, vc}`
  - `t` = UTC 时间 `yyyy-MM-dd HH:mm:ss`；`r` = 毫秒时间戳
  - `vc` = `md5(appId + "_" + GREE_KEY + "_" + t + "_" + r)`
- 登录：`psw = md5(md5(md5(密码) + 密码) + t)`；`datVc = md5(GREE_KEY + "_" + user + "_" + psw + "_" + t)`
- 其它请求：`datVc = md5(GREE_KEY + "_" + 参与签名的字段依次用 "_" 拼接)`（如 token、uid、homeId）
- 响应 `r=200` 成功；**`r=402` 表示 token 失效 → 自动重新登录**后重发

**待确认项（项目唯一硬风险）**：遥控指令下发的端点。两种可能：
- A：HTTP 端点（如 `/App/DevControl` 类），请求体直接带 `uid/token/homeId + cmds[{mac, opt, p}]` 或加密 `pack`；
- B：与海外版一致 —— 登录后经 TCP/MQTT 长连接下发与局域网相同的 `pack` 报文。

海外版（GREE+/EWPE Smart）的云端形态已确认：MQTT（如 `mqtt-eu.gree.com:1984`）或 `QueryOnline` 返回的设备专属裸 TCP（如 `eu05.as.gree.com:16385`），**报文格式与局域网完全相同**，仅传输层不同。抓包一次即可定论（§8）。

### 2.3 App 内双通道策略

```
进入控制页
  ├─ 并行尝试 UDP 直连（超时 ~800ms）
  │    ├─ 成功 → 局域网模式（延迟低、不耗云配额），持续轮询状态
  │    └─ 失败 → 云端模式（登录 → 设备列表 → 下发命令）
  └─ 手动开关可强制指定通道
```

- 不建议用 WiFi SSID 判断所在网络（需要定位权限），直接探测即可。
- 云端控制任何网络下都可用，与官方 App 行为一致。

---

## 3. 技术栈选型

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | **Kotlin 2.x**（协程 + Flow） | 安卓官方首选；UDP/轮询用 Flow 表达最自然 |
| UI | **Jetpack Compose + Material 3 Expressive（M3E）** | 声明式 UI；形状变形、弹性动效、ButtonGroup 等新组件贴合遥控器交互（§3.1） |
| 架构 | **MVVM + Repository** | ViewModel → Repository → LanDataSource / CloudDataSource |
| 云端网络 | **OkHttp + Retrofit** | 生态成熟；MD5 签名用 OkHttp Interceptor 统一注入 |
| 局域网网络 | **JDK 内置 `DatagramSocket`** | UDP 不需要第三方库 |
| 序列化 | **kotlinx-serialization-json** | `ignoreUnknownKeys` 应对格力接口的脏字段 |
| 加密 | **`javax.crypto` + `java.security.MessageDigest`** | AES/ECB、AES/GCM、MD5 系统自带，零依赖 |
| 本地存储 | **DataStore Preferences** | token、设备缓存、通道偏好 |
| 敏感信息 | **Android Keystore**（或 security-crypto） | 密码/token 加密落盘 |
| DI | **Hilt**（可选，小项目可手动注入） | — |
| 最低版本 | minSdk 26（Android 8.0）/ targetSdk 34+ | 覆盖绝大多数设备 |
| 构建 | **AGP 8.x + Gradle Kotlin DSL**，云端 CI 出包（GitHub Actions / CNB，§11） | 本地不装 Android SDK，push 即得 APK |

### 3.1 UI 设计语言：Material 3 Expressive（M3E）

- **库版本现状**（[官方 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)，2026-10）：material3 **1.4.0**（stable，2025-09）已内置 M3E 基础——`MotionScheme.expressive()`、`ButtonGroup`（连体按钮组）、`ButtonShapes` 按压形状变形等；**1.5.0-beta02** 起 `materialExpressTheme` / `expressiveLightColorScheme` 转正。个人项目建议直接钉 **1.5.0-beta02+**，求稳用 1.4.0。
- **本 App 用得上的 M3E 能力**：
  - 模式切换（制冷/制热/除湿/送风/自动）→ `ButtonGroup` 连体按钮组 + 按压形状变形（morphing）；
  - 温度 +/- 大按钮 → `ButtonShapes` 按压缩放/圆角变形，配合 expressive 弹性动效；
  - 全局动效 → `MotionScheme.expressive()` 一行接入；
  - 主色跟随系统取色（Material You dynamic color）；
  - 刷新/等待状态 → `WavyProgressIndicator`、新版 LoadingIndicator。
- ⚠️ 1.4.0 起 `material-icons-core` 不再被传递依赖，用到图标需**显式添加**。

---

## 4. 依赖库清单

示例版本，实际以各官方最新稳定版为准：

| 依赖 | 坐标 | 用途 |
|---|---|---|
| Compose BOM | `androidx.compose:compose-bom`（用最新版） | 统一 Compose 版本；material3 现为 1.4.0 stable / 1.5.0-beta02（2026-10） |
| Material 3 (M3E) | `androidx.compose.material3:material3:1.5.0-beta02`（或 1.4.0 stable） | Expressive 主题/动效/形状变形；另需显式加 `androidx.compose.material:material-icons-core` |
| Activity Compose | `androidx.activity:activity-compose` | 入口 |
| Lifecycle | `androidx.lifecycle:lifecycle-viewmodel-compose` / `lifecycle-runtime-compose` | MVVM |
| 协程 | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1` | 异步、轮询 |
| 序列化 | `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` | JSON 编解码 |
| Retrofit | `com.squareup.retrofit2:retrofit:2.11.0` + `converter-kotlinx-serialization` | 云端 API |
| OkHttp | `com.squareup.okhttp3:okhttp:4.12.0` + `logging-interceptor` | HTTP 客户端、抓包调试日志 |
| DataStore | `androidx.datastore:datastore-preferences:1.1.1` | 键值存储 |
| Security | `androidx.security:security-crypto:1.1.0-alpha06`（可选；官方已停止演进，也可直接用 Keystore 自封装） | 加密落盘 |
| Hilt | `com.google.dagger:hilt-android:2.51.1`（可选） | 依赖注入 |
| Timber | `com.jakewharton.timber:timber:5.0.1`（可选） | 日志 |
| 测试 | `junit`、`kotlinx-coroutines-test`、`okhttp3:mockwebserver`、`robolectric` | 协议编解码可纯单测（AES 已知向量对拍） |

**AndroidManifest 权限**：
- `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`
- `CHANGE_WIFI_MULTICAST_STATE` —— UDP 广播扫描前需持有 `MulticastLock`，否则部分路由环境下收不到设备响应
- 若云端走裸 TCP（B 方案），需 networkSecurityConfig 放行对应 host 的明文 TCP

---

## 5. 项目结构（建议）

```text
app/src/main/java/com/<你的域名>/greeremote/
├── App.ky                         # 应用入口
├── data/
│   ├── model/                     # AcState、Device、Home 等数据类
│   ├── lan/                       # 局域网协议
│   │   ├── LanTransport           # DatagramSocket 收发、广播/单播
│   │   ├── CipherV1 / CipherV2    # AES-ECB / AES-GCM 编解码
│   │   └── GreeLanClient          # scan/bind/status/cmd 状态机
│   ├── cloud/                     # 云端协议
│   │   ├── SignInterceptor        # OkHttp 拦截器：统一注入 api/t/datVc
│   │   ├── GreeCloudApi           # Retrofit 接口定义
│   │   └── GreeCloudClient        # 登录、402 重登、设备列表、下发命令
│   └── repo/
│       └── AcRepository           # 双通道选择与合并，对上层屏蔽细节
├── ui/
│   ├── login/                     # 登录页
│   ├── devices/                   # 设备列表页
│   ├── remote/                    # 遥控页（温度盘、模式、风速、扫风…）
│   └── theme/
└── util/                          # MD5、Base64、时间戳工具
```

---

## 6. 关键实现要点

1. **协议层零依赖、可单测**：加解密与报文编解码写成纯 Kotlin 函数，用 gree-remote/hvac-client 的 AES 已知向量做对拍测试。
2. **双加密器自动协商**：bind 时先 V1（ECB），无响应换 V2（GCM）；一旦成功记住该设备的代际。
3. **签名集中处理**：`api{...}`、`datVc` 的拼字段规则容易抄错，用拦截器/基类统一生成，字段顺序严格按公开实现。
4. **402 自动重登**：任何云端请求收到 402 → 重登录 → 重放原请求（限一次，防死循环）。
5. **状态轮询节流**：局域网 3~5s 一次没问题；**云端轮询要克制**（按需/下拉刷新），避免触发风控。
6. **UDP 细节**：先广播 `255.255.255.255:7000`，拿到响应后改用该 IP 单播直连；持 `MulticastLock`；支持手动指定 IP。
7. **设备 MAC 歧义**：列表返回的多个 MAC（`00` 结尾与否）都展示，默认选响应命令的那个，允许手动切换。
8. **token 与密码安全**：Keystore 加密后落盘；日志拦截器在 release 关闭，避免泄漏签名与 token。
9. **UI 反馈**：命令下发后以设备 `res/dat` 回包为准更新界面，区分「已发送」与「已确认」。

---

## 7. 与官方 App 的功能对照（目标范围）

| 功能 | 局域网 | 云端 | 说明 |
|---|---|---|---|
| 开关机/温度/模式/风速 | ✅ | ✅ | 核心闭环 |
| 上下/左右扫风、指示灯、静音、强劲、节能、电辅热、X-Fan、健康 | ✅ | ✅ | 字段齐全 |
| 当前室温显示 | ✅（部分固件上报） | 视端点 | 抓包确认 |
| 定时/场景 | ✅ | ✅（GetScenes/StartOrCancelScene） | 场景可直接调用官方已配置的场景 |
| 跨网络控制 | — | ✅ | 本项目核心诉求 |

---

## 8. 前置调研：一次抓包定生死（建议第一步就做）

**目的**：确认云端「下发控制命令」的端点与报文格式（§2.2 的 A/B 假设二选一）。

1. 工具：HttpCanary（安卓本机抓包）或电脑端 mitmproxy / Charles。
2. 操作路径：登录 → 拉设备列表 → 在遥控页点开关/调温 → 记录每一个 HTTPS 请求的 URL、请求体、响应体。
3. 重点观察：控制命令是 HTTP 端点直发，还是建立 TCP/MQTT 长连接发 `pack`。
4. 若遇证书固定（大概率没有）：用 Frida/objection 做 unpinning，或参考 tomikaa87/gree-remote issue #63 中「GREE+ debug 模式」的思路。
5. 顺手在家抓一次 UDP 7000（Wireshark 过滤 `udp.port == 7000`），确认自家空调固件走 V1 还是 V2 加密。

**判定标准**：能用抓到的报文在 Postman/curl 里复现一次「开机 + 调温」成功，即视为协议打通，风险解除。

---

## 9. 风险与边界

| 风险 | 影响 | 对策 |
|---|---|---|
| 云接口为非官方逆向契约 | 格力改版可能失效 | 协议层集中封装，跟随社区更新；本地降级到局域网仍可用 |
| 账号风控 | 高频请求可能触发 402/封号 | 轮询节流、指数退避、仅自用 |
| AP 隔离/多 AP 漫游 | UDP 发现失败 | 手动填 IP；云端兜底 |
| 新旧固件加密混用 | bind 无响应 | V1/V2 自动协商 |
| 合规边界 | 签名密钥、接口属厂商资产 | 仅个人自用，不公开分发含密钥的构建产物，不商用 |

---

## 10. 里程碑（业余时间估算）

| 阶段 | 内容 | 预估 |
|---|---|---|
| M0 | 抓包定案云端控制端点（§8） | 0.5~1 天 |
| M1 | 局域网直连版：UDP 协议层 + 遥控 UI，同 WiFi 下完整可用 | 1~2 周 |
| M2 | 云端版：登录/设备列表/跨网络控制 + 双通道策略 | 约 1 周（取决于 M0 结果） |
| M3 | 打磨：状态轮询、多设备、异常提示、图标/主题 | 持续 |

> 建议先做 M1：局域网协议完全公开、无风险，最快拿到「能用的遥控器」，云端部分建立在它之上（报文格式相同）。

---

## 11. 构建与 CI（GitHub Actions / CNB，本地零环境）

**策略**：代码推仓库 → 云端自动出 APK → 构建页下载安装。本地只留代码编辑器（VS Code / Android Studio 均可，不装 SDK），构建全部交给 CI。

### GitHub Actions（仓库放 GitHub）

```yaml
# .github/workflows/build.yml
name: build-apk
on:
  push:
    branches: [main]
  workflow_dispatch:            # 支持手动触发
jobs:
  build:
    runs-on: ubuntu-latest      # 自带 Android SDK，无需另装
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4        # Gradle 缓存加速
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: gree-remote-debug
          path: app/build/outputs/apk/debug/*.apk
```

### CNB 云原生构建（仓库放 cnb.cool，国内直连快）

```yaml
# .cnb.yml —— 与官方示例 examples/ecosystem/android-demo 同款结构
main:
  push:
    - docker:
        image: mobiledevops/android-sdk-image:34.0.1   # 按 SDK 需求选镜像
        volumes:
          - /root/.gradle:cow                          # 声明式 Gradle 缓存
      stages:
        - name: android-build
          script: ./gradlew assembleDebug
        - name: show-artifacts
          script: ls -l app/build/outputs/apk/debug/
```

> CNB 官方有 Android 模板仓库可直接 `cnb-init-from` 起步；构建产物在流水线详情页获取，具体收集方式以 [CNB 文档](https://cnb.cool) 为准。

### 选择建议

| 场景 | 建议 |
|---|---|
| 仓库在 GitHub、访问顺畅 | GitHub Actions（生态最成熟，artifact 下载方便） |
| 国内网络优先 / 仓库在 CNB | CNB（有官方 Android 模板一键起步） |
| 想双保险 | 两份配置并存，同一份代码两边都能出包 |

**签名**：自用装 debug APK 即可；要 release 正式签名，两平台都用 secrets 注入 keystore（base64），密钥绝不进仓库。

---

## 12. 参考资料

### 12.0 活跃度核实（2026-11，经 GitHub API 逐一验证）

| 项目 | 创建 | 最近推送 | 状态 |
|---|---|---|---|
| [tomikaa87/gree-remote](https://github.com/tomikaa87/gree-remote) | 2017-09 | **2026-07** | 协议权威源头，未归档；issue 区 2026-07 仍在讨论新固件 V2 适配 |
| [cmroche/greeclimate](https://github.com/cmroche/greeclimate) | 2020-06 | **2026-10** | HA 生态 Python 库，活跃（78★） |
| [davo22/greeclimate](https://github.com/davo22/greeclimate) | 2026-02 | **2026-10** | 云端 MQTT fork，新增云控制支持，V1+V2 |
| [inwaar/gree-hvac-client](https://github.com/inwaar/gree-hvac-client) | 2019-06 | **2026-01** | Node 实现，维护中 |
| [thesmokinator/gree-climate](https://github.com/thesmokinator/gree-climate) | 2026-07 | 2026-07 | 新库，V1+V2 双支持 |
| [georgevgs/gree-ac](https://github.com/georgevgs/gree-ac) | 2026-07 | 2026-07+ | 新参考项目，MIT |
| [luc10/gree-api-client](https://github.com/luc10/gree-api-client) | 2025-08 | — | 云端接口族 2025 年仍在跑通的实证（含中国区服务器表） |
| Home Assistant gree 集成 | — | 持续 | [文档](https://www.home-assistant.io/integrations/gree/) 显示 2026.x 仍在演进字段 |

> **时效性结论**：局域网协议层"老而未死"——报文格式由**存量空调固件**决定，数百万台设备的模块不可能集体升级，厂商必须保持向后兼容；且 2025–2026 年仍有新实现、新适配持续出现。真正的时效风险集中在云端"控制命令端点"（从未有公开资料），以 §8 抓包定案，双通道设计（§2.3）作为云接口失效时的降级保险。

**局域网协议（成熟）**
- tomikaa87/gree-remote —— 协议逆向源头（scan/bind/status/cmd、加密细节）：<https://github.com/tomikaa87/gree-remote>
- gree-remote issue #63 —— V2（AES-GCM）bind 的完整参数与自动降级讨论：<https://github.com/tomikaa87/gree-remote/issues/63>
- thesmokinator/gree-climate —— Rust 实现，V1/V2 双支持：<https://github.com/thesmokinator/gree-climate>
- inwaar/gree-hvac-client —— Node.js 实现（npm: gree-hvac-client）：<https://github.com/inwaar/gree-hvac-client>
- Home Assistant Gree Climate 官方集成：<https://www.home-assistant.io/integrations/gree/>
- georgevgs/gree-ac —— 「Web 遥控 + 本地桥接服务」完整参考项目（2026-07 新建，MIT）：<https://github.com/georgevgs/gree-ac>

**云端协议（国内 grih.gree.com）**
- 逆向「格力+」云接口的 Python 实现（登录/设备列表/场景/签名算法；**2019 年资料，只当"协议形状"参考**）：<https://github.com/xuqiu/hass/blob/master/components/gree/__init__.py>
- luc10/gree-api-client —— 2025-08 的云 API 客户端，实测 grih.gree.com 接口族（含中国区）仍在线：<https://github.com/luc10/gree-api-client>

**云端协议（海外，形态参考）**
- davo22/greeclimate —— 云端 MQTT 发现与控制：<https://github.com/davo22/greeclimate>
- Homey 论坛帖 —— QueryOnline + 设备专属 TCP（16385）细节：<https://community.homey.app/t/app-pro-gree-hvac-app/30801/240>
- bekmansurov/gree-hvac-protocol —— 设备端连接的格力服务器列表（按区域）：<https://github.com/bekmansurov/gree-hvac-protocol>

---

*本文档基于公开逆向资料整理，供个人学习与自用参考。*
