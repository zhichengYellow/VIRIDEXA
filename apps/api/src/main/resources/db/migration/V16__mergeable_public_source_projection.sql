-- Same authorization rule as V15, using one mergeable projection instead of a
-- wide UNION materialization. Aggregate and paginated consumers do not spool all JSON.
CREATE OR REPLACE VIEW public_source_record AS
 SELECT c.id,c.source_id,c.pipeline,c.locator,c.normalized_payload,c.content_sha256,c.normalization_state,c.reason,
 f.decision_id,f.reviewer_id,
 CASE WHEN f.id IS NOT NULL THEN 'HUMAN_VERIFIED' ELSE a.knowledge_confidence END AS knowledge_confidence,
 'PUBLIC' AS visibility
 FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id
 LEFT JOIN published_scientific_fact f ON f.id=c.id
 LEFT JOIN source_record_access a ON a.candidate_id=c.id
 WHERE (f.id IS NOT NULL AND (a.candidate_id IS NULL OR a.visibility='PUBLIC'))
 OR (a.visibility='PUBLIC' AND a.knowledge_confidence<>'UNRESOLVED'
 AND a.content_sha256=c.content_sha256 AND a.source_sha256=s.sha256
 AND s.integrity_state='VERIFIED' AND s.license_state<>'RESTRICTED'
 AND c.normalization_state='CANDIDATE' AND c.normalized_payload IS NOT NULL
 AND NOT EXISTS(SELECT 1 FROM scientific_decision d WHERE d.candidate_id=c.id)
 AND NOT EXISTS(SELECT 1 FROM scientific_review_state r WHERE r.candidate_id=c.id AND r.state<>'PENDING'));
