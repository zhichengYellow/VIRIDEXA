# VIRIDEXA Phase 5 · Scientific Workbench

验收日期：2026-10-25。入口：[植序科研实验台](http://localhost:5173/workbench)。正式科学事实仍为 **0**，没有发生真实具名科学审核。本阶段交付的是科研样本探索、描述性图表、保守可比性检查和证据联动产品；技术夹具中的发布不代表人工科学核验。

## 实际实现

`/workbench` → WorkbenchController / AdminWorkbenchController → WorkbenchService → 既有 PlantService、ScientificService、GraphService 及现有 MySQL 样本/观测/发布视图的有界只读投影。

没有复制科研数据，也没有创建第二套样本或图表数据库。没有新增数据库服务或缓存。沿用 V1—V12 的主外键、发布视图和索引；样本观测通过既有 `ix_lipid_sample`、来源外键及主键读取，本轮不需要新增 Flyway 结构。查询事务只读、超时 10 秒；公开及候选探索均使用应用只读数据源，不使用 reviewer 写连接或 root。root 仅用于本地只读数据审计及显式隔离库的生命周期管理。

界面包括植物名称搜索、植物筛选、样本分页、来源/部位/类型/审核状态/未关联筛选、最多四个样本选择、测量逐页读取、最多 120 个工作区数据点、图表/列表切换、可比性结果和证据侧栏。管理员必须先在原审核台登录，再点击“查看管理员候选范围”。仅有 URL 中的 `scope=admin` 无法取得候选数据。

Apache ECharts 6.1 按需注册 BarChart、Grid、Tooltip、Aria 和 Canvas，路由懒加载；图表依照[官方模块化加载说明](https://echarts.apache.org/handbook/en/basics/import/)注册实际使用的组件，依照[官方可访问性说明](https://echarts.apache.org/handbook/en/best-practices/aria/)启用描述。相同 API 数据另提供键盘可访问的证据按钮与文本数据表。ResizeObserver 适配容器尺寸；卸载时释放图表。没有后台力导向或动画循环。

图表有三种展示：

- 样本指标并列：按指标、分析物、计量基础和单位分组，每条测量保留原样本 ID，不计算未获支持的差异。
- 单样本组分分布：仅包含 OIL_FA_COMPOSITION / FAME_COMPOSITION，再按样本、类型、基础、单位分别成组。不会将 USDA 食品营养记录混入种子油，也不会将不同样本拼接或归一到 100%。
- 数据列表：保留原始值、标准化值、状态及证据入口。显式 null 与省略的空数值都不会转成 0。

筛选、样本选择、证据读取与比较都有 AbortController 和请求序号检查。切换范围即时清空候选数组、比较、证据及画布；401/403 后清空候选范围。网络失败显示错误和重新读取入口。手机使用纵向分区，表格在局部容器滚动，长标识换行。

## 当前数据库与计数口径

| 实际登记 | 数量 | 使用边界 |
|---|---:|---|
| 正式科学事实 | 0 | 公开实验台不绘制任何确认测量 |
| 当前公开植物 / 可见测量样本 | 0 / 0 | 不能补造目录植物或使用夹具填充 |
| 已登记来源版本 | 18 | 全部完整性 VERIFIED、许可 UNKNOWN；只公开安全元数据 |
| 独立 scientific_sample | 7,068 | 全部 UNLINKED，identity_text 全 NULL，没有植物外键 |
| 独立脂类观测 | 10,436 | 待审候选，含油率 288、FAME 1,648、油脂组分 100、食品营养 8,400 |
| 课程 research_sample / measurement | 89 / 89 | 独立历史登记；88 SOURCE_CHECKED、1 MISSING，全部仍待新版具名复核 |
| 可供授权探索的样本登记 / 测量登记 | 7,157 / 10,525 | 两个账本相加，不代表已去重的生物学样本总量 |
| 全部科学候选 | 95,238 | 94,805 规范化候选及 433 HELD；实验台测量探索不等于展示全部形态候选 |

独立样本没有可证明的植物 FK，也没有明确的物种、部位和逐测量实验方法字段。1,947 条规范化记录保留 paper.method；实验台将其作为“论文层面方法备注”展示，不将论文组分分析方法套用于含油率，也不把它赋为逐样本确认方法。不会按 sample_key 或论文标题猜测身份。课程样本的 89 个植物 FK 与唯一研究的 FK 均完整；87 个部位已登记，2 个缺失，但全部课程测量的计量基础缺失。visual_cell_check / transcription / page_render 是转录方式，保留在定位元数据中，不作为实验方法。

来源覆盖的 count 是该来源直接承载的可见测量样本登记关联数，**不是**论文引用次数、完整原文覆盖或唯一生物样本数。来源版本中 0 个可见测量不代表文件不存在。课程账本还使用 `file:516` 等实际源资产入口，与 scientific_source_version 分开标识；界面区分“来源版本”和“当前范围来源条目”，不把文件入口计作新版本。

实查与原始 SQL 分组见 [PHASE5_DATA_AUDIT.md](PHASE5_DATA_AUDIT.md) 和 [data-audit.json](../reports/phase5/data-audit.json)。本轮没有导入、更新、合并或科学发布生产记录。

## 可比性规则与案例

ComparabilityEngine 是技术兼容性检查，不是科学推断。返回 DIRECT、CONVERTIBLE、SIDE_BY_SIDE 或 INSUFFICIENT，并逐项列出身份、部位、指标、分析物、基础、方法、来源完整性、许可/发布/审核和单位结果。记录不足时仍列出可检查的维度。

直接比较要求至少两个不同样本、已核验同一 taxon、相同部位、相同指标及分析物、明确相同基础、已知相同实验方法、有效数值、获准来源和正式发布。未提供分析物的组分不能视为同一种脂肪酸。单位严格保留大小写，`Mg/g` 不能按 `mg/g` 换算。只在明确质量分数基础上支持 `%`、`g/100g`、`g/kg`、`mg/g` 的精确转换；后两者乘 0.1 转百分比。未知基础、食品营养、混合 FAME/油脂类别不转换。

真实来源支持的隔离交互案例：

- `course-measurement:1`：课程样本 pdf19-p001-01，原值 10.65%、PDF 第 1 页/印刷第 11 页，SOURCE_REPORTED、LEGACY_REVIEW_REQUIRED。
- `candidate:00577bcb…2ece`：lauraceae-biodiesel-2014-sample-064，原值 35.70、标准值 35.7%、as-reported oil content，UNLINKED、PENDING。
- `candidate:000e7907…8821`：xiangxi-seed-oils-2018-sample-055 的 C14:0，0.25%、FAME GC content / %，UNLINKED、PENDING。

前两条不能直接计算差异：身份、部位、基础和方法等依据不足。FAME 例子仅显示该真实返回的组分，不能冒充整套组成或闭合到 100%。当前生产数据没有已获支持的 DIRECT / CONVERTIBLE 案例；这两类规则仅由隔离、自洽的质量分数技术夹具验证（例如 2.5 g/kg = 0.25%，相同明确干种子质量基础、部位与索氏重量法）。夹具不会进入正式库。

## API、权限与联动

公开前缀 `/api/v1/workbench`，管理员前缀 `/api/v1/admin/workbench`：

| 接口 | 行为与上限 |
|---|---|
| GET /overview | 当前范围聚合、真实来源条目与数据缺口 |
| GET /plants?q=&limit= | 植物检索，上限 20 |
| GET /plants/{id} | 复用植物/图谱身份和来源信息 |
| GET /samples | q、plantId、sourceId、kind、plantPart、status、unlinked；游标分页，上限 50 |
| GET /points | 1—4 个不同 sampleIds、kind、cursor；每页上限 60 |
| GET /points/{id}/evidence | 当前权限下的点、真实来源摘要与定位、来源链、原审核入口 |
| POST /compare | 2—12 个不同 pointIds，服务器重读当前状态，保留 CSRF |

公开测量必须通过既有 published_scientific_fact 视图，撤回或许可变化后立即从新查询退出。管理员端点同时经过 SecurityConfig 和方法权限检查。数值 ID 范围、SHA 标识、枚举、查询文本、分页上限及游标查询/范围绑定均由服务端验证，SQL 使用参数绑定。不存在或不公开的直接证据与上下文返回 404。私有路径、原始 payload、个人审核信息不出现在公开 DTO；UNKNOWN 许可不提供原文或截图预览。

图谱跳转携带 nodeId；管理员跳转先核验真实会话，再读取候选节点，不回退为未经授权的候选读取。课程来源链使用实际 file / evidence 关系，不伪造 scientific_candidate 关系。候选证据链复用 GraphService 的 SUPPORTED_BY 关系，返回真实关联原论文、页表定位和 SHA，最多 20 条并提示续查；不会停留在抽取 JSON 文件。

Agent 的原 POST /runs 增加可选 context：plantId、sampleIds、sourceId、kind、plantPart。前端显示该结构，后端重新验证全部记录的公开权限。即使管理员也不能通过此上下文将候选发给公开 Agent。搜索回退实际按已校验的样本/来源 ID 调用原有六工具，纯类型筛选也会查询正式测量；部位只保留为研究上下文，不补全数据中没有的部位。模型仍未配置，没有真实模型联调或费用。

## 自动测试与运行验证

| 分类 | 结果 | 环境 |
|---|---:|---|
| Spring 常规测试 | 105/105 | 原 85 + 可比性 13、工作台边界 5、Agent 上下文 2 |
| MySQL + REST | 37/37 | 原图谱 10、治理 11、Agent 7 + 工作台 9；独立 schema，临时最小权限账号 |
| Vue 状态/请求契约 | 22/22 | 原 10 + 上下文 2、实验台 10 |
| Python 科研回归 | 35/35 | 真实文件回归 20 + 管线核心 15 |
| 合计 | **199/199** | 相比 Phase 4 新增 41；没有失败或跳过 |

Vue 类型检查与生产构建通过。ECharts 工作台懒加载块约 512 kB（gzip 173 kB），构建仍有超过 500 kB 的体积提示，未关闭提示或阈值。Compose 的 API/MySQL 健康检查与 Nginx 页面验证通过。浏览器测试是实际桌面/手机交互，单独记录，不并入自动测试数量。

MySQL 首轮暴露严格 GROUP BY 查询及关联原文献许可夹具遗漏；均修复并重新执行。测试继续使用真实发布校验，不通过降级 SQL mode 或绕过审核消除失败。分页测试在隔离库额外放入明确命名的 65 条一次性技术记录，验证 60 条上限、续页、末页、首屏之外的证据定位；结束即清理。生产页没有这些夹具。

浏览器步骤、截图、网络恢复及权限失效结果见 [BROWSER_INTERACTION.md](../reports/phase5/BROWSER_INTERACTION.md)。可重现命令：

```sh
./apps/api/mvnw -f apps/api/pom.xml -s .mvn/settings.xml package
npm --prefix apps/web test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
python3 -m unittest discover -s tests
python3 -m unittest discover -s scripts/pipeline/tests -p 'test_*.py' -v
```

MySQL 验证先用 `governance_test_env.py setup`、`run_governance_test_app.py` 迁移临时库，`governance_test_env.py seed --graph --report-directory reports/phase5` 克隆有限真实来源例子，再运行 `python3 scripts/pipeline/run_workbench_integration.py`。该脚本拒绝生产 schema，运行四个集成测试类；浏览器验收后停止隔离进程并执行 `governance_test_env.py cleanup`。不要在正式库创建技术审核账户或运行夹具 SQL。

## 保留的限制

正式科学事实、可公开植物及测量样本仍为 0；需要真实人员完成身份、单位基础、证据和许可核验后才会出现正式图表。7,068 个独立样本的植物关系仍无法证明，课程记录尚需新版具名复核，来源许可均未知。当前有依据的直接转换仅限明示质量分数，不提供方法学推断、显著性检验、跨样本组分合成或自由归一化。

正式库没有管理员，候选交互在隔离账户下验收；不宣称发生人工科学审核。Research Agent 无真实 Key，只有公开领域检索回退。工作台资源块仍可优化，当前上限是每页 60、工作区 120 点，未执行大规模压力基准。

科研六组内容指纹、课程三表内容指纹与受保护文件 292/292 均匹配基线。没有修改保护资料、伪造正式事实或审核身份，也没有推送 GitHub。

补充复核：可比性基础识别已统一处理连字符和下划线，`as-reported oil content` 不会被判定为明确基础；新增对应回归测试。

最终重跑：105 项 Maven、37 项隔离 MySQL/REST、22 项 Vue、35 项 Python 全部通过。Docker 构建期间磁盘曾耗尽，清理805.9MB可重建构建缓存后，API恢复健康，受阻的隔离事务及测试正常完成。没有删除数据库卷。隔离测试库、临时数据库身份和测试Web容器已清理；部署JAR与本地JAR相同，正式科研指纹再次匹配。
