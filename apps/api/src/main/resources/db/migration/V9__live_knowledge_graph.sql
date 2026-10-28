-- Live read models only. No scientific rows copied, published or reviewed.
CREATE VIEW graph_candidate AS
 SELECT c.*,COALESCE(r.state,IF(c.normalization_state='HELD','HELD','PENDING')) AS graph_state,
 IF(f.id IS NULL,'CANDIDATE','PUBLISHED') AS publication_state
 FROM scientific_candidate c LEFT JOIN scientific_review_state r ON r.candidate_id=c.id
 LEFT JOIN published_scientific_fact f ON f.id=c.id;
CREATE VIEW graph_catalog AS
 SELECT p.*,s.id AS source_id,e.locator,
 (p.visibility_state='PUBLIC' AND p.review_state='APPROVED' AND t.identity_state='VERIFIED'
 AND s.license_state='PERMITTED' AND s.integrity_state='VERIFIED'
 AND EXISTS(SELECT 1 FROM review_event r WHERE r.entity_type='PLANT' AND r.entity_key=p.record_key
 AND r.actor_user_id IS NOT NULL AND r.resulting_state='APPROVED'
 AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id))) AS is_public
 FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id LEFT JOIN evidence e ON e.id=t.evidence_id
 LEFT JOIN source_asset a ON a.id=e.source_asset_id
 LEFT JOIN scientific_source_version s ON s.sha256=a.sha256;
CREATE VIEW graph_taxon AS
 SELECT t.*,
 (t.identity_state='VERIFIED' AND s.license_state='PERMITTED' AND s.integrity_state='VERIFIED' AND EXISTS(SELECT 1 FROM graph_catalog p LEFT JOIN taxon leaf ON leaf.id=p.taxon_id LEFT JOIN taxon parent ON parent.id=leaf.parent_id
 WHERE p.is_public=1 AND t.id IN(p.taxon_id,leaf.parent_id,parent.parent_id))) AS is_public,
 s.id AS source_id,e.locator
 FROM taxon t LEFT JOIN evidence e ON e.id=t.evidence_id LEFT JOIN source_asset a ON a.id=e.source_asset_id
 LEFT JOIN scientific_source_version s ON s.sha256=a.sha256;
CREATE VIEW graph_trait AS
 SELECT c.id AS candidate_id,c.source_id,c.locator,c.graph_state,c.publication_state,o.vocabulary_version,
 k.field_key,j.ordinality,j.source_term,j.concept
 FROM graph_candidate c JOIN ontology_projection o ON o.candidate_id=c.id
 JOIN JSON_TABLE('["leaf_shape","leaf_margin","flower_colour","growth_form","fruit_type","fruit_shape"]','$[*]' COLUMNS(field_key VARCHAR(32) PATH '$')) k
 JOIN JSON_TABLE(JSON_EXTRACT(o.projection,CONCAT('$.traits.',k.field_key)),'$[*]' COLUMNS(ordinality FOR ORDINALITY,source_term TEXT PATH '$.source_term',concept VARCHAR(80) PATH '$.concept' NULL ON EMPTY NULL ON ERROR)) j
 WHERE c.pipeline='MORPHOLOGY' AND j.source_term IS NOT NULL;
