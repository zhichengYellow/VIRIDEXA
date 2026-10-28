# Phase 3 relationship audit

Read-only audit of the live `viridexa` MySQL database against migrations V1–V8. Queries were executed through `scripts/pipeline/run_science.py:mysql()` with `scripts/pipeline` on `sys.path`. No candidate payload, evidence excerpt, measurement value, locality, identity text, or other scientific fact was selected or copied. Machine-readable counts and source fingerprints are in [`reports/phase3/relationship-audit.json`](../reports/phase3/relationship-audit.json).

## Current baseline

| Relation/table | Rows |
| --- | ---: |
| plant / plant_name | 33 / 75 |
| taxon / taxon_name | 47 / 14 |
| research_sample / measurement | 89 / 89 |
| candidate_record / evidence | 748 / 907 |
| source_asset / source_record | 275 / 6,554 |
| scientific_source_version | 18 |
| scientific_candidate | 95,238 |
| scientific_sample / scientific_lipid_observation | 7,068 / 10,436 |
| scientific_evidence_link / scientific_decision | 2,036 / 0 |
| ontology_projection | 95,238 |
| historical_mapping / historical_evidence_link | 6,554 / 359 |

All 33 plants are `REVIEW_PENDING` / `RESEARCH_PREVIEW` and linked to a taxon. The 75 plant names contain 63 `VERIFIED_FROM_PRINTED_HEADING` and 12 `NEEDS_REVIEW`; 14 taxon names are `VERIFIED_FROM_PRINTED_HEADING`. “Verified from printed heading” is a source transcription status, not a public scientific identity confirmation.

Scientific candidate states: LIPID 10,436 CANDIDATE + 260 HELD; MORPHOLOGY 82,997 CANDIDATE; TAXONOMY 1,372 CANDIDATE + 173 HELD. All 7,068 scientific samples are `UNLINKED`. All 10,436 lipid observations satisfy the live graph edge rule: observation and candidate share a source ID, the sample FK resolves, and the normalized study/sample IDs exactly match the sample’s study/sample keys. Of these, 10,013 also have matching sample and observation source IDs; the remaining 423 cross-source pairs still match both explicit study/sample keys. A source ID difference alone does not invalidate these sample edges. All 2,036 evidence links use `EXPLICIT_SOURCE_DOCUMENT` and resolve to a candidate and document source. All linked document sources, like all 18 registered sources, have `UNKNOWN` license and `VERIFIED` integrity. There are 0 scientific decisions and 0 rows in `published_scientific_fact`.

Ontology projections use `lexical-vocabulary-v1`: `SOURCE_PRESENT` 32,998, `PARTIAL_UNKNOWN` 61,807, and `UNMAPPABLE_SOURCE` 433. A projection state is vocabulary mapping metadata, not review approval or confirmation of a scientific fact.

## Public metadata boundary

No scientific facts, normalized candidate payloads, raw source strings, names that imply identity, evidence text, measurement values, or source excerpts should be exposed from this baseline. With every source license `UNKNOWN`, none of the scientific candidate chains qualifies for publication under V3/V5/V6 view constraints; the live view correctly returns zero rows. Do not publish database `path` values as public source URLs, and do not include raw payloads, locator JSON, rationale, or license notes.

A limited catalog can expose only opaque source/candidate identifiers and provenance/governance metadata, with names explicitly marked `SOURCE_REPORTED` / `PENDING` where name cataloging is needed. Such a label means a source supplied a string; it does not assert canonical identity. Avoid `CONFIRMED`, `APPROVED`, or `PUBLIC` for these candidate names. Keep catalog entries unavailable for scientific fact browsing while licenses are `UNKNOWN`.

Exact safe metadata columns to support this catalog:

- Source: `scientific_source_version.id`, `sha256`, `version_label`, `license_state`, `integrity_state`.
- Candidate reference: `scientific_candidate.id`, `source_id`, `pipeline`, `normalization_state`, `content_sha256`. For explicitly keyed sample provenance, read only `normalized_payload.study_id` and `normalized_payload.sample_id` and compare them to `scientific_sample.study_key` / `sample_key`; retain observation `candidate_id`, `source_id`, and `sample_id` as the relationship endpoints.
- Optional controlled vocabulary status: `ontology_projection.vocabulary_version`, `mapping_state`, `projection_sha256`.
- If listing source-reported names, emit only a separately derived name string with status `SOURCE_REPORTED` and a candidate/source reference; never expose `raw_payload` or claim identity confirmation.

The audited 18 source IDs and SHA-256 fingerprints are provided in the JSON report for integrity/provenance checking. SHA-256 hashes and opaque IDs are safe identifiers; dataset paths are retained in the local audit JSON solely to make the live DB rows traceable, not recommended as public metadata.

## Safe metadata relation candidates

- `source_asset.sha256 = scientific_source_version.sha256` yields 18 exact-hash edges across 275 assets and 263 distinct asset hashes; 257 assets have no matching scientific source version. The edge can support source/file provenance metadata (opaque IDs and hashes) only. It does not grant permission to expose an asset or its contents.
- The taxonomy candidates contain 536 `accepted_taxon_id` assertions, all resolvable to a candidate with the same `source_id`, `source_namespace`, and `source_taxon_id`; 84 `parent_taxon_id` assertions also resolve under those exact constraints. These are source-native identifier relationships, suitable as provenance edges. No name-similarity or cross-source plant/sample inference was used. They remain source-reported candidate links, not confirmed biological identity.
- The plant catalog gate has 0 eligible records: all 33 plants are `RESEARCH_PREVIEW` and `REVIEW_PENDING`, leaving 0 public, approved, verified-identity, permitted-source chains. Do not auto-promote preview rows. Public scientific candidates must come only from `published_scientific_fact`.

Recommended relation names if represented in a metadata graph: `SOURCE_FILE` for exact SHA-256 equality; `SOURCE_ACCEPTED_TAXON_ID` and `SOURCE_PARENT_TAXON_ID` for those same-version, same-namespace ID links. Preserve endpoint source version IDs and hashes. Do not infer `SAME_TAXON`, plant-to-sample identity, or a scientific fact edge from shared text.

## Implemented V9–V12 projection

The opening audit is the pre-implementation V1–V8 snapshot. Final implementation and live counts are recorded in [PHASE3_ACCEPTANCE.md](PHASE3_ACCEPTANCE.md). Public sources expose safe basename labels and whitelisted structured locators, never arbitrary raw locator JSON or server paths. Public candidate IDs/payloads remain inaccessible unless published; the narrower baseline recommendation above was not a permission to expose pending scientific names.

V12 additionally follows the 89 explicit `research_sample.plant_id` and `measurement.research_sample_id` foreign keys as COURSE_SAMPLE / COURSE_MEASUREMENT and HAS_RESEARCH_RECORD / HAS_MEASUREMENT. They remain administrator-only SOURCE_REPORTED / CANDIDATE, distinct from the 7,068 unlinked independent scientific samples. No name-based identity inference was introduced. Final public graph: 36 metadata nodes / 18 edges / 0 scientific facts. Administrator projection: 495,727 contextual nodes / 508,765 edges, including 392,781 per-observation trait terms.
