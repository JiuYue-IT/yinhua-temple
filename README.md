# 因花寺 / 人生支线

一个将许愿、问签与现实行动连接起来的数字寺庙体验。用户在许愿池输入心事，AI 同时生成总结和详细回应：正殿显示总结签文，菩提果实展开详细内容。

当前前端唯一开发入口：`front_all/因花寺-完整游戏/index.html`。
后端：`server/`；接口类型：`contracts/types.ts`。

后端使用 Java 17 / Spring Boot 3.5，支持 OpenAI 兼容与 Anthropic 协议；前端为静态 HTML、CSS、JavaScript 和已打包的 React / GSAP 章节。

## 启动完整前端

先安装 JDK 17。在 `server/` 复制 `config.example.env` 为 `.env`，按服务商要求填写地址、密钥和真实模型名，再执行 `.\mvnw.cmd spring-boot:run`，打开 `http://localhost:8080/`。
也可双击 `front_all/因花寺-完整游戏/启动预览.cmd`，预览服务器会将 `/api` 代理到本机 8080。
当前已接入直接问签：许愿池提交后立即生成，正殿展示总结签文，菩提果实展开详细回应。
无需 npm 构建。`original/` 中的点香、许愿、礼佛、菩提章节被 iframe 使用，必须保留。

不配置 AI 也可以体验明确标记的预置案例。默认设备模式为 `dryrun`，无需硬件；串口接入方式见 [后端说明](server/README.md)。真实 API 密钥只放在后端的本地 `.env`，不要写入网页。

Kimi 的 OpenAI 兼容配置示例（密钥自行填写）：

```dotenv
AI_PROVIDER=openai
AI_ENDPOINT=https://api.moonshot.cn/v1
AI_API_KEY=填写你自己的密钥
AI_MODEL=kimi-k2.6
AI_STRUCTURED_OUTPUT=true
AI_OPENAI_THINKING=disabled
DEVICE_MODE=dryrun
```

`thinking.type` 是服务商特有参数，其他服务不支持时将 `AI_OPENAI_THINKING` 留空。模型名必须是账号实际可用的名称。

## 由后端托管前端

在项目根目录的 PowerShell 执行：

```powershell
$env:WEB_DIST='../front_all/因花寺-完整游戏/'
Set-Location server
.\mvnw.cmd spring-boot:run
```

打开 `http://localhost:8080/`。页面与 `/api` 同源。AI 未配置时提供明确标注的预置案例恢复入口。
当前流程、输入输出契约和验证记录见 [直接问签与双层输出](docs/09-直接问签接入与双层输出.md)。

## 开发与协作

仓库分支、提交和合并步骤见 [仓库操作指南](docs/仓库操作指南.md)。日常开发从 `develop` 创建新分支，先推送新分支，再提 PR 到 `develop`，检查通过后合并。`main` 保留稳定版本，由 `develop` 提 PR 更新。运行和验证说明见 [CONTRIBUTING.md](CONTRIBUTING.md)。

当前后端仅保留一个活动会话，适合本地和单组演示；多人同时在线使用需要后续改造会话隔离。当前视觉及交互问题见 [待办](docs/10-待办.md)。

第三方代码和素材说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

项目自有代码采用 [MIT License](LICENSE)，允许使用、修改、分发及商用，需保留版权和许可证声明。第三方组件和媒体素材遵循各自授权，详见第三方说明。

## 本地旧版本与生成产物

`cyber-temple-journey`、`cyber-temple-journey-unused`、`temple-entry`、`wishing-pool-complete`
已从项目根目录移入本地 `archive/`。它不属于 GitHub 发布内容。旧源码可作为参考，不能用旧页面整体覆盖当前视觉版本。

`design/` 是本地设计实验目录，公开发布不包含它。依赖缓存、构建目录、测试截图、结果和日志也不提交。`server/src/main/resources/static/playground.html` 是后端联调工具，保留使用。
