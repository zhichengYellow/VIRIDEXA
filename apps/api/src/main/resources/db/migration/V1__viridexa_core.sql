CREATE TABLE app_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE app_role (
    id SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_key VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id SMALLINT UNSIGNED NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role(id)
) ENGINE=InnoDB;

CREATE TABLE source_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    asset_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    relative_path VARCHAR(700) NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    media_type VARCHAR(160) NULL,
    asset_kind VARCHAR(40) NOT NULL,
    ingest_state VARCHAR(32) NOT NULL DEFAULT 'INDEXED',
    metadata JSON NULL,
    indexed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_source_asset_state CHECK (ingest_state IN ('INDEXED','PROCESSING','PROCESSED','ERROR','HELD')),
    INDEX ix_source_asset_hash (sha256),
    INDEX ix_source_asset_kind_state (asset_kind, ingest_state)
) ENGINE=InnoDB;

CREATE TABLE research_source (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_asset_id BIGINT UNSIGNED NULL,
    source_type VARCHAR(40) NOT NULL,
    title VARCHAR(500) NOT NULL,
    authors TEXT NULL,
    publication_year SMALLINT UNSIGNED NULL,
    doi VARCHAR(255) NULL,
    url TEXT NULL,
    citation TEXT NULL,
    license_note TEXT NULL,
    metadata JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_research_source_asset FOREIGN KEY (source_asset_id) REFERENCES source_asset(id),
    INDEX ix_research_source_type_year (source_type, publication_year)
) ENGINE=InnoDB;

CREATE TABLE evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    evidence_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_asset_id BIGINT UNSIGNED NOT NULL,
    locator_type VARCHAR(32) NOT NULL,
    locator JSON NOT NULL,
    raw_excerpt LONGTEXT NULL,
    evidence_image_path VARCHAR(700) NULL,
    evidence_image_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    evidence_state VARCHAR(32) NOT NULL DEFAULT 'EXTRACTED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_evidence_asset FOREIGN KEY (source_asset_id) REFERENCES source_asset(id),
    CONSTRAINT ck_evidence_state CHECK (evidence_state IN ('EXTRACTED','SOURCE_CHECKED','REVIEWED','REJECTED','UNLINKED')),
    INDEX ix_evidence_asset_locator (source_asset_id, locator_type)
) ENGINE=InnoDB;

CREATE TABLE taxon (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    taxon_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    parent_id BIGINT UNSIGNED NULL,
    taxon_rank VARCHAR(24) NOT NULL,
    canonical_name VARCHAR(500) NOT NULL,
    identity_state VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
    evidence_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_taxon_parent FOREIGN KEY (parent_id) REFERENCES taxon(id),
    CONSTRAINT fk_taxon_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    INDEX ix_taxon_name_rank (canonical_name(191), taxon_rank),
    INDEX ix_taxon_parent_rank (parent_id, taxon_rank)
) ENGINE=InnoDB;

CREATE TABLE taxon_name (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    taxon_id BIGINT UNSIGNED NOT NULL,
    name_role VARCHAR(24) NOT NULL,
    language_tag VARCHAR(16) NOT NULL,
    name_text VARCHAR(500) NOT NULL,
    review_state VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
    evidence_id BIGINT UNSIGNED NULL,
    CONSTRAINT fk_taxon_name_taxon FOREIGN KEY (taxon_id) REFERENCES taxon(id),
    CONSTRAINT fk_taxon_name_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    INDEX ix_taxon_name_text (name_text(191), language_tag)
) ENGINE=InnoDB;

