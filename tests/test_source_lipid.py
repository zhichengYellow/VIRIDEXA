import json
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts/pipeline"))
from source_lipid import _number, extract


ROOT = Path(__file__).resolve().parents[2] / "VIRIDEXA-protected"


class LipidSourceTests(unittest.TestCase):
    def test_real_sources_emit_distinct_kinds_and_provenance(self):
        records = list(extract(ROOT))
        self.assertTrue(records)
        kinds = {r["normalized"]["kind"] for r in records if r["normalized"]}
        self.assertTrue({"OIL_CONTENT", "FAME_COMPOSITION", "OIL_FA_COMPOSITION", "FOOD_NUTRIENT"} <= kinds)
        self.assertTrue(all(r["source_path"].startswith("data/") for r in records))
        locators = [(r["source_path"], json.dumps(r["locator"], sort_keys=True)) for r in records]
        self.assertEqual(len(locators), len(set(locators)))
        usda = [r for r in records if r["source_path"].endswith(".zip")]
        self.assertTrue(usda)
        self.assertTrue(all(r["normalized"]["kind"] == "FOOD_NUTRIENT" for r in usda if r["normalized"]))
        literature = [r for r in records if r["source_path"].endswith("candidates.csv") and r["normalized"]]
        self.assertTrue(all(r["normalized"]["paper"]["sha256"] and r["normalized"]["source_location"]["pdf_page"] for r in literature))
        oil = next(r for r in records if r["source_path"].endswith("literature_samples.csv") and r["normalized"])
        component = next(r for r in literature if r["normalized"]["sample_id"] == oil["normalized"]["sample_id"])
        self.assertEqual(oil["normalized"]["study_id"], component["normalized"]["study_id"])
        self.assertEqual(oil["normalized"]["paper"]["sha256"], component["normalized"]["paper"]["sha256"])
        extracted = [r for r in records if r["source_path"] == "data/fatty_acid_literature_extraction.json"]
        extracted_oil = [r for r in extracted if r["locator"]["field"] == "oil_content"]
        self.assertEqual(len(extracted_oil), 172)
        ids = {(r["normalized"]["study_id"], r["normalized"]["sample_id"]) for r in extracted_oil if r["normalized"]}
        self.assertEqual(len(ids), 172)
        self.assertTrue(all(r["raw"]["sample"].get("identity_status") for r in extracted_oil))
        self.assertTrue(all("plant_id" not in r["normalized"] for r in extracted_oil if r["normalized"]))
        self.assertTrue(any(r["normalized"] and r["normalized"]["analyte"] == "C18:1 (oleic acid)" for r in extracted if r["locator"]["field"] == "fatty_acids"))

    def test_missing_values_and_unknown_basis(self):
        self.assertEqual(_number(""), (None, "missing"))
        self.assertEqual(_number("NaN"), (None, "invalid"))
        self.assertEqual(_number("bad"), (None, "invalid"))
        self.assertEqual(_number("2.75"), (2.75, "ok"))
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); (root / "data/raw/course").mkdir(parents=True)
            pdf = root / "data/raw/course/课程设计资料.pdf"; pdf.write_bytes(b"test pdf")
            (root / "data/oil_samples.json").write_text(json.dumps({"source_pdf_sha256": hashlib.sha256(pdf.read_bytes()).hexdigest(), "samples": [{"sample_id": "x", "oil_content_pct": None}]}))
            rows = list(extract(root))
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["normalized"]["kind"], "OIL_CONTENT")
            self.assertIsNone(rows[0]["normalized"]["value"])
            self.assertIsNotNone(rows[0]["reason"])

    def test_percentage_bounds_hold(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); (root / "data/raw/course").mkdir(parents=True)
            pdf = root / "data/raw/course/课程设计资料.pdf"; pdf.write_bytes(b"test pdf")
            doc = {"source_pdf_sha256": hashlib.sha256(pdf.read_bytes()).hexdigest(), "samples": [{"sample_id": "x", "oil_content_pct": 120}]}
            (root / "data/oil_samples.json").write_text(json.dumps(doc))
            row = next(extract(root))
            self.assertIsNone(row["normalized"])
            self.assertIn("outside", row["reason"])


if __name__ == "__main__":
    unittest.main()
