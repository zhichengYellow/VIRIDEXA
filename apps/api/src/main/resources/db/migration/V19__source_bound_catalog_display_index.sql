-- Normalize the display authorization already recorded in immutable metadata events.
-- This index contains no observations or scientific review decision.
CREATE TABLE catalog_display_access (
 plant_id BIGINT UNSIGNED PRIMARY KEY,
 event_id BIGINT UNSIGNED NOT NULL,
 source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scientific_name VARCHAR(500) NOT NULL,
 FOREIGN KEY(plant_id) REFERENCES plant(id),
 FOREIGN KEY(event_id) REFERENCES review_event(id),
 FOREIGN KEY(source_id) REFERENCES scientific_source_version(id)
) ENGINE=InnoDB;
CREATE INDEX ix_scientific_source_sha ON scientific_source_version(sha256);
INSERT INTO catalog_display_access
 SELECT p.id,r.id,s.id,s.sha256,t.canonical_name
 FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id
 JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256
 JOIN review_event r ON r.entity_type='PLANT' AND r.entity_key=p.record_key
 WHERE r.action='CATALOG_METADATA_RELEASE' AND r.resulting_state='METADATA_ONLY'
 AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.scope'))='CATALOG_METADATA_ONLY'
 AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.source_sha256'))=s.sha256
 AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.scientific_name'))=t.canonical_name
 AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id);
CREATE OR REPLACE VIEW graph_catalog AS
 SELECT p.*,s.id AS source_id,e.locator,
 (p.visibility_state='PUBLIC' AND s.integrity_state='VERIFIED' AND (
 (p.review_state='APPROVED' AND t.identity_state='VERIFIED' AND s.license_state='PERMITTED'
  AND EXISTS(SELECT 1 FROM review_event decision WHERE decision.entity_type='PLANT' AND decision.entity_key=p.record_key AND decision.actor_user_id IS NOT NULL AND decision.resulting_state='APPROVED'
   AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=decision.entity_type AND later.entity_key=decision.entity_key AND later.id>decision.id)))
 OR (p.review_state='REVIEW_PENDING' AND (
  (x.source_id=s.id AND x.source_sha256=s.sha256 AND BINARY x.scientific_name=BINARY t.canonical_name
   AND r.entity_type='PLANT' AND r.entity_key=p.record_key AND r.action='CATALOG_METADATA_RELEASE' AND r.resulting_state='METADATA_ONLY'
   AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=r.entity_type AND later.entity_key=r.entity_key AND later.id>r.id))
  -- Old controlled metadata CLI remains valid until the next index refresh.
  OR (x.plant_id IS NULL AND EXISTS(SELECT 1 FROM review_event old WHERE old.entity_type='PLANT' AND old.entity_key=p.record_key AND old.action='CATALOG_METADATA_RELEASE' AND old.resulting_state='METADATA_ONLY'
   AND JSON_UNQUOTE(JSON_EXTRACT(old.raw_payload,'$.scope'))='CATALOG_METADATA_ONLY'
   AND JSON_UNQUOTE(JSON_EXTRACT(old.raw_payload,'$.source_sha256'))=s.sha256
   AND JSON_UNQUOTE(JSON_EXTRACT(old.raw_payload,'$.scientific_name'))=t.canonical_name
   AND NOT EXISTS(SELECT 1 FROM review_event later WHERE later.entity_type=old.entity_type AND later.entity_key=old.entity_key AND later.id>old.id)))
 )))) AS is_public
 FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id LEFT JOIN evidence e ON e.id=t.evidence_id
 LEFT JOIN source_asset a ON a.id=e.source_asset_id LEFT JOIN scientific_source_version s ON s.sha256=a.sha256
 LEFT JOIN catalog_display_access x ON x.plant_id=p.id LEFT JOIN review_event r ON r.id=x.event_id;
