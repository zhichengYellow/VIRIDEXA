CREATE TABLE scientific_evidence_link (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 document_source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 locator JSON NOT NULL,
 relation VARCHAR(32) NOT NULL,
 FOREIGN KEY (candidate_id) REFERENCES scientific_candidate(id),
 FOREIGN KEY (document_source_id) REFERENCES scientific_source_version(id),
 CHECK (relation IN ('EXPLICIT_SOURCE_DOCUMENT')),
 UNIQUE KEY uq_candidate_document(candidate_id,document_source_id)
) ENGINE=InnoDB;
