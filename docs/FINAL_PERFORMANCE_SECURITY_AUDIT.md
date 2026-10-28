# VIRIDEXA · Final Performance & Security Audit

验收日期：2026-10-27—28（Asia/Shanghai）。结论：已落实并验证本地优化和安全修复；**有条件部署，不能宣称公网验收全部完成**。没有已确认且未修复的 P0；镜像 CVE 扫描、部署磁盘容量、真实域名/Cloudflare/HTTPS 和真实模型预算仍有检查点。

## 1. 范围、数据与测量边界

从当前源码、V23 Flyway、构建和实际运行环境重新检查，不采用 Phase 6 的 203 项旧指标。当前自动化测试 **318 项通过、0 失败、0 跳过**（Spring 151、真实隔离 MySQL/REST 71、Vue 39、Python 工程 38、科学管线 15、备份约束回归 4）；生产另有 20 项只读 API 检查。各套件日志保留执行时间，318 是本轮去重套件合计，不包含反复执行同一测试或浏览器点击数。

生产只读核实：公开目录 60,028；候选 95,238；标准化候选 94,805；暂缓 433；公开来源记录 94,798；许可未知来源版本 18；正式科学事实、科学审核决定、生产账户、管理员均为 0。目录数不是独立且人工核验的物种数。公开来源记录不等于 HUMAN_VERIFIED。来源关系与科学内容指纹回归一致。

受保护目录只读挂载；292/292 文件 SHA-256 匹配，没有修改原始字节。新增 WebP 是独立授权的 UI 图鉴图片编码，与受保护科研资料无关。未推送 GitHub，未修改他人项目或真实服务器配置。

本地 Docker 虚拟磁盘只有约 0.5–0.65 GB 空闲（15 GB 总量，96–97% 使用），完整候选表约 631 MB，因此没有建立全量数据库副本或做生产压测。API 基线采用独立 `viridexa_governance_test_*` schema：1,201 条真实来源候选、1,002 条目录、385 个独立样本，并保留实际 FK、来源和状态；没有伪造正式事实。全量生产数据仅执行有界只读 EXPLAIN ANALYZE 和回归。禁止将小样本延迟当作全量生产 SLO。

## 2. API、JVM 与数据库连接基线

同一隔离数据、直接访问隔离 API 18082、并发 2、每路由预热 2 次、测量 40 次（每轮 360 次）。绕过 Nginx 测后端；静态资源及网关另测。单轮本地测量会受共享 MySQL、JIT、磁盘缓存及调度影响；40 次中的 p99 接近最大值，不能据此推导公网 p99。没有编造历史改善比例。

| 接口 | 前 p50 / p95 / p99 ms | 后 p50 / p95 / p99 ms | 前 / 后 req/s | 错误 |
|---|---:|---:|---:|---:|
| home-catalog | 52.535 / 72.914 / 100.323 | 48.236 / 56.776 / 58.128 | 35.449 / 40.212 | 0 / 0 |
| name-search | 62.823 / 70.583 / 72.082 | 72.244 / 108.86 / 119.458 | 31.449 / 26.194 | 0 / 0 |
| plant-detail | 19.338 / 25.521 / 25.833 | 13.123 / 22.127 / 24.462 | 101.012 / 137.399 | 0 / 0 |
| graph-neighbors | 128.402 / 194.6 / 203.081 | 119.962 / 139.756 / 156.247 | 14.666 / 16.582 | 0 / 0 |
| workbench-samples | 39.192 / 43.992 / 49.949 | 48.407 / 58.517 / 74.593 | 51.229 / 40.567 | 0 / 0 |
| evidence-lineage | 9.309 / 12.792 / 17.729 | 10.353 / 14.846 / 16.946 | 202.052 / 181.318 | 0 / 0 |
| technology-showcase | 83.14 / 93.679 / 97.858 | 23.791 / 28.323 / 31.345 | 24.367 / 83.259 | 0 / 0 |
| agent-meta | 46.534 / 57.43 / 59.565 | 45.246 / 65.363 / 73.066 | 42.443 / 41.029 | 0 / 0 |
| admin-candidates | 25.921 / 37.659 / 45.457 | 26.49 / 33.492 / 42.018 | 73.226 / 72.156 | 0 / 0 |

