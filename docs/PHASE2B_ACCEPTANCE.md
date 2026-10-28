# Phase 2B 审核与发布验收（2026-10-24）

本阶段实现已经落地到 Spring Boot、MySQL V8 和 Vue 工作台，并完成隔离环境的技术验收。用户明确选择本轮不参与科学审核。因此 **没有创建正式管理员，没有发生真实具名科学审核，没有正式发布科学事实**。测试账号和测试审批仅存在于可销毁的隔离数据库；其行为不能作为科学结论或许可结论。

## 当前正式库

| 指标 | Phase 2 执行前复核 | Phase 2B 完成 |
|---|---:|---:|
| 候选与隔离记录合计 | 95,232 | 95,238 |
| 规范化待审候选 | 94,799 | 94,805 |
| HELD 原始隔离记录 | 433 | 433 |
| 未关联研究/食品样本 | 7,068 | 7,068 |
| 来源版本 / 许可 UNKNOWN | 17 / 17 | 18 / 18 |
| 正式事实 | 0 | 0 |
| 科学审核决定 / 正式用户 | 0 / 0 | 0 / 0 |

增加的 6 条是从清单内课程快照恢复的历史性状候选；增加的来源版本是其快照本身。没有扩大 WFO 全球导入范围，也没有向正式库写入测试夹具。最终库证据为 `reports/phase2b/final-database.json`，保护文件校验为 `source-audit.json`，292/292 SHA-256 匹配。

分类候选 1,372、分类隔离 173；形态候选 82,997；脂质/食品候选 10,436、隔离 260。样本是独立实体，433 条隔离记录和 7,068 个未关联样本不能相加视为“失败记录”。同一研究样本跨 CSV/JSON 的 423 条观测使用规范样本身份，来源文件不同不代表样本不同；批准时核对 study_id、sample_id、kind、unit、basis 和数值，禁止错误串样本。

## 历史 88 + 6 的去向

88 条课程油脂行的旧 `oil_content_status=VERIFIED` 是字段核对状态，整行仍为 `NEEDS_REVIEW`。每条已通过原值、规范化数值和百分号单位精确匹配到含油率候选，关系为 `OIL_CONTENT_VALUE_EXACT`。另外 28 条同样本关联只标为 `SAME_SAMPLE_SOURCE_EVIDENCE`，不冒充含油率核验。88 条均没有正式科学审核决定。

6 条旧性状（229、230、231、232、233、235）有历史 CONFIRMED 事件，但 actor_user_id 与 historical_actor_ref 都为空，后续事件已经退回 `RECLASSIFY_PENDING_NAMED_REVIEW`。已恢复为 6 条可查询、可提交补证或审核的 MORPHOLOGY 候选，保留旧实体号、原字段、旧事件、源行键及快照哈希，明确为“待具名复核”。不从旧状态推断审核人员。

逐条报告：`reports/phase2b/history/oil_88.jsonl`、`trait_confirmed_6_history.jsonl`、`trait_pending_candidates.jsonl`。历史与新来源的关系共 359 条：精确含油率 88、同样本证据 28、精确来源键 231、6 条性状与旧记录/事件 12。它们是血缘关系，不是 359 条已确认科学事实。

## 6,554 条历史来源行

| 分类 | 数量 | 处理含义 |
|---|---:|---|
| 可建立规范化来源关系 | 152 | 依据精确来源键或原始实体证据建立关系 |
| 来源已知但事实无法关联 | 2,183 | 保留来源、原行与原因，不推断候选关系 |
| 来源信息不完整 | 0 | 当前行没有命中该规则；不代表科学证据完整 |
| 重复或历史冗余 | 0 | 没有在同来源/表内发现规则定义的重复原行哈希 |
| 无法识别或冲突 | 4,219 | 未命中保守映射规则，仍保留原内容和来源哈希 |

