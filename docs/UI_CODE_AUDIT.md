# UI-X 前端代码审计

范围：`apps/web/src` 的 Vue 页面与组件、路由、全局 CSS，以及 `apps/web/package.json`。仅做代码读取与结构审计；未读取研究原始资料或 `.ipynb`，未运行构建/测试。行号基于本次审计时工作区文件。

## 路由与页面骨架

路由集中在 [`router.ts`](../apps/web/src/router.ts#L5)：首页 `/`；登录和注册共用 `AuthView`；`/account`、`/workbench`、`/assistant` 标记 `requiresAuth`；审核、植物详情、知识图谱、技术页公开路由；最后是 404（L8–18）。守卫对受限路由调用账户刷新并保留 redirect（L23–28）。`App.vue` 是当前唯一全局壳：品牌/主导航/管理员审核入口/账户与引导按钮、`RouterView`、研究指南及页脚（[App.vue](../apps/web/src/App.vue#L14)）。导航是共享 UI，但当前写在 App 模板内；品牌 SVG 也内嵌在此处（L18–30）。

| 页面 | 页面职责及复用点 |
|---|---|
| `HomeView.vue` | 搜索公开植物目录、记录卡片、引导卡；复用 plants store 与 onboarding（[L1–12](../apps/web/src/views/HomeView.vue#L1)）。 |
| `AuthView.vue` | 登录/注册共用表单，另含安全配置、Turnstile 与回跳逻辑（[L1–21](../apps/web/src/views/AuthView.vue#L1)）。 |
| `AccountView.vue` | 账户状态、身份摘要、引导入口和功能链接；再次刷新 account store（[L1–7](../apps/web/src/views/AccountView.vue#L1)）。 |
| `ReviewView.vue` | 管理审核、会话与候选列表/详情；内嵌目录审核面板（[L1–22](../apps/web/src/views/ReviewView.vue#L1)）。 |
| `WorkbenchView.vue` | 样本/测量筛选、证据侧栏、比较结果和 ECharts；可切换管理员范围（[L1–22](../apps/web/src/views/WorkbenchView.vue#L1)）。 |
| `PlantDetailView.vue` | 植物属性、来源记录和图谱入口；并行读取 workbench API、来源 API 与植物详情 API（[L1–19](../apps/web/src/views/PlantDetailView.vue#L1)）。 |
| `AssistantView.vue` | 问答、运行追踪、证据卡片、研究上下文；复用 assistant API/state 与 `StateNotice`（[L1–15](../apps/web/src/views/AssistantView.vue#L1)）。 |
| `KnowledgeGraphView.vue` | 图谱查询/筛选/节点详情及 admin 模式；Cytoscape 画布（[L1–3](../apps/web/src/views/KnowledgeGraphView.vue#L1)）。 |
| `TechnologyView.vue` | Graph、Workbench、Assistant、安全及展示统计的服务状态汇总（[L1–10](../apps/web/src/views/TechnologyView.vue#L1)）。 |
| `NotFoundView.vue` | 未匹配路径的回退页；路由按需加载（路由 L18）。 |

组件已有清晰的局部复用边界：`ResearchGuide` 跨路由挂在 App，`GettingStartedCard` 供首页展示，`ResearchContextBar` 连接研究模块，`StateNotice` 统一状态提示，`TurnstileChallenge` 封装挑战生命周期，`CatalogReviewPanel` 隔离目录审核（见各组件同名文件）。`StateNotice` 的 tone→role 与样式变体定义见 [StateNotice.vue L1–4](../apps/web/src/components/StateNotice.vue#L1)。

## 样式级联与重复

`main.ts` 明确按 `style.css` → `experience.css` → `account.css` 顺序导入全局 CSS（[main.ts L5–7](../apps/web/src/main.ts#L5)）。三份表并非隔离模块：基础 tokens 和大量通用页面类在 `style.css`，品牌/体验覆盖集中于 `experience.css`，账户页及账户头部覆盖在 `account.css`；同一选择器会跨文件级联。

- `.site-header` 在 `style.css` L37、L66、L223、L266 有基线/媒体声明；`experience.css` L12、L61、L64 再覆盖，且 L61、L64 对同一移动断点重复定义；`account.css` L2–3 又补 tablet/mobile 导航和账户按钮规则。由于后载规则和媒体查询交错，最终移动布局依赖源码顺序，适合先集中 Header 规则再提取 AppShell。
- `:root` tokens 在 `style.css` L3–23 与 `experience.css` L2–7 重复定义，后者重设颜色、背景、圆角和 focus ring。需要区分基础变量与品牌主题变量，避免两个 token 来源漂移。
- `.hero-shell` / `.hero-inner` / `.hero-copy` 等首页选择器在 `style.css` L80–106 和 `experience.css` L21–33 重复；这是有意主题覆盖但边界不显式。建议把首页专属基础和装饰层留在单一页面样式文件，体验层只保留少量 token/主题规则。
- `style.css` L263 定义通用 `.state-notice` 和 `.module-links` / `.research-context-links`；`CatalogReviewPanel.vue` scoped 样式仍定义本地 `.state-notice--warning` 容器呈现（组件 L16），注意 scoped 与全局类可以叠加；后续抽 Inspector 时统一状态容器前先核对告警密度和语义。
- `style.css` L66、L223、L266 及 `experience.css` L61、L64 多个同断点 Header/mobile 规则；`account.css` L3、L8、L16 也有多段 `max-width:760px`。优先做去重与归属整理，不要同时改断点行为和组件结构。

## 图表、图谱与异步状态生命周期

- Workbench 是复杂度最高的图表生命周期：动态导入 ECharts、以 epoch/host/mode/group 校验异步渲染、使用 `ResizeObserver`，并在图表 host 变化和卸载时 dispose（[WorkbenchView.vue L12–21](../apps/web/src/views/WorkbenchView.vue#L12)、[L38–42](../apps/web/src/views/WorkbenchView.vue#L38)）。这部分适合保留在页面/专用图表组件内；不要把 ECharts 实例放入通用 Inspector。可提取纯展示型 `WorkbenchChart`，props 接收已经分组的 rows，事件只返回 point id；先保持页面负责数据与销毁边界。
- Knowledge Graph 的 Cytoscape 初始化、节点/边样式、事件绑定在 `onMounted`，卸载时 `destroy()`（[KnowledgeGraphView.vue L48–49](../apps/web/src/views/KnowledgeGraphView.vue#L48)）。图实例与 DOM 容器高度耦合，应作为独立 Canvas 组件候选，页面继续持有 API、权限切换及选中详情。
- 异步清理方式不完全统一：Assistant 在卸载时 abort 当前 controller（[AssistantView.vue L8–9](../apps/web/src/views/AssistantView.vue#L8)）；PlantDetail 按路由变化 abort 请求（[PlantDetailView.vue L12、L18–19](../apps/web/src/views/PlantDetailView.vue#L12)）；ResearchGuide 和 CatalogReviewPanel 有 controller/epoch 保护（各自 L13、L14 及组件 L8–14）。ReviewView 主要通过 selection epoch 防止过期选择回写（[ReviewView.vue L132–133](../apps/web/src/views/ReviewView.vue#L132)）。共享请求工具可统一 Accept/错误解析/取消约定，但身份、CSRF 与管理 API 不应被抹平。
- account store 在 App mounted 刷新，受限路由守卫刷新，AccountView mounted 再刷新（[App.vue L9–11](../apps/web/src/App.vue#L9)、[router.ts L23–28](../apps/web/src/router.ts#L23)、[AccountView.vue L7](../apps/web/src/views/AccountView.vue#L7)）。确认 `refresh()` 是否去重后再改调用边界；可避免重复请求，但应保留路由守卫拿到最新权限的保障。

## 适合复用的边界与安全重构顺序

1. **AppShell**：先抽 App 内的 Header / footer / RouterView 壳，保持路由守卫、标题与导航行为不变；Header 自身仍连接 account/onboarding。当前唯一入口证据为 [App.vue L14–53](../apps/web/src/App.vue#L14)。此步骤与 CSS 按页面归属整理可以分开提交，第一步仅搬模板以便回归。
2. **Inspector**：Workbench 的右侧证据详情（[WorkbenchView.vue 模板证据侧栏，L55附近](../apps/web/src/views/WorkbenchView.vue#L55)）、知识图谱的节点/边详情（`KnowledgeGraphView.vue` 模板中的 `.kg-detail`）和植物详情的来源/属性内容具有“当前对象 + 字段 + 来源/限制”共同模式。先复用通用面板外壳/标题/空状态插槽，不统一各自 API DTO 或审核动作。
3. **Status**：以现有 `StateNotice` 为状态壳基线（[StateNotice.vue L1–4](../apps/web/src/components/StateNotice.vue#L1)；全局视觉规则 [style.css L263](../apps/web/src/style.css#L263)），逐步替换页面各自的 loading/error/empty 呈现。先保留每页的角色语义、重试按钮与独有状态细节。
4. **Icon**：当前使用大量 Unicode 箭头、星号和搜索符号，品牌 mark 则是 App 内联 SVG（[App.vue L18–30](../apps/web/src/App.vue#L18)）。可先从 AppShell 的装饰/动作符号建立小型 `Icon` 映射或 SVG 片段；不要批量机械替换内容性符号或图表图元，也不要新增图标依赖（package 只有 Vue/Router/Pinia、Cytoscape、ECharts 和构建依赖，见 [package.json L16–30](../apps/web/package.json#L16)）。

边界建议：先做可逆的结构迁移，维持页面 API、store 和 route contract；页面样式迁移与组件提取分开；图表/Cytoscape 实例不跨页面共享；每轮只收敛一个 CSS 重复簇。`package.json` 提供 `typecheck`、`build` 和 Node test 脚本（L9–14），本审计未运行它们。

## 重构后复核（2026-10-27）

上文是修改前快照，旧行号与 CSS 路径不再代表现行实现；旧样式原文保留在 reports/ui-x/retired-styles。实际视觉审计另有 33 张修改前截图。

| 确认的问题 | 实施与验证 |
|---|---|
| 首页无真实授权植物视觉，品牌主次不足 | 真实银杏 Hero、三条真实记录精选、来源带、研究 Bento；有独立许可与图片归属 |
| 首页检索与产品叙事混在一个长页面 | 独立 `/explore` 图鉴，保留 URL 查询、真实分页、卡片/列表及快速预览 |
| 三份全局 CSS 互相覆盖，页头重复 | AppShell + tokens；三份旧 CSS 退出运行；工作区样式改为路由按需加载 |
| 研究页同质化 | 图谱三栏/手机抽屉、实验台对象-测量-证据、助手问题轨-结果-时间线、审核高密度队列 |
| 手机长分类状态及候选摘要撑宽 | min-width:0、明确换行边界、候选摘要缩写但完整 ID 留在 Inspector；39组最终几何检查无溢出 |
| 画布改变尺寸后图谱位置不适配 | ResizeObserver + resize/fit，断开与帧取消；手机/桌面切换关闭遗留抽屉，焦点和 Escape 验证 |
| 技术看板旧测试数不代表当前运行 | 真实 API 统计独立读取；构建公开脱敏 engineering-evidence.json，记录本轮304项及时间 |
| 重复新手欢迎卡无独立职责 | 未使用的 GettingStartedCard 移至报告归档；AppShell 持续保留统一 ResearchGuide |

自动化和真实浏览器结果见 reports/ui-x/test-summary.json、browser-report.json 与 index.html。本轮没有将纯契约测试称为完整组件或视觉测试。
