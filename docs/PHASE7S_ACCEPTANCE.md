# VIRIDEXA Phase 7S · 部署前安全验收

日期：2026-10-26。仅修改应用与本项目配置，没有修改 Ubuntu、防火墙、DNS、其他项目或原始科研文件。没有 GitHub 推送。完整入口清单见 [攻击面审计](PHASE7S_SURFACE_AUDIT.md) 和 `reports/phase7s/api-surface.json`。

## 已实现的防护

| 攻击面 | 实际保护 |
|---|---|
| 公共植物、图谱、来源读取 | 发布过滤不变；查询长度、分页、单跳展开与结果数量限制；Nginx 搜索区；SQL 参数化与超时 |
| 注册 | 启用 Turnstile 时必须服务端成功验证；CSRF、邮箱/IP 限流、并发哈希限制、固定 MEMBER 注册事务与最小权限 DB 身份 |
| 公众和旧管理员登录 | 同一失败风险评估；普通登录不强制验证码；账户 3 次或 IP 10 次失败后挑战；12 次/账户、60 次/IP 的失败硬上限；10 分钟窗口自动恢复 |
| 管理员写入、原始证据预览 | 原 ADMIN/CSRF/具名审核规则完整保留；独立写限流；预览仍受许可、类型、大小、路径和 SHA 校验约束 |
| Agent | 登录要求不变；每日用户/全站配额、并发 2、模型调用 3、工具调用 6、总/工具截止时间；新增可选保守费用预算；不开放审核和写库工具 |
| 数据导入 | 仍为操作员 CLI 和独立 importer 身份，没有新增 HTTP 导入接口 |
| 健康、内部文档 | API 只开放 health；Nginx 不对外代理 Actuator；本地 API docs 需 ADMIN，生产 overlay 关闭 docs |
| 请求及代理链 | JSON 请求体 32 KiB，含 chunked；无上传接口、禁用 multipart；Tomcat 连接/线程/等待与 JDBC 超时；仅窄可信网段的直接代理可提供私有规范化头 |

应用级公开查询上限为 100，植物目录更小（50）、图谱更小（40），实验台保持自身上限。通用 query/q 最长 120，cursor 2048，其他参数 512，总长度 4096。未知性状仍交给原领域规则，不强制映射。MyBatis 和主 JDBC 查询 5 秒，账户/审核 JDBC 8 秒，原图谱与实验台事务限制保留。慢查询超时后连接池能继续处理查询，已用真实隔离 MySQL 验证。

原始证据与许可状态没有因安全改造获得新的公开权限。公开 API 仍看不到待审候选。

## Turnstile 的实际边界

新增可复用 Vue 组件和固定 HTTPS Siteverify 客户端。后端独立检查 success、白名单 hostname、固定预期 action 和 challenge_ts（有效期 300 秒，允许有限时钟偏差）。Token 最大 2048 字符；并发验证 2；连接超时 2 秒、总截止时间默认 4 秒；响应体最大 16 KiB。Token 哈希占位缓存阻止并发重放，Cloudflare 提供跨实例单次兑换约束；验证失败、过期、重复或不可用均不会执行注册/认证后续步骤。记录不输出完整 Token 或 Secret。

本地默认未配置 Turnstile，页面明确说明。`compose.production.yaml` 强制启用 production 模式，并要求安全环境提供真实 site key、Secret 和精确域名；生产配置拒绝官方测试密钥、空配置和 localhost。没有重置密码或发邮件的接口，本轮未额外创建这些功能。

官方测试接口已真实调用：成功、失败、已使用三类 Secret 均得到对应响应，见 `official-test-siteverify.json`。本次观察到成功响应包含时间和 example.com hostname，但缺少 action；系统严格拒绝这类响应，没有为测试放宽规则。浏览器官方测试组件能显示成功，但提交后未创建账户。完整成功/失败/过期/重放/超时/并发流程用可控 Siteverify 协议响应与真实隔离 MySQL 测试，明确属于模拟供应商。

