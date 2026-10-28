# Phase 2B historical source mapping

`scripts/pipeline/governance_history.py` inventories the immutable 6,554-row
`source_record` table and reads current scientific candidates. Its default mode
is a read-only dry run. It writes full per-row decisions and exact-key evidence
links below `reports/phase2b/history/`; `--apply` uses insert-if-absent followed by
content checks, and never updates a historical mapping or link.

Candidate links distinguish exact source-key matches from same-sample evidence.
For the 88 visually checked oil fields, `OIL_CONTENT_VALUE_EXACT` is used only
when a candidate is an `OIL_CONTENT` value with matching unit and numerically
equal raw and normalized values. Same-sample candidates with other measurements
are marked `SAME_SAMPLE_SOURCE_EVIDENCE`; that relation does not assert the oil
value. Generic nested event keys do not create candidate links. No review actor
is inferred from legacy references.

The report includes each source row hash and source asset path/hash, plus
candidate IDs, candidate source paths/hashes, locators, and content hashes.
`oil_88.jsonl` traces every snapshot row whose oil field was image-verified;
these still have whole-record `NEEDS_REVIEW` status. The six historical trait
confirmation events are in `trait_confirmed_6_history.jsonl`. Their six
deterministic pending candidate payloads are in `trait_pending_candidates.jsonl`.
They use the manifest-verified snapshot path and hash, preserve source-native
species names as unverified context, and carry the exact historical mapping
source-record key. No old confirmation creates a scientific decision. Each has no
`actor_user_id` or `historical_actor_ref`, and the following
`RECLASSIFY_PENDING_NAMED_REVIEW` events preserve the state as pending a named
reviewer.

The V8 apply inserted 6 pending morphology candidates and 6,554 immutable
historical mappings. The evidence ledger now distinguishes exact oil value
links, same-sample source evidence, exact source-key evidence, and the 12 links
from the six candidates to their original trait/event records. The immediate
insert replay had an identical science fingerprint; no decisions or review
states were created or changed.

Run the live database integration checks with:

```sh
python tests/test_governance_history.py
```

Detailed current dry-run counts are recorded in
`reports/phase2b/history/report.json`; the source payloads remain in
`source_record` and are not copied into Git.
