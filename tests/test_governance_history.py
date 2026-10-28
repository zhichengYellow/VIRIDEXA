"""Integration checks against the real local Phase 2B snapshot database."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts" / "pipeline"))
import governance_history as gh


class GovernanceHistoryLiveDatabaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records = gh.fetch_records()
        cls.candidates = gh.fetch_candidates()
        cls.mappings, cls.links = gh.map_records(cls.records, cls.candidates)

    def test_real_source_record_inventory_and_allowed_classes(self):
        self.assertEqual(len(self.records), 6554)
        self.assertTrue(all(m["classification"] in gh.CLASSES for m in self.mappings))
        self.assertEqual(len({m["source_record_key"] for m in self.mappings}), len(self.records))

    def test_historical_88_oil_rows_and_six_anonymous_confirmations(self):
        oils = [r for r in self.records if r["source_table_name"] == "plant_oil_samples"
                and (r["payload"] or {}).get("oil_content_status") == "VERIFIED"]
        reviews = [r["payload"] for r in self.records if r["source_table_name"] == "knowledge_review_events"]
        confirmed = [p for p in reviews if p.get("action") == "UPDATE" and
                     (p.get("after_data") or {}).get("verification_status") == "CONFIRMED" and
                     str(p.get("entity_id")) in {"229","230","231","232","233","235"}]
        self.assertEqual(len(oils), 88)
        self.assertEqual(len(confirmed), 6)
        self.assertTrue(all(p.get("actor_user_id") is None and p.get("historical_actor_ref") is None for p in confirmed))

    def test_dry_run_is_deterministic_and_links_are_unique(self):
        again, links_again = gh.map_records(self.records, self.candidates)
        self.assertEqual(self.mappings, again)
        self.assertEqual(self.links, links_again)
        self.assertEqual(len({x["id"] for x in self.links}), len(self.links))
        self.assertTrue(all(x["relation"] in {"OIL_CONTENT_VALUE_EXACT", "SAME_SAMPLE_SOURCE_EVIDENCE", "EXACT_SOURCE_KEY_EVIDENCE"} for x in self.links))
        oil_links = [x for x in self.links if x["relation"] == "OIL_CONTENT_VALUE_EXACT"]
        self.assertEqual(len(oil_links), 88)

    def test_six_reviewable_trait_candidates_are_deterministic_and_pending(self):
        rows = gh.build_trait_candidates(self.records)
        again = gh.build_trait_candidates(self.records)
        self.assertEqual(rows, again)
        self.assertEqual(len(rows), 6)
        self.assertTrue(all(r["pipeline"] == "MORPHOLOGY" for r in rows))
        self.assertTrue(all(r["raw"]["historical_review_state"] == "REVIEW_PENDING_NAMED_REVIEW" for r in rows))
        self.assertTrue(all(r["raw"]["source_native_identity"] for r in rows))
        self.assertTrue(all(r["raw"]["historical_mapping_source_record_key"] for r in rows))
        self.assertTrue(all(r["classification"] == "CANDIDATE" for r in rows))


if __name__ == "__main__":
    unittest.main()
