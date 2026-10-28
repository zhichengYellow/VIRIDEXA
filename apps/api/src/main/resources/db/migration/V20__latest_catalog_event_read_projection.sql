-- One indexed latest-event projection, instead of repeating correlated audit scans.
-- Latest withdrawal/rejection still immediately removes a public directory entry.
CREATE VIEW catalog_latest_event AS
 SELECT entity_key,MAX(id) AS event_id FROM review_event WHERE entity_type='PLANT' GROUP BY entity_key;
CREATE OR REPLACE VIEW graph_catalog AS
 SELECT p.*,s.id AS source_id,e.locator,
 (p.visibility_state='PUBLIC' AND s.integrity_state='VERIFIED' AND (
  (p.review_state='APPROVED' AND t.identity_state='VERIFIED' AND s.license_state='PERMITTED'
   AND r.actor_user_id IS NOT NULL AND r.resulting_state='APPROVED')
  OR (p.review_state='REVIEW_PENDING' AND r.action='CATALOG_METADATA_RELEASE' AND r.resulting_state='METADATA_ONLY'
   AND ((x.event_id=r.id AND x.source_id=s.id AND x.source_sha256=s.sha256 AND BINARY x.scientific_name=BINARY t.canonical_name)
    OR (x.plant_id IS NULL AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.scope'))='CATALOG_METADATA_ONLY'
     AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.source_sha256'))=s.sha256
     AND JSON_UNQUOTE(JSON_EXTRACT(r.raw_payload,'$.scientific_name'))=t.canonical_name)))
 )) AS is_public
 FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id LEFT JOIN evidence e ON e.id=t.evidence_id
 LEFT JOIN source_asset a ON a.id=e.source_asset_id LEFT JOIN scientific_source_version s ON s.sha256=a.sha256
 LEFT JOIN catalog_display_access x ON x.plant_id=p.id
 LEFT JOIN catalog_latest_event latest ON latest.entity_key=p.record_key LEFT JOIN review_event r ON r.id=latest.event_id;
CREATE OR REPLACE VIEW graph_taxon AS
 WITH public_catalog AS (
  SELECT DISTINCT taxon_id,review_state,source_id FROM graph_catalog WHERE is_public=1
 ), relation_scope AS (
  SELECT taxon_id,review_state,source_id FROM public_catalog
  UNION ALL
  SELECT leaf.parent_id,p.review_state,p.source_id FROM public_catalog p JOIN taxon leaf ON leaf.id=p.taxon_id WHERE leaf.parent_id IS NOT NULL
  UNION ALL
  SELECT parent.parent_id,p.review_state,p.source_id FROM public_catalog p JOIN taxon leaf ON leaf.id=p.taxon_id JOIN taxon parent ON parent.id=leaf.parent_id WHERE parent.parent_id IS NOT NULL
 )
 SELECT t.*,(s.integrity_state='VERIFIED' AND EXISTS(
  SELECT 1 FROM relation_scope p WHERE p.taxon_id=t.id
  AND ((t.identity_state='VERIFIED' AND s.license_state='PERMITTED') OR (p.review_state='REVIEW_PENDING' AND p.source_id=s.id))
 )) AS is_public,s.id AS source_id,e.locator
 FROM taxon t LEFT JOIN evidence e ON e.id=t.evidence_id LEFT JOIN source_asset a ON a.id=e.source_asset_id
 LEFT JOIN scientific_source_version s ON s.sha256=a.sha256;