真实域名的正向注册验证仍需真实 Cloudflare 凭据。不能把官方测试组件的绿勾或 Mock 测试当作真实生产验证通过。实现依据：[服务端验证规范](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/)、[官方测试密钥](https://developers.cloudflare.com/turnstile/troubleshooting/testing/)。

## Nginx 与可信代理

五个独立区域：普通 API 20/s + burst 40；搜索/图谱 8/s + burst 24；登录注册 10/min + burst 8；Agent 提交 2/s + burst 8；管理写操作 10/min + burst 5。Agent 读取/轮询同时受普通 API 区限制，避免两位正常研究者的轮询争用提交限额。所有阈值可通过 `VIRIDEXA_NGINX_RATE_*` 调整。

先在独立 mock upstream 验证观测模式，然后验证拦截模式；每组固定 8 次请求，未对真实生产业务或第三方做压力测试。新区域默认已启用拦截。新服务器首次调参可设 `VIRIDEXA_NGINX_OBSERVE=on`，查看脱敏 access log 的 limit 状态后再关闭观测；已有认证限流始终拦截。429 有 JSON 提示和 Retry-After；恢复、静态资源豁免、请求体限制均已验证。

Nginx 覆盖客户端的 XFF 和私有 IP/协议头，清除 Forwarded/CF 转发遗留头，并原样保留含端口的 Host。Spring 不启用全局 ForwardedHeader 信任，只接受来自配置的窄直接代理 CIDR 的 `X-Viridexa-Client-IP` / `X-Viridexa-Proto`；生产只信任 Nginx 固定地址 /32。空配置直接使用 socket peer，0.0.0.0/0 等过宽信任会启动失败。

Cloudflare CDN 与 Tunnel 使用不同 include。CDN 更新脚本获取官方 IPv4/IPv6 列表，校验后原子生成候选文件，实际获取 22 段；不会自动应用、防火墙放行或重载。协议头同时受原 socket peer 白名单约束。Tunnel overlay 自动使用只信任固定 cloudflared 地址的独立 include。已本地验证伪造头被忽略、模拟可信边缘可还原地址与 HTTPS；真实公网代理链仍待核验。参考：[Cloudflare 原始访客 IP 说明](https://developers.cloudflare.com/support/troubleshooting/restoring-visitor-ips/restoring-original-visitor-ips/)。

安全响应头包含 CSP、nosniff、DENY、Referrer/Permissions Policy；HSTS 只在可信 HTTPS 状态下发出。日志不记录查询字符串、请求体、凭据或访客 IP 的公开统计。

## 测试与浏览器验收

| 测试 | 结果 |
|---|---|
| Maven / Spring / 安全单元 | 142 通过 |
| 隔离 MySQL 与 REST | 58 通过 |
| Vue / TypeScript 测试 | 37 通过 |
| Python 与部署策略 | 31 通过 |
| 科研管线回归 | 15 通过 |
| 总计 | 283 通过，0 失败，0 跳过 |

另有有界 Nginx 观测/拦截/恢复/可信代理模拟、非 root 只读 Nginx 容器启动、生产/Tunnel Compose 配置检查、Vue 类型检查与生产构建，以及当前本机 Compose 健康和 API 回归。上述脚本场景不混入自动化用例计数。

最终回归发现旧并发审核测试的第一次并行 CSRF 获取可能得到不同底层 Token，导致预期版本冲突误报 403。现在登录返回前会保存一个新 Token，CSRF bootstrap 在会话内同步；保留登录 Session ID 轮换和所有 CSRF 校验。回归要求并发审核仍为一次 200、一次 409，没有放宽审核权限或跳过失败。

浏览器实际测试正常登录无需挑战、连续错误后渐进挑战、测试凭据被严格拒绝、验证码资源不可用与恢复、登录后身份和退出，以及 375/768/1024/1440px 布局。修复了官方组件最小宽度造成的平板裁切；截图和布局数据在 `reports/phase7s/`。截图中的测试站点和技术身份均为隔离环境，未执行生产科学决定。

## 部署与未完成事项

本地生产数据仍为 5 条公开目录、0 条正式科学事实、0 个真实管理员；当前实际许可 UNKNOWN 来源版本为 18 个，未擅自修订许可；科研候选与来源状态保持原样。原始文件 SHA-256 292/292 匹配，见 `runtime-and-integrity.json`。独立隔离数据库及技术身份在测试后清理。

| 状态 | 范围 |
|---|---|
| 本地验证完成 | 安全服务、单实例限额、接口授权、失败关闭、Nginx 两种模式、代理模拟、非 root Nginx、Compose 基线、浏览器布局和恢复 |
| 需要真实凭据 | Turnstile 生产正向验证、真实域名/action；现有真实 LLM 仍未配置，不属于本轮真实联调成果 |
| 公网部署后验证 | TLS/HSTS/Secure Cookie、DNS、CDN/Tunnel 的真实 socket peer、访客 IP、同源 Host/CSRF、实际流量阈值 |
| 需要服务器管理员操作 | 云侧 ingress/防火墙、证书、域名与 Tunnel 授权、端口和子网冲突确认、Secret 注入；本轮未代为修改 |

单实例内存登录窗口重启会重置；Agent 文件配额可持久化但不适用于多副本共享。费用预算依赖管理员提供供应商价格，未配置时只有请求与 Token 预算，不能宣称已控制实际账单。客户端取消会停止请求与过期结果展示；已经进入数据库的工作仍以语句/事务超时收敛，不保证立即取消每个 SQL。

可选 cloudflared 镜像固定为官方 2026.10.3 发布对应版本；Docker Registry 获取 manifest 超时，未拉取或启动 Tunnel，因此其真实运行仍待验证。没有实施公网 DDoS 测试，也不保证可完全阻止 DDoS。

Ubuntu 操作说明见 [部署配置](PHASE7S_DEPLOYMENT.md) 和 [部署前清单](PHASE7S_UBUNTU_CHECKLIST.md)。没有进行完整依赖漏洞扫描或公网渗透认证；本报告只覆盖列出的实际检查。