统计接口的热查询明显降低，其余接口有改善也有波动，没有把全部差异归因于代码。每轮错误率均 0/360。Agent 生命周期另测 3 次：前约 424–466 ms，后约 431–650 ms，均 DEGRADED、3 个真实业务工具、0 次模型调用；轮询间隔 200 ms 包含在生命周期内。无真实模型、Token 消耗或第三方费用数据。

JMX 只在隔离 JVM 通过本机 attach 启用，无远程 JMX 监听。前/后堆上限均 512 MiB，约 185/187 秒窗口内堆峰值 105,769,240 / 109,756,232 字节；GC 增量 3 次/117 ms 与 6 次/39 ms。MXBean processCpuLoad 峰值约 0.151 / 0.150，是 JVM 报告的归一化进程 CPU 比例。窗口含预热、空闲及后轮故障测试，不能直接当吞吐收益或泄漏证明。只读 Hikari 池共 6 连接：普通基线最多占用 2、等待 0；故障注入最多占用 6、等待 2，最后恢复 active=0 / idle=6 / waiting=0。其他写池未纳入此 JMX 对比。

同一隔离候选表 WRITE 锁 7 秒，最多 8 并发：8 次请求在约 5.0–5.3 秒返回脱敏 503；解锁后三次恢复 200，约 5–9 ms。没有锁生产表、重启共享数据库或修改全局参数。没有声称 JDBC 中断可以瞬时撤销正在服务端执行的 SQL。

证据：[API 前](../reports/final-hardening/baseline-api.json)、[API 后](../reports/final-hardening/after-api.json)、[JVM](../reports/final-hardening/jvm-summary.json)、[故障恢复](../reports/final-hardening/resource-recovery.json)、[既有 performance_schema 摘要](../reports/final-hardening/mysql-digests.tsv)。摘要为累计窗口，包含准备和两轮测试，不是单次 API 的独立 SQL 分位数。未在共享 MySQL 上打开全局慢日志。

## 3. 已确认问题和修复

| 等级 | 问题与证据 | 处理、代码位置与状态 |
|---|---|---|
| P1，已修复 | 来源/规范化统计扫描大候选行；重复请求重复计算相同公开聚合 | `V23__cover_source_normalization_aggregates.sql` 把既有 source_id 索引扩展为 `(source_id,pipeline,normalization_state)`，保留 FK 左前缀；`ShowcaseController` 聚合一次；`ShowcaseAggregateCache` 单条、5 秒、单航班缓存。生产 Flyway 已成功应用 V23（约 5.1 秒）。 |
| P1，已修复 | 新增备份脚本最初没识别 mysqldump 的组合式约束禁用行 | `database-backup.py` 仅移除已知 OLD_* 保存/禁用及恢复控制行，其他约束控制形式拒绝；不修改 INSERT 字面量。`database-restore-test.py` 同一连接在 DDL 前、DDL 后/数据前、数据后验证 FK/UNIQUE=1。旧演练报告明确标为 `superseded-invalid`；新演练重新通过。 |
| P1，已修复 | 375px 实验台证据 ID 和来源标签撑宽至 731px | `apps/web/src/styles/workspaces.css` 恢复 details 为 block、覆盖条目使用有界网格，长标识换行。实际复测页面宽度 375px，来源文字完整可读，没有隐藏数据。 |
| P2，已修复 | 静态 JS/CSS 未压缩、未区分版本资源和 HTML 的缓存策略 | `infra/nginx/web.conf.template` gzip level 5、Vite 哈希资源 immutable 一年；图片一小时并重验证；HTML no-store/重验证；API `proxy_cache off; gzip off`。ETag/304 实测。 |
| P2，已修复 | 三张展示 JPEG 总 817,089 字节 | 同尺寸 WebP 总 405,366 字节；署名、许可及视觉用途不变。原 JPEG 留在 `docs/assets/botanical-originals/`，移出公开构建。修正登录页 CSS 背景与 img 使用同一 WebP，避免重复下载两种编码。 |
| P2，已修复 | JVM、池和 Agent 业务运行情况缺乏受控读取入口 | Actuator 仅 health/metrics 暴露；metrics 由 Spring Security 限 ADMIN，Nginx `/actuator/` 404。新增 `OperationMetrics` 工具耗时与取消/超时计数，工具/状态标签用固定枚举，不记录问题、IP 或记录 ID。 |
| P1，部署配置已修复 | 未来 1 GiB MySQL 容器与默认 1 GiB temp-table RAM 不匹配；日志未统一滚动 | `compose.production.yaml` 限 max_connections=40、temp RAM=64M、mmap=128M、日志 10m×3；API 只读根文件系统、drop ALL、no-new-privileges、64m /tmp；Tunnel 日志同样限额。配置解析和独立只读 API 容器验证通过，未重配当前共享 MySQL 全局设置。 |

