# Data sources and interpretation

VIRIDEXA keeps source statements, extracted records, candidate assertions, and human decisions distinguishable. Presence in the catalog does not certify a taxon, measurement, or identity. Import scope and source-specific caveats are maintained in the [import pipeline](IMPORT_PIPELINE.md), [showcase acceptance record](SHOWCASE_ACCEPTANCE.md), and dated audit reports.

## Current public baseline

The v0.1.0 local database contains 60,031 plant rows and 60,028 public catalogue entries. These are not a verified species count. V24 records 74 iFlora name assertions (upstream release unspecified) and 23,670 exact accepted-species matches from the user-supplied **Catalogue of Life China 2022** workbook. Names overlap; they must not be summed as distinct plants. In total 23,704 catalogue entries have traceable Chinese names (39.49%); 36,327 remain unmatched. No source is relabelled as 2026. Formal scientific facts remain 0.

The workbook contains 301,674 data rows across kingdoms. Strict filtering retains 38,787 eligible Plantae accepted-species rows with valid Chinese names; 178,296 rows belong to other kingdoms, 23,607 are non-species ranks, 60,583 are synonyms, 210 have no valid Chinese name and 191 fail the strict canonical-species check. Of 60,028 public catalogue entries, 23,670 match exactly and 36,358 do not. Some unmatched entries already have course/iFlora names, explaining the coverage difference. Synonym inference is deliberately not attempted for this workbook. SHA-256: `d566a32862eda72fdc4b48cb236e5b9e9322ae5b775be7ff9d7980761a705257`.

Only factual name metadata is indexed for display; the full SP2000 workbook is ignored and not redistributed. [SP2000](http://www.sp2000.org.cn/), [iFlora API](https://api.iflora.cn/) and the original per-row evidence remain the source of attribution. Existing reliable preferred names are preserved, and repeated assertions retain separate source history. Current additional distinct Chinese aliases are 0; repeated identical names from multiple sources are not counted as aliases. Existing Latin-name and synonym search remains available.

Existing source families include course-PDF extracts, Flora of China/eFloras and NHM taxonomic materials, WFO relationship data, GBIF records, USDA food-composition data, and literature-derived measurements. They have different schemas, scopes, rights, and meanings. For example, the WFO rows are a target relationship slice, not a global flora; NHM and Flora of China counts can overlap; food-composition values are not seed-oil measurements. Consult the source audit before comparing or summing counts.

## Provenance and review

Every claim should retain its source locator, source version where known, extraction method, and review state. Machine extraction is not human verification. The named review workflow is the authority for formal scientific decisions; the current formal-fact count is zero. Unknown license states remain unknown and must not be treated as permission to republish.

## Local data

Protected raw inputs and derived files live under ignored `data/`; clean clones do not need them to start the web showcase. See [DATA_LAYOUT.md](DATA_LAYOUT.md) for expected locations and handling rules. Do not use legacy database snapshots as current migrations or infer that an empty placeholder directory means a source has been downloaded.

## Release work and media

V24 Chinese-name provenance and V25 local media persistence have been applied. There are 21 iNaturalist photos across 17 exact-species catalogue entries (18 CC BY, 3 CC0), plus 3 licensed static fallback images. The union covers 19 catalogue entries, including two distinct Ginkgo name records; this is not a distinct-species coverage claim. Identity conflicts are 0 under the implemented exact-name rule. See [media attribution](MEDIA_ATTRIBUTION.md) and the [release record](VIRIDEXA_V0.1.0_RELEASE.md).
