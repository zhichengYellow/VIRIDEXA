# VIRIDEXA · 真实课程目录发布补充验收

2026-10-26。用户在初次Phase7验收后明确要求：“真实发布你也一并解决，课设需要展示的主要是技术栈和美观性”。本报告更新当前生产状态；原Phase7报告保留为发布前快照。

## 当前可展示结果

当前本机生产应用 http://localhost:5173 已开放5条真实课程植物目录：苏铁、银杏、云南黄果冷杉、巴山冷杉、杉松。中文名与学名取自已有课程标题记录，来源均可追溯到真实PDF页码与SHA-256。首页一次读取最多6条，当前完整展示这5条。

|状态|实际数量|
|---|---:|
|公开植物目录|5|
|公开图谱节点|52（18来源、18文件、5植物、11分类单元）|
|公开图谱关系|42（18文件摘要关联、5目录身份引用、8来源分类父级、11分类来源关系）|
|正式科学事实 / 科学审核决定|0 / 0|
|真实生产账户|0|
|目录元数据发布事件|5，重复执行仍为5|
|来源许可UNKNOWN|18；未改成PERMITTED|
|保护文件匹配|292/292|
|真实模型调用|0；领域检索降级正常|

这次发布是**目录名称和来源元数据的展示授权**。原始全文、截图、外观观测、含油率与脂肪酸候选未发布。分类identity_state仍是原有VERIFIED_FROM_PRINTED_HEADING，没有改为VERIFIED；页面明确显示“来源记载 · 分类身份待复核”。不存在具名科学人工审核，不把部署自动化记成真人管理员。

## 实现与权限

V13 Flyway增量迁移将目录展示授权与分类科学核验分开，复用plant、taxon和review_event；未建立第二套植物模型。

- 既有具名管理员完整目录审核路径保持其身份、许可和审核条件。
- 新的本机受控部署命令记录CATALOG_METADATA_RELEASE，scope=CATALOG_METADATA_ONLY，绑定精确学名及实际文件SHA。actor_user_id为NULL，payload明确executor为AUTOMATED_DEPLOYMENT、human_scientific_review=false，并保存本次用户授权引用。没有创建假管理员或密码。
- 目录必须PUBLIC、REVIEW_PENDING、来源完整性VERIFIED，且最新目录事件为匹配当前学名/来源的元数据授权。后续撤回或证据变化立即阻断公开读取。
- Plant API和图谱共享graph_catalog可见性。实验台不再把所有可见目录硬编码成身份VERIFIED；Agent仍仅返回SOURCE_REPORTED/METADATA_ONLY记录。
- 未知许可不开放原文；来源许可表、科学候选与审核表未修改。公开数据库账号仍只读。没有新增匿名发布接口。

实际命令：

```sh
python3 scripts/pipeline/release_catalog_metadata.py \
  --plant-ids 1,2,3,4,5 \
  --authorization-reference user-instruction-2026-10-26-real-catalog-release \
  --report reports/phase7/release/plan.json
```

同样参数加`--apply`执行授权发布，实际应用报告为`applied.json`，重复执行报告为`replay.json`。本机命令需要既有受控数据库部署权限，不能由公开用户或模型调用。发布前有实际文件哈希检查；事务锁定植物，事件按授权/记录/来源幂等，保留撤回能力，不改科学审核状态。

## 生产浏览器实测

1. 首页中文输入“银杏”并搜索 → 返回真实植物2及课程PDF第12页。
2. 点击植物 → 详情显示实际学名、SOURCE_REPORTED身份、METADATA_ONLY发布、UNKNOWN许可与真实SHA。
3. 从详情打开图谱 → 植物与分类节点 → 来源分类父级与原文献元数据；来源显示PDF2/印刷页12，未提供受限全文。
4. 从图谱进入实验台 → plantId=2保留，五株目录可见；没有正式样本，因此测量不补零、不造图表。
5. 从实验台交给助手 → 服务端重新核对plantId=2 → 实际getPlantProfile与getGraphNeighbors两个工具 → 返回plant:2及pi:2和来源卡片。运行ID `0a6c3b73-0df6-4881-a50a-68ee89db63be`，无生成式模型参与。
6. 首页、详情、图谱、实验台、助手和技术看板在375/768/1024/1440px共24组布局检查，无横向溢出。完整截图和尺寸在`reports/phase7/release/`。

截图：`home-1440.png`、`home-375.png`、`plant-production.png`、`graph-production.png`、`source-production.png`、`workbench-production.png`、`agent-production.png`、`technology-1440.png`。截图使用生产真实数据，未使用测试夹具。

## 验证结果

Spring 106、隔离MySQL/REST 43、Vue 26、Python 26、Pipeline Python 15，共**216项通过，失败/跳过均0**。新增MySQL业务测试验证：元数据开放不提升身份或许可，不产生正式事实；SHA错误不可见；管理员撤回后公开查询404。Vue类型检查和生产构建通过，既有ECharts惰性包体积警告保留。Compose API/数据库健康、Nginx运行，容器JAR与最终打包SHA一致。

[机器可读验证](../reports/phase7/release/verification.json)确认：来源、候选、科学决定、独立样本、脂肪酸观测、证据关系内容指纹全部与发布前一致。变化仅为5条目录visibility和5条明确的元数据发布事件；审核账户、科学事实均未增加。隔离数据库、临时身份和进程已清理。未修改保护文件，未推送GitHub。

当前可直接用于本机课设演示。没有提供公网服务器与域名配置，因此未宣称已部署公网；真实LLM仍需服务器端Key。正式科研测量仍等待真实具名审核。
