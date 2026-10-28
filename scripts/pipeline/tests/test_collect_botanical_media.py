import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("media", Path(__file__).parents[1] / "collect_botanical_media.py")
media = importlib.util.module_from_spec(spec); spec.loader.exec_module(media)

class CollectorPolicyTest(unittest.TestCase):
    def test_license_and_host_allowlist(self):
        good = {"url": "https://inaturalist-open-data.s3.amazonaws.com/photos/1/original.jpg", "license_code": "cc-by"}
        self.assertTrue(media.approved_photo(good))
        self.assertFalse(media.approved_photo({**good, "license_code": "cc-by-nc"}))
        self.assertFalse(media.approved_photo({**good, "url": "https://evil.example/image.jpg"}))
        self.assertFalse(media.approved_photo({**good, "url": "http://inaturalist-open-data.s3.amazonaws.com/a.jpg"}))

    def test_exact_species_identity(self):
        self.assertTrue(media.bare_binomial("Ginkgo biloba"))
        self.assertFalse(media.bare_binomial("Ginkgo biloba L."))
        self.assertFalse(media.bare_binomial("Camellia × oleifera"))

    def test_limit_is_bounded(self):
        for value in (0, 31):
            with self.subTest(limit=value), self.assertRaises(ValueError):
                media.collect("Ginkgo biloba", 2, value)
        with self.assertRaises(ValueError): media.collect("Ginkgo biloba", 0)
        with self.assertRaises(ValueError): media.collect("Ginkgo biloba L.", 2)