CREATE TABLE plant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_record_key VARCHAR(191) NULL,
    taxon_id BIGINT UNSIGNED NULL,
    review_state VARCHAR(32) NOT NULL DEFAULT 'REVIEW_PENDING',
    visibility_state VARCHAR(32) NOT NULL DEFAULT 'RESEARCH_PREVIEW',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_plant_taxon FOREIGN KEY (taxon_id) REFERENCES taxon(id),
    CONSTRAINT ck_plant_review CHECK (review_state IN ('EXTRACTED','REVIEW_PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_plant_visibility CHECK (visibility_state IN ('PRIVATE','RESEARCH_PREVIEW','PUBLIC')),
    INDEX ix_plant_taxon_state (taxon_id, review_state, visibility_state)
) ENGINE=InnoDB;

CREATE TABLE plant_name (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    plant_id BIGINT UNSIGNED NOT NULL,
    name_role VARCHAR(24) NOT NULL,
    language_tag VARCHAR(16) NOT NULL,
    name_text VARCHAR(500) NOT NULL,
    review_state VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
    evidence_id BIGINT UNSIGNED NULL,
    CONSTRAINT fk_plant_name_plant FOREIGN KEY (plant_id) REFERENCES plant(id),
    CONSTRAINT fk_plant_name_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    INDEX ix_plant_name_text (name_text(191), language_tag),
    INDEX ix_plant_name_plant_role (plant_id, name_role)
) ENGINE=InnoDB;

CREATE TABLE trait_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trait_key VARCHAR(120) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    value_kind VARCHAR(24) NOT NULL,
    canonical_unit VARCHAR(80) NULL,
    definition TEXT NULL,
    vocabulary_version VARCHAR(40) NOT NULL DEFAULT '1',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE trait_observation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    observation_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    plant_id BIGINT UNSIGNED NOT NULL,
    trait_definition_id BIGINT UNSIGNED NOT NULL,
    raw_value LONGTEXT NOT NULL,
    normalized_text TEXT NULL,
    normalized_number DECIMAL(24,10) NULL,
    unit VARCHAR(80) NULL,
    qualifier VARCHAR(80) NULL,
    review_state VARCHAR(32) NOT NULL DEFAULT 'EXTRACTED',
    evidence_id BIGINT UNSIGNED NULL,
    CONSTRAINT fk_trait_obs_plant FOREIGN KEY (plant_id) REFERENCES plant(id),
    CONSTRAINT fk_trait_obs_definition FOREIGN KEY (trait_definition_id) REFERENCES trait_definition(id),
    CONSTRAINT fk_trait_obs_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    INDEX ix_trait_obs_query (trait_definition_id, normalized_number, review_state)
) ENGINE=InnoDB;

CREATE TABLE research_study (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    study_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    research_source_id BIGINT UNSIGNED NOT NULL,
    study_title VARCHAR(500) NOT NULL,
    study_date VARCHAR(80) NULL,
    methods_summary TEXT NULL,
    metadata JSON NULL,
    CONSTRAINT fk_study_source FOREIGN KEY (research_source_id) REFERENCES research_source(id)
) ENGINE=InnoDB;

CREATE TABLE research_sample (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sample_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_sample_key VARCHAR(191) NULL,
    research_study_id BIGINT UNSIGNED NOT NULL,
    plant_id BIGINT UNSIGNED NULL,
    taxon_id BIGINT UNSIGNED NULL,
    identification_text VARCHAR(500) NULL,
    sample_label VARCHAR(500) NULL,
    plant_part VARCHAR(255) NULL,
    collection_unit VARCHAR(255) NULL,
    testing_unit VARCHAR(255) NULL,
    locality TEXT NULL,
    linkage_state VARCHAR(32) NOT NULL DEFAULT 'UNLINKED',
    review_state VARCHAR(32) NOT NULL DEFAULT 'REVIEW_PENDING',
    evidence_id BIGINT UNSIGNED NULL,
    raw_payload JSON NULL,
    CONSTRAINT fk_sample_study FOREIGN KEY (research_study_id) REFERENCES research_study(id),
    CONSTRAINT fk_sample_plant FOREIGN KEY (plant_id) REFERENCES plant(id),
    CONSTRAINT fk_sample_taxon FOREIGN KEY (taxon_id) REFERENCES taxon(id),
    CONSTRAINT fk_sample_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    CONSTRAINT ck_sample_linkage CHECK (linkage_state IN ('LINKED','NEEDS_REVIEW','UNLINKED')),
    INDEX ix_sample_plant_study (plant_id, research_study_id),
    INDEX ix_sample_state (review_state, linkage_state)
) ENGINE=InnoDB;

CREATE TABLE measurement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    measurement_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    research_sample_id BIGINT UNSIGNED NOT NULL,
    measurement_kind VARCHAR(32) NOT NULL,
    analyte VARCHAR(160) NULL,
    value_raw VARCHAR(255) NULL,
    value_numeric DECIMAL(24,10) NULL,
    unit VARCHAR(80) NULL,
    denominator VARCHAR(160) NULL,
    analytical_basis VARCHAR(160) NULL,
    method VARCHAR(500) NULL,
    measurement_state VARCHAR(32) NOT NULL DEFAULT 'REVIEW_PENDING',
    evidence_id BIGINT UNSIGNED NULL,
    raw_payload JSON NULL,
    CONSTRAINT fk_measurement_sample FOREIGN KEY (research_sample_id) REFERENCES research_sample(id),
    CONSTRAINT fk_measurement_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    CONSTRAINT ck_measurement_kind CHECK (measurement_kind IN ('OIL_CONTENT','FATTY_ACID','OTHER')),
    CONSTRAINT ck_measurement_state CHECK (measurement_state IN ('EXTRACTED','REVIEW_PENDING','SOURCE_CHECKED','APPROVED','REJECTED','MISSING')),
    INDEX ix_measurement_query (measurement_kind, analyte, measurement_state),
    INDEX ix_measurement_sample (research_sample_id, measurement_kind)
) ENGINE=InnoDB;

