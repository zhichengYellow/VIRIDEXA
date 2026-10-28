-- A public name assertion is independent of scientific approval; prior names remain intact.
CREATE TABLE botanical_name_assertion (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 plant_name_id BIGINT UNSIGNED NOT NULL,
 source_url VARCHAR(1000) NOT NULL,
 source_version VARCHAR(200) NOT NULL,
 authority_taxon_id VARCHAR(200) NOT NULL,
 matched_scientific_name VARCHAR(500) NOT NULL,
 match_method VARCHAR(40) NOT NULL,
 confidence VARCHAR(40) NOT NULL,
 display_basis VARCHAR(160) NOT NULL,
 queried_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_botanical_assertion_name FOREIGN KEY (plant_name_id) REFERENCES plant_name(id),
 CONSTRAINT ck_botanical_assertion_method CHECK(match_method IN ('ACCEPTED_EXACT','IDENTIFIER','EXPLICIT_SYNONYM')),
 CONSTRAINT ck_botanical_assertion_confidence CHECK(confidence IN ('SOURCE_REPORTED','CROSS_CHECKED')),
 INDEX ix_botanical_assertion_name(plant_name_id)
) ENGINE=InnoDB;
