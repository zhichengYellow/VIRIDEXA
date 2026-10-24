import unittest
import json
import tempfile
from pathlib import Path

from scripts.pipeline.source_botany import _taxonomy, extract


DATA = Path("/Users/binyellow/VIRIDEXA-protected")


class SourceBotanyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stats = {}
        cls.rows = list(extract(DATA, cls.stats))

    def test_taxonomy_missing_name_is_held(self):
        normalized, reason = _taxonomy(None, "ACCEPTED")
        self.assertIsNone(normalized)
        self.assertEqual(reason, "missing scientific name")

    def test_course_and_mapping_ledgers_are_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "data"
            root.mkdir()
            (root / "plants.json").write_text(json.dumps({"plants": [{
                "scientific_name": "Plantus testus L.", "scientific_name_status": "VERIFIED_FROM_PRINTED_HEADING",
                "name_zh": "测试植物", "source_key": "p1", "data_source": "course.pdf",
                "source_pdf_sha256": "abc", "source_pdf_pages": [1]}]}), encoding="utf-8")
            (root / "nhm_taxon_identity_review_20261011.csv").write_text("requested_name,state\nPlantus testus,REVIEW\n", encoding="utf-8")
            (root / "nhm_taxon_import_manifest_20261011.csv").write_text("taxon,wfo\nPlantus testus,wfo-1\n", encoding="utf-8")
            rows = list(extract(root))
            course = next(row for row in rows if row["source_path"].endswith("plants.json"))
            self.assertEqual(course["normalized"]["name_zh"], "测试植物")
            self.assertEqual(course["normalized"]["review_status"], "PENDING_HUMAN_REVIEW")
            ledger = [row for row in rows if row["source_path"].endswith(".csv")]
            self.assertEqual(len(ledger), 2)
            self.assertTrue(all(row["normalized"] is None for row in ledger))

    def test_real_gbif_taxonomy_records_are_located_and_unapproved(self):
        rows = [r for r in self.rows if r["source_path"].endswith("match_candidates_20261011.jsonl")]
        self.assertTrue(rows)
        row = rows[0]
        self.assertEqual(row["pipeline"], "TAXONOMY")
        self.assertGreater(row["locator"]["record_ordinal"], 0)
        self.assertEqual(row["normalized"]["review_status"], "PENDING_HUMAN_REVIEW")

    def test_real_trait_archives_yield_morphology(self):
        rows = [r for r in self.rows if r["pipeline"] == "MORPHOLOGY" and r["normalized"]]
        self.assertTrue(any("combined.zip" in r["source_path"] for r in rows))
        self.assertTrue(any("flora_of_china.zip" in r["source_path"] for r in rows))
        self.assertTrue(rows[0]["locator"].get("archive_member"))
        self.assertTrue(rows[0]["normalized"]["traits"])

    def test_real_wfo_slice_reports_scan_counts_and_candidates_for_review(self):
        rows = [r for r in self.rows if "wfo_plantlist" in r["source_path"]]
        self.assertTrue(rows)
        self.assertTrue(all(r["normalized"]["review_status"] == "PENDING_HUMAN_REVIEW" for r in rows))
        self.assertIn("data/raw/wfo/", rows[0]["source_path"])
        for member in ("name.tsv", "taxon.tsv", "synonym.tsv"):
            self.assertGreater(self.stats["wfo"][member]["scanned"], 0)


class LocalLedgerTests(unittest.TestCase):
    def test_course_and_mapping_ledgers_in_isolated_fixture(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "data"
            root.mkdir()
            (root / "plants.json").write_text(json.dumps({"plants": [{
                "scientific_name": "Plantus testus L.", "scientific_name_status": "VERIFIED_FROM_PRINTED_HEADING",
                "name_zh": "测试植物", "source_key": "p1", "data_source": "course.pdf",
                "source_pdf_sha256": "abc", "source_pdf_pages": [1]}]}), encoding="utf-8")
            (root / "nhm_taxon_identity_review_20261011.csv").write_text("requested_name,state\nPlantus testus,REVIEW\n", encoding="utf-8")
            (root / "nhm_taxon_import_manifest_20261011.csv").write_text("taxon,wfo\nPlantus testus,wfo-1\n", encoding="utf-8")
            rows = list(extract(root))
            course = next(row for row in rows if row["source_path"].endswith("plants.json"))
            self.assertEqual(course["normalized"]["name_zh"], "测试植物")
            self.assertEqual(course["normalized"]["review_status"], "PENDING_HUMAN_REVIEW")
            ledger = [row for row in rows if row["source_path"].endswith(".csv")]
            self.assertEqual(len(ledger), 2)
            self.assertTrue(all(row["normalized"] is None for row in ledger))


if __name__ == "__main__":
    unittest.main()
