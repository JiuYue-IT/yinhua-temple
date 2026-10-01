# 第三方代码和资源说明

项目自有代码采用仓库根目录的 [MIT License](LICENSE)；该许可证不替代第三方组件或素材的原有授权，也不自动授予媒体素材的使用权限。

## 随前端打包的代码

- React / React DOM：前端 `hall.js`、香炉 `assets/ritual.js` 等打包文件含 React 代码，保留其中的版权声明。React 采用 MIT License，完整文本见下方。
- GSAP / ScrollTrigger：本项目包含 GSAP 3.15.0，打包文件及 `vendor/` 文件中标注 Copyright 2026, GreenSock，并注明适用 [GSAP Standard License](https://gsap.com/standard-license)。保留这些声明；项目代码许可证不覆盖 GSAP。

## 通过包管理器取得的依赖

Java 依赖以 `server/pom.xml` 为准，包括 Spring Boot、Jackson、Hibernate Validator、Anthropic Java SDK、jSerialComm 及其传递依赖。浏览器测试使用 `tools/e2e/package-lock.json` 锁定 Playwright Core。它们由 Maven / npm 下载，不将依赖缓存提交到仓库；使用和再分发时遵循各依赖发布包内的许可证。

## 图片、视频、音频

前端当前使用随项目提供的图片和视频，其作者、来源及具体授权应由项目维护者登记。代码许可证不会自动授予这些素材的再分发或商用权。

`music-preview/制作说明.json` 记录配乐为程序合成的五声音阶、笛声、钟声及环境噪声，未使用 Suno API。该记录说明制作方法，不替代作者授权。

提交新素材时，应在此登记来源及授权；保留第三方要求的署名。

## React MIT License

来源：[facebook/react/LICENSE](https://github.com/facebook/react/blob/main/LICENSE)。

```text
MIT License

Copyright (c) Meta Platforms, Inc. and affiliates.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
