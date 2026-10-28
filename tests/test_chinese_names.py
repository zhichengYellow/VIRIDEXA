import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts/pipeline'))
from enrich_chinese_names import accepted_names
class ChineseNamesTests(unittest.TestCase):
 def row(self,**kw):return {'scientificName':'Camellia oleifera','canonicalName':'Camellia oleifera','vernacularName':'油茶','taxonID':'accepted-1','taxonomicStatus':'accepted','taxonRank':'Species',**kw}
 def test_exact_species_keeps_multiple_source_names(self):
  names,reason=accepted_names('Camellia oleifera',{'Message':'Success','Data':[self.row(),self.row(vernacularName='普通油茶')]});self.assertEqual(names,[('普通油茶','accepted-1'),('油茶','accepted-1')]);self.assertIsNone(reason)
 def test_genus_and_infraspecific_names_never_inherit_species_common_name(self):
  for n in ['Camellia','Camellia oleifera var. monosperma','Camellia × test']:
   self.assertEqual(accepted_names(n,{'Message':'Success','Data':[self.row()]})[0],[])
 def test_synonym_and_near_name_are_not_exact_accepted_match(self):
  for row in [self.row(taxonomicStatus='synonym'),self.row(canonicalName='Camellia japonica'),self.row(taxonRank='Genus'),self.row(vernacularName='Camellia oleifera')]:self.assertFalse(accepted_names('Camellia oleifera',{'Message':'Success','Data':[row]})[0])
 def test_conflicting_accepted_ids_are_quarantined(self):
  self.assertEqual(accepted_names('Camellia oleifera',{'Message':'Success','Data':[self.row(),self.row(taxonID='other')]})[1],'CONFLICTING_ACCEPTED_IDENTITIES')
 def test_upstream_quota_is_not_a_missing_name(self):
  self.assertEqual(accepted_names('Camellia oleifera',{'Message':'Request times Error，超过每天最大可访问次数:100'})[1],'UPSTREAM_QUOTA_EXHAUSTED')
if __name__=='__main__':unittest.main()
