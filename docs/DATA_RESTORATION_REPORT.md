# VIRIDEXA · 项目内数据恢复报告

执行日期：2026-10-28（Asia/Shanghai）。本次为复制恢复，没有移动或删除外部备份，没有执行生产导入、清空数据库、改写科学内容、Git clean 或 GitHub 推送。

## 实际恢复数量

| 范围 | 文件数 | 字节数 | 校验 |
|---|---:|---:|---|
| 仓库外实际 data/ + database/ 全部文件 | 317 | 344,717,826 | 源与目标全部 SHA-256 一致 |
| 上述文件中的原始资产清单 | 292 | 222,484,424 | **292/292 匹配** |
| 上述文件中的额外提取/管线结果等 | 25 | 122,233,402 | 同样逐文件校验 |
| 已在本项目 .runtime/backups/ 的工具备份副本 | 6 | 9,327,554 | 全部一致 |
| 本次复制合计 | 323 | 354,045,380 | 无覆盖 |

原始清单副本：`reports/data-restoration/original-asset-manifest.md`。当前清单 `docs/ASSET_MANIFEST.md` 保留全部原始哈希，将目录位置改为项目相对路径；旧 `database/` 的12份档案映射到 `data/backups/legacy-database/`。没有改写档案 SQL 或当前 Flyway 文件。

首次恢复317份；再次执行恢复工具：**复制0份，已存在且一致317份**。不同内容的已有目标会在预检时拒绝，不会覆盖。复制工具拒绝符号链接及源目标目录嵌套，使用排他创建；有 dry-run，可重跑。外部源文件与恢复目标在最后再次逐个校验。新增的验证输出独立记录，不混入“复制文件数”。

本次课程 dry-run 在 `data/processed/` 新生成2个验证输出；它们是派生报告/SQL，未经执行。最终 data/ 内325份文件均被 Git 忽略，`git ls-files data database` 为0。未来新增数据和生成文件可能增加此数量，原始292份清单不会被这些可重建文件充数。

## 当前路径与分类

详见 [DATA_LAYOUT.md](DATA_LAYOUT.md)。现有来源逻辑位置 `data/...` 保持不变，避免破坏数据库证据定位、源 ID 和导入幂等性。运行默认根由外部目录改为项目根，数据目录默认 `./data`。容器只读挂载项目 data/，没有挂载整个项目或 .env。

今后 SP2000 → `data/raw/sp2000/`；iPlant → `data/raw/iplant/`；新采集图片 → `data/raw/images/`。三个目录已建立，本次不声称完成新的下载或名录导入。原有图片仍在 data/images/ 以保留历史映射。

历史数据库文件只作归档：4份 gzip 快照位于 data/backups/，原 database/ 原样分类于 legacy-database/；工具备份今后写入 managed/。外部备份和原工具备份均暂时保留。

## 路径修改清单

本次涉及以下 27 个文件（含新增工具、测试、文档）：

- `.env.example`
- `.gitignore`
- `README.md`
- `compose.yaml`
- `docs/ASSET_MANIFEST.md`
- `docs/CLEAN_SLATE_CHECKPOINT.md`
- `docs/DATA_LAYOUT.md`
- `docs/DATA_PROTECTION_REPORT.md`
- `docs/IMPORT_PIPELINE.md`
- `docs/VIRIDEXA_ARCHITECTURE.md`
- `scripts/build_asset_manifest.py`
- `scripts/dev-env.sh`
- `scripts/pipeline/governance_history.py`
- `scripts/pipeline/governance_test_env.py`
- `scripts/pipeline/import_course_extracts.py`
- `scripts/pipeline/import_snapshot_records.py`
- `scripts/pipeline/release_catalog_metadata.py`
- `scripts/pipeline/release_showcase.py`
- `scripts/pipeline/run_science.py`
- `scripts/pipeline/scientific.py`
- `scripts/restore_project_data.py`
- `scripts/security/check-local-runtime.py`
- `scripts/security/database-backup.py`
- `scripts/security/database-restore-test.py`
- `tests/test_restore_project_data.py`
- `tests/test_source_botany.py`
- `tests/test_source_lipid.py`

本地 `.env` 的数据目录已更新，既有密钥原样保留，文件权限为0600；未输出凭据。历史 reports/ 中的原始日志和恢复前清单保留当时的实际路径，不篡改为新的执行记录。恢复工具的备份来源参数仍指向外部副本；业务导入和运行配置已不依赖该副本。

## 真实管线与图片验证

新项目路径执行 `run_science.py --dry-run`，共 **95,232 条**：

- Taxonomy：1,545。
- Morphology：82,991。
- Lipid：10,696。
- 可规范化：94,799；隔离：433；来源版本17；提交事务批次0；新确认0。

这些是本次原始三管线重解析结果，不是新增入库数。生产库后续另有6条历史性状候选，故三管线结果95,232与当前候选95,238不是同一统计口径；本次没有删除或覆盖后续记录。

课程提取工具 dry-run 同样成功：33株植物、89个样本、125条性状、88条原图支持含油率记录和1条缺失值；没有把源资料状态当作本轮具名核验。输出位于 data/processed/ 的新运行目录。

图片链检查：**231张可解析图片、45/45裁图映射存在**；没有把受限原图复制进公开静态资源目录。**4/4 gzip 备份完整解压校验通过**，没有执行其 SQL。

## 测试与运行验收

本轮 **327/327 自动化测试通过，0失败、0跳过**：Spring151、隔离 MySQL/REST71、Vue43、Python43、管线15、备份控制4。新增5个恢复测试覆盖原始字节、归档路径、重复执行、冲突不覆盖、哈希失败、符号链接及 dry-run。

Vue 类型检查与生产构建通过；保留原有 ECharts >500KB 构建警告，未将它伪称为已消除。Compose API 与 MySQL healthy，Web运行并HTTP200；Web没有配置Docker healthcheck，因此不写为healthy。Nginx配置检查通过。API已重新创建以采用新只读挂载；当前MySQL容器与数据卷没有重建。

生产只读 API 回归 **20项通过**；公开目录 60,028，公开来源记录 94,798，正式科学事实 0。科学内容、样本、证据和审核决定指纹保持不变，用户与管理员未创建。隔离测试使用新路径校验真实文件，结束后删除隔离schema、临时数据库身份和测试进程，不影响生产。

## 留存和注意事项

`../VIRIDEXA-protected/` 原目录完整保留，本次没有修改其原始字节或删除任何文件。Git 忽略规则不会保护文件免受人为删除；不得执行危险清理。恢复后原文、图片、审核备注的公开访问规则保持不变。首次备份副本的未来清理应由用户另行确认。

证据目录：`reports/data-restoration/`，包括首次/重复复制统计、逐文件哈希、292项清单校验、图片及gzip验证、全部测试日志、三管线dry-run、Compose挂载与清理记录。
