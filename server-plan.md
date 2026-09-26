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
- [ ] M2 会话状态机 + HTTP
- [ ] M3 串口桥接
- [ ] M4 AI 生成
- [ ] M5 加固与交付
