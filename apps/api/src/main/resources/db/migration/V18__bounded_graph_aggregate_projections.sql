-- Narrow count projections over the same live relationships; no graph JSON spool.
CREATE VIEW graph_public_node_count AS SELECT node_type,SUM(n) AS record_count FROM (
SELECT 'SOURCE' AS node_type,COUNT(*) AS n FROM scientific_source_version GROUP BY 'SOURCE'
UNION ALL
SELECT 'FILE' AS node_type,COUNT(*) AS n FROM source_asset a WHERE EXISTS(SELECT 1 FROM scientific_source_version s WHERE s.sha256=a.sha256) GROUP BY 'FILE'
UNION ALL
SELECT 'PLANT' AS node_type,COUNT(*) AS n FROM graph_catalog p LEFT JOIN taxon t ON t.id=p.taxon_id WHERE p.is_public=1 GROUP BY 'PLANT'
UNION ALL
SELECT 'TAXON' AS node_type,COUNT(*) AS n FROM graph_taxon t WHERE t.is_public=1 GROUP BY 'TAXON'
UNION ALL
SELECT 'NAME' AS node_type,COUNT(*) AS n FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') GROUP BY 'NAME'
UNION ALL
SELECT 'NAME' AS node_type,COUNT(*) AS n FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') GROUP BY 'NAME'
UNION ALL
SELECT 'SAMPLE' AS node_type,COUNT(*) AS n FROM scientific_sample s WHERE s.id IN(SELECT o.sample_id FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id) GROUP BY 'SAMPLE'
UNION ALL
SELECT IF(c.pipeline='LIPID','MEASUREMENT',c.pipeline) AS node_type,COUNT(*) AS n FROM graph_candidate c WHERE c.id IN(SELECT id FROM public_source_record) GROUP BY IF(c.pipeline='LIPID','MEASUREMENT',c.pipeline)
UNION ALL
SELECT 'TRAIT' AS node_type,COUNT(*) AS n FROM graph_trait WHERE candidate_id IN(SELECT id FROM public_source_record) GROUP BY 'TRAIT'
) counted GROUP BY node_type;
CREATE VIEW graph_public_edge_count AS SELECT relation_type,SUM(n) AS record_count FROM (
SELECT 'SOURCE_FILE' AS relation_type,COUNT(*) AS n FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256 GROUP BY 'SOURCE_FILE'
UNION ALL
SELECT 'IDENTITY_REFERENCE' AS relation_type,COUNT(*) AS n FROM graph_catalog p WHERE p.taxon_id IS NOT NULL AND p.is_public=1 GROUP BY 'IDENTITY_REFERENCE'
UNION ALL
SELECT 'PARENT_TAXON' AS relation_type,COUNT(*) AS n FROM graph_taxon t JOIN graph_taxon parent ON parent.id=t.parent_id WHERE t.is_public=1 AND parent.is_public=1 GROUP BY 'PARENT_TAXON'
UNION ALL
SELECT 'HAS_NAME' AS relation_type,COUNT(*) AS n FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') GROUP BY 'HAS_NAME'
UNION ALL
SELECT 'HAS_NAME' AS relation_type,COUNT(*) AS n FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') GROUP BY 'HAS_NAME'
UNION ALL
SELECT 'SOURCED_FROM' AS relation_type,COUNT(*) AS n FROM graph_taxon t WHERE t.source_id IS NOT NULL AND t.is_public=1 GROUP BY 'SOURCED_FROM'
UNION ALL
SELECT 'SOURCED_FROM' AS relation_type,COUNT(*) AS n FROM graph_candidate c WHERE c.id IN(SELECT id FROM public_source_record) GROUP BY 'SOURCED_FROM'
UNION ALL
SELECT 'HAS_MEASUREMENT' AS relation_type,COUNT(*) AS n FROM scientific_lipid_observation o JOIN graph_candidate c ON c.id=o.candidate_id AND c.source_id=o.source_id JOIN scientific_sample sm ON sm.id=o.sample_id WHERE JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.study_id'))=sm.study_key AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id'))=sm.sample_key AND c.id IN(SELECT id FROM public_source_record) GROUP BY 'HAS_MEASUREMENT'
UNION ALL
SELECT 'SOURCED_FROM' AS relation_type,COUNT(*) AS n FROM scientific_sample s WHERE s.id IN(SELECT o.sample_id FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id) GROUP BY 'SOURCED_FROM'
UNION ALL
SELECT 'SUPPORTED_BY' AS relation_type,COUNT(*) AS n FROM scientific_evidence_link l JOIN graph_candidate c ON c.id=l.candidate_id WHERE c.id IN(SELECT id FROM public_source_record) GROUP BY 'SUPPORTED_BY'
UNION ALL
SELECT 'HAS_TRAIT' AS relation_type,COUNT(*) AS n FROM graph_trait WHERE candidate_id IN(SELECT id FROM public_source_record) GROUP BY 'HAS_TRAIT'
UNION ALL
SELECT 'ACCEPTED_TAXON' AS relation_type,COUNT(*) AS n FROM graph_candidate c JOIN scientific_candidate t FORCE INDEX (ix_graph_taxon_reference) ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND t.graph_namespace=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')),32) AND t.graph_native_taxon_id=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')),191) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin) WHERE c.pipeline='TAXONOMY' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id'))<>'' AND JSON_TYPE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))='STRING' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace'))='STRING' AND c.id IN(SELECT id FROM public_source_record) AND t.id IN(SELECT id FROM public_source_record) GROUP BY 'ACCEPTED_TAXON'
UNION ALL
SELECT 'PARENT_TAXON' AS relation_type,COUNT(*) AS n FROM graph_candidate c JOIN scientific_candidate t FORCE INDEX (ix_graph_taxon_reference) ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND t.graph_namespace=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')),32) AND t.graph_native_taxon_id=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')),191) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin) WHERE c.pipeline='TAXONOMY' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id'))<>'' AND JSON_TYPE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))='STRING' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace'))='STRING' AND c.id IN(SELECT id FROM public_source_record) AND t.id IN(SELECT id FROM public_source_record) GROUP BY 'PARENT_TAXON'
UNION ALL
SELECT 'SOURCE_NAME_RECORD' AS relation_type,COUNT(*) AS n FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id JOIN scientific_candidate c ON c.graph_scientific_name=LEFT(t.canonical_name,191) COLLATE utf8mb4_bin JOIN public_source_record f ON f.id=c.id WHERE p.is_public=1 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name'))=BINARY t.canonical_name GROUP BY 'SOURCE_NAME_RECORD'
) counted GROUP BY relation_type;
