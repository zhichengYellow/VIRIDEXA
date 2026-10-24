# Migration mapping plan

No migration import has run. The 2026-10-24 source snapshot is read-only; the map below is a plan for a repeatable dry-run and isolated-target import.

## Source-to-target crosswalk

| Source evidence | V2 destination | Mapping and status rule |
|---|---|---|
| `plants` | `plant`, `taxon`, `plant_name`, `candidate_record` | Preserve source plant ID as a source identifier. Keep plant editorial record distinct from taxon identity. Map `name_zh`, `scientific_name`, and `name_en` into typed names with their existing independent statuses. If `name_zh` duplicates `scientific_name`, retain the raw field but do not infer a Chinese common name; report it as a name-quality exception. Missing row-level source citations remain missing. Preserve `is_public`; do not infer publication from missing status. |
| `plant_families`, `plant_genera` | `taxon` | Preserve rank and parent relationships. Store legacy IDs and original Chinese/Latin labels. Do not silently merge homonyms. |
| `plant_aliases` | `plant_name` | Preserve language and `source_status`; only verified aliases participate in public name lookup. Duplicate/colliding names are reported for review. |
| `trait_categories`, `plant_traits`, `trait_options` | `trait_definition` | Preserve taxonomy and option labels as controlled vocabulary with source IDs. Label fixes remain recorded rather than folded into raw source labels. |
| `plant_trait_values` | `trait_observation`, `evidence`, `review_event` | Preserve extracted text/value, source, locator, and `EXTRACTED`/`REVIEW_PENDING`/verification state. Import does not upgrade extracted rows to verified. |
| `plant_knowledge_sources`, `plant_knowledge_entries` | `research_source`, `evidence`, `candidate_record` | Keep all entries in their current `NEEDS_REVIEW` state. Preserve source URL/document and exact entry key. |
| `plant_oil_samples` | `research_sample`, `oil_measurement`, `evidence` | Preserve each sample separately, including OCR vs manual record kind, raw text, plant part, collection/test basis candidates, source page, and sample status. NULL or unreadable oil remains missing, never zero. |
| `fatty_acid_components` | `fatty_acid_measurement` | Import only rows already formally verified after confirming their parent sample and source. Preserve component, raw value, percentage unit and page. No values are merged across samples/studies. |
| `fatty_acid_candidates` | `candidate_record` | All 295 rows remain `REVIEW_PENDING` until a human review accepts them with sample, source, unit/basis, and evidence. No candidate is published by migration. |
| `plant_datasets`, `plant_dataset_relations` | `research_source`, plant/source relation | Preserve dataset identity, relation status, source key, and association note, including unresolved relations. |
| `plant_external_identifiers`, `plant_taxonomy_references`, `external_taxon_candidates` | `external_identifier`, `evidence`, `candidate_record` | Preserve original authority/accession and matching state. Candidate taxon matches remain candidates. |
| `external_import_batches`, `external_import_candidates`, `external_import_reviews` | `candidate_record`, `review_event`, source batch relation | Keep GBIF batch, candidate payload, match confidence/state, and reviewer decision as separate records. Rejected/unmatched rows remain retained. |
| `plant_images`, `plant_image_references`, `data/images/manifest.json` | `media_asset`, `plant_media`, `evidence` | Preserve stored name, source key, checksums, page, crop bounding box, primary marker, and image status. Verify file existence and hash; leave unresolved links unattached rather than guessing. |
| `knowledge_review_events` | `review_event` | Preserve event order, entity type/ID, action/status transition, notes and timestamp. Keep actor NULL when it was NULL. |
| `audit_logs` | `review_event` or operational audit stream | Keep separate from science review events unless an event is proven to represent a scientific transition. Preserve nullable actor and payload exactly. |
| `users`, `auth_events`, `auth_login_attempts`, `auth_email_challenges` | `user`, `role`, security audit store | Account migration needs independent credential and retention review. Preserve hashes only if allowed; never manufacture a user for a NULL historical actor. Do not migrate transient email challenges. |
| `favorites`, `user_search_history`, `user_plant_history`, `plant_daily_views` | `favorite` and separately governed activity/aggregate tables | Preserve user relations only when the user is migrated. Reconcile privacy/retention before carrying activity history. |
| WFO archive, NHM combined archive, Flora of China archive, Global Spectrum XLSX | `research_source`, `evidence`, taxonomy/trait candidate rows | Record file hash and dataset version; retain raw archive unchanged. Transform to candidates first and preserve row/worksheet IDs, not just normalized labels. |
| GBIF candidate CSV/JSONL, USDA ZIP, fatty-acid papers/extracts, source PDF and screenshots | `research_source`, `evidence`, `research_sample`/`candidate_record` | Keep source files and extracted locators. Unmatched literature samples are retained as unlinked samples; no association is inferred from plant name alone. |

## Repeatable migration protocol

1. Verify a source backup checksum and restore it into a fresh isolated source-snapshot database. Record dump time, source server version, schema/table counts, and read-only grants.
2. Run a deterministic dry run keyed by `(source_system, source_table, source_primary_key, source_content_hash)`. Do not write into the production v2 target during this step.
3. Emit a report with per-table source count, mapped count, duplicate/collision count, unlinked count, status distribution, unit/basis gaps, broken file references, and preserved NULL actors.
4. Require invariants: mapped + explicitly held/unmapped = source rows; no source hash changes; no candidate status promotion; no reviewer fabrication; no NULL-to-zero conversions; no sample cross-link without evidence.
5. Restore the snapshot to a new target and apply Flyway there. Import inside a transaction where practical; for large batches record a durable import batch ID and idempotency key.
6. Compare source and target counts, status distributions, sum/checksums of keys and numeric values, source locator coverage, foreign-key orphans, and review history. Write CSV/JSON diff reports under `data/processed/migration-runs/<run-id>/`.
7. Verify target restore from the migration backup before treating it as acceptable. Keep all source snapshots and original files read-only.

An adapter-based import will be implemented after the first dry run. The phase-one `GET /api/v1/plants` reads verified rows from a clone of the legacy database; it is a runtime proof, not a v2 migration.
