-- Catalogue display authorization is distinct from scientific identity verification.
-- Only a deliberately recorded, current, source-bound metadata release is eligible.
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
