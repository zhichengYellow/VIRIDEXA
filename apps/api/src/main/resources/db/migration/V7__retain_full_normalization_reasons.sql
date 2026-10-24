-- Source-specific validation reasons must be retained without truncation.
ALTER TABLE scientific_candidate MODIFY reason VARCHAR(255) NULL;
