CREATE OR REPLACE VIEW published_scientific_fact AS
 SELECT c.*, d.id AS decision_id, d.reviewer_id
 FROM scientific_candidate c
 JOIN scientific_source_version s ON s.id=c.source_id
 JOIN scientific_decision d ON d.candidate_id=c.id
 WHERE d.action='APPROVED' AND c.normalization_state='CANDIDATE'
 AND s.integrity_state='VERIFIED' AND s.license_state='PERMITTED'
 AND NOT EXISTS (SELECT 1 FROM scientific_decision later WHERE later.candidate_id=c.id AND later.id>d.id)
 AND NOT EXISTS (
   SELECT 1 FROM scientific_evidence_link link
   JOIN scientific_source_version document ON document.id=link.document_source_id
   WHERE link.candidate_id=c.id AND (document.license_state<>'PERMITTED' OR document.integrity_state<>'VERIFIED')
 );