CREATE TABLE candidate_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    candidate_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_asset_id BIGINT UNSIGNED NOT NULL,
    evidence_id BIGINT UNSIGNED NULL,
    candidate_type VARCHAR(60) NOT NULL,
    review_state VARCHAR(32) NOT NULL DEFAULT 'REVIEW_PENDING',
    proposed_entity_key VARCHAR(191) NULL,
    raw_payload JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_candidate_asset FOREIGN KEY (source_asset_id) REFERENCES source_asset(id),
    CONSTRAINT fk_candidate_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    CONSTRAINT ck_candidate_state CHECK (review_state IN ('REVIEW_PENDING','APPROVED','REJECTED','UNLINKED')),
    INDEX ix_candidate_type_state (candidate_type, review_state)
) ENGINE=InnoDB;

CREATE TABLE media_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    media_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_asset_id BIGINT UNSIGNED NOT NULL,
    relative_path VARCHAR(700) NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    alt_text VARCHAR(500) NULL,
    media_state VARCHAR(32) NOT NULL DEFAULT 'INDEXED',
    metadata JSON NULL,
    CONSTRAINT fk_media_source_asset FOREIGN KEY (source_asset_id) REFERENCES source_asset(id),
    INDEX ix_media_hash (sha256)
) ENGINE=InnoDB;

CREATE TABLE plant_media (
    plant_id BIGINT UNSIGNED NOT NULL,
    media_asset_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'ILLUSTRATION',
    evidence_id BIGINT UNSIGNED NULL,
    PRIMARY KEY (plant_id, media_asset_id, relation_type),
    CONSTRAINT fk_plant_media_plant FOREIGN KEY (plant_id) REFERENCES plant(id),
    CONSTRAINT fk_plant_media_asset FOREIGN KEY (media_asset_id) REFERENCES media_asset(id),
    CONSTRAINT fk_plant_media_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id)
) ENGINE=InnoDB;

CREATE TABLE external_identifier (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    identifier_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    identifier_source VARCHAR(80) NOT NULL,
    identifier_value VARCHAR(255) NOT NULL,
    plant_id BIGINT UNSIGNED NULL,
    taxon_id BIGINT UNSIGNED NULL,
    match_state VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
    evidence_id BIGINT UNSIGNED NULL,
    raw_payload JSON NULL,
    CONSTRAINT fk_external_plant FOREIGN KEY (plant_id) REFERENCES plant(id),
    CONSTRAINT fk_external_taxon FOREIGN KEY (taxon_id) REFERENCES taxon(id),
    CONSTRAINT fk_external_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id),
    INDEX ix_external_source_value (identifier_source, identifier_value)
) ENGINE=InnoDB;

CREATE TABLE review_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    research_source_id BIGINT UNSIGNED NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_key VARCHAR(191) NOT NULL,
    action VARCHAR(60) NOT NULL,
    prior_state VARCHAR(32) NULL,
    resulting_state VARCHAR(32) NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    historical_actor_ref VARCHAR(191) NULL,
    happened_at DATETIME(6) NULL,
    note TEXT NULL,
    raw_payload JSON NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_review_source FOREIGN KEY (research_source_id) REFERENCES research_source(id),
    CONSTRAINT fk_review_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id),
    INDEX ix_review_entity_time (entity_type, entity_key, happened_at),
    INDEX ix_review_actor_time (actor_user_id, happened_at)
) ENGINE=InnoDB;

CREATE TABLE favorite (
    user_id BIGINT UNSIGNED NOT NULL,
    plant_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, plant_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_favorite_plant FOREIGN KEY (plant_id) REFERENCES plant(id)
) ENGINE=InnoDB;

INSERT INTO app_role(role_key, description) VALUES
    ('RESEARCHER', 'Can access approved research records'),
    ('REVIEWER', 'Can review evidence and record review events'),
    ('ADMIN', 'Can administer VIRIDEXA users and configuration');
