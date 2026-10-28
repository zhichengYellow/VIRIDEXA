-- Aggregate metadata only. Nodes, edges, evidence and scientific decisions stay live.
-- Recomputed by the controlled release CLI, never by the public read-only account.
CREATE TABLE graph_statistics_snapshot (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 scope VARCHAR(12) NOT NULL,
 computed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 node_counts JSON NOT NULL,
 edge_counts JSON NOT NULL,
 validation JSON NOT NULL,
 CHECK(scope IN ('PUBLIC','ADMIN')),
 UNIQUE KEY uq_graph_snapshot(scope,source_fingerprint),
 INDEX ix_graph_snapshot_latest(scope,id)
) ENGINE=InnoDB;
