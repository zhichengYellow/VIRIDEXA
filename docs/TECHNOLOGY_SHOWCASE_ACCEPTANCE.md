# Technology Showcase acceptance · 2026-10-28

## Delivered

`/technology` now exposes the architecture by default, a dependency-based stack ribbon, chapter navigation, nine source-family introductions, an evidence-lineage narrative, dated engineering evidence, existing live statistics and module links, and author-provided contact/support images.

Source coverage: World Flora Online, NHM, Flora of China, GBIF, SP2000 2022, iFlora, USDA FoodData Central, course/research documents, and iNaturalist media. Cards explain actual usage and limitations, with institution links. Downloaded files are not described as fully imported. Chinese-name and media records have independent registration semantics; scientific source-version counts do not measure all name/media coverage.

Source visibility, machine extraction and human verification remain separate. Historical 341-test acceptance and the dated deployment-preparation snapshot are labelled separately from this UI change. Live API values remain API-driven and missing values are not replaced by demonstration numbers.

## This change: executed validation

- Vue type check and production build: passed. Existing ECharts large lazy-chunk warning remains. No animation or UI dependency added.
- Frontend tests: 52 passed, 0 failed, 0 skipped (includes two source-profile contract tests).
- Browser: 1280×720 desktop and 375×812 mobile; source chapter navigation, contact expansion/collapse and both original PNG loads passed. No horizontal document overflow at either size.
- QR assets: 233130 and 166971 bytes; both SHA-256 comparisons against user-supplied originals matched. Original data bytes unchanged.
- Compose inspection: API and MySQL healthy, web running. Backend code and schema unchanged; full Maven/MySQL regression was not rerun for this presentation-only change. No new scientific review or publication performed.

## Visual evidence

- [Desktop sources](technology-evidence/sources-desktop.png)
- [Mobile sources](technology-evidence/sources-mobile.png)
- [Desktop contact, expanded](technology-evidence/contact-desktop.png)
- [Mobile contact, expanded](technology-evidence/contact-mobile.png)
- [Mobile contact, collapsed](technology-evidence/contact-mobile-collapsed.png)

## Boundaries

The new dated check snapshot is not a new production deployment approval. Existing deployment disk-headroom and final integration gates remain. The user-provided QR codes are deliberately public on this page; the rest of `data/` stays protected. No GitHub push performed.
