# 后端（软工 B）模块开发计划

依据：`04-软工B-后端AI与接口契约.md`（HTTP/数据唯一规范）、`03-电子信息-硬件与串口协议.md` 第 5 节（串口唯一规范）。
每次开发会话只做一个模块；开始前让 AI 先读本文件和对应规范章节，完成后在下方「进度」打勾。

## 环境检查结果（2026-09-26）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| JDK | OK | Microsoft OpenJDK 17.0.20，JAVA_HOME 已设置（另有 JDK 21 可用） |
| Maven | 可用 | 无全局 `mvn`；本机有 wrapper 缓存 Maven 3.9.11，使用项目内 `.\mvnw.cmd` |
| 依赖下载 | OK | Maven Central、start.spring.io 可访问；本地仓库尚无 Spring Boot，首次构建需下载 |
| Node | OK | v24（供软工 A 前端使用） |
| 串口 | 待硬件 | 现有 COM4/5/7/8/9/10 均为蓝牙串口，ESP32 未接入；先用 `DEVICE_MODE=dryrun` |
| AI 凭据 | 缺 | 未配置项目用的 `AI_ENDPOINT / AI_API_KEY / AI_MODEL`，M4 前需确定服务商 |
| Git | 已初始化 | 每个模块完成后由 AI 提交一次 |

## 模块划分

### M1 工程骨架 + 数据契约（目标 H0.5—1）
- 在 `server/` 用 Spring Initializr 生成 Spring Boot **3.x**（锁定 3.x 最新补丁版，不用 4.x）Maven 工程：web、validation；加 `jSerialComm`（锁定版本）。
- `application.yml`：端口 8080，读取 `AI_ENDPOINT/AI_API_KEY/AI_MODEL/SERIAL_PORT/DEVICE_MODE`（默认 dryrun）。
- DTO（record）：Input、Story、Option、Reflection、ReceiptDraft、SessionSnapshot、Receipt、ApiError、CreateSessionRequest、ChoiceRequest、ReceiptRequest、Health。
- `StoryValidator`：按码点计数的全部字段约束（04 §3.2）。
- 预置案例 `resources/presets/team-project.json`。
- `GET /api/health` 先返回固定 dryrun 状态。
- 交付给 A：`contracts/session-example.json`、`contracts/types.ts`。
- 验收：`.\mvnw.cmd spring-boot:run` 启动，curl health 成功；预置 JSON 通过 validator 单测。

### M2 会话状态机 + 全部 HTTP 接口（预置模式 + dryrun 设备）（目标 H1—2）
- `StoryProvider` 接口 + `PresetStoryProvider`；`DeviceBridge` 接口 + `DryRunDeviceBridge`（只记日志和 lastEvent）。
- `SessionService`：单会话、状态流转、后台生成任务 + 生成代号（防迟到回写）、requestId 幂等（REQUEST_CONFLICT / SESSION_BUSY / REQUEST_EXPIRED）、choice 改选、receipt 首次确认幂等、reset。
- `SessionController`：04 §4 全部 6 个接口；全局异常处理输出 `{"error":{code,message}}`。
- 事件映射表（04 §6.2）在 Service 中按顺序发出。
- JUnit：同 requestId 只一次 DRAW、preset 拒绝自定义 input、A→回望→改 B→收据为 B、reset 后旧任务不污染。
- 验收：A 可以用真实后端跑通整个预置流程。

### M3 USB 串口桥接（目标 H1—2 与硬件联调，H2 闸口）
- `SerialDeviceBridge`（jSerialComm，115200 8N1）：读线程按 `\n` 分帧、容忍 `\r\n`、单行 512 字节上限；写加锁。
- hello/ping 握手、bootId、每命令 UUID、忽略旧 bootId 回执、log 类型忽略。
- ACK 跟踪：1 秒无 accepted → unknown；DRAW done 最多等 5 秒；不自动重发 DRAW。
- 断线检测 + 重连：握手 → RESET → 按当前会话状态恢复显示（绝不重放 DRAW）。
- health 返回真实 online/offline 与 lastEvent/lastAck。
- 附：一个串口端口枚举小工具/启动日志，方便现场找 COM 口；分包/粘包解析单测。
- 验收：网页点击 → ESP32 摇签一次；拔 USB 网页继续；重连不补摇。

