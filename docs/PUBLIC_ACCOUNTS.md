# 公众账户、登录注册与研究权限验收

验收日期：2026-10-26。本次在现有研究功能上接入公众身份，不创建另一套科学审核流程。

## 访问分层

| 身份 | 可使用范围 |
|---|---|
| 访客 | 首页、植物目录/详情、基础知识图谱、获准公开的来源元数据、技术看板 |
| 已登录公众成员 | 访客能力 + Research Agent、Scientific Workbench 的样本/测量/证据查询与比较；仍只读取获准发布数据 |
| 授权管理员 | 成员能力 + 既有候选审阅、管理员图谱和实验台候选范围；科学决定仍走原审核服务 |

`/assistant`、`/workbench`、`/account` 使用路由身份检查。Agent 创建/读取/取消任务、实验台样本/测量/证据/比较接口同时由 Spring Security 要求认证。模型状态、数据覆盖与植物目录元数据仍可公开读取，供首页、植物详情与技术看板复用。未知 Agent 写入路径继续 denyAll。

401 会引导重新登录，保留原始研究 URL、筛选条件、问题与 hash。返回地址只接受支持的本地路由，禁止外部跳转。切换账户会清除旧 Agent owner，任务不能跨账户继承。前端隐藏不属于当前身份的管理员入口，真正的权限边界由服务端执行。

## 实现与安全

- 新增 `/login`、`/register`、`/account`。登录/注册共用页面，采用象牙白、森林绿、浅青柠与淡紫配色；统一表单、错误反馈与响应式布局。
- 注册填写昵称、邮箱、密码和密码确认；成功后自动以 MEMBER 身份登录。登录注册指南可展开，亦可通过 `/register?guide=1` 直接打开。
- 密码采用现有 BCrypt 编码器；注册接受 12–72 UTF-8 字节，拒绝控制字符，防止 BCrypt 静默截断。密码不进入 URL、本地存储或报告。
- 登录后轮换 Session ID 与 CSRF；注销使 Session 失效。账号资料仅存在内存状态中，刷新通过私有 session API 恢复。
- CSRF、并发哈希上限、服务端身份/IP 限流和 Nginx 登录注册限流共同生效。重复邮箱与登录错误具有清晰提示。
- V14 增加 MEMBER 角色和固定注册存储过程；新账号不能提交 role/ADMIN 字段。存储过程事务同时创建用户和 MEMBER 关联，失败回滚。
- 新 `viridexa_accounts` 数据库身份仅 SELECT 三张身份表与 EXECUTE 固定注册过程；无直接 DML、审核写入或科研数据修改权限。科学公开查询仍使用 `viridexa_ro`，审核仍使用原独立权限。
- 删除审核台原来重复的管理员登录表单，管理员改走同一登录页面，权限不变。

邮箱验证与邮件找回尚未配置，页面已明确说明。没有伪造邮件发送或密码重置入口。当前未替用户创建生产人类账户或公开默认密码，生产真实管理员仍需现有受控初始化命令。

## 部署与复现

首次部署先准备服务账号密钥（写入已忽略的本地 `.env`，不输出）：

```sh
python3 scripts/pipeline/provision_public_accounts.py --prepare-only
./apps/api/mvnw -f apps/api/pom.xml -s .mvn/settings.xml package
docker compose up -d --build api
python3 scripts/pipeline/provision_public_accounts.py
```

第二次命令必须在 V14 已由 Flyway 成功迁移后执行。该脚本只创建最小权限数据库服务身份，不创建人类管理员。重跑保留既有服务密钥和账号。

前端构建：`npm --prefix apps/web run build`。部署 Nginx 配置后执行 `docker compose exec -T web nginx -t` 和 `docker compose exec -T web nginx -s reload`。公网部署另需 HTTPS、`SESSION_COOKIE_SECURE=true`、实际域名与真实邮件策略；本轮验收范围为本机 Compose。

## 自动化与浏览器结果

| 检查 | 结果 |
|---|---|
| Maven 单元 / Spring Web 安全 | 118/118 |
| 隔离 MySQL / REST 集成 | 51/51 |
| Vue / TypeScript 逻辑测试 | 33/33 |
| Python 治理与初始化 | 26/26 |
| 科研管线回归 | 15/15 |
| 合计 | 243 通过，0 失败，0 跳过 |
| Vue 类型检查与生产构建 | 通过 |
| Compose | API、MySQL healthy；Nginx 配置与实际 HTTP 通过 |

覆盖注册事务、重复邮箱、并发注册、非法角色注入、密码哈希、实际 Session/CSRF 轮换、错误密码、禁用账户、最小 DB 权限、数据库故障回滚、MEMBER 不可访问审核、访客不可创建/读取/取消 Agent 任务、成员实际工具检索、身份切换任务隔离及科学候选过滤。

浏览器仅在明确隔离数据库创建技术测试成员，并使用隔离技术管理员登录；未提交任何生产科学审核决定。实际验证注册密码不一致、注册自动登录、刷新恢复、错误密码清空、成员拒绝审核、管理员同入口登录、审核台注销、原研究问题登录后恢复、真实只读领域工具轨迹、退出后重新要求登录、实验台成员视图不展示管理员候选入口。

375/768/1024/1440px 检查登录、注册、首页、访客审核说明与隔离成员账户，共 20 组布局检查，无横向溢出。截图位于 `reports/public-accounts/`，`public-*` 为生产匿名页面；`*-isolated*` 仅为隔离技术测试，不是生产科学审批。

构建仍有既有 ECharts 延迟图表块 506.93 kB 提示；图谱与图表保留路由/按需加载，本轮不以消除该警告宣称性能提升。

## 生产数据与限制

本次实际公开目录 5 条，正式科学事实 0 条，人类账户/管理员 0 个；新注册功能已就绪，可由用户自行创建公众账户。没有替代人工审核。真实模型未配置，Agent 仍为真实领域服务检索降级模式。

原始保护文件 SHA-256 292/292 匹配。账号功能未修改科学候选、证据来源或原始文件。未推送 GitHub。

机器报告：`reports/public-accounts/test-summary.json`、`production-regression.json`、`production-db-permissions.json`、`responsive-layouts.json`；日志见同目录。
