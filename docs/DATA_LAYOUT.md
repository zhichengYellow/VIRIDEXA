# 项目内数据目录约定

自 2026-10-28 起，VIRIDEXA 的运行数据集中在项目内 `data/`，整个目录不进入 Git。操作应从项目根目录执行。

```text
data/
  raw/
    course/                 原始课程 PDF
    wfo/                    WFO 原始归档
    nhm_traits/             NHM / Flora of China 原始归档
    fatty_acids/            脂质论文、USDA 原始资料
    sp2000/                 今后下载的 SP2000 中文名录
    iplant/                 今后下载的 iPlant 资料
    images/                 今后采集的原始植物图片与许可登记
  external/gbif/            已有 GBIF 采集结果（保留历史定位）
  images/                   已有图片、裁图及原始映射（保留历史定位）
  pdf_extract/              已有 PDF 提取结果
  source_pages/             原始页图
  ocr/                      OCR 结果
  processed/                可重建的管线输出
  backups/
    *.sql.gz                已有历史快照
    legacy-database/        原 database/ 的 SQL 与历史迁移档案
    managed/                备份工具产生的私有备份及其校验清单
```

为保持证据定位和导入幂等性，没有任意搬动已登记的 JSON、CSV、图片或归档文件；这些文件可能仍直接位于 `data/`。新采集任务必须把原始资料保存到 `data/raw/` 对应来源目录，并登记原始 URL、版本、SHA-256 与许可。空目录仅表示已准备存放位置，不表示 SP2000 / iPlant 已下载或导入。

## 路径与权限

- 本机数据目录配置：`.env` 中 `VIRIDEXA_DATA_DIR=./data`。
- 需要证据校验的本机 Spring 进程使用 `VIRIDEXA_PROTECTED_ROOT=.`，即包含 `data/` 的项目根目录；保留该环境变量名称以复用现有领域服务。
- Compose 仅将 `./data` 挂载到 `/science-inputs/data:ro`，容器内逻辑根仍为 `/science-inputs`。这样已保存的 `data/raw/...` 定位无需改写数据库。源码、Git 和 `.env` 不被挂载。
- `data/backups/legacy-database/` 不是 Flyway 输入。当前迁移仍只位于 `apps/api/src/main/resources/db/migration/`；不能把历史 SQL 自动导入当前 MySQL。
- 来源图片的公开权限不因恢复目录改变。未知许可的原图、论文全文和页图继续受原有权限控制；Nginx 没有新增 `data/` 静态目录映射。

## 备份与 Git

外部恢复来源仍保留；`.runtime/backups/` 中旧工具备份也暂时保留副本。今后备份脚本统一写入 `data/backups/managed/`，恢复演练仅针对隔离 schema。

`.gitignore` 忽略整个 `data/`，包括原始资料、论文、未知再分发许可图片、SP2000 / iPlant 下载、SQL 备份和提取结果；批量候选 JSONL 也被忽略。必要的公开示例和测试夹具应放在明确的代码测试目录，并单独审核许可。不要使用强制添加绕过忽略规则。

**禁止对这些目录执行危险的 `git clean` 或数据库卷清空。Git 忽略规则不是文件备份保护机制。** 本次复制不删除仓库外备份；删除旧备份必须由用户另行明确决定。
