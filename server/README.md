# 人生支线 · 后端（server/）

Java 17 + Spring Boot 3.5.16。负责会话状态、AI 故事生成、预置案例和 USB 串口桥接。
接口字段以 `docs/04-软工B-后端AI与接口契约.md` 为准，串口协议以 `docs/03` 第 5 节为准。

## 1. 启动

环境：JDK 17（`java -version` 能看到 17），不需要单独安装 Maven，项目自带 `mvnw.cmd`。第一次启动会下载依赖，需要能访问 Maven Central。

```powershell
cd server
.\mvnw.cmd spring-boot:run
```

看到下面这段就说明启动成功：

```
==================== 人生支线后端已启动 ====================
  接口：http://localhost:8080/api/health
  AI  ：已配置（anthropic / claude-opus-4-8）   ← 或「未配置，只能使用预置案例」
  设备：dryrun 模拟（不连接硬件）
  网页：未找到 web/dist/index.html（开发时用 Vite 代理 /api 到本端口）
============================================================
```

停止：在该窗口按 `Ctrl+C`。端口被占用时，先关掉之前没退出的后端窗口。

## 2. 配置（server/.env）

后端启动时自动读取 `server/.env`（每行 `KEY=VALUE`，不加引号）。模板见 `config.example.env`：

```powershell
Copy-Item config.example.env .env   # 然后编辑 .env
```

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `AI_ENDPOINT` | 空 | 中转站地址，写到域名即可 |
| `AI_API_KEY` | 空 | 密钥。**只写在 .env，不提交、不外发** |
| `AI_MODEL` | `claude-opus-4-8` | 当前中转站上 `claude-opus-5` 会拒答，不要用 |
| `AI_PROVIDER` | `anthropic` | 或 `openai`（走 `/v1/chat/completions`，地址写到 `/v1`） |
| `AI_AUTH` | `x-api-key` | 中转站要求 `Authorization: Bearer` 时改为 `bearer` |
| `AI_EFFORT` | `low` | 生成深度；中转站不认识时留空 |
| `AI_STRUCTURED_OUTPUT` / `AI_REFUSAL_FALLBACK` | `true` | 中转站报 400 时依次改为 `false` |
| `DEVICE_MODE` | `dryrun` | `serial` 连接 ESP32 |
| `SERIAL_PORT` | 空 | 如 `COM3`，启动日志会列出本机全部串口 |
| `WEB_DIST` | `../web/dist/` | 前端构建产物目录（以 `/` 结尾） |

- 同名系统环境变量优先于 `.env`。
- 不填 AI 也能启动，只是 `mode: live` 返回 503，预置案例照常可用。
- 修改 `.env` 后要重启后端。

## 3. 演示部署（单端口）

```powershell
cd web;    npm run build          # 生成 web/dist
cd ..\server; .\mvnw.cmd spring-boot:run
# 浏览器打开 http://localhost:8080/
```

后端会托管 `../web/dist/`，页面和 `/api` 同源，不需要代理和 CORS。

## 4. 接硬件（ESP32）

1. 关闭 Arduino 串口监视器（同一端口只能被一个程序占用）。
2. 在设备管理器或后端启动日志里找到 ESP32 的 COM 号（通常显示 CP210x / CH340）。
3. 在 `.env` 设置 `DEVICE_MODE=serial`、`SERIAL_PORT=COMx`，然后重启后端。
4. 日志出现「设备握手成功」，并且 `/api/health` 的 `device.status` 为 `online`，即连接成功。
5. 拔掉 USB 后网页流程继续；插回会自动重连，只恢复当前画面，不会补摇签。

## 5. 常见问题

| 现象 | 处理 |
| --- | --- |
| 生成失败 `AI_UNAVAILABLE`，日志显示 `refusal` | 换 `AI_MODEL`（当前中转站 opus-5 不可用） |
| 日志显示 401 | 检查密钥；或设置 `AI_AUTH=bearer` |
| 日志显示 400 | 依次设置 `AI_REFUSAL_FALLBACK=false`、`AI_STRUCTURED_OUTPUT=false`、`AI_EFFORT=` |
| 生成超时 `AI_TIMEOUT` | 网络慢或模型慢：换 `claude-sonnet-5`，或调高 `application.yml` 中的 `app.ai.task-timeout-seconds` |
| `DEVICE_MODE=serial` 但一直 offline | COM 号不对、串口被占用、固件没有输出 hello |
| 中文日志在 PowerShell 里乱码 | 控制台编码问题，不影响功能；可先执行 `chcp 65001` |

## 6. 测试

```powershell
.\mvnw.cmd test                 # 51 个测试，不访问真实 AI 与串口，不读取 .env
.\scripts\try-live.ps1          # 后端运行中执行：用真实 AI 生成 3 个不同背景
```

测试覆盖：请求幂等与只摇一次签、预置模式拒绝自定义输入、A→回望→改 B→收据、reset 后迟到结果不污染、AI 超时 / 格式错误 / 拒答、串口分包粘包 / 重启 / 拔线 / 无回执、码点计数边界、托管前端与 CORS。

## 7. 检查记录（2026-09-26）

| 项目 | 结果 | 方式 |
| --- | --- | --- |
| HTTP 全部接口、状态流转 | 通过 | 自动测试 + 真实服务手动走完预置流程 |
| 真实 AI 生成 | 通过（3/3，claude-opus-4-8，每次约 19 秒） | `try-live.ps1` + 人工阅读内容 |
| 托管 web/dist、启动摘要 | 通过 | 真实服务 |
| 串口协议 | **仅模拟通过** | 用模拟固件测试；**未接真实 ESP32** |
| 摇签、TFT 实物效果 | **未验证** | 需与硬件同学现场联调 |

## 8. 已知限制

- 只支持一个活动会话；后端重启会丢失会话（前端会拿到 404，需重新开始）。
- 真实 AI 生成约 15—20 秒，离 30 秒总时限余量不大。
- 预置案例会固定停 2 秒（`app.preset.delay-ms`），让摇签画面有时间呈现。
- 串口桥接没有在真实 ESP32 上跑过；打开串口时板卡是否自动复位，需要实机确认。
- `done` 回执只表示固件定时结束，不代表签筒实际摇动成功。
