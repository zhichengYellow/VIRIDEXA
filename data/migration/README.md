# Data migration workspace

This directory is for deterministic, reviewable migration code and reports. The phase-one task only produces the v2 model and mapping plan; it does not transform or import research rows.

Migration runs must read a checksum-verified source snapshot, emit a dry-run report first, preserve source identifiers and source-row keys, and write only to an isolated target database. Candidate, rejected, unlinked, and formally verified records must remain distinct. The original database and files are never migration targets.
