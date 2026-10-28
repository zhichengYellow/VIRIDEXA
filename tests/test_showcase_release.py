import importlib.util,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts/pipeline'))
from release_showcase import eligible
from index_showcase_catalog import valid
class ShowcaseReleasePolicy(unittest.TestCase):
 def row(self):return dict(dataset='data/raw/nhm_traits/combined.zip',integrity='VERIFIED',license='UNKNOWN',state='CANDIDATE',normalized={'scientific_name':'Ginkgo biloba','traits':{'habit':'tree'}},decision=False,review=None,pipeline='MORPHOLOGY')
 def test_public_visibility_is_not_human_verification(self):
  r=self.row();self.assertIsNone(eligible(r));self.assertEqual('CANDIDATE',r['state']);self.assertEqual('UNKNOWN',r['license'])
 def test_rejected_and_revoked_sources_cannot_be_released(self):
  for state in ['APPROVED','REJECTED','NEEDS_EVIDENCE','REVOKED']:
   r=self.row();r['review']=state;self.assertEqual('EXISTING_REVIEW_DECISION',eligible(r))
 def test_long_source_prose_is_not_bulk_republished(self):
  r=self.row();r['normalized']['traits']['description']='x'*301;self.assertEqual('LONG_TEXT_NOT_PUBLIC',eligible(r))
 def test_missing_values_are_not_silently_zeroed(self):
  r=self.row();r.update(pipeline='LIPID',dataset='data/oil_samples.json',normalized={'value':None,'basis':'dry','unit':'%','sample_id':'s','study_id':'study'});self.assertEqual('MISSING_MEASUREMENT_BASIS',eligible(r));r['normalized']['value']=0;self.assertIsNone(eligible(r))
 def test_unknown_legacy_and_held_rows_are_excluded(self):
  r=self.row();r['dataset']='LEGACY_SNAPSHOT';self.assertEqual('NO_STRUCTURED_DISPLAY_BASIS',eligible(r));r=self.row();r['state']='HELD';self.assertEqual('HELD_OR_UNNORMALIZED',eligible(r))
 def test_source_integrity_and_restriction_are_enforced(self):
  r=self.row();r['integrity']='MISMATCH';self.assertEqual('SOURCE_INTEGRITY',eligible(r));r=self.row();r['license']='RESTRICTED';self.assertEqual('RESTRICTED_LICENSE',eligible(r))
 def test_catalog_names_do_not_create_identifications(self):
  for name in ['unknown','Ginkgo','银杏','<script>alert(1)</script>','Ginkgo sp.']:self.assertFalse(valid(name))
  self.assertTrue(valid('Ginkgo biloba'));self.assertTrue(valid('Ginkgo biloba L.'))
if __name__=='__main__':unittest.main()
