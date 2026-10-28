-- Visibility is independent of named scientific review. Default: no source release.
CREATE TABLE source_record_access (
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 visibility VARCHAR(16) NOT NULL,
 knowledge_confidence VARCHAR(24) NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 authorization_reference VARCHAR(191) NOT NULL,
 display_basis VARCHAR(191) NOT NULL,
 released_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(candidate_id) REFERENCES scientific_candidate(id),
 CHECK(visibility IN ('PUBLIC','RESTRICTED','INTERNAL')),
 CHECK(knowledge_confidence IN ('SOURCE_REPORTED','MACHINE_EXTRACTED','UNRESOLVED')),
 INDEX ix_source_access_visibility(visibility,candidate_id)
) ENGINE=InnoDB;
CREATE TABLE source_release_event (
 event_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 visibility VARCHAR(16) NOT NULL,
 authorization_reference VARCHAR(191) NOT NULL,
 payload JSON NOT NULL,
 happened_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(candidate_id) REFERENCES scientific_candidate(id),
 CHECK(visibility IN ('PUBLIC','RESTRICTED','INTERNAL'))
) ENGINE=InnoDB;
CREATE VIEW public_source_record AS
 SELECT c.id,c.source_id,c.pipeline,c.locator,c.normalized_payload,c.content_sha256,c.normalization_state,c.reason,
 f.decision_id,f.reviewer_id,'HUMAN_VERIFIED' AS knowledge_confidence,'PUBLIC' AS visibility
 FROM scientific_candidate c JOIN published_scientific_fact f ON f.id=c.id
 WHERE NOT EXISTS(SELECT 1 FROM source_record_access a WHERE a.candidate_id=c.id AND a.visibility<>'PUBLIC')
 UNION ALL
 SELECT c.id,c.source_id,c.pipeline,c.locator,c.normalized_payload,c.content_sha256,c.normalization_state,c.reason,
 NULL,NULL,a.knowledge_confidence,'PUBLIC'
 FROM source_record_access a JOIN scientific_candidate c ON c.id=a.candidate_id
 JOIN scientific_source_version s ON s.id=c.source_id
 WHERE a.visibility='PUBLIC' AND a.knowledge_confidence<>'UNRESOLVED'
 AND a.content_sha256=c.content_sha256 AND a.source_sha256=s.sha256
 AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED'
 AND c.normalization_state='CANDIDATE' AND c.normalized_payload IS NOT NULL
 -- A release cannot silently revive a rejected, returned or revoked scientific record.
 AND NOT EXISTS(SELECT 1 FROM scientific_decision d WHERE d.candidate_id=c.id)
 AND NOT EXISTS(SELECT 1 FROM scientific_review_state r WHERE r.candidate_id=c.id AND r.state<>'PENDING');
CREATE OR REPLACE VIEW graph_candidate AS
 SELECT c.*,CASE WHEN f.id IS NOT NULL THEN COALESCE(r.state,'APPROVED')
 WHEN pub.id IS NOT NULL THEN pub.knowledge_confidence
 ELSE COALESCE(r.state,IF(c.normalization_state='HELD','HELD','PENDING')) END AS graph_state,
 CASE WHEN f.id IS NOT NULL THEN 'PUBLISHED' WHEN pub.id IS NOT NULL THEN 'SOURCE_PUBLIC' ELSE 'CANDIDATE' END AS publication_state
 FROM scientific_candidate c LEFT JOIN scientific_review_state r ON r.candidate_id=c.id
 LEFT JOIN published_scientific_fact f ON f.id=c.id LEFT JOIN public_source_record pub ON pub.id=c.id;