### M4 AI 故事生成（目标 H2—4）
- 确定服务商后写 `LiveStoryProvider` 适配器（默认按 OpenAI 兼容 chat/completions 写，便于换国内服务商）。
- 提示词：固定任务说明 + 字段约束 + 完整示例；用户 Input 作为 JSON 数据单独传入。
- Java HttpClient，请求 25 秒超时、任务 30 秒上限，不自动重试；提取文本 → 去掉 ```json 包裹 → Jackson → Validator。
- 错误码：AI_TIMEOUT / AI_UNAVAILABLE / AI_FORMAT_ERROR；未配置时创建返回 503 AI_NOT_CONFIGURED。
- 日志不打印密钥、完整背景和完整模型响应。
- 验收：三个不同背景各生成一次通过校验，人工检查内容质量。

### M5 联调加固与交付（目标 H4—7）
- 可选：后端托管 `web/dist`，单端口演示。
- 补边界测试、清理日志、锁定依赖版本。
- `server/README.md`：配置示例（无密钥）、实测启动命令、COM 口查找、预置模式切换、已知限制与检查记录（实机/模拟分开写）。

## 进度
- [x] M1 工程骨架 + 数据契约（2026-09-26，10 个测试通过，health 实测可用）
  - Spring Boot **3.5.16**（Initializr 已不提供 3.x，手动锁定）、jSerialComm 2.11.4、Maven wrapper 3.9.11
  - 包结构：`config`（AppProperties）/ `model`（DTO+枚举）/ `validation`（@Text 码点校验）/ `story`（Validator、PresetCatalog）/ `web`
  - 预置案例在 `resources/presets/*.json`，启动时校验，不合法则启动失败
  - 契约交付：`contracts/session-example.json`、`contracts/types.ts`
  - 启动：`cd server; .\mvnw.cmd spring-boot:run`；测试：`.\mvnw.cmd test`
- [x] M2 会话状态机 + HTTP（2026-09-26，累计 28 个测试通过，真实服务 HTTP 全流程实测通过）
  - 新增包：`error`（ApiException、ErrorCodes）/ `session`（SessionService）/ `device`（DeviceBridge、DryRun、DeviceConfig）
  - `StoryProvider` 接口 + `LiveStoryProvider` 占位（一律 AI_UNAVAILABLE，M4 替换）；`PresetStoryProvider` 带 2 秒固定停顿（`app.preset.delay-ms`）
  - 状态转换与设备事件在同一把锁内；后台任务用 generation 代号防迟到回写；30 秒总超时后中断任务
  - `DeviceBridge.send` 约定为非阻塞（M3 串口实现需入队发送，不能在锁内等 ACK）
  - `DEVICE_MODE=serial` 目前会启动失败并提示 M3 未实现
- [x] M3 串口桥接（2026-09-26，累计 39 个测试通过；无实机，仅用模拟固件验证）
  - `device/serial/`：SerialTransport 抽象、JSerialCommTransport（115200 8N1）、LineFramer（\n 分帧、容忍 \r\n、512 字节上限）、SerialDeviceBridge
  - 握手：打开串口后等 hello，收不到就每 1.5 秒 ping；在线后 5 秒无输出发 ping，12 秒无输出视为离线；读写失败自动关闭并每 2 秒重连
  - 每条命令带当前 bootId + 新 UUID；旧 bootId 回执忽略；BOOT_MISMATCH 触发重新握手
  - ACK：1 秒无 accepted → unknown；done 超时（DRAW 5 秒，其它 2 秒）→ unknown；绝不自动重发
  - 每次（重新）握手：先发 RESET，再由 SessionService.restoreDeviceDisplay 恢复当前画面，绝不重放 DRAW
  - 离线时事件直接丢弃，不排队；ESP32 复位时 ROM 启动的非 JSON 输出会被忽略
  - 启动日志会列出本机全部串口，方便找 ESP32 的 COM 号

### 与硬件联调清单（ESP32 到位后）
1. 关闭 Arduino 串口监视器（同一端口不能两个程序同时占用）。
2. 设备管理器或后端启动日志中找到 ESP32 的 COM 号（通常显示 CP210x / CH340 / USB-SERIAL）。
3. PowerShell：`$env:DEVICE_MODE="serial"; $env:SERIAL_PORT="COMx"; .\mvnw.cmd spring-boot:run`
4. 日志中出现「设备握手成功」，并且 `GET /api/health` 显示 `online`，说明握手成功；随后固件应收到一次 RESET。
5. 用预置案例走一轮：DRAW 摇一次 → STORY → A 为 REFLECT → 改 B 为 STORY → RECEIPT → reset 为 RESET。
6. 拔掉 USB：health 变为 offline，网页继续；插回后固件只收到 RESET 和当前画面，没有 DRAW。
7. 需要实机确认：打开串口时板卡是否复位并正常输出 hello（ESP32 自动复位电路和 DTR/RTS 相关）；若卡在下载模式或反复复位，记录现象后再调整。
8. 固件的 ROM 启动信息、非 JSON 输出会被忽略；但调试信息请按协议包装为 `{"type":"log",...}`。
- [ ] M4 AI 生成
- [x] M4 AI 生成（2026-09-26，累计 46 个测试通过；用本地假 HTTP 服务验证两种协议，**尚未用真实中转站跑过**）
  - `AI_PROVIDER=anthropic`（默认）：官方 `anthropic-java` 2.65.0 SDK，baseUrl 指向中转站，`AI_AUTH=x-api-key|bearer`
    - 默认模型 `claude-opus-5`；不显式设 thinking（默认自适应），`output_config.effort=low` 控制延迟
    - `output_config.format` JSON Schema 结构化输出（`resources/ai/story-schema.json`）
    - 服务端拒答回退 `fallbacks:"default"` + beta `server-side-fallback-2026-07-01`
    - 先看 stop_reason：refusal → AI_UNAVAILABLE；max_tokens → AI_FORMAT_ERROR
  - `AI_PROVIDER=openai`：Java HttpClient 调 `/v1/chat/completions`，`response_format: json_object`
  - 两种协议都不自动重试；请求 25 秒超时 → AI_TIMEOUT；超时或重置时会取消正在等待的请求
  - 提示词 `resources/ai/story-system-prompt.txt`，示例自动取预置案例；用户输入作为 JSON 数据放在用户消息里
  - 日志只记录状态码、stop_reason、token 数和字段问题，不记录密钥、用户背景、模型原文
  - 真实验证脚本：`server/scripts/try-live.ps1`（3 个不同背景）
  - **真实中转站验证（2026-09-26）**：`claude-opus-5` 在该中转站对任何请求（包括「写一句秋天的诗」）都返回 refusal，不可用；
    换成 `claude-opus-4-8` 后 3 个背景全部生成成功并通过校验，每次约 18—19 秒；`claude-sonnet-5` 约 16 秒，也全部通过
    - 当前 `.env` 使用 `claude-opus-4-8`、effort=low、结构化输出开、回退开
    - 人工检查：三个故事都从未选道路出发，遵守「每天两小时」等限制，结局都带条件表达；回望各只出现在一个选项上且有依据
    - 注意：生成约 19 秒，离 30 秒总时限余量不大；现场网络差时可换 sonnet-5 或把 `app.ai.task-timeout-seconds` 调高
- [ ] M5 加固与交付
