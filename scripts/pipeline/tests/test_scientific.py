import sys
import unittest
import tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scientific import normalize, canonical, digest, scan, records

class ScientificTests(unittest.TestCase):
    def lipid(self, **kw):
        row = dict(sample_id='study-local-1', basis='oil mass', measurement_kind='OIL_FA_COMPOSITION', unit='%', value='12.3')
        row.update(kw)
        return normalize(row, 'LIPID')
    def test_missing_name(self):
        self.assertEqual(normalize({}, 'TAXONOMY')[1], 'MISSING_SCIENTIFIC_NAME')
    def test_no_invented_chinese(self):
        self.assertIsNone(normalize({'scientificName':'Cycas revoluta'}, 'TAXONOMY')[0]['vernacularName'])
    def test_synonym_reference_preserved(self):
        self.assertEqual(normalize({'scientificName':'A b','acceptedNameUsageID':'123'}, 'TAXONOMY')[0]['acceptedNameUsageID'], '123')
    def test_unknown_trait_held(self):
        self.assertEqual(normalize({'measurementType':'magic'}, 'MORPHOLOGY')[1], 'UNMAPPED_TRAIT')
    def test_trait_raw_not_mutated(self):
        row = dict(measurementType='leaf shape',measurementValue=' ovate ')
        self.assertEqual(normalize(row,'MORPHOLOGY')[0]['value'],'ovate')
        self.assertEqual(row['measurementValue'], ' ovate ')
    def test_missing_value_stays_null(self):
        self.assertIsNone(self.lipid(value='NA')[0]['value'])
    def test_zero_is_zero(self):
        self.assertEqual(self.lipid(value='0')[0]['value'], '0')
    def test_incompatible_unit(self):
        self.assertEqual(self.lipid(unit='mg/100g')[1], 'INCOMPATIBLE_UNIT')
    def test_no_forced_identity(self):
        self.assertEqual(self.lipid()[0]['linkage_state'],'UNLINKED')
    def test_invalid_number(self):
        for v in ('NaN','Infinity','-1','101','abc'):
            self.assertIsNotNone(self.lipid(value=v)[1])
    def test_distinct_bases(self):
        self.assertNotEqual(self.lipid()[0], self.lipid(measurement_kind='FAME_COMPOSITION')[0])
    def test_unknown_basis(self):
        self.assertEqual(self.lipid(basis='')[1], 'MISSING_SAMPLE_OR_BASIS')
    def test_content_fingerprint(self):
        self.assertEqual(digest(canonical({'b':1,'a':2})),digest(canonical({'a':2,'b':1})))
        self.assertNotEqual(digest('v1'),digest('v2'))
    def test_csv_locator_order(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'test.csv'; p.write_text('scientificName\nCycas revoluta\n')
            self.assertEqual(list(records(p)),[{'scientificName':'Cycas revoluta'}])

    def test_source_hash_mismatch_and_missing(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); (root/'data').mkdir(); (root/'data/a.csv').write_text('real source')
            manifest=root/'manifest.md'
            manifest.write_text('| data/a.csv | /old/location | 11 | '+digest('real source')+' | |\n| data/missing.csv | /old/location | 0 | '+digest('')+' | |\n')
            self.assertEqual([r['state'] for r in scan(root,manifest)],['VERIFIED','MISSING'])
            (root/'data/a.csv').write_text('changed source')
            self.assertEqual(scan(root,manifest)[0]['state'],'MISMATCH')

if __name__=='__main__': unittest.main()