CREATE OR REPLACE VIEW graph_catalog AS
 SELECT p.*,s.id AS source_id,e.locator,
 (p.visibility_state='PUBLIC' AND s.integrity_state='VERIFIED' AND (
  (p.review_state='APPROVED' AND t.identity_state='VERIFIED' AND s.license_state='PERMITTED'
   AND EXISTS(SELECT 1 FROM review_event r WHERE r.entity_type='PLANT' AND r.entity_key=p.record_key
    AND r.actor_user_id IS NOT NULL AND r.resulting_state='APPROVED'
    AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id)))
  OR (p.review_state='REVIEW_PENDING' AND EXISTS(SELECT 1 FROM review_event r
   WHERE r.entity_type='PLANT' AND r.entity_key=p.record_key AND r.action='CATALOG_METADATA_RELEASE'
    AND r.resulting_state='METADATA_ONLY'
    AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.scope'))='CATALOG_METADATA_ONLY'
    AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.source_sha256'))=s.sha256
    AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.scientific_name'))=t.canonical_name
    AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id)))
 )) AS is_public
 FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id LEFT JOIN evidence e ON e.id=t.evidence_id
 LEFT JOIN source_asset a ON a.id=e.source_asset_id
 LEFT JOIN scientific_source_version s ON s.sha256=a.sha256;
CREATE OR REPLACE VIEW graph_taxon AS
 SELECT t.*,
 (s.integrity_state='VERIFIED' AND EXISTS(
  SELECT 1 FROM graph_catalog p LEFT JOIN taxon leaf ON leaf.id=p.taxon_id LEFT JOIN taxon parent ON parent.id=leaf.parent_id
  WHERE p.is_public=1 AND t.id IN(p.taxon_id,leaf.parent_id,parent.parent_id)
   AND ((t.identity_state='VERIFIED' AND s.license_state='PERMITTED')
    OR (p.review_state='REVIEW_PENDING' AND p.source_id=s.id))
 )) AS is_public,
 s.id AS source_id,e.locator
 FROM taxon t LEFT JOIN evidence e ON e.id=t.evidence_id LEFT JOIN source_asset a ON a.id=e.source_asset_id
 LEFT JOIN scientific_source_version s ON s.sha256=a.sha256;

CREATE OR REPLACE VIEW graph_public_node AS
SELECT CONCAT('source:',id) AS id,'SOURCE' AS node_type,SUBSTRING_INDEX(dataset,'/',-1) AS display_label,IF(integrity_state='VERIFIED','VERIFIED_INTEGRITY',integrity_state) AS verification_state,'METADATA_ONLY' AS publication_state,id AS source_id,JSON_OBJECT() AS locator,JSON_OBJECT('licenseState',license_state,'version',version_label,'sha256',sha256) AS attributes FROM scientific_source_version
UNION ALL
SELECT CONCAT('file:',a.id),'FILE',SUBSTRING_INDEX(a.relative_path,'/',-1),'SOURCE_REPORTED','METADATA_ONLY',NULL,JSON_OBJECT(),JSON_OBJECT('sha256',a.sha256,'mediaType',a.media_type) FROM source_asset a WHERE EXISTS(SELECT 1 FROM scientific_source_version s WHERE s.sha256=a.sha256)
UNION ALL
SELECT CONCAT('plant:',p.id),'PLANT',COALESCE(t.canonical_name,CONCAT('Plant ',p.id)),'SOURCE_REPORTED',IF(p.is_public=1,'METADATA_ONLY','CANDIDATE'),p.source_id,p.locator,JSON_OBJECT('reviewState',p.review_state,'visibilityState',p.visibility_state) FROM graph_catalog p LEFT JOIN taxon t ON t.id=p.taxon_id WHERE p.is_public=1
UNION ALL
SELECT CONCAT('taxon:',t.id),'TAXON',t.canonical_name,'SOURCE_REPORTED',IF(t.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator,JSON_OBJECT('rank',t.taxon_rank,'identityState',t.identity_state) FROM graph_taxon t WHERE t.is_public=1
UNION ALL
SELECT CONCAT('pname:',n.id),'NAME',n.name_text,'SOURCE_REPORTED',IF(p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),p.source_id,e.locator,JSON_OBJECT('role',n.name_role,'language',n.language_tag,'reviewState',n.review_state) FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED')
UNION ALL
SELECT CONCAT('tname:',n.id),'NAME',n.name_text,'SOURCE_REPORTED',IF(t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),t.source_id,e.locator,JSON_OBJECT('role',n.name_role,'language',n.language_tag,'reviewState',n.review_state) FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED')
UNION ALL
SELECT CONCAT('sample:',s.id),'SAMPLE',CONCAT(s.study_key,' / ',s.sample_key),'SOURCE_REPORTED',IF(EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id WHERE o.sample_id=s.id),'SOURCE_PUBLIC','CANDIDATE'),s.source_id,JSON_OBJECT(),JSON_OBJECT('studyId',s.study_key,'sampleKey',s.sample_key,'linkageState',s.linkage_state) FROM scientific_sample s WHERE s.id IN(SELECT o.sample_id FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id)
UNION ALL
SELECT CONCAT('candidate:',c.id),IF(c.pipeline='LIPID','MEASUREMENT',c.pipeline),COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.scientific_name')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.scientificName')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.synonym_name')),CONCAT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id')),' / ',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.analyte')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.kind')))),CONCAT(c.pipeline,' / ',LEFT(c.id,12))),c.graph_state,c.publication_state,c.source_id,c.locator,JSON_OBJECT('pipeline',c.pipeline,'normalizationState',c.normalization_state) FROM graph_candidate c WHERE c.id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('trait:',candidate_id,':',field_key,':',ordinality),'TRAIT',CONCAT(field_key,': ',source_term),graph_state,publication_state,source_id,locator,JSON_OBJECT('field',field_key,'sourceTerm',source_term,'concept',concept,'vocabularyVersion',vocabulary_version) FROM graph_trait WHERE candidate_id IN(SELECT id FROM public_source_record);

