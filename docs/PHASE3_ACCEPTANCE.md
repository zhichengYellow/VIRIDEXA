# VIRIDEXA Phase 3 · Knowledge Graph Explorer 验收

验收日期：2026-10-24。实现位于当前工作区，未推送 GitHub。真实具名科学审核本轮没有发生，正式科学事实仍为 **0**。技术测试中的批准与撤回只发生在隔离数据库。

## 可运行入口

- http://localhost:5173/knowledge-graph — Cytoscape.js 3.34.4 图谱。
- http://localhost:5173/technology — 技术与知识边界看板。
- 管理员先在 `/review` 使用真实管理员会话登录，再切换图谱管理员模式。正式库没有管理员，本轮没有创建冒充人工的账户。既有一次性初始化命令保留供真实管理员使用。

## 实时投影架构

Vue → Spring GraphController / AdminGraphController → GraphService → MySQL 实时视图 → 既有领域表与发布视图。不复制科学记录，不引入图数据库。V9–V12 新增图谱视图、虚拟访问键与索引；图谱没有独立事实存储。GraphContract 为未来图谱、Agent 只读工具和分析台提供统一节点、边、来源与状态结构。

节点包含 id、nodeType、displayLabel、verificationState、publicationState、sourceReference、attributes。边包含 id、source、target、relationType、displayLabel、verificationState、publicationState、sourceReference。来源引用包含真实来源版本 ID、SHA-256、许可和安全定位。元数据完整性、来源陈述、词表投影与科学审核分别表达；HASH_MATCH 不表示科学确认。

公开读取复用 `published_scientific_fact`。目录另需通过公开、审批、身份核验及来源条件。管理员读取由 Spring Security ADMIN 权限控制，使用已有会话；图谱只读，不另设审批逻辑。候选详情链接 Phase 2B 审核台。公开 API 不返回原始 payload、内部备注或服务器路径。UNKNOWN 许可只展示安全元数据，不提供原文、图片或文件下载。

## 当前真实数量

以下为正式库 V1–V12 查询结果，不是浏览器一次加载量。明细及原始响应见 `reports/phase3/runtime-counts-performance.json`。

| 范围 | 节点 | 边 | 正式科学事实 |
|---|---:|---:|---:|
| 公开图谱 | 36 | 18 | 0 |
| 管理员实时全量投影 | 495,727 | 508,765 | 0 |

公开仅有 18 个 SOURCE 来源版本、18 个 FILE 文件元数据，18 条 SOURCE_FILE 精确摘要对应。33 条植物均未获准公开，因此公开植物入口为 0；名称搜索没有结果不代表没有科研资料。

| 管理员节点类型 | 数量 |
|---|---:|
| SOURCE / FILE | 18 / 275 |
| PLANT / TAXON / NAME | 33 / 47 / 89 |
| SAMPLE / MEASUREMENT | 7,068 / 10,696 |
| MORPHOLOGY / TAXONOMY | 82,997 / 1,545 |
| TRAIT | 392,781 |
| COURSE_SAMPLE / COURSE_MEASUREMENT | 89 / 89 |

TRAIT 是每个观测中的词表条目上下文投影，不是 392,781 个独立科学事实，也不是全局物种性状结论。MEASUREMENT 包含待规范化的历史候选，不能据节点数声称有效测量覆盖。

| 关系 | 数量 | 依据与边界 |
|---|---:|---|
| SOURCE_FILE | 18 | 文件 SHA-256 相等，元数据对应 |
| IDENTITY_REFERENCE | 33 | 既有 plant.taxon_id 外键，目录待核验 |
| PARENT_TAXON | 127 | 43 个目录父级 + 84 个同版本原生分类 ID |
| ACCEPTED_TAXON | 536 | 同来源版本、命名空间及原生 ID 完整相等 |
| HAS_NAME | 89 | 75 个植物名称 + 14 个分类名称，来源转录状态不等于身份确认 |
| HAS_RESEARCH_RECORD | 89 | 课程账本明确 plant_id 外键，SOURCE_REPORTED / CANDIDATE |
| HAS_MEASUREMENT | 10,525 | 10,436 个独立样本测量 + 89 个课程测量 |
| HAS_TRAIT | 392,781 | 词表投影上下文，待审 |
| SOURCED_FROM | 102,531 | 直接来源、样本来源或课程证据外键 |
| SUPPORTED_BY | 2,036 | 既有 EXPLICIT_SOURCE_DOCUMENT 证据链 |

