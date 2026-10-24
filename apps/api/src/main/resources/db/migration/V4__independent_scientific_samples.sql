ALTER TABLE scientific_candidate ADD UNIQUE KEY uq_candidate_source (id,source_id);
CREATE TABLE scientific_sample (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 study_key VARCHAR(191) NOT NULL,
 sample_key VARCHAR(191) NOT NULL,
 identity_text TEXT NULL,
 linkage_state VARCHAR(16) NOT NULL DEFAULT 'UNLINKED',
 FOREIGN KEY (source_id) REFERENCES scientific_source_version(id),
 CHECK (linkage_state IN ('UNLINKED','NEEDS_REVIEW','LINKED')),
 UNIQUE KEY uq_scientific_sample(study_key,sample_key),
 UNIQUE KEY uq_sample_source(id,source_id),
 INDEX ix_scientific_sample_linkage(linkage_state)
) ENGINE=InnoDB;
CREATE TABLE scientific_lipid_observation (
 candidate_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 source_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sample_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 kind VARCHAR(32) NOT NULL,
 raw_value TEXT NULL,
 value_numeric DECIMAL(30,12) NULL,
 unit VARCHAR(80) NULL,
 basis TEXT NOT NULL,
 analyte VARCHAR(255) NULL,
 FOREIGN KEY (candidate_id,source_id) REFERENCES scientific_candidate(id,source_id),
 FOREIGN KEY (sample_id) REFERENCES scientific_sample(id),
 CHECK (kind IN ('OIL_CONTENT','OIL_FA_COMPOSITION','FAME_COMPOSITION','FOOD_NUTRIENT')),
 INDEX ix_lipid_kind_analyte(kind,analyte),
 INDEX ix_lipid_sample(sample_id)
) ENGINE=InnoDB;
