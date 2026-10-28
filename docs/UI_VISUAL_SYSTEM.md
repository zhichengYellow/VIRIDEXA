# VIRIDEXA UI-X 视觉与交互规范

2026-10-27 · Botanical Atlas × Modern SaaS × Scientific Workstation。

## 基础语言

统一 Token 在 `apps/web/src/styles/tokens.css`：象牙白 #F7F8F3、森林文字 #173B32、品牌绿 #307C60、浅绿 #D7EAE1、雾蓝 #E0ECF8、浅杏 #F1DACC。系统中文无衬线栈，避免额外字体请求。展示标题采用更大字距节奏，研究内容保持细密可读；等宽字体只用于记录编号和运行字段。圆角 8–18px 为主，图鉴 Hero 独立植物轮廓裁切。动效 180ms，尊重减少动画偏好。

图标来自自绘 UiIcon 单色 SVG 集合。颜色配合文字、线型、节点形状和状态标签表达，不以颜色独自表示审核状态。图片是另行核对许可的植物图鉴插图，不写入科研证据表，不改变源数据许可证。

## 页面构图

- 首页：非对称检索 Hero / 真实银杏图片、真实目录精选、关联式研究 Bento、来源带与编辑式页脚。
- `/explore`：URL 可恢复名称条件、桌面探索侧栏、按需 6 条分页、图鉴/列表、原生 dialog 快速预览。缺图明确留白，不将变种或栽培品种配成种级图像。
- 详情：大幅植物视觉、分区锚点、主阅读列及来源侧栏。不存在的分布/用途/栽培属性不补写。
- 图谱：可收起筛选、独立深色画布、悬浮感控制、节点/证据 Inspector、文本关系列表；手机抽屉有关闭、Escape 和 Tab 边界。ResizeObserver 在尺寸改变时更新画布，卸载断开并销毁实例。
- 实验台：对象与样本左轨、单位明确的图表/文本表格中央区、证据与可比性 Inspector；完整 JSON 来源链按需展开。原有 ECharts 按需加载、请求取消和实例释放保留。
- 助手：Field Notes 问题轨、研究结果、真实执行时间线、来源卡；明确领域检索降级和模型未配置。
- 审核：可折叠高密度候选表、组合条件、证据/原值/标准值、固定决策核对区。未提供第二套审批业务。
- 登录/注册：真实植物叙事图与表单双栏，公众入口、登录引导与管理权限说明分开。
- 技术展厅：技术 Hero、Vue→Nginx→Spring Boot→MyBatis→MySQL 请求链、三条管线真实聚合、可展开来源登记、功能演示、安全边界、带时间戳工程验收快照。

## 组件与样式所有权

新建/复用 AppShell、PageHeader、CommandSearch、BotanicalImage、BotanicalCard、UiIcon、StatusIndicator；保留 ResearchGuide、ResearchContextBar、StateNotice、CatalogReviewPanel 的业务语义。导航、研究上下文、引导、表单、空态、状态和授权图片共享同一系统。

`tokens.css` 为基础；`public.css` 为首页/图鉴；`account-system.css` 为账户及引导；`workspaces.css` 由四个研究路由按需引入；`plant-detail.css` 与 `technology.css` 属于各自延迟路由。旧 style.css / experience.css / account.css 已退出源码，原样保存在 reports/ui-x/retired-styles 以供回看。未继续叠加旧主题覆盖。

不安装 shadcn/Vben/Dify/Langfuse/Reka 整套依赖。所有服务端权限、CSRF、原始值、来源及科学审核状态沿用原实现。