映射表有 6,554 条，原始 source_record 未被覆盖。无法映射没有被当作规范化成功。`governance_history.py` 默认 dry-run，`--apply` 仅插入不存在的确定性键，再核对内容与关系；冲突即失败，不覆盖人工结论。重跑后六组完整科学数据指纹一致，见 history/report.json。原因与处理类别保存在数据库，逐行输出见 history/mappings.jsonl。

## 分类与性状规范化

`lexical-vocabulary-v1` 完整投影 95,238 条候选。SOURCE_PRESENT 32,998、PARTIAL_UNKNOWN 61,807、UNMAPPABLE_SOURCE 433。投影状态不等于科学审核状态。

| 可复现字段 | 实际覆盖候选数 |
|---|---:|
| 来源与命名空间 | 94,805 |
| 接受名/异名原生 ID | 84,363 |
| 显式父级 | 85 |
| 科 / 属 | 71 / 62 |
| 来源提供的中文名 | 36 |
| 有性状字段 | 78,782 |
| 存在未知性状词 | 61,807 |
| 脂质测量基础 | 10,436 |

覆盖字段可以重叠，不可相加。科属仅用明确分类或可重现的父 ID 链；中文名状态为 SOURCE_REPORTED_PENDING，缺失保持缺失。支持叶形、叶缘、花色、生长型、果实类型的明确词表映射；未知词保留 source_term 与空 concept，不使用模糊近似。含油率、油脂组分、FAME 和食品营养仍为不同种类和计量基础。完整范围与重放指纹见 ontology-report.json 与 PHASE2B_ONTOLOGY.md。

## 实际审核和发布服务

`GET /api/v1/admin/science/candidates` 支持来源、物种文本、状态、样本、管线筛选与分页（size 1–100）；`GET /candidates/{id}/lineage` 返回候选原文、规范值、定位、来源版本、文献与审核历史。

`POST /api/v1/admin/science/candidates/{id}/decisions` 接受 action、expectedVersion、rationale、requestId。账户身份取自已认证会话，再核对启用的 ADMIN 数据库角色。批准、拒绝、退回补证与撤回均在独立写库事务中，版本 CAS、行锁与请求内容哈希处理冲突及重放。原始候选不被审核服务修改。拒绝后需先退回补证再重新批准；撤回保留原审批。

批准检查规范化状态、原始文件真实 SHA-256、定位、来源及关联原文献的完整性和许可、领域字段、独立样本、数值、单位与计量基础。缺失值不变成 0；实际数值 0 仍是有效数值。未关联物种的研究样本可以作为独立样本接受审核，但不强制变成某个植物的事实。

`POST /api/v1/admin/science/sources/{id}/license` 与科学审核分开，记录账户、理由、时间和版本；`GET /sources/{id}/history` 可追溯许可变化。原始 SHA-256 和路径不能由审核账号改写。

Vue `/review` 已接通真实会话、CSRF、分页筛选、证据详情、来源许可判断、审核提交、历史与成功/失败/冲突反馈。证据预览必须 ADMIN、PERMITTED、VERIFIED，再核验真实文件哈希；仅支持受限大小的 PDF/PNG/JPEG。未知许可不预览，不向匿名用户提供原文。

公开科学查询统一使用 published_scientific_fact：最新具名批准、规范化候选、直接及原文献来源 VERIFIED+PERMITTED。撤回、拒绝或许可纠正立即影响视图，历史仍在。公开返回来源版本、文件名、哈希、定位、决定 ID/时间，去掉原文、内部理由、审核人员标识和服务器路径。`/api/v1/science/identify` 仅使用正式事实与明确概念，区分 MATCHES、NO_MATCH、EVIDENCE_INSUFFICIENT；未知特征不是冲突。识别目前最多检查 1,000 条，并以 truncated 显式声明截断。图谱、Agent 和样本对比继续复用 ScientificService 的发布规则及 DTO。

## 认证和数据库权限

