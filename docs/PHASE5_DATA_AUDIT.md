# Phase 5 data audit baseline

This audit queried the live `viridexa` MySQL database read-only through `scripts/pipeline/run_science.py` using the root account selected by that runner. Credentials were read from the Docker environment and were not printed. No database state, protected source, or source row dump was changed or saved. Machine-readable counts, exact grouping values, fingerprints, and limitations are in [reports/phase5/data-audit.json](../reports/phase5/data-audit.json).

## Current scientific data

The database has 18 verified source versions, all with `license_state=UNKNOWN`. It has 95,238 candidates: 94,805 `CANDIDATE` and 433 `HELD`. Pipeline counts are 1,372 taxonomy candidates plus 173 held, 82,997 morphology candidates, and 10,436 lipid candidates plus 260 held. There are no review decisions and no published scientific facts. The 10,436 lipid observations cover 7,068 independent samples, all `UNLINKED`; the evidence-link table has 2,036 explicit document links.

The scientific sample schema has `identity_text` as TEXT and no plant foreign key. Every sample's identity text is NULL. The normalized lipid payloads also contain none of the checked explicit fields for plant part (`plant_part`, `plantPart`, `tissue`, `part`), method (`method`, `measurement_method`), or taxon identity (`taxon`, `taxon_name`, `scientific_name`, `scientificName`, `species`). These checks concern explicit per-measurement fields. 1,947 normalized lipid records retain nested `paper.method` annotations (936 KOH-methanol/GC-MS/peak-area normalization, 891 FAME/GC analysis, 120 GC-MS after methylation) (for example GC-MS/peak-area normalization). Workbench preserves these as paper-level source method notes, without applying a composition assay note to an oil-yield observation or treating it as sample-specific method confirmation. The scientific sample path still provides no asserted plant/taxon identity, part, or confirmed per-measurement method. A linkage state alone is not an identity assertion.

Observed lipid groups preserve multiple bases: FAME composition has 810 records with basis `FAME composition; header prints (%)` and 838 with `FAME GC content / %`; food nutrients have 8,400 records in `G` per 100 g food; oil content has 288 reported-percent records; oil fatty-acid composition has 100 relative-percent records. These distinct basis labels should remain queryable as recorded.

## Course ledger

The course ledger has 89 research samples, all with a plant FK and identification text; 87 have an explicit `plant_part` value and 2 do not. The parts are 58 `种仁`, 27 `种子`, and 2 `果实`. There are 89 measurements: 88 `SOURCE_CHECKED` and one `MISSING`, all `OIL_CONTENT` in `%`. The recorded methods are 74 `visual_cell_check_from_300dpi_page_render`, 11 `visual_table_row_transcription_from_300dpi_page_render`, 3 `visual_oil_cell_check_from_300dpi_page_render`, and one NULL. Analytical basis, denominator, and analyte are NULL for all. These are explicit course-ledger fields and remain separate from independent scientific sample identity.

A direct read-only join count returned `research_study=1`, `research_sample=89`, `resolved_course=89`, and `orphan_course=0`; all course samples resolve to the one study. The first draft incorrectly reported zero studies because it mistook the digest of a hash-query result for a count; this is corrected here and recorded transparently in the JSON caveats. The read-only public API `GET /api/v1/graph/meta` returned HTTP 200 with `nodeCounts={SOURCE:18, FILE:18}`, `edgeCounts={SOURCE_FILE:18}`, and `publishedFactCount=0`. Its public metadata omits PLANT; the Phase 3 acceptance report explicitly records 0 public plants (`docs/PHASE3_ACCEPTANCE.md`, lines 25–32).

## Source and publication gate

All 18 source versions have verified hashes, but all 18 licenses remain unknown. Three representative registered source paths and SHA-256 values are recorded in the JSON audit. The current public graph baseline in the Phase 3 acceptance report is 0 public plants, 18 source versions, and 18 source-file links. No source license or scientific approval should be inferred from integrity verification.

## Stable comparison baseline

The JSON audit records the six ordered content fingerprints returned by the pipeline runner for sources, candidates, decisions, scientific samples, lipid observations, and document evidence. It also records sorted-row SHA-256 rollups for `research_study`, `research_sample`, and `measurement`; the study fingerprint covers the one actual study row. These provide a before/after comparison point for the authorized Phase 5 work without storing row contents.

Schema evidence: `scientific_sample` and `scientific_lipid_observation` are created in `V4__independent_scientific_samples.sql`; source, candidate, decision, and publication gates are in `V3__scientific_evidence.sql`; course sample and measurement fields are in `V1__viridexa_core.sql`; graph projections are in `V12__course_ledger_graph_relations.sql`.
