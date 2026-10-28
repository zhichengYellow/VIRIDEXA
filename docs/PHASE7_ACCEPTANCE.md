# VIRIDEXA Phase 7 验收报告

> 后续状态更新：用户授权的5条课程植物目录元数据现已真实发布，科学事实仍为0。以下保留为最初验收/待办快照；当前发布与浏览器结果见 [目录发布补充报告](PHASE7_CATALOG_RELEASE.md)。

日期：2026-10-26。工程接入与隔离闭环已完成；生产真实闭环停在真实管理员、来源许可、具名审核与模型Key检查点。

## 实际生产结果

|项目|本轮最终实际状态|
|---|---|
|生产管理员 / 用户|0 / 0，未冒充用户创建|
|课程植物 / 公开目录|33 / 0，已准备5条逐项核对建议|
|正式科学事实 / 科学审核决定|0 / 0，未发生真实具名科学审核|
|候选及隔离原始记录总数|95,238|
|规范化候选 / HELD原始记录|94,805 / 433；两者为同一记录集的互斥口径|
|独立研究样本 / 未关联|7,068 / 7,068；样本数不与候选行数相加|
|历史课程来源行|6,554，保留原记录与既有归类，本轮只读核对|
|来源版本 / 许可UNKNOWN|18 / 18，完整性均VERIFIED；哈希核验不证明许可|
|原始受保护文件|292/292 SHA-256与清单匹配|
|真实模型调用|0；无Key，五项真实供应商验收全部NOT_RUN|
|部署|本机Compose；未验收公网部署|

数据与证据见 [实时数据审计](../reports/phase7/data-audit.json)、[最终完整性](../reports/phase7/final-integrity.json)、[实际运行接口](../reports/phase7/final-runtime.json)。本轮与执行前的来源、候选、样本、测量、证据关系、审核决定、目录、账户与审计事件内容指纹一致；没有生产发布操作。

## 交付实现

- 安全一次性管理员CLI：隐藏输入、BCrypt、12–72 UTF-8字节限制，无默认密码、无匿名创建接口。命名锁与事务处理并发，同身份幂等，不同身份拒绝；随机临时过程及异常清理避免残留。实际MySQL子库验证后销毁。操作见 [管理员初始化](PHASE7_ADMIN_INIT.md)。
- 真实目录治理API与Vue审核面板：复用现有plant/taxon/evidence/source和review_event模型。授权ADMIN、实际账户复核、CSRF、精确学名与来源SHA确认、许可/完整性检查、文件实际哈希校验、行锁与事件版本、幂等UUID、事务回滚。目录发布与科学候选批准分离。无新增科学模型或数据库迁移。
- 目录API：`GET /api/v1/admin/science/catalog`、`GET /{id}`、`POST /{id}/decisions`，后两者以前述目录路径为前缀。RELEASE/WITHDRAW只改变目录状态并追加审计，不能批量批准科学观测。reviewer仅新增业务必需列UPDATE与事件INSERT权限，公开账号仍只读。
- 审核页支持目录ID与候选ID深链接，登录后可恢复候选；候选不在第一页也能读取真实证据。修复未加载证据时的提交入口与错误状态。目录操作内联确认并保留历史，移动端可用。
- 首页明确区分“目录已公开”与科学观测审核；缺少中文名时使用真实学名并说明未核对中文名。
- Agent复用六个只读领域工具与远程OpenAI兼容协议。保留3次模型、6次工具、并发2、总30秒、工具5秒及用量限制。模拟工具协议、注入、权限和失败降级测试通过；取消历史运行也重新检查科学发布状态，撤回声明不继续返回。
- `/technology` 展示实时目录/事实数、模型配置与真实联调之间的区别、人工操作及部署范围；空PLANT计数显示0。没有把降级编排称为真实模型规划。

## 首批人工审核准备与发现

[人工操作检查点](PHASE7_OPERATOR_CHECKLIST.md)列出5株真实课程植物、页码及目录入口；完整直接来源链与规范化候选链分别保存在 [目录建议](../reports/phase7/catalog-proposals.json)。所有来源许可仍UNKNOWN，尚不具备自动公开依据。

[逐条审核队列](../reports/phase7/review-queue.json)包含原值、标准值、样本、基础、真实定位、SHA、许可、历史、技术检查与推荐检查点。

