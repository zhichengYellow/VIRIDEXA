# Phase 7 人工操作检查点（未执行生产审批）

> 后续状态更新：用户授权的5条课程植物目录元数据现已真实发布，科学事实仍为0。以下保留为最初验收/待办快照；当前发布与浏览器结果见 [目录发布补充报告](PHASE7_CATALOG_RELEASE.md)。

生产真实管理员、公开目录和科学事实目前均为0。以下是已准备的真实记录建议，不代表核验通过。

## 1. 创建本人管理员

在仓库根目录运行 `python3 scripts/pipeline/bootstrap_admin.py --email '<你的真实邮箱>' --display-name '<你的真实姓名>'`。使用密码管理器生成独立随机密码，在隐藏提示中输入；不要发到聊天或写入Git。随后登录 http://localhost:5173/review。

## 2. 来源许可与目录核对

先确认对应展示范围与来源使用依据，再在审核台“来源许可判断”中逐条记录许可。无法确认时保留UNKNOWN，目录发布按钮不会放行。再打开目录核对入口，实际检查学名、来源页与文件SHA；目录发布不批准性状或测量。

|植物ID|来源声明的学名|原始资料定位|核对入口|
|---|---|---|---|
|1|Cycas revoluta Thunb.|PDF 1 / 印刷页 11|[目录核对](http://localhost:5173/review?catalogId=1)|
|2|Ginkgo biloba L.|PDF 2 / 印刷页 12|[目录核对](http://localhost:5173/review?catalogId=2)|
|3|Abies ernestii var. salouenensis (Bordères et Gaussen) W. C. Cheng et L. K. Fu|PDF 3 / 印刷页 13|[目录核对](http://localhost:5173/review?catalogId=3)|
|4|Abies fargesii Franch.|PDF 4 / 印刷页 14|[目录核对](http://localhost:5173/review?catalogId=4)|
|5|Abies holophylla Maxim.|PDF 4 / 印刷页 14|[目录核对](http://localhost:5173/review?catalogId=5)|

完整哈希、来源版本、许可与候选ID见 `catalog-proposals.json`。规范化JSON候选与课程PDF是不同来源版本，清单分别保留，不把它们混作同一原始文件。

## 3. 科学候选：先复核与补证

- 六条历史外观：`review-queue.json` 中每条都列出原始值、规范化值、证据、现状态和 `approval_blockers`。目前全部缺少审核API要求的规范化植物身份字段；先核对目录身份并形成可追溯的规范化纠正版本，再进入批准步骤，不能直接给旧匿名确认补一个审核人。
- 88条含油率：课程样本/植物链与独立研究样本链分别展示。原始百分数和转录一致不等于物种或计量基础科学确认；`as-reported oil content` 不能当成干重/湿重基础。来源UNKNOWN时不能批准；未关联样本不自动归入植物。
- 脂肪酸首批候选：当前满足明确身份、关联样本、类型及非空单位/基础的数量为0。缺少身份的FAME、油脂组分和USDA营养记录继续分开。

没有真实具名审核发生，因此没有生产科学发布链报告；隔离技术夹具的发布/撤回不计入生产数量。

## 4. 配置真实模型（可选人工步骤）

在服务器安全环境或本地被Git忽略、权限600的 `.env` 配置 AGENT_MODEL_ENABLED、AGENT_API_KEY、AGENT_BASE_URL 和 AGENT_MODEL；Key不能进入Vue或聊天。执行 `docker compose up -d --force-recreate api`，再检查 `/api/v1/agent/meta`。具体兼容协议与5个受控用量验收案例见 `docs/PHASE7_MODEL_SETUP.md`。配置成功本身不等于联调通过。
