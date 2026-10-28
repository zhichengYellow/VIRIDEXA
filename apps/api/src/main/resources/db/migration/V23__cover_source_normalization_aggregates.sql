-- EXPLAIN ANALYZE on the 95,238-row target showed a non-covering index scan
-- fetching candidate rows for source/pipeline/state aggregates (~805ms in the
-- recorded local run). Extend the existing source index rather than add a
-- redundant source-prefix index. No scientific values or permissions change.
ALTER TABLE scientific_candidate
 DROP INDEX ix_science_source,
 ADD INDEX ix_science_source(source_id,pipeline,normalization_state),
 ALGORITHM=INPLACE, LOCK=NONE;
