-- Preserve V13 metadata and scientific semantics, using indexed parent/plant FK lookups
-- instead of scanning the entire public catalog once for every taxon.
CREATE OR REPLACE VIEW graph_taxon AS
 SELECT t.*,
 (s.integrity_state='VERIFIED' AND (
 EXISTS(SELECT 1 FROM graph_catalog p WHERE p.taxon_id=t.id AND p.is_public=1
  AND ((t.identity_state='VERIFIED' AND s.license_state='PERMITTED') OR (p.review_state='REVIEW_PENDING' AND p.source_id=s.id)))
 OR EXISTS(SELECT 1 FROM taxon leaf JOIN graph_catalog p ON p.taxon_id=leaf.id
  WHERE leaf.parent_id=t.id AND p.is_public=1
  AND ((t.identity_state='VERIFIED' AND s.license_state='PERMITTED') OR (p.review_state='REVIEW_PENDING' AND p.source_id=s.id)))
 OR EXISTS(SELECT 1 FROM taxon parent JOIN taxon leaf ON leaf.parent_id=parent.id JOIN graph_catalog p ON p.taxon_id=leaf.id
  WHERE parent.parent_id=t.id AND p.is_public=1
  AND ((t.identity_state='VERIFIED' AND s.license_state='PERMITTED') OR (p.review_state='REVIEW_PENDING' AND p.source_id=s.id)))
 )) AS is_public,s.id AS source_id,e.locator
 FROM taxon t LEFT JOIN evidence e ON e.id=t.evidence_id LEFT JOIN source_asset a ON a.id=e.source_asset_id
 LEFT JOIN scientific_source_version s ON s.sha256=a.sha256;