公开读取使用 viridexa_ro。审核服务单独使用 viridexa_reviewer：指定表 SELECT，审核/许可事件 INSERT，审核状态/版本和来源许可列 UPDATE，没有候选原文写权限或用户创建权限。Flyway 与导入仍各用独立身份，运行时不使用 root。54 个外键、29 个唯一约束及19 个 CHECK 保持启用（全 schema 计数）。

会话登录 `/api/v1/admin/session`、注销及审核写入均要求 CSRF，HTTP Basic 关闭；会话轮换、HttpOnly、SameSite=Strict、20 分钟空闲失效。当前服务仅绑定本机回环；部署公网需要配置 HTTPS 和 Secure Cookie。

首次初始化顺序：构建 API → Compose 启动并运行 Flyway → `python3 scripts/pipeline/provision_governance.py` → 重建/重启 api 读取独立服务凭证。服务密钥只保存于被忽略的本地 .env。人工管理员由本人运行一次性命令：

```sh
python3 scripts/pipeline/bootstrap_admin.py --email <本人邮箱> --display-name <本人姓名>
```

密码在终端交互输入，不放入聊天、源码或命令参数。本轮没有执行该初始化命令。初始化不批准科学事实或许可。

## 测试与真实浏览器

| 测试层 | 结果 | 环境 |
|---|---:|---|
| Java/Spring MVC、状态机、识别、隐私、文件完整性 | 35/35 | mock MVC/临时文件，含原11项 |
| MySQL + Spring RANDOM_PORT HTTP | 11/11 | 专属临时 schema，真实来源4条克隆，技术夹具账号 |
| Python 来源/本体/历史数据库读取 | 20/20 | 真实保护文件只读及正式库只读 |
| Python 基础解析/归一化 | 15/15 | 单元夹具 |
| Vue 类型检查、生产构建 | 通过 | Node 22.20.0 |
| 浏览器 | 7 个交互场景通过 | 生产构建 Vue + 隔离 API/MySQL |

共81个自动测试方法，较上阶段35个增加46个，无失败、无跳过。旧35项实际由11项Java与24项Python组成，不是35项Spring测试。浏览器场景不重复计入81。

MySQL 集成验证批准/重放/撤回、拒绝/补证、并发唯一赢家、数据库写故障整笔回滚、错误单位和 HELD、脂肪酸独立样本、串样本拒绝、许可撤回、隐私、CSRF/越权/分页及只读写入拒绝、受控 PDF 预览。数据库故障使用隔离 schema 的额外 CHECK 触发失败，不关闭现有完整性约束。

浏览器通过实际点击验证登录、许可判断、性状提交 APPROVED、撤回 REVOKED、HELD 的422、双窗口409和退出；审批理由均明确 ISOLATED TECHNICAL TEST ONLY。隔离公开查询曾读取1条技术批准记录，同期正式公开查询0条。证据见 browser-checks.json、browser-publication-check.json 及三张截图。绝不将这些账户称为真实科学审核人。

验收中发现并修复了登录缺少 CSRF、空注销响应解析、成功提示被详情刷新清除、MySQL repeatable-read 并发状态读取及长错误提示溢出问题。投影导入曾遇 Docker 磁盘已满，失败报告保留；仅清理可再生构建缓存后重跑，内容指纹一致。没有跳过失败测试宣称成功。

日志：java-build.txt、mysql-rest-integration.txt、python-source-tests.txt、python-core-tests.txt、vue-typecheck.txt、vue-build.txt、compose-build.txt、compose-runtime.txt。临时数据库、账号与测试容器在验收后删除，清理报告单独保存；正式库仍0用户/0决定/0事实。

## 剩余科学治理工作

需真实人员逐条处理科学审核和18个未知许可来源；4,219条未识别历史行与2,183条无精确事实关联行仍待研究；433条 HELD 和7,068个未关联样本保持独立语义。61,807条部分未知性状未强制映射。WFO当前仍是目标切片，未全量处理 reference/typematerial，也没有制造引用。测试通过只证明技术流程，不能代替物种、计量及科学内容核验。

本轮不推送 GitHub，不修改或清理受保护原始科研文件。