## 4. 高频 SQL 执行计划

完整 SQL、JSON 估算、实际迭代器计划保存在 [前计划](../reports/final-hardening/baseline-sql-plans.json) 与 [后计划](../reports/final-hardening/after-sql-plans.json)。每条设置 session MAX_EXECUTION_TIME=5000，不修改数据。docker exec 墙钟还包括 CLI/调度成本，不能与引擎实际时间混用。

| SQL | 真实计划与结果 |
|---|---|
| 规范化来源聚合 | 前：约 805 ms，总候选约 95k，经非覆盖索引访问大行；后：`Covering index scan ... ix_science_source`，扫描约 43.4 ms，完整分组约 93.6 ms。只是单次全量只读测量，不外推到所有查询。 |
| 名称检索 COUNT | 前全量约 2.4 s、后约 2.27 s；前导 `%query%` 及名称/科属 OR 仍需扫描，最新发布事件投影约 60k。未盲加无法服务前导通配符的普通索引。 |
| 目录详情 | 点查仍受到最新目录事件视图物化成本影响。尝试相关子查询可改善点查，却使组合检索变慢，已拒绝该重写，保留统一权限视图。 |
| 公开来源分组 | 约 2.8–3.3 s：扫描 94,798 可见来源，包含许可/审核状态检查。5 秒小缓存减少重复成本，但冷加载仍较慢；没有移除实时权限规则换速度。 |
| 样本测量分页 | 样本主键扫描及关联索引，实际取 10 条约数毫秒。不同样本、FAME/油脂/食品基础仍独立。 |

无重复新索引；V23 替换既有左前缀索引，不关闭外键、不变更候选语义或审核历史。深分页采用现有 afterId/游标，图谱深度固定 1，邻接页最多40条边/41节点；不把全部动态关系传给浏览器。没有引入新的数据存储或搜索架构。

全量生产只读回归另记录：植物邻居10边约4.046秒、形态候选邻居约4.969秒、来源邻居10边约6.992秒，图谱检索约1.660秒；这些是单次实际请求，不能与隔离小库的分位数混同。`GraphService.neighbors()` 对去重后的端点逐一 `detail()`，是有页上限的多次查询，仍可能重复计算权限视图。此热点尚未完成安全的批量投影优化，列为P1剩余性能问题；公开 meta 约13ms来自明确标注日期的发布批次快照，不代表动态邻居同样快。

## 5. 缓存与权限隔离

`/assets/` 仅服务构建哈希资源，`Cache-Control: public,max-age=31536000,immutable`；`/botanical/` 支持 ETag/Last-Modified，`max-age=3600,must-revalidate`；HTML 与工程验收 JSON 重验证。API 无共享缓存、无会话响应压缩，登录、候选、审核和 CSRF 不会被静态缓存规则覆盖。

服务端仅缓存公开计数/管线/来源条数，单条有界缓存，5 秒 TTL。每次重新读取来源版本/哈希/许可/完整性；以追加发布事件数量、review/decision/license 事件 ID 和来源版本作为版本键。失效或 SQL 错误不返回旧缓存；元数据实时读取，响应仍 `no-store`，附 aggregateAsOf 和统计口径。该缓存不保存候选详情、审核备注或会话。

测试覆盖 TTL、并发单次加载、版本变更、查询失败清空，及真实 MySQL 中撤回事件/来源许可改变后立即失效。植物、图谱、工作台、Agent 仍通过现有公共投影重新验证对象权限；既有 Agent 历史答案会重新过滤已撤回证据。直接 SQL 绕开事件账本的管理员手工修库不属于受支持发布流程，统计最迟 TTL 刷新；不以缓存决定对象授权。