CREATE VIEW graph_node AS
SELECT CONCAT('source:',id) AS id,'SOURCE' AS node_type,SUBSTRING_INDEX(dataset,'/',-1) AS display_label,IF(integrity_state='VERIFIED','VERIFIED_INTEGRITY',integrity_state) AS verification_state,'METADATA_ONLY' AS publication_state,id AS source_id,JSON_OBJECT() AS locator,JSON_OBJECT('licenseState',license_state,'version',version_label,'sha256',sha256) AS attributes FROM scientific_source_version
UNION ALL
SELECT CONCAT('file:',a.id),'FILE',SUBSTRING_INDEX(a.relative_path,'/',-1),'SOURCE_REPORTED','METADATA_ONLY',NULL,JSON_OBJECT(),JSON_OBJECT('sha256',a.sha256,'mediaType',a.media_type) FROM source_asset a WHERE EXISTS(SELECT 1 FROM scientific_source_version s WHERE s.sha256=a.sha256)
UNION ALL
SELECT CONCAT('plant:',p.id),'PLANT',COALESCE(t.canonical_name,CONCAT('Plant ',p.id)),'SOURCE_REPORTED',IF(p.is_public=1,'METADATA_ONLY','CANDIDATE'),p.source_id,p.locator,JSON_OBJECT('reviewState',p.review_state,'visibilityState',p.visibility_state) FROM graph_catalog p LEFT JOIN taxon t ON t.id=p.taxon_id
UNION ALL
SELECT CONCAT('taxon:',t.id),'TAXON',t.canonical_name,'SOURCE_REPORTED',IF(t.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator,JSON_OBJECT('rank',t.taxon_rank,'identityState',t.identity_state) FROM graph_taxon t
UNION ALL
SELECT CONCAT('pname:',n.id),'NAME',n.name_text,'SOURCE_REPORTED',IF(p.is_public=1 AND n.review_state='APPROVED','METADATA_ONLY','CANDIDATE'),p.source_id,e.locator,JSON_OBJECT('role',n.name_role,'language',n.language_tag,'reviewState',n.review_state) FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id
UNION ALL
SELECT CONCAT('tname:',n.id),'NAME',n.name_text,'SOURCE_REPORTED',IF(t.is_public=1 AND n.review_state='APPROVED','METADATA_ONLY','CANDIDATE'),t.source_id,e.locator,JSON_OBJECT('role',n.name_role,'language',n.language_tag,'reviewState',n.review_state) FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id
UNION ALL
SELECT CONCAT('sample:',s.id),'SAMPLE',CONCAT(s.study_key,' / ',s.sample_key),'SOURCE_REPORTED',IF(EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN published_scientific_fact f ON f.id=o.candidate_id WHERE o.sample_id=s.id),'PUBLISHED','CANDIDATE'),s.source_id,JSON_OBJECT(),JSON_OBJECT('studyId',s.study_key,'sampleKey',s.sample_key,'linkageState',s.linkage_state) FROM scientific_sample s
UNION ALL
SELECT CONCAT('candidate:',c.id),IF(c.pipeline='LIPID','MEASUREMENT',c.pipeline),COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.scientific_name')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.scientificName')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.synonym_name')),CONCAT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id')),' / ',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.analyte')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.kind')))),CONCAT(c.pipeline,' / ',LEFT(c.id,12))),c.graph_state,c.publication_state,c.source_id,c.locator,JSON_OBJECT('pipeline',c.pipeline,'normalizationState',c.normalization_state) FROM graph_candidate c
UNION ALL
SELECT CONCAT('trait:',candidate_id,':',field_key,':',ordinality),'TRAIT',CONCAT(field_key,': ',source_term),graph_state,publication_state,source_id,locator,JSON_OBJECT('field',field_key,'sourceTerm',source_term,'concept',concept,'vocabularyVersion',vocabulary_version) FROM graph_trait;
CREATE VIEW graph_edge AS
SELECT CONCAT('sf:',s.id,':',a.id) AS id,CONCAT('source:',s.id) AS from_id,CONCAT('file:',a.id) AS to_id,'SOURCE_FILE' AS relation_type,'SHA-256 相同的注册文件' AS display_label,'HASH_MATCH' AS verification_state,'METADATA_ONLY' AS publication_state,s.id AS source_id,JSON_OBJECT('match','EXACT_SHA256') AS locator FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256
UNION ALL
SELECT CONCAT('pi:',p.id),CONCAT('plant:',p.id),CONCAT('taxon:',p.taxon_id),'IDENTITY_REFERENCE','目录分类身份引用','SOURCE_REPORTED',IF(p.is_public=1,'METADATA_ONLY','CANDIDATE'),p.source_id,p.locator FROM graph_catalog p WHERE p.taxon_id IS NOT NULL
UNION ALL
SELECT CONCAT('tp:',t.id),CONCAT('taxon:',t.id),CONCAT('taxon:',t.parent_id),'PARENT_TAXON','来源分类父级','SOURCE_REPORTED',IF(t.is_public=1 AND parent.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t JOIN graph_taxon parent ON parent.id=t.parent_id
UNION ALL
SELECT CONCAT('pn:',n.id),CONCAT('plant:',n.plant_id),CONCAT('pname:',n.id),'HAS_NAME','目录记录名称','SOURCE_REPORTED',IF(p.is_public=1 AND n.review_state='APPROVED','METADATA_ONLY','CANDIDATE'),p.source_id,e.locator FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id
UNION ALL
SELECT CONCAT('tn:',n.id),CONCAT('taxon:',n.taxon_id),CONCAT('tname:',n.id),'HAS_NAME','分类单元名称','SOURCE_REPORTED',IF(t.is_public=1 AND n.review_state='APPROVED','METADATA_ONLY','CANDIDATE'),t.source_id,e.locator FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id
UNION ALL
SELECT CONCAT('ts:',t.id),CONCAT('taxon:',t.id),CONCAT('source:',t.source_id),'SOURCED_FROM','分类原始证据','SOURCE_REPORTED',IF(t.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t WHERE t.source_id IS NOT NULL
UNION ALL
SELECT CONCAT('cs:',c.id),CONCAT('candidate:',c.id),CONCAT('source:',c.source_id),'SOURCED_FROM','观测直接来源',c.graph_state,c.publication_state,c.source_id,c.locator FROM graph_candidate c
UNION ALL
SELECT CONCAT('sm:',o.candidate_id),CONCAT('sample:',o.sample_id),CONCAT('candidate:',o.candidate_id),'HAS_MEASUREMENT','独立样本观测',c.graph_state,c.publication_state,c.source_id,c.locator FROM scientific_lipid_observation o JOIN graph_candidate c ON c.id=o.candidate_id AND c.source_id=o.source_id JOIN scientific_sample sm ON sm.id=o.sample_id WHERE JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.study_id'))=sm.study_key AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id'))=sm.sample_key
UNION ALL
SELECT CONCAT('ss:',s.id),CONCAT('sample:',s.id),CONCAT('source:',s.source_id),'SOURCED_FROM','样本登记来源','SOURCE_REPORTED',IF(EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN published_scientific_fact f ON f.id=o.candidate_id WHERE o.sample_id=s.id),'PUBLISHED','CANDIDATE'),s.source_id,JSON_OBJECT('study_id',s.study_key,'sample_id',s.sample_key) FROM scientific_sample s
UNION ALL
SELECT CONCAT('doc:',l.id),CONCAT('candidate:',l.candidate_id),CONCAT('source:',l.document_source_id),'SUPPORTED_BY','明确关联原文献',c.graph_state,c.publication_state,l.document_source_id,l.locator FROM scientific_evidence_link l JOIN graph_candidate c ON c.id=l.candidate_id
UNION ALL
SELECT CONCAT('ct:',candidate_id,':',field_key,':',ordinality),CONCAT('candidate:',candidate_id),CONCAT('trait:',candidate_id,':',field_key,':',ordinality),'HAS_TRAIT','来源性状词与词表投影',graph_state,publication_state,source_id,locator FROM graph_trait
UNION ALL
SELECT CONCAT('accepted_taxon_id:',c.id,':',t.id),CONCAT('candidate:',c.id),CONCAT('candidate:',t.id),'ACCEPTED_TAXON','同来源版本原生分类 ID 引用',c.graph_state,IF(c.publication_state='PUBLISHED' AND t.publication_state='PUBLISHED','PUBLISHED','CANDIDATE'),c.source_id,c.locator FROM graph_candidate c JOIN graph_candidate t ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))=JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')) AND JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace'))=JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) WHERE c.pipeline='TAXONOMY'
UNION ALL
SELECT CONCAT('parent_taxon_id:',c.id,':',t.id),CONCAT('candidate:',c.id),CONCAT('candidate:',t.id),'PARENT_TAXON','同来源版本原生分类 ID 引用',c.graph_state,IF(c.publication_state='PUBLISHED' AND t.publication_state='PUBLISHED','PUBLISHED','CANDIDATE'),c.source_id,c.locator FROM graph_candidate c JOIN graph_candidate t ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))=JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')) AND JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace'))=JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) WHERE c.pipeline='TAXONOMY';
