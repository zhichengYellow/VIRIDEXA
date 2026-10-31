import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from compatibility import ordered,portable_view,trait_view,trait_count_view,trait_support,portable_table
class CompatibilityTests(unittest.TestCase):
 def test_dependency_order_and_cycles(self):
  self.assertEqual(ordered(['child','parent'],{'child':{'parent'}}),['parent','child'])
  with self.assertRaises(ValueError):ordered(['a','b'],{'a':{'b'},'b':{'a'}})
 def test_definer_removed_without_dropping_visibility(self):
  sql='CREATE ALGORITHM=UNDEFINED DEFINER=`old`@`%` SQL SECURITY DEFINER VIEW `v` AS SELECT * FROM `viridexa`.`plant` WHERE is_public=1'
  result=portable_view(sql,'viridexa');self.assertNotIn('`old`',result);self.assertNotIn('`viridexa`.',result);self.assertIn('WHERE is_public=1',result)
 def test_unknown_json_table_is_not_silently_removed(self):
  with self.assertRaises(ValueError):portable_view('CREATE VIEW x AS SELECT JSON_TABLE(x)', 'viridexa')
 def test_traits_preserve_ordinal_and_explicit_null_handling(self):
  sql=trait_view();self.assertNotIn('JSON_TABLE',sql);self.assertIn('n.n+1 AS ordinality',sql);self.assertIn("('NULL','OBJECT','ARRAY')",sql);self.assertIn('source_term',sql);self.assertIn('graph_trait',trait_count_view())
 def test_capacity_guard_rejects_future_loss_instead_of_truncating(self):
  sql=trait_support();self.assertIn('CHECK',sql);self.assertIn('<=1000',sql)
  self.assertEqual(sql.count('CREATE TABLE'),1)
  with self.assertRaises(ValueError):trait_support(0)
 def test_ascii_key_compatibility_preserves_binary_and_ascii_domain(self):
  ddl="CREATE TABLE `t` (\n  `key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,\n  PRIMARY KEY (`key`)\n) ENGINE=InnoDB"
  result=portable_table(ddl)
  self.assertNotIn('CHARACTER SET ascii',result)
  self.assertIn('CHARACTER SET utf8mb4 COLLATE utf8mb4_bin',result)
  self.assertIn('CHAR_LENGTH(`key`)=OCTET_LENGTH(`key`)',result)
  self.assertIn('PRIMARY KEY (`key`)',result)
if __name__=='__main__':unittest.main()