[静态交付检查](../reports/final-hardening/static-delivery.json) 验证 gzip 解压内容一致、304、HTML 重验证、API no-store 与无 Content-Encoding。

## 6. 前端资源与浏览器性能

构建总字节、各文件 SHA/gzip 大小见 [前构建](../reports/final-hardening/baseline-build.json) / [后构建](../reports/final-hardening/after-build.json)。本轮初始 dist 2,135,602 字节，最终构建 1,724,823 字节（包含验收 JSON）；图片编码减少 411,723 字节。首屏核心 JS 仍约 78.76 kB，图谱约 456.82 kB、ECharts 约 506.93 kB，均为路由/页面级加载；ECharts 已采用 core + 所需组件，没有把图表库塞入首页。506.93 kB 警告仍保留，不为消除警告危险拆包。

首页冷缓存本地实验，每条件 3 次；桌面 1440×1000，移动布局 375×812、CPU 4 倍减速、150ms RTT、下行 200000 B/s、上行 93750 B/s。采用 Chrome 原生 tracing，非真实用户采样。

| 中位数 | 前 | 后 |
|---|---:|---:|
| 桌面 LCP | 343.48 ms | 761.38 ms |
| 移动 LCP | 2072.21 ms | 1990.65 ms |
| 桌面实验 INP 候选 | 75.71 ms | 86.59 ms |
| 移动实验 INP 候选 | 273.57 ms | 211.76 ms |
| 两端 CLS | 0 | 0 |
| 桌面交互观测窗口内传输字节 | 1,021,860 | 485,317 |
| 移动交互观测窗口内传输字节 | 525,825 | 264,332 |

INP 候选来自每页打开/关闭快捷搜索的 EventTiming，**不等于真实用户 p75 INP**；传输窗口结束于交互，不是严格相同整页加载边界。桌面 LCP/交互复测更慢，不能宣传全面性能提升，也不足以认定生产回归原因。当前仍小于一秒，但需部署后 RUM/多次稳定设备测量。不同窗口 JS 堆变化不能用作内存泄漏结论。

原始追踪与 [汇总](../reports/final-hardening/browser-performance-summary.json) 可复核。早期直接 `tab.screenshot()` 与模拟视口尺寸不一致的手机图片不作为布局验收；补测使用原生 viewport 与 `getScreenshot()`，正确截图带 `mobile`/`after-wrap` 标记并列入报告索引。

图谱展开/折叠、文本关系替代视图、工作台独立样本图表、证据打开、Agent 上下文、登录与管理员退回补证已浏览器操作。3 次图谱→Agent 路由切换，画布节点从 3 变 0，再进入维持 3；这证明 DOM 资源销毁，不代表已证明长期堆内存完全无泄漏。375/768/1024/1440 检查图谱和技术看板，无页面横向溢出；实验台长 ID 溢出已单独前后复测。

## 7. 接口攻击面、身份和数据安全

实际控制器注解清单为 [route-inventory.json](../reports/final-hardening/route-inventory.json)（60 条方法路径，另有配置式 Actuator/docs/静态入口）。以 `SecurityConfig` 和领域对象权限为准，不根据前端菜单决定权限。

| 类别 | 实际边界 |
|---|---|
| 公共只读 | plants/science/graph/showcase/security-meta/agent-meta；领域投影只放公开目录或授权来源记录，UNKNOWN 许可不放全文/截图，明确区分来源记载/机器提取/人工核验。 |
| 账户 | login/register/admin-session 匿名 POST 仍要求 CSRF；account 其余身份操作认证；普通成员不获 ADMIN。未实现邮件找回，不虚构对应路由。 |
| 高成本 | Agent run/start/cancel/read 需登录、按 owner 校验；工作台样本、点、证据、比较需登录，admin scope 在服务端验角色。 |
| 管理端 | `/api/v1/admin/**` ADMIN；审核事务/乐观锁/幂等/具名账户记录；受控证据预览还检查许可、哈希、真实路径、类型及 20MB 上限。 |
| 导入/初始化 | CLI、独立迁移/导入/账户/审核 DB 身份，没有匿名 HTTP 导入或创建管理员接口。公开查询 DB 账号只有 SELECT。 |
| 内部 | Actuator 仅 health/metrics，metrics ADMIN 且 Nginx 隐藏全部 `/actuator/`；生产 docs disabled，未授权仍先被 Security 拦截。 |

