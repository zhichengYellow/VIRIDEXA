import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts/pipeline'))
from import_sp2000_names import qualify
class Sp2000NamesTest(unittest.TestCase):
 def row(self,**changes):
  value={'界':'Plantae','等级':'种','名称状态':'接受名','拉丁名':'Ginkgo biloba','种':'Ginkgo biloba','中文名':'银杏','code':'source-id'};value.update(changes);return value
 def test_exact_species_has_source_identity(self):
  result,reason=qualify(self.row());self.assertIsNone(reason);self.assertEqual(result,{'scientificName':'Ginkgo biloba','nameZh':'银杏','authorityId':'source-id'})
 def test_genus_name_cannot_be_inherited(self):
  self.assertEqual(qualify(self.row(等级='属',中文名='银杏属'))[1],'NON_SPECIES')
 def test_synonyms_and_other_kingdom_are_not_guessed(self):
  self.assertEqual(qualify(self.row(名称状态='异名'))[1],'NOT_ACCEPTED');self.assertEqual(qualify(self.row(界='Animalia'))[1],'OTHER_KINGDOM')
 def test_missing_or_latin_chinese_name_remains_missing(self):
  for value in [None,'','Ginkgo biloba','银杏 Ginkgo']:
   with self.subTest(value=value):self.assertEqual(qualify(self.row(中文名=value))[1],'NO_VALID_CHINESE_NAME')
 def test_infraspecific_and_conflicting_species_columns_are_rejected(self):
  for name in ['Ginkgo biloba var. aurea','Ginkgo','Ginkgo × alba']:
   self.assertEqual(qualify(self.row(**{'拉丁名':name}))[1],'NON_CANONICAL_SPECIES')
  self.assertEqual(qualify(self.row(种='Other species'))[1],'NON_CANONICAL_SPECIES')
if __name__=='__main__':unittest.main()
