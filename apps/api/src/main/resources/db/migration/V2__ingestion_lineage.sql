CREATE TABLE source_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_record_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_asset_id BIGINT UNSIGNED NOT NULL,
    source_table_name VARCHAR(100) NOT NULL,
    source_row_key VARCHAR(700) NOT NULL,
    source_row_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    transform_state VARCHAR(32) NOT NULL DEFAULT 'INDEXED',
    target_entity_type VARCHAR(40) NULL,
    target_entity_key VARCHAR(191) NULL,
    payload JSON NOT NULL,
    imported_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_source_record_asset FOREIGN KEY (source_asset_id) REFERENCES source_asset(id),
    CONSTRAINT ck_source_record_state CHECK (transform_state IN ('INDEXED','PARSED','LINKED','HELD','ERROR')),
    UNIQUE KEY uq_source_record_origin (source_asset_id, source_table_name, source_row_key(191)),
    INDEX ix_source_record_table_state (source_table_name, transform_state),
    INDEX ix_source_record_hash (source_row_sha256)
) ENGINE=InnoDB;

CREATE TABLE operational_audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    source_asset_id BIGINT UNSIGNED NOT NULL,
    source_record_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    historical_actor_ref VARCHAR(191) NULL,
    happened_at DATETIME(6) NULL,
    payload JSON NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_operational_audit_asset FOREIGN KEY (source_asset_id) REFERENCES source_asset(id),
    CONSTRAINT fk_operational_audit_record FOREIGN KEY (source_record_key) REFERENCES source_record(source_record_key),
    CONSTRAINT fk_operational_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id),
    INDEX ix_operational_audit_time (happened_at),
    INDEX ix_operational_audit_actor (historical_actor_ref)
) ENGINE=InnoDB;
