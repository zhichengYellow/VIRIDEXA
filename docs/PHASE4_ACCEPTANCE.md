# VIRIDEXA Phase 4 · Evidence-Grounded Research Agent

验收日期：2026-10-25。入口：[Research Agent](http://localhost:5173/assistant)。本轮未配置模型 API Key，**没有执行真实供应商模型联调，没有发生真实模型费用**。模拟协议测试、模拟规划器与真实数据服务的集成测试、正式库的传统检索降级分别记录，不能互相替代。

## 已交付的运行链

`/assistant` → Spring AgentController → 有界 AgentService → ResearchTools → 既有 PlantService / ScientificService / GraphService → MyBatis/MySQL 的公共发布规则。

配置模型后通过 OpenAI 风格 Chat Completions 工具协议驱动受控循环；模型负责选择工具和已有证据 ID，最终科学内容由服务器从真实工具结果构建。**自由生成的科学描述不直接进入回答**，未知引用被拒绝。当前未配置模型时使用明确标记的 SEARCH_FALLBACK，真实查询领域服务，不模拟模型调用。

接口：

- `GET /api/v1/agent/meta`：真实配置、公开植物目录/科学事实计数、工具和执行限制。
- `POST /api/v1/agent/runs`：`{question}`，返回 202 和任务快照。
- `GET /api/v1/agent/runs/{id}`：同一会话读取状态、证据及轨迹。
- `POST /api/v1/agent/runs/{id}/cancel`：实际取消服务端运行。

POST 保持 CSRF 检查。任务归属于服务器会话中的随机 owner，其他会话读取或取消返回 404。结果为 no-store。没有审批、写库、账户管理或任意 URL/SQL 工具。即便调用方是管理员，本阶段 Agent 也只使用公共视图；候选预览继续使用既有审核台与管理员图谱，不向模型发送私有审核资料。

## 六个工具与证据边界

| 工具 | 复用服务 | 输出与限制 |
|---|---|---|
| searchPlants | PlantService.search、ScientificService.plantSearch、GraphService.search | 名称/分类相关目录、已发布事实及公开来源，结果去重，上限 10；不存在的中文名保持缺失 |
| getPlantProfile | PlantService.getById | 公开目录身份字段；不把历史匿名批准的形态字段转换为新正式事实 |
| getGraphNeighbors | GraphService.neighbors | 一跳真实关系及来源，上限 10；包含真实关系 ID、端点与状态 |
| getResearchSamples | ScientificService.measurements | 仅已发布测量，保留 sampleId/studyId/kind/unit/basis；不以文本推测物种关联 |
| compareOilMeasurements | ScientificService.measurements | 2–4 个独立样本，要求每个样本有非空数值、相同分析物、种类、明确计量基础与百分比单位；上限每样本 10 行 |
| getEvidenceLineage | ScientificService.lineage/documents、GraphService.detail/neighbors | 原始来源版本、SHA-256、许可与安全定位；只生成真实可访问的 API 和图谱入口 |

比较明确区分 OIL_CONTENT、OIL_FA_COMPOSITION、FAME_COMPOSITION。FOOD_NUTRIENT 不进入植物油比较；`as-reported`、unknown、未说明等不明确基础不能仅因字段相同而判定可比。没有单位自动换算、跨样本合并或缺失值补零。

Evidence DTO 包含 id、label、category、verificationState、publicationState、fields、sources、href、graphNodeId。METADATA/CATALOG 与 PUBLISHED 分开呈现，来源完整性不等于科学核验。每项展示内容来自服务端工具输出，模型不能新增数值、物种关系、中文名、页码或审核结论。读取已完成运行时，会重新核对已发布科学事实及目录可见性；撤回的证据从历史回答中移除。

## Tool Trace 与前端

记录实际工具顺序、执行状态、耗时、结果数、记录 ID、超时/失败/重复阻止信息。轨迹可折叠，不包含隐藏推理、系统提示、原始模型全文、凭据或私有审核记录。界面提供三个示例、最近问题、当前页面内最多 8 次研究摘要、来源卡、图谱定位、取消、显式重试及移动布局。最近问题保存在本机浏览器，可清除；证据与对话摘要不持久化到浏览器存储。

模型使用配置来自环境变量，前端只显示是否配置及模型标识，不显示 Key。技术看板明确区分配置状态与真实联调状态，并展示工具、限制和实测结果。

## 资源、用量与部署

| 限制 | 当前默认值 / 硬边界 |
|---|---|
| 模型调用 | 每任务最多 3 次，可调低，不能超过 3 |
| 实际工具调用 | 每任务最多 6 次，可调低，不能超过 6 |
| 并行任务 | 2，可配置为 1 |
| 总截止时间 | 30 秒，可配置 5–90 秒 |
| 独立工具超时 | 5 秒，可配置 1–15 秒 |
| 单次模型超时 | 12 秒，可配置 1–30 秒，同时受总截止时间约束 |
| 输入/输出 | 问题最多 1500 字；上下文最多 24000 字符；请求/响应体有界；模型输出最多 1200 tokens |
| 每用户 / 全站每日请求 | 20 / 200 |
| 每用户 / 全站每日 token 预算 | 30000 / 300000 |
| 运行保留 | 完成任务约 10 分钟；内存最多 128 任务，超限拒绝新任务 |

预算采用独立运行卷 `/agent-runtime` 的原子写入账本，部署重启后仍保留每日限额（Asia/Shanghai 日界）。账本只含日期、哈希预算身份和计数，不持久化问题、科学原文或凭据；没有为 Agent 增加科研数据库写权限。匿名访问按连接地址保守共享预算，同一反向代理/NAT 后的匿名访问可能共享额度；登录用户按账户计数。当前面向单 API 实例，不可据此宣称已支持分布式配额。

模型调用前按输入字节和输出上限预占 token 预算，供应商返回有效实际 usage 时结算；缺少用量或失败时保守保留预占。未知用量不伪报为零。只有配置单位价格且实际用量完整时才返回 USD 估算，估算不是账单金额。模型不可用、429、无效工具参数、重复请求与上下文超限均安全降级。异步模型响应订阅有 128 KiB 上限，超时取消底层请求，避免响应头已返回但正文停滞占满工作线程。

Docker API 继续以非 root app 用户运行；保护资料以只读卷挂载。首次浏览器请求发现新预算卷归 root 导致 503，已修正专用卷所有者，在镜像预建 app 的 0700 目录，并增加启动时可写性探测。没有通过将 API 改为 root 或开放科学数据库写权限解决。

## 实际测试

| 分类 | 通过 | 环境 |
|---|---:|---|
| Spring 常规自动化测试 | 85/85 | 既有 47 + Agent 新增 38；包含模拟模型、真实本地 HTTP 协议 stub、状态/权限/超时/预算/取消 |
| MySQL + REST 集成 | 28/28 | 既有图谱 10、治理 11、Agent 7；受前缀保护的独立 schema 与临时数据库身份 |
| Python 源文件/核心回归 | 35/35 | 20 + 15，真实文件和历史治理回归 |
| Vue 状态测试 | 10/10 | 既有图谱 7 + 助手 3 |
| 合计 | **158/158** | 比 Phase 3 的 110 项新增 48，无失败或跳过 |

Vue 类型检查、生产构建、Docker Compose 构建部署及健康检查通过。日志位于 `reports/phase4/`。初次单元测试的空值夹具、集成测试的遗漏工具调用/文献许可夹具，以及实际 CSRF 错误分派问题都已修正并重跑，失败日志保留，不以跳过测试宣称成功。

集成测试中的模拟模型会调用真实 GraphService 和 ScientificService；测试也通过现有审核 API 在隔离库技术性发布、撤回一条真实来源支持的测量，验证同一个 Agent run 随发布状态更新。夹具审核账户明确标记非人工审核；没有进入正式库。这是技术验证，不是具名科学核验。

模型协议依据 [OpenAI 官方 Function Calling 文档](https://developers.openai.com/api/docs/guides/function-calling)：模型返回工具调用，由应用执行并提交 tool_call_id 对应结果。供应商差异仍需真实 Key 的实际联调验证；本轮没有宣称任意“兼容”供应商均已通过。

## 三个验收案例

**A · 可公开来源问题**：“公开来源及许可有哪些？”正式库实际返回 perilla_seed_oil.pdf 等来源，展示真实版本、SHA-256、UNKNOWN 许可及 SOURCE_FILE 关系，3 次工具调用完成。当前公开植物目录 0 条，不能为了展示 Agent 补造可公开植物档案。来源回答可用，具体植物资料仍取决于后续真实发布。

**B · 多工具研究问题**：“Jatropha 含油率与脂肪酸组成有哪些已发布记录可比较？”实际执行 searchPlants → getResearchSamples → searchPlants（来源目录）→ getGraphNeighbors → getEvidenceLineage，共 5 次工具。回答明确“没有获得足够的正式测量支持科研比较”，仅列来源元数据，不提供虚构含油率、组分或物种关联。

**C · 模型服务不可用**：正式未配置模型，助手明确 SEARCH_FALLBACK；传统植物 API 继续返回真实空目录，来源图谱仍为 36 节点 / 18 关系。点击证据卡能定位真实图谱节点。模拟 provider 429/超时/异常正文的降级由确定性自动化测试验证；没有真实 Key，因此不报告真实厂商故障联调通过。

桌面、移动端、取消与重试的实际交互见 `reports/phase4/BROWSER_INTERACTION.md` 及截图。并行测试期间曾出现一次 5 秒工具超时，轨迹如实展示；用户重试后来源查询成功。这是局部实测，不是所有负载下的延迟保证。

## 当前数据与未解决限制

正式库保持用户 0、科学审核决定 0、正式科学事实 0；候选 95,238，其中规范化待审 94,805、隔离 433；独立未关联科研样本 7,068；许可未知来源版本 18。科研内容六组有序 SHA 指纹未变，保护文件 292/292 匹配。未修改保护原始资料，未自动发布候选，未推送 GitHub。

没有真实模型 Key、实际供应商工具/结构化输出兼容性和真实成本尚未联调。当前回答采用服务器证据卡和固定说明，未开放自由生成的科研解释；多轮语义记忆不传入模型，每次提问独立规划。任务历史和轨迹保存在当前 API 进程内，重启后旧任务返回 404；每日预算独立持久化。管理员候选预览不属于本轮外部模型工具范围。全站配额仅适合单实例；匿名共享地址限额较保守。没有本地大模型、向量库或新图数据库。

## 验证与配置入口

- 常规后端：`./apps/api/mvnw -f apps/api/pom.xml -s .mvn/settings.xml package`。
- Vue：`npm --prefix apps/web test`、`npm --prefix apps/web run typecheck`、`npm --prefix apps/web run build`。需 Node 22。
- 科研回归：`python3 -m unittest discover -s tests -v` 与 `python3 -m unittest discover -s scripts/pipeline/tests -p 'test_*.py' -v`。
- 集成环境使用既有 `governance_test_env.py setup`、`run_governance_test_app.py` 完成全新 Flyway 迁移，再运行 `governance_test_env.py seed --graph`。显式运行 `python3 scripts/pipeline/run_agent_integration.py`；该入口拒绝正式 schema，仅运行 28 项隔离测试。停掉测试进程后 `governance_test_env.py cleanup` 清理临时库与账号。
- 模型配置见 `.env.example` 的 AGENT_*：开启 AGENT_MODEL_ENABLED 并设置 AGENT_API_KEY、AGENT_BASE_URL、AGENT_MODEL。不得把真实 Key 提交仓库。需要实际供应商联调后才能报告其兼容性、实际模型和费用。

最终复核见 `final-verification.json`：正式与本地 JAR SHA 相同；六组科研内容指纹未变；隔离 schema 0、临时账号 0、私有测试 env 已删除。新建独立预算卷也验证为非 root app 用户可写，见 `fresh-volume-permissions.txt`。
