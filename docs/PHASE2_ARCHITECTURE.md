# Scientific knowledge engine

## Trust boundary

Protected input files are read-only and outside Git. The 292-entry existing manifest is verified before source parsing and again before any replay. Reports and large staging spools are written only under repository `reports/phase2`; spools are ignored by Git. No archive is extracted over an input file, no cleanup targets the protected tree, and no ingestion writes a review decision.

`source_botany.extract` and `source_lipid.extract` provide small source-specific adapters. `run_science.py` owns identity keys, provenance, transactions, duplicate checks, reports and persistence. ZIP members are read as streams. WFO is a target slice selected from actual course names and expanded through native parent/synonym identifiers; the entire name/taxon/synonym members are scanned, with scope exclusions counted separately. WFO binomial matching is explicitly a machine candidate rule and is not authoritative identity resolution. NHM and Flora of China snapshots are kept as independent sources even where they overlap.

## Storage and lineage

- V3: `scientific_source_version` identifies path + byte hash; `scientific_candidate` preserves raw row, locator, normalized payload, content hash, held reason and pipeline. `scientific_decision` requires an actual `app_user` FK, rationale and decision timestamp. Latest decision controls release.
- V4: independent `scientific_sample` uses research study + native sample key. Oil content and composition from the same literature sample share that key. `scientific_lipid_observation` has an exact candidate FK and sample FK, type, analyte, nullable numeric value, raw value, unit and original basis. No scientific plant identity is forced on these samples.
- V5: `scientific_evidence_link` attaches original PDFs only where source declarations identify the document and its hash matches the protected manifest. Page/table/row fields are copied from actual extraction metadata. No citation is inferred from a similar filename.
- V7: full normalization reason text is preserved (255 characters), including source-specific validation failures.
- V6: the publication view requires a latest named APPROVED decision, a normalized candidate, permitted license and verified integrity for both the extraction source and every explicitly linked supporting document. Unknown license withholds publication.

Candidate IDs derive from immutable source version + pipeline + exact record locator. Changed source bytes produce new versions and candidates, retaining older rows and reviews. A different normalization result at the same immutable locator is detected as a content conflict; it is never used to overwrite an existing reviewed candidate. A future extraction-rule revision must be explicitly versioned before importing changed output for unchanged inputs.

Every per-field numeric conversion remains conservative: raw text is retained, decimal input is checked, incompatible types are held, and absent values remain SQL NULL. Four lipid kinds remain separate: OIL_CONTENT, OIL_FA_COMPOSITION, FAME_COMPOSITION and FOOD_NUTRIENT. USDA entries are food nutrient amounts per food basis and are never seed-oil composition. No cross-basis comparison or percent conversion is inferred.

## Transaction and replay protocol

```sh
python3 scripts/pipeline/scientific.py --dry-run
python3 scripts/pipeline/run_science.py --dry-run --output reports/phase2/delivery-dry
python3 scripts/pipeline/run_science.py --apply --replay reports/phase2/delivery-dry --batch-size 1000 --output reports/phase2/delivery-applied
python3 scripts/pipeline/run_science.py --apply --replay reports/phase2/delivery-dry --batch-size 1000 --output reports/phase2/delivery-replayed
python3 scripts/pipeline/collect_quality.py
```

Replay verifies the staging file checksum and all original file hashes. SQL uses MySQL JSON_TABLE bulk INSERT SELECT with primary-key anti-joins; foreign keys, unique keys, checks and strict conversion remain enabled. All related writes in a batch commit together. A failed batch rolls back on connection termination; earlier committed batches are safe to replay. Per-run reports record committed batches and content checksums. Database fingerprints cover full candidate contents, provenance, sources, decisions, sample state and observations, rather than just row counts. A conflicting transformation yields a failed report and leaves prior conclusions intact.

The initial row-by-row import was interrupted deliberately to replace it with the verified bulk implementation. Its completed batches were preserved; replay continues against them without deleting target data. Its diagnostic log remains separately labelled.

## Access and reusable contracts

The API uses `viridexa_ro` with SELECT only and a readonly datasource. Flyway uses `viridexa_migrator`. After migrations, `provision_science_importer.py` grants the ingestion account INSERT only on scientific source/candidate/sample/observation/evidence tables; it cannot UPDATE or insert named decisions. This deliberately retires the broad historical Phase 1 ingestion grants. Old import scripts must not be run with the narrowed Phase 2 identity.

Public `/api/v1/science` endpoints query the common publication view: facts, plants, morphology conditions, measurements, coverage, lineage/source and evidence relationships. No candidate is exposed as a confirmed fact. Existing plant endpoints also require public/approved identity, a latest named review and permitted source. Empty public scientific results are expected until real review and license review happen.

`/api/v1/admin/science/{quality,candidates,sources}` requires a database-backed ADMIN login via HTTP Basic; BCrypt hashes and enabled state are checked against existing users. No administrator or reviewer account is fabricated. Other roles cannot inspect this interface. HTTP Basic must be placed behind TLS outside local loopback deployment. Review writing is not exposed through the readonly API; decisions require a separately authorized scientific-review workflow.

`ScientificService` is the shared read boundary for a future Agent. DTOs include Fact, Coverage, GraphNode, GraphEdge and SampleComparison. Relations currently describe explicit evidence links; unverified species/sample associations do not create graph edges. No graph/vector database or inference framework is introduced.

## Known boundaries

This phase does not claim full WFO normalization, complete historical snapshot normalization, automatic human review, or resolved plant links for all research samples. Historical source rows remain preserved in `source_record`; the quality report classifies unnormalized ledger entries as missing a dedicated historical adapter. Some API payloads retain source-specific JSON, so future consumers should use the typed envelope and basis fields rather than assume uniform botanical rank/trait vocabularies. Fully typed per-trait analytics and a review-write UI remain follow-on work.
