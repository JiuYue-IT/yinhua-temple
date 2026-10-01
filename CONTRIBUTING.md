# 参与开发

## 本地运行

需要 JDK 17。项目自带 Maven Wrapper，不需要单独安装 Maven。默认硬件模式为 `dryrun`，无需连接设备。

```powershell
Set-Location server
Copy-Item config.example.env .env
# 编辑 .env 中的服务商、地址、密钥和模型名。
.\mvnw.cmd spring-boot:run
```

打开 <http://localhost:8080/>。不配置 AI 时，可以使用页面中明确标记的预置案例。前端为可直接托管的静态文件，启动不需要 npm 构建。

修改 `original/incense/src/` 的 React 源码后，需要更新页面实际加载的 `assets/ritual.js`；当前源代码与已打包文件都保留。该部分的独立构建流程尚待补齐。

## 分支和 Pull Request

有写权限的成员创建自己的分支，例如 `feat/cursor-performance` 或 `fix/poem-overlap`，通过 Pull Request 合并。外部贡献者可先 Fork。建议开启主分支保护，要求至少一人审核且后端测试通过。

PR 说明需包含修改目的、实际影响、验证方式，以及尚未验证的内容。视觉修改附修改前后截图，并检查桌面和手机尺寸。现有待解决问题见 [性能与视觉方案](docs/10-AI连接修复与前端性能优化方案.md)。

## 验证

后端：

```powershell
Set-Location server
.\mvnw.cmd test
```

测试不读取个人 `.env`，不访问真实 AI，也不连接真实串口。

前端加载检查（需要 Node.js 20 或更新版本，先启动本地后端，并安装 Google Chrome）：

```powershell
Set-Location tools/e2e
npm ci
$env:BASE='http://127.0.0.1:8080'
# 若 Chrome 不在默认 Windows 路径，设置 CHROME 为本机浏览器可执行文件路径。
npm run audit
```

`direct.e2e.js` 测试完整问签流程，但需要另起一个指向本地模型替身的后端（18080）。在独立 PowerShell 窗口中：

```powershell
Set-Location server
$env:LB_ENV_FILE='target/no-env-in-e2e'
$env:AI_PROVIDER='openai'
$env:AI_ENDPOINT='http://127.0.0.1:18081/v1'
$env:AI_API_KEY='test-key'
$env:AI_MODEL='test-reading'
$env:AI_OPENAI_THINKING=''
$env:DEVICE_MODE='dryrun'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=18080'
```

待后端启动后，在 `tools/e2e` 执行 `npm run e2e`。测试脚本自行启动 18081 的模型替身，结束后关闭替身。测试结束后关闭独立后端窗口；不要把这些测试环境变量用于正常服务。

`npm run audit:live` 会调用当前后端配置的真实 AI，可能产生 API 费用，请只在需要真实验证时执行。日常加载检查和模型替身测试无需真实密钥。

发布前在项目根目录执行 `python tools/publication_audit.py`，检查 Git 可见文件及现有历史中的常见凭据、个人配置文件和超过 GitHub 单文件限制的文件。检查不输出密钥；它是自动筛查，不能替代素材来源和文档内容的人工核对。

## 配置和素材

不要提交真实密钥、个人 `.env`、串口配置、日志、`node_modules` 或构建缓存。需要新增配置时修改 `server/config.example.env`。

所有新素材应说明作者、来源和授权；项目第三方声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