- 六条历史外观的类型为植株类型2、生长环境1、叶形1、树皮纹理1、球果形状1。全部保留历史证据，但规范化payload缺少审核API要求的植物身份字段；除许可与人工核对外，仍需有来源依据的规范化纠正版本。没有改写旧payload或虚构具名人。这六条不能直接一键批准。
- 88条历史SOURCE_CHECKED含油率逐条匹配现候选、数值、文件与定位；这只是技术一致性，不代表科学确认。课程样本/植物链与独立scientific_sample链分开。后者仍UNLINKED；as-reported百分数缺乏确定干重/湿重基础，不宣称可直接比较。
- 脂肪酸满足明确身份、已关联样本、正确类型及单位/基础的首批候选为0。FAME、油脂组成与USDA营养记录继续隔离，未强行选择示例作为生产审核队列。

因此生产具名审核、真实目录搜索全链和正式科学发布链均未完成，等待本人操作；隔离技术发布不计入上述数量。

## 自动化与浏览器

|范围|通过|失败/跳过|
|---|---:|---|
|Spring默认测试与打包|106|0 / 0|
|隔离MySQL + REST|42|0 / 0|
|Vue交互纯逻辑测试|26|0 / 0|
|Python科研治理|26|0 / 0|
|Pipeline Python|15|0 / 0|
|合计|215|0 / 0|

相对Phase6的203项新增12项：管理员CLI 6、真实MySQL目录审核5、撤回后取消运行证据复查1。未用浏览器检查数或额外MySQL初始化检查重复计入215。Vue类型检查、生产构建通过；ECharts惰性块约506.93kB（gzip171.66kB）保留既有体积警告，本轮不声称性能提升。

命令与输出：`./apps/api/mvnw -f apps/api/pom.xml -s .mvn/settings.xml package` → `maven.log`；`python3 scripts/pipeline/run_workbench_integration.py` → `mysql.log`；`npm --prefix apps/web test/run typecheck/run build`及Python unittest对应日志位于 [Phase7报告目录](../reports/phase7)。隔离环境准备使用 `governance_test_env.py setup`、`seed --graph`，完成后cleanup。

初轮测试确实发现并修复：MySQL RR快照导致目录并发判断过期；ledger的FOR UPDATE超过最小权限，改为FOR SHARE而不扩大事件更新权限；非法ID异常应返回400；Agent取消返回绕过撤回过滤。失败日志保留为 `mysql-initial-failure.log` 与 `mysql-lock-privilege-failure.log`，最终42项全部通过，未跳过失败。

[浏览器详细报告与截图索引](../reports/phase7/BROWSER_INTERACTION.md)：24组页面/尺寸检查；隔离目录发布→搜索→详情→图谱→来源→撤回；科学候选技术发布→实验台→证据→图谱→Agent→撤回；生产空态和模型降级。真实工具事件含记录ID、顺序和耗时，模型调用为0。撤回后的历史GET与取消返回均清除声明，见 [撤回验证](../reports/phase7/agent-revocation.json)。

Compose API与MySQL健康，Nginx运行且真实API可读；容器JAR SHA与最终打包相同。Session轮换、HttpOnly、SameSite Strict、退出失效、CSRF与匿名401已验证。错误密码不回显、不进入应用日志。HTTPS部署必须另设SESSION_COOKIE_SECURE=true，本轮本机HTTP没有冒充HTTPS验收。

## 人工接续

1. 本人按管理员初始化文档在本机隐藏输入独立密码并登录；Codex未创建生产身份。
2. 核对来源许可依据、5条目录学名与证据，实际操作发布；UNKNOWN时保留阻断。
3. 完成身份/基础补证与规范化纠正，再逐条作出科学审核。不得以历史匿名状态直接批准。
4. 仅服务器端安全配置远程模型Key，按 [真实模型配置及五例验收](PHASE7_MODEL_SETUP.md)开展受控调用。当前 [真实模型报告](../reports/phase7/real-model-runs.json)全部NOT_RUN，Token与费用未知，而非0成本联调成功。

未推送GitHub，未修改保护文件，未删除生产审核历史。临时隔离数据库及身份已清理。真实管理员登录、真实目录发布、真实科学核验、真实供应商联调和公网部署仍未发生。
