import hashlib
from pathlib import Path
import tempfile
import unittest
from scripts.restore_project_data import restore

class RestoreProjectDataTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.source=self.root/'backup';self.project=self.root/'project';self.source.mkdir();self.project.mkdir()
        for rel,content in [('data/raw/source.pdf',b'original'),('database/schema.sql',b'old schema')]:
            p=self.source/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(content)
        self.manifest=self.root/'manifest.md';self.manifest.write_text('\n'.join('| '+str(p.relative_to(self.source))+' | old-path | '+str(p.stat().st_size)+' | '+hashlib.sha256(p.read_bytes()).hexdigest()+' | |' for p in self.source.rglob('*') if p.is_file()))
    def test_copy_and_repeat_preserve_bytes_and_archive_boundary(self):
        first=restore(self.source,self.project,self.manifest,True)
        self.assertEqual(first['copied'],2)
        self.assertEqual((self.project/'data/backups/legacy-database/schema.sql').read_bytes(),b'old schema')
        self.assertFalse((self.project/'database').exists())
        self.assertEqual(restore(self.source,self.project,self.manifest,True)['unchanged'],2)
        self.assertEqual((self.source/'data/raw/source.pdf').read_bytes(),b'original')
    def test_different_existing_file_aborts_entire_preflight_without_overwrite(self):
        p=self.project/'data/raw/source.pdf';p.parent.mkdir(parents=True);p.write_bytes(b'existing')
        with self.assertRaises(FileExistsError):restore(self.source,self.project,self.manifest,True)
        self.assertEqual(p.read_bytes(),b'existing');self.assertFalse((self.project/'data/backups').exists())
    def test_manifest_mismatch_prevents_copy(self):
        (self.source/'data/raw/source.pdf').write_bytes(b'changed')
        with self.assertRaises(ValueError):restore(self.source,self.project,self.manifest,True)
        self.assertFalse((self.project/'data').exists())
    def test_symlink_cannot_redirect_destination(self):
        other=self.root/'elsewhere';other.mkdir();(self.project/'data').symlink_to(other,target_is_directory=True)
        with self.assertRaises(ValueError):restore(self.source,self.project,self.manifest,True)
        self.assertEqual(list(other.iterdir()),[])
    def test_dry_run_does_not_write(self):
        self.assertEqual(restore(self.source,self.project,self.manifest)['copied'],2)
        self.assertEqual(list(self.project.iterdir()),[])
if __name__=='__main__':unittest.main()
