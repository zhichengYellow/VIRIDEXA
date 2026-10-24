-- Immutable source versions and candidates; publication is a separate named decision.
CREATE TABLE scientific_source_version (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 dataset VARCHAR(191) NOT NULL,
 version_label VARCHAR(191) NOT NULL,
 path VARCHAR(700) NOT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 license_state VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
 integrity_state VARCHAR(16) NOT NULL,
 UNIQUE KEY uq_science_version(dataset, sha256),
 CHECK (license_state IN ('UNKNOWN','PERMITTED','RESTRICTED')),
 CHECK (integrity_state IN ('VERIFIED','MISMATCH','INCOMPLETE'))
) ENGINE=InnoDB;
CREATE TABLE scientific_candidate (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 pipeline VARCHAR(16) NOT NULL,
 locator JSON NOT NULL,
 raw_payload JSON NOT NULL,
 normalized_payload JSON NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 normalization_state VARCHAR(16) NOT NULL,
 reason VARCHAR(80) NULL,
 FOREIGN KEY (source_id) REFERENCES scientific_source_version(id),
 CHECK (pipeline IN ('TAXONOMY','MORPHOLOGY','LIPID')),
 CHECK (normalization_state IN ('CANDIDATE','HELD')),
 INDEX ix_science_pipeline(pipeline, normalization_state),
 INDEX ix_science_source(source_id)
) ENGINE=InnoDB;
CREATE TABLE scientific_decision (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reviewer_id BIGINT UNSIGNED NOT NULL,
 action VARCHAR(16) NOT NULL,
 rationale TEXT NOT NULL,
 decided_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (candidate_id) REFERENCES scientific_candidate(id),
 FOREIGN KEY (reviewer_id) REFERENCES app_user(id),
 CHECK (action IN ('APPROVED','REJECTED','REVOKED')),
 INDEX ix_science_decision(candidate_id,id)
) ENGINE=InnoDB;
CREATE VIEW published_scientific_fact AS
 SELECT c.*, d.id AS decision_id, d.reviewer_id
 FROM scientific_candidate c
 JOIN scientific_source_version s ON s.id=c.source_id
 JOIN scientific_decision d ON d.candidate_id=c.id
 WHERE d.action='APPROVED' AND c.normalization_state='CANDIDATE'
 AND s.integrity_state='VERIFIED' AND s.license_state='PERMITTED'
 AND NOT EXISTS (SELECT 1 FROM scientific_decision later WHERE later.candidate_id=c.id AND later.id>d.id);