BOLA/IDOR 通过每个图谱/样本/证据 ID 和 Agent owner 的服务端检查；公开 JSON 白名单不含 raw_payload、reviewer_id、私有理由、密码哈希或绝对服务器路径。生产 20 路只读结果检查这些字段不泄漏。参数预编译，ID/分页/种类/单位由服务端校验，未知节点 404、越界深度/大小 400。Vue 文本转义，无 v-html；ECharts tooltip 进行 HTML 转义。模型端点由服务器配置、HTTPS、禁重定向，用户不控制 URL；来源和模型文本都不提升权限。没有把 PUBLIC 候选批量转为 VERIFIED。

## 8. 分级限流、可信代理和 Turnstile

Nginx：普通 API 20r/s + burst40；检索/图谱 8r/s + burst24；认证 10r/m + burst8；Agent POST 2r/s + burst8；管理员 POST 10r/m + burst5（确切规则以模板为准，参数可配置）。静态图片/JS/CSS 不受 API 限速。认证既有限流保持拦截，其余组可用 `VIRIDEXA_NGINX_OBSERVE=on` 观测。

隔离 mock 上游在每组 8 请求的有界验证中：观测时非认证组全部 200 且日志出现 REJECTED_DRY_RUN；拦截时各组出现 429、Retry-After=10，等待后恢复；静态连续 8 次不误伤，32KiB 以上 body 返回 413。测试故意伪造不同 XFF，不会变成不同可信来源。不能据此证明校园网大量共享 IP 无误伤，首次公网部署应先观测并按合法流量调参，保留用户级配额。

默认直连模式不信任任意 CF/XFF；Nginx 覆写转发头并保留含端口 Host。生产 API 仅信任指定 Nginx /32；CDN 官方 IP 更新脚本校验格式/拒绝 0.0.0.0/0，Tunnel 使用独立 cloudflared→Nginx 链路模板。受信/不受信头、原始 Host、同源登录和 CSRF 回归均通过。真实 CDN 源站 ACL、Tunnel 拓扑与公网地址仍待部署验证。

Turnstile 已有前后端组件、服务器 Siteverify、hostname/action/300s 有效期/重放/响应体与超时限制；正常登录渐进式挑战，注册启用后必须服务端验证。生产模式拒绝官方测试密钥和不完整配置，不记录 Token/Secret。

官方 dummy secret 的实际远程协议检查：pass 返回 success=true/hostname=example.com，但不带 action；fail 返回 invalid-input-response；spent 返回 timeout-or-duplicate。**dummy pass 缺 action 在本应用仍拒绝，未为测试放宽校验**。生产密钥/真实 widget 未配置，移动端真实 Cloudflare 验证尚未验收。其余有效/无效/过期/重复/直接绕前端/服务不可用由隔离 Mock/REST 测试验证，不能冒充生产联调。

