ALTER TABLE scientific_decision DROP CHECK scientific_decision_chk_1;
ALTER TABLE scientific_decision ADD CONSTRAINT ck_decision_action CHECK (action IN ('APPROVED','REJECTED','NEEDS_EVIDENCE','REVOKED')),
 ADD request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD prior_version BIGINT UNSIGNED NULL, ADD resulting_version BIGINT UNSIGNED NULL,
 ADD UNIQUE KEY uq_decision_request(reviewer_id,request_id);
CREATE TABLE scientific_review_state (
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 FOREIGN KEY (candidate_id) REFERENCES scientific_candidate(id),
 CHECK (state IN ('PENDING','APPROVED','REJECTED','NEEDS_EVIDENCE','REVOKED')),
 INDEX ix_review_state(state, candidate_id)
) ENGINE=InnoDB;
CREATE TABLE source_governance (
 source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 version BIGINT UNSIGNED NOT NULL DEFAULT 0,
 FOREIGN KEY (source_id) REFERENCES scientific_source_version(id)
) ENGINE=InnoDB;
CREATE TABLE source_license_event (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reviewer_id BIGINT UNSIGNED NOT NULL,
 license_state VARCHAR(16) NOT NULL,
 rationale TEXT NOT NULL,
 prior_version BIGINT UNSIGNED NOT NULL,
 resulting_version BIGINT UNSIGNED NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 decided_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_license_request(reviewer_id,request_id),
 FOREIGN KEY (source_id) REFERENCES scientific_source_version(id),
 FOREIGN KEY (reviewer_id) REFERENCES app_user(id),
 CHECK (license_state IN ('UNKNOWN','PERMITTED','RESTRICTED'))
) ENGINE=InnoDB;
CREATE TABLE historical_mapping (
 source_record_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 classification VARCHAR(40) NOT NULL,
 reason TEXT NOT NULL,
 mapping_payload JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 FOREIGN KEY (source_record_key) REFERENCES source_record(source_record_key),
 INDEX ix_historical_class(classification)
) ENGINE=InnoDB;
CREATE TABLE historical_evidence_link (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 source_record_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 scientific_candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 relation VARCHAR(40) NOT NULL,
 FOREIGN KEY (source_record_key) REFERENCES source_record(source_record_key),
 FOREIGN KEY (scientific_candidate_id) REFERENCES scientific_candidate(id),
 UNIQUE KEY uq_historical_link(source_record_key,scientific_candidate_id,relation)
) ENGINE=InnoDB;
CREATE TABLE ontology_projection (
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 vocabulary_version VARCHAR(40) NOT NULL,
 projection JSON NOT NULL,
 projection_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 mapping_state VARCHAR(24) NOT NULL,
 FOREIGN KEY (candidate_id) REFERENCES scientific_candidate(id)
) ENGINE=InnoDB;