ALTER TABLE scientific_candidate ADD graph_scientific_name VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (LEFT(JSON_UNQUOTE(JSON_EXTRACT(normalized_payload,'$.scientific_name')),191)) VIRTUAL,
 ADD INDEX ix_public_source_name(graph_scientific_name,pipeline);

CREATE OR REPLACE VIEW graph_public_edge AS
SELECT CONCAT('sf:',s.id,':',a.id) AS id,CONCAT('source:',s.id) AS from_id,CONCAT('file:',a.id) AS to_id,'SOURCE_FILE' AS relation_type,'SHA-256 相同的注册文件' AS display_label,'HASH_MATCH' AS verification_state,'METADATA_ONLY' AS publication_state,s.id AS source_id,JSON_OBJECT('match','EXACT_SHA256') AS locator FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256
UNION ALL
SELECT CONCAT('pi:',p.id),CONCAT('plant:',p.id),CONCAT('taxon:',p.taxon_id),'IDENTITY_REFERENCE','目录分类身份引用','SOURCE_REPORTED',IF(p.is_public=1,'METADATA_ONLY','CANDIDATE'),p.source_id,p.locator FROM graph_catalog p WHERE p.taxon_id IS NOT NULL AND p.is_public=1
UNION ALL
SELECT CONCAT('tp:',t.id),CONCAT('taxon:',t.id),CONCAT('taxon:',t.parent_id),'PARENT_TAXON','来源分类父级','SOURCE_REPORTED',IF(t.is_public=1 AND parent.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t JOIN graph_taxon parent ON parent.id=t.parent_id WHERE t.is_public=1 AND parent.is_public=1
UNION ALL
SELECT CONCAT('pn:',n.id),CONCAT('plant:',n.plant_id),CONCAT('pname:',n.id),'HAS_NAME','目录记录名称','SOURCE_REPORTED',IF(p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),p.source_id,e.locator FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED')
UNION ALL
SELECT CONCAT('tn:',n.id),CONCAT('taxon:',n.taxon_id),CONCAT('tname:',n.id),'HAS_NAME','分类单元名称','SOURCE_REPORTED',IF(t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),t.source_id,e.locator FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED')
UNION ALL
SELECT CONCAT('ts:',t.id),CONCAT('taxon:',t.id),CONCAT('source:',t.source_id),'SOURCED_FROM','分类原始证据','SOURCE_REPORTED',IF(t.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t WHERE t.source_id IS NOT NULL AND t.is_public=1
UNION ALL
SELECT CONCAT('cs:',c.id),CONCAT('candidate:',c.id),CONCAT('source:',c.source_id),'SOURCED_FROM','观测直接来源',c.graph_state,c.publication_state,c.source_id,c.locator FROM graph_candidate c WHERE c.id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('sm:',o.candidate_id),CONCAT('sample:',o.sample_id),CONCAT('candidate:',o.candidate_id),'HAS_MEASUREMENT','独立样本观测',c.graph_state,c.publication_state,c.source_id,c.locator FROM scientific_lipid_observation o JOIN graph_candidate c ON c.id=o.candidate_id AND c.source_id=o.source_id JOIN scientific_sample sm ON sm.id=o.sample_id WHERE JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.study_id'))=sm.study_key AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id'))=sm.sample_key AND c.id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('ss:',s.id),CONCAT('sample:',s.id),CONCAT('source:',s.source_id),'SOURCED_FROM','样本登记来源','SOURCE_REPORTED',IF(EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id WHERE o.sample_id=s.id),'SOURCE_PUBLIC','CANDIDATE'),s.source_id,JSON_OBJECT('study_id',s.study_key,'sample_id',s.sample_key) FROM scientific_sample s WHERE s.id IN(SELECT o.sample_id FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id)
UNION ALL
SELECT CONCAT('doc:',l.id),CONCAT('candidate:',l.candidate_id),CONCAT('source:',l.document_source_id),'SUPPORTED_BY','明确关联原文献',c.graph_state,c.publication_state,l.document_source_id,l.locator FROM scientific_evidence_link l JOIN graph_candidate c ON c.id=l.candidate_id WHERE c.id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('ct:',candidate_id,':',field_key,':',ordinality),CONCAT('candidate:',candidate_id),CONCAT('trait:',candidate_id,':',field_key,':',ordinality),'HAS_TRAIT','来源性状词与词表投影',graph_state,publication_state,source_id,locator FROM graph_trait WHERE candidate_id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('accepted_taxon_id:',c.id,':',t.id),CONCAT('candidate:',c.id),CONCAT('candidate:',t.id),'ACCEPTED_TAXON','同来源版本原生分类 ID 引用',c.graph_state,IF(c.publication_state IN ('PUBLISHED','SOURCE_PUBLIC') AND IF(EXISTS(SELECT 1 FROM public_source_record target_fact WHERE target_fact.id=t.id),'SOURCE_PUBLIC','CANDIDATE') IN ('PUBLISHED','SOURCE_PUBLIC'),'SOURCE_PUBLIC','CANDIDATE'),c.source_id,c.locator FROM graph_candidate c JOIN scientific_candidate t FORCE INDEX (ix_graph_taxon_reference) ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND t.graph_namespace=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')),32) AND t.graph_native_taxon_id=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')),191) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin) WHERE c.pipeline='TAXONOMY' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id'))<>'' AND JSON_TYPE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))='STRING' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace'))='STRING' AND c.id IN(SELECT id FROM public_source_record) AND t.id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('parent_taxon_id:',c.id,':',t.id),CONCAT('candidate:',c.id),CONCAT('candidate:',t.id),'PARENT_TAXON','同来源版本原生分类 ID 引用',c.graph_state,IF(c.publication_state IN ('PUBLISHED','SOURCE_PUBLIC') AND IF(EXISTS(SELECT 1 FROM public_source_record target_fact WHERE target_fact.id=t.id),'SOURCE_PUBLIC','CANDIDATE') IN ('PUBLISHED','SOURCE_PUBLIC'),'SOURCE_PUBLIC','CANDIDATE'),c.source_id,c.locator FROM graph_candidate c JOIN scientific_candidate t FORCE INDEX (ix_graph_taxon_reference) ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND t.graph_namespace=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')),32) AND t.graph_native_taxon_id=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')),191) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin) WHERE c.pipeline='TAXONOMY' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id'))<>'' AND JSON_TYPE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))='STRING' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace'))='STRING' AND c.id IN(SELECT id FROM public_source_record) AND t.id IN(SELECT id FROM public_source_record)
UNION ALL
SELECT CONCAT('source-name:',p.id,':',f.id),CONCAT('plant:',p.id),CONCAT('candidate:',f.id),'SOURCE_NAME_RECORD','原文学名精确相同的来源记录',f.knowledge_confidence,'SOURCE_PUBLIC',f.source_id,f.locator
FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id JOIN scientific_candidate c ON c.graph_scientific_name=LEFT(t.canonical_name,191) COLLATE utf8mb4_bin
 JOIN public_source_record f ON f.id=c.id
WHERE p.is_public=1 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name'))=BINARY t.canonical_name
;