[网关结果](../reports/final-hardening/nginx-bounded-checks.json)、[官方协议](../reports/final-hardening/turnstile-official-protocol.json)。官方依据：[测试密钥](https://developers.cloudflare.com/turnstile/troubleshooting/testing/)、[服务器校验](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/)。

## 9. Agent 资源治理及可观测性

保留六个只读工具，没有写入/审核/账户工具。默认并发2、最多3次模型/6次工具、总截止30秒、单工具5秒、单模型12秒、工具/结果数/输入大小上限；用户每日20请求、全站200，Token/费用预算使用持久配额账本。参数再次验证，重复工具请求受控；最终回答只选择真实工具证据 ID，不采纳模型自行编造的科研正文；撤回后重新检查回答来源。

取消/超时中断本地 future，未完成结果不采纳；单工具 JDBC 仍受驱动和 SQL timeout 约束。不能承诺撤销第三方已经发生的计费。目前真实 LLM 未配置；仅 Mock 协议测试和真实领域降级检索。若供应商实际用量高于预留量，本次结算会如实超过预估并阻止后续预约；这不是供应商硬账单上限，需同时配置供应商账户预算（P2）。

Actuator/Micrometer 提供 HTTP 次数/延迟/状态、JVM/GC/CPU、只读 Hikari 池；新增工具耗时及任务终止指标。管理员读取隔离容器 metrics 验证通过，未授权401/普通成员403；公网网关404，技术看板仅显示安全汇总和实际测试快照。磁盘、日志滚动和主机 CPU 由部署运维继续监测，不向公众展示完整内部 metrics、IP 或日志。

## 10. 安全响应头及依赖扫描

保留既有强制 CSP（self + 明确的 Cloudflare 域，禁 object、限制 base/form/frame-ancestors）、nosniff、DENY frame、Referrer-Policy、Permissions-Policy。没有为了 Report-Only 降低原有保护；更严格 style/nonce 策略尚未做公网 Report-Only 观察，作为后续项。ECharts/Cytoscape 使用动态 style，当前 style-src unsafe-inline 保留。HTTP 不发送 HSTS；可信 HTTPS 才一年、不启 preload。生产 Session Secure=true、HttpOnly、SameSite=Lax，CORS 仅指定 origin、无任意凭据跨域。

2026-10-27 实际依赖扫描：Maven compile/runtime 闭包93个坐标，OSV querybatch 93/93，0公告；npm完整锁文件（含 dev）audit critical/high/moderate/low均0，没有需要区分的已发现开发依赖漏洞。这个结果不证明不存在未知漏洞，也不是源代码可利用性证明。

**镜像扫描未完成**：Docker Scout 对 nginx:1.27-alpine、mysql:8.0.44、当时本地 API 镜像均因 LOGIN REQUIRED 失败；没有输出 CVE=0，也未擅自登录 Docker 或升级镜像。最终 API 镜像在本轮重新构建，正式上线前必须对最终 digest 重新认证扫描。OS 包和基础镜像漏洞状态仍 UNKNOWN（P1 验收缺口）。

[依赖报告](../reports/final-hardening/dependencies/summary.json) / [镜像失败证据](../reports/final-hardening/dependencies/images/summary.json)。凭据仅存在忽略的本地环境/运行目录；备份 SQL 不进 Git/公开静态目录。日志与报告仅保留必要脱敏信息。

## 11. 容器与备份恢复

生产 overlay 无 DB/API/web 宿主端口，数据库内部网络与 API 出网分离，专用数据库卷、只读科研挂载，无 privileged。API 非 root、70% MaxRAMPercentage、资源限制；网关非 root 只读并使用独立 tmpfs。配置检查包括 Tunnel 不能加入 DB/API 网络，preflight 检查 HTTPS/凭据存在/端口/权限/日志，不打印 Secret。

独立隔离 API 容器使用最终镜像、512MiB/1CPU、只读根、drop ALL、no-new-privileges；health200，私有/metrics/docs拒绝匿名；`/app` 写入拒绝，限定 tmpfs 可写。测试没有把未来生产 overlay 的 MySQL 1GiB 限制应用到当前共享实例，因此该容量仍需完整部署实测。当前本地 Compose DB/API 仅绑定127.0.0.1供开发验收，不等于已经部署公网隔离网络。

备份和恢复 CLI 默认 dry-run，备份0700目录/0600文件；SHA和精确行数；恢复只能新建随机 `viridexa_restore_test_*`，不接受生产目标覆盖；完成后只删除自己创建的测试库。原演练因禁用约束识别缺陷已失效。修正后的演练在干净4候选/1植物、40表15视图的独立库通过：完整规范 INSERT 数据 SHA 一致、生成列略过、同一恢复连接三个检查点均 FK=1/UNIQUE=1，目标库清理完成。循环FK/父子顺序不支持时拒绝恢复，不关闭约束硬导入。没有执行生产全量备份/全量恢复演练（空间不足），上线前必须安排独立磁盘并完成。

[只读容器](../reports/final-hardening/hardened-container.json)、[备份运行说明](../reports/final-hardening/backup-runbook.md)、[最新恢复](../reports/final-hardening/backup-restore-drill-20261028T093516-a2325301.json)。

## 12. 测试和浏览器验收

全部套件日志及 [去重测试汇总](../reports/final-hardening/test-summary.json) 可复核：没有跳过失败测试，没有把隔离发布记录计入生产。覆盖状态机/事务回滚/并发冲突、权限、CSRF、原始值、来源、样本隔离、单位、来源版本/重复导入、Agent超时/配额/取消/提示注入、分页/图谱深度、数据库故障和安全响应。

浏览器实际路径：隔离环境首页→Ginkgo检索→详情→图谱展开/折叠→登录→工作台；无对应样本时如实空态，切换真实样本后图表/证据→带上下文 Agent；Agent 真实降级结果及工具轨迹；管理员测试账户退回补证成功，版本0→1。仅技术测试，不是具名科学审核。首次 Agent 浏览器请求曾504，重试202/200成功；日志未证明首次到达应用，原因未确定，未抹去此限制。公开生产首页/技术看板及20项只读API实际核实，不创建生产账户或科学决定。

截图与响应式记录见 [可审阅索引](../reports/final-hardening/index.html)。移动真实 Turnstile未配置；完整长期负载/真实弱设备RUM/公网HTTPS没有测试通过声明。

## 13. 剩余风险与部署前人为配置

| 等级 | 未完成/边界 | 部署前操作 |
|---|---|---|
| P1 | 最终镜像 CVE UNKNOWN | 由有权限人员登录扫描器或提供获准扫描工具，扫描最终镜像 digest，处理可利用高危问题。 |
| P1 | 本地 Docker 剩余约0.5GB；未做全量恢复/目标MySQL资源限额测试 | 为专用数据库、日志、临时文件及备份配置足够磁盘；不要全局 prune 他人镜像/卷。新磁盘完成全量备份恢复和资源受限回归。 |
| P1 | 域名、TLS、Cloudflare 生产验证和源站防绕过未完成 | 选择 CDN 或 Tunnel 一种实际拓扑；配置生产 site/secret/hostname、Secure Cookie、origin、可信peer、源站ACL；浏览器验证 CSRF/真实IP/HSTS/widget。服务器操作由服务器管理员授权执行。 |
| P1 | 生产管理员0 | 用户用既有受控一次性初始化输入私有密码，验证真实账户登录；不创建默认或公开凭据。 |
| P1 | 全量动态图谱邻居单次约4–7秒，端点详情仍多次查询 | 在保持每个端点权限校验的前提下继续 profile/批量投影；未宣称已达生产延迟目标，不能用小库128ms掩盖。 |
| P2 | 全量通配检索及公开统计冷加载仍较慢 | 上线后监测慢查询/合法流量，再考虑匹配语义或更窄投影；目前不改写统一权限规则。 |
| P2 | 桌面 LCP复测变慢，移动实验INP候选仍约212ms | 稳定设备增加样本/RUM，分离调度、解码和渲染原因；不宣称整体提升。 |
| P2 | 供应商实际Token/费用可能超过本次预留 | 配置供应商账户硬预算、低额度真实联调；未知费用保守记账，取消不保证免计费。 |
| P2 | 登录风险表最多10000项，容量满暂拒请求 | 有界内存且有限窗口，公网监控容量和误伤；不为单实例增加Redis。 |
| P2 | 注册重复邮箱返回409，可用于低速账号枚举 | 登录仍统一失败信息；注册受挑战及限流。若需更强账户隐私，设计邮箱验证和统一受理流程；本轮不冒称已实现。 |
| P2 | CSP更严格策略/共享NAT限流经验不足 | 保留现有CSP；先Report-Only观察候选策略，校园网真实合法流量用观测模式调参。 |

正式科研事实仍为0，真实模型调用0；这些是已知真实状态，不作为性能/安全失败或虚构非零成果。不得把技术测试成功等同于科学事实经人工审核。

## 14. 不建议的过度设计

不增加 Redis、Elasticsearch、Neo4j、向量库、Kubernetes 或微服务；不为前导通配符盲加普通索引；不缓存私有候选/会话；不以关闭权限/CSRF/FK/日志换跑分；不为了500kB警告拆坏图表交互；不在共享服务器做压力攻击或改全局防火墙。保留当前视觉系统和科研语义，优先监测已证明的热点。

验收清理：本轮隔离应用、临时 Nginx、只读测试容器已停止，隔离库及其4个临时数据库身份已删除，临时凭据文件已移除。私有备份留在忽略目录；本地生产 Compose 保持运行。见 `reports/final-hardening/cleanup.json`。