7,068 个独立科研样本仍 UNLINKED。课程的 89 条明确植物外键不会用于推断这 7,068 个样本的物种。423 个跨来源样本测量通过真实 study_key/sample_key 完整一致性建立关系，并非按名字猜测。18 个来源版本许可 UNKNOWN。候选总计 95,238，其中规范化待审 94,805、隔离原始行 433；它们与上述上下文节点不可相加计覆盖率。

## API 与性能边界

公开 `GET /api/v1/graph/meta`、`/search`、`/nodes/{id}`、`/nodes/{id}/neighbors`；管理员相同路径前缀 `/api/v1/admin/graph`。

搜索支持 q、limit、cursor；邻居支持 relationType、nodeType、verificationState、publicationState、limit、cursor、depth。只允许 depth=1、limit=1..40；单次最多 41 节点和 40 边。审核状态与发布状态独立筛选，发布状态仅有 METADATA_ONLY、CANDIDATE、PUBLISHED；HELD 是审核/规范化状态。游标绑定模式、节点、查询、筛选及分页大小，跨上下文拒绝。客户端最多 120 节点 / 200 边，搜索结果与画布分开，按需展开、折叠并保留共享分支。固定位置布局避免无界力导向运算；路由延迟加载，图谱包约 450 kB（gzip 144 kB）。

当前本机真实库单次计时：公开 meta 213 ms、默认搜索 605 ms、名称搜索 580 ms、来源一跳 844 ms。全量离线节点/关系聚合分别 9,307 / 9,838 ms；这不是浏览器加载方式，也不代表管理员所有查询均在一秒内。早期未优化查询曾超时，已通过同版本原生 ID 索引、按节点类型剪枝、定向性状边查询及发布门控修复。保留初始失败报告，不将失败删除后宣称首次成功。

## 验证结果

| 测试 | 通过 | 说明 |
|---|---:|---|
| Spring 常规测试 | 47/47 | graph 规则、权限及既有回归 |
| MySQL + 随机端口 REST 集成 | 21/21 | GraphMySqlIT 10 + GovernanceMySqlIT 11；全新 V1–V12 隔离 schema |
| Python 源文件/治理与核心测试 | 35/35 | 20 + 15，真实文件读取及导入回归 |
| Vue 图谱状态测试 | 7/7 | 上限、折叠、共享关系、循环、游标上下文、清空 |
| 合计 | **110/110** | 比 Phase 2B 的 81 项增加 29 项，无失败或跳过 |

另完成 Vue 类型检查和生产构建、Docker Compose 构建部署、真实浏览器检查。详细日志位于 `reports/phase3/`，浏览器过程与截图见 `BROWSER_INTERACTION.md`。REST 测试验证批准后可读、撤回后消失、来源一致、候选隔离、匿名与非管理员拒绝、孤立节点、循环、数量边界、故障回滚和课程样本链；这些技术夹具未进入正式库。

正式部署健康检查 UP，匿名管理员 API 401，limit=41 及 depth=2 为 400，不存在植物节点为 404。迁移 1–12 全部成功。公开使用 SELECT 身份，管理员图谱同样是只读服务；具备写权限的审核身份仍限于既有审核事务。

## 迁移恢复与数据保护

本机 Docker 磁盘紧张使初次 V11 在线 DDL 失败（1114），随后组合虚拟列 DDL 被 MySQL 拒绝（1846）。失败语句未改变科学行，未通过关闭约束或删除数据绕过。经过隔离 DDL 探测，将已有隐藏函数索引先移除，再逐项添加虚拟列与索引；受保护的 Flyway repair 工具只允许失败 V11 且目标列尚不存在时运行。最终 V11、V12 成功，并通过全新隔离库完整重建测试。日志与恢复前后指纹保留在 reports/phase3。

仅清理了可重建 Docker build cache；未清理数据库卷、受保护科研文件或其他应用数据。最终 292/292 原始文件 SHA-256 匹配；来源、候选内容、决定、样本、脂肪酸观测和文献证据六组有序内容指纹与迁移恢复前完全相同。正式库用户 0、决定 0、正式事实 0，不能将系统测试通过解释为科学审核通过。

## 限制与后续人工工作

公开植物和测量目前没有获准发布内容，因此产品如实展示来源网络与边界说明。管理员才能查看候选植物、形态和样本链；真实管理员仍需核验科学内容与来源许可。没有建立文本相似度物种关联，未知值保持未知。当前只支持有界一跳，未实现全图分析或 Research Agent 推理服务。技术看板明确 Agent 尚未上线。

最终隔离环境清理验证：测试 schema 0、临时 vg_* 数据库身份 0、私有测试环境文件已移除。最新包含发布状态筛选的 MySQL REST 回归仍为 21/21 通过。
