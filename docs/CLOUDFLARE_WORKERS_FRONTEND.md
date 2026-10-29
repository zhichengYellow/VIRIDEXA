# Cloudflare Workers Free · 首轮静态前端适配

日期：2026-10-29。仓库：`zhichengYellow/VIRIDEXA`。此轮仅准备部署并进行本地验证，没有登录 Cloudflare、上传版本、实际发布或推送 GitHub。

## 控制台填写

在 Workers 的 Git 构建设置中选择本仓库：

| 字段 | 值 |
| --- | --- |
| Worker 名称 | `viridexa` |
| 根目录 / Root directory | `apps/web` |
| 生产分支 | `main` |
| Node 版本 | `22.20.0`（现有 `.nvmrc`；可同时设置构建变量 `NODE_VERSION=22.20.0`） |
| 构建命令 | `npm run build` |
| 部署命令 | `npx wrangler deploy` |
| 非生产分支预览命令 | `npx wrangler preview` |

Wrangler 已作为精确版本 `4.154.0` 写入前端开发依赖和 lockfile。控制台应安装开发依赖，不设置 `NODE_ENV=production` / `npm ci --omit=dev`，否则 Vite、TypeScript、Wrangler 不可用。平台自动安装依赖；若改为自行安装的流水线，先在 `apps/web` 执行 `npm ci`。

当前 Wrangler 的 `preview` 为 open beta，本轮只检查了 `--help`，没有创建云端 Preview。也可以按官方说明选择 `npx wrangler versions upload` 生成非生产 Version URL；它与分支隔离 Preview 不同，不会自动晋升生产。两种命令均需要实际账户权限，不能把本地检查当作发布成功。

确认账户仍为 Workers Free，不启用付费服务、自动升级或额外绑定。配置中没有 D1、R2、KV、远程模型、容器或其他服务绑定，不需要数据库密码或 LLM Key。零成本适配不表示超出 Free 服务限制后仍有无上限资源保证。

## 本地复核

```sh
cd apps/web
# 使用 .nvmrc 指定的 Node 22
npm ci
npm run build
npm test
npx wrangler deploy --dry-run
# 仅本地模拟，不会部署到 Cloudflare
npx wrangler dev --ip 127.0.0.1 --port 8787
```

`npm run preview:cloudflare` 也可启动本地模拟。它不同于控制台的远程 `wrangler preview`。`npm run preview` 是 Vite 预览，不能替代 Workers 路由校验。

## 部署边界与 API 审计

`apps/web/wrangler.jsonc` 将 `assets.directory` 设为 `./dist`，`not_found_handling` 设为 `single-page-application`。没有 Java Worker 入口，没有把 Spring Boot/MySQL 搬到 Workers。`vite.config.ts` 中 `/api` 代理只在 Vite 开发服务器生效，不会随构建部署。

| 请求 / 资源组 | 无后端的状态 |
| --- | --- |
| `/api/v1/plants`、suggestions、植物详情和 sources | 实际目录、名称检索、联想、来源与详情查询不可用 |
| `/api/v1/science/identify` | 外观条件匹配不可用 |
| `/api/v1/graph` 与 `/api/v1/admin/graph` | 真实节点、关系、证据查询不可用 |
| `/api/v1/workbench` 与 `/api/v1/admin/workbench` | 样本、数值、比较与证据不可用 |
| `/api/v1/agent` | 真实模型与领域检索降级均需要后端，静态站不能运行 Agent |
| `/api/v1/account`、`/api/v1/csrf`、`/api/v1/security/meta` | 会话、登录注册、CSRF 与动态安全配置不可用 |
| 管理员候选、审核、撤回及其他管理 `/api` | 不可用，不放宽原有权限 |
| `/api/v1/showcase` | 实时统计不可用，显示缺失而非编造数量 |
| `/media/…` | 原 Compose 的本地媒体挂载不会上传，动态媒体不可用 |
| `/botanical/…`、`/support/…`、静态来源介绍与历史验收 JSON | 已审阅的公开素材、两张作者二维码和明确标注的历史快照可展示 |

全站 `BackendNotice` 对真实公开安全元数据进行有界连接检测；HTML 回退、错误 JSON、超时与网络失败均显示“后端尚未连接 · 前端展示模式”，可重试，组件卸载取消请求。不将该提示当作认证或授权依据。植物查询收到 HTML 时显示明确的不可用原因，查询失败不展示“目录 0 条”来冒充数据库真实总数。

注意：此静态 SPA 配置下，缺失 `/api` 请求在本地模拟可能收到 `200 text/html`（首页回退），不是成功的 JSON API。本轮保留纯静态配置，没有新增伪 API；客户端检测拒绝把 HTML 解析为数据库结果。未来 Workers API 需要明确处理 `/api/*`，并重新设计数据发布和权限。

技术看板的 Java、Nginx、Spring Security 描述是现有完整工程架构；不意味着这些组件在当前 Workers 静态部署运行。页面顶端连接状态与“历史快照”标签用于区分。

## 产物与数据安全

`postbuild` 自动执行 `scripts/check-static-assets.mjs`：扫描完整 dist，拒绝链接、私有目录、非允许扩展名、超过25 MiB文件及识别出的常见凭据格式。`public/.assetsignore` 作为额外上传排除，不依赖它隐藏已经进入 dist 的资料。检查通过的构建产物为 **44 个文件，2,631,488 字节**，仅包含打包的前端、公开素材和历史快照，没有 `.env`、数据库备份、论文、科研原始数据和服务器配置。

额外执行 `gitleaks dir apps/web/dist --redact --no-banner`：未发现泄漏。模式扫描不能证明所有未知格式秘密不存在；本次同时核对了构建目录与公开素材范围。`.wrangler/`、`.dev.vars*` 已加入 Git 忽略规则。API Key 不需要也不得填入 `VITE_*` 构建变量。

## 本轮实际结果

- Node 22.20.0；Vite 8.3.5；Wrangler 4.154.0。
- `npm ci`：通过，139 个包安装完成。
- `npm run build`：Vue 类型检查、Vite 生产构建、44文件产物检查通过；保留原有 ECharts 大型懒加载 chunk 提示，未为部署扩大架构。
- `npm test`：**55通过、0失败、0跳过**，新增静态 HTML 回退、有效元数据、取消信号和网络失败覆盖。
- `wrangler deploy --dry-run`：通过，无服务绑定，无实际上传。
- Wrangler 本地预览：`/`、`/technology`、带导航请求头的 `/plants/9445` 均返回 SPA HTML；公开二维码返回 PNG。`/api/v1/plants` 返回 HTML，客户端正确显示后端缺失。
- 浏览器：直接打开 `/technology` 深链接，静态介绍可读、实时数据为缺失；进入 `/explore` 显示连接提示与查询失败，不伪造植物记录。见 [静态技术页截图](cloudflare-evidence/static-technology.png)、[无后端查询截图](cloudflare-evidence/static-explore.png)。
- 未执行 Java/MySQL 迁移或修改科研原始文件；本轮不声明后端回归或真实云端部署成功。

## 官方依据

- [Workers Static Assets SPA 配置](https://developers.cloudflare.com/workers/static-assets/routing/single-page-application/)
- [Workers Builds 根目录、构建、部署和预览命令](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/)

后续 D1 + Workers API 另行设计，不能把当前本地 MySQL 数据或原始论文直接作为静态资产发布。
