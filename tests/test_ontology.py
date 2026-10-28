import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts' / 'pipeline'))
from ontology import VOCABULARY_VERSION, project

class OntologyProjectionTests(unittest.TestCase):
    def test_reproducible(self):
        a=project('MORPHOLOGY', {'source_namespace':'NHM','normalized_traits':{'leaf_shape':['ovate']}})
        self.assertEqual(a, project('MORPHOLOGY', {'source_namespace':'NHM','normalized_traits':{'leaf_shape':['ovate']}}))
        self.assertEqual(VOCABULARY_VERSION,'lexical-vocabulary-v1')
    def test_unknown_is_retained_not_nearest_mapped(self):
        p,s,_=project('MORPHOLOGY', {'normalized_traits':{'leaf_shape':['ovate-ish']}})
        self.assertEqual(s,'PARTIAL_UNKNOWN')
        self.assertEqual(p['traits']['leaf_shape'],[{'source_term':'ovate-ish','concept':None}])
    def test_missing_chinese_is_null(self):
        p,_,coverage=project('TAXONOMY', {'scientific_name':'Abies alba'})
        self.assertIsNone(p['chinese_name'])
        self.assertEqual(p['chinese_name_state'],'NOT_REPORTED')
        self.assertFalse(coverage['family'])
    def test_native_id_keeps_namespace(self):
        p,_,_=project('TAXONOMY', {'source_namespace':'WFO','source_taxon_id':'123','parent_taxon_id':'456','accepted_taxon_id':'789'})
        self.assertEqual(p['source_native_ids']['source_taxon_id'],{'namespace':'WFO','id':'123'})
        self.assertEqual(p['taxonomy']['parent_native_id'],{'namespace':'WFO','id':'456'})
        self.assertEqual(p['source_native_ids']['accepted_taxon_id'],{'namespace':'WFO','id':'789'})
    def test_lipid_basis_and_aliases(self):
        a,_,_=project('LIPID', {'kind':'FAME_COMPOSITION','value':12,'unit':'%','basis':'FAME basis','analyte':'C18:1'})
        b,_,_=project('LIPID', {'kind':'OIL_CONTENT','value':12,'unit':'%','basis':'as-reported oil content'})
        self.assertEqual(a['lipid']['basis'],'FAME basis'); self.assertEqual(a['lipid']['kind'],'FAME_COMPOSITION')
        self.assertEqual(b['lipid']['kind'],'OIL_CONTENT'); self.assertIsNone(b['lipid']['analyte'])

    def test_held_retains_unknown_raw_and_unmappable(self):
        p,state,cov=project('TAXONOMY',None,{'scientificName':'X y','mysteryField':'raw'})
        self.assertEqual(state,'UNMAPPABLE_SOURCE')
        self.assertEqual(p['unmapped_raw']['mysteryField'],'raw')
        self.assertFalse(cov['source'])

    def test_chinese_is_pending_source_report(self):
        p,_,_=project('TAXONOMY', {'name_zh':'中文名'})
        self.assertEqual(p['chinese_name_state'],'SOURCE_REPORTED_PENDING')

if __name__=='__main__': unittest.main()
