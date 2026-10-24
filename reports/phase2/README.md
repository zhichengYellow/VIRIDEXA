# Phase 2 最终证据索引

- `source-audit.json`：292 份保护清单文件全部哈希匹配。
- `delivery-dry/report.json`：最终来源扫描、标准化、隔离分类及 staging 校验值。
- `delivery-applied/report.json`：真实入库报告。
- `delivery-replayed/report.json`：完整重放六组内容与状态指纹一致。
- `quality.json`：实际数据库候选、样本、计量种类、许可、历史账本及 Flyway 版本统计。
- `source-scan-details.json`：WFO 分成员切片边界、USDA 完整扫描与范围排除。
- `e2e-examples.json`、`e2e-tests.txt`：三条真实文件到库及发布隔离示例。
- `python-tests.txt`：15 个基础测试。
- `real-source-tests.txt`：9 个真实来源/边界测试；`lipid-final-tests.txt` 是 lipid 最终变更后的 3 项复测。
- `package.txt`：最终 Java 构建及 11 个测试；`maven-tests.txt` 是较早构建日志。
- `mysql-tests.txt`：隔离 schema 内 V1—V7 迁移与 15 项检查。
- `container-build.txt`、`container-api-tests.txt`：最终容器构建健康状态和科学 API 检查。
- `complete-applied/failure.json`：实际长原因字段错误与已提交批次恢复信息，错误已由 V7 修复；不是跳过。

较早的 applied/final-applied/complete-dry 等是中间检查点，不代表最终总量。95,232 是候选/原始证据记录数，包含来源提取重叠；正式科学事实数为 0。WFO 是明确的范围切片。详细验收边界见 `docs/PHASE2_STATUS.md`。
