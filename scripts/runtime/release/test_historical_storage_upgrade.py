"""Fail-closed offline contracts for the published IE2 JAR historical-world seed."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

SOURCE=Path(__file__).with_name('run.py')
SPEC=importlib.util.spec_from_file_location('ie2_historical_native_oracle',SOURCE)
assert SPEC is not None and SPEC.loader is not None
oracle=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(oracle)


class HistoricalStorageSeedTests(unittest.TestCase):
    def setUp(self):
        folder=tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.path=Path(folder.name)/'historic.jar'
        with ZipFile(self.path,'w') as archive:
            archive.writestr('plugin.yml','name: InfinityExpansion2\nversion: 2.0.17\n')
        self.original=self.path.read_bytes()
        self.digest=hashlib.sha256(self.original).hexdigest()

    def verify(self):
        return oracle.verify_historical_ie2_jar(
            self.path,len(self.original),self.digest)

    def test_good_pinned_plugin_jar_is_read_only(self):
        self.assertEqual(self.verify(),self.digest)
        self.assertEqual(self.path.read_bytes(),self.original)

    def test_modified_bytes_are_never_mistaken_for_historical_release(self):
        self.path.write_bytes(self.original+b'!')
        with self.assertRaisesRegex(AssertionError,'byte size'):
            self.verify()
        self.path.write_bytes(self.original[:-1]+b'X')
        with self.assertRaisesRegex(AssertionError,'SHA-256'):
            self.verify()

    def test_missing_or_symlinked_jar_fails_closed(self):
        self.path.unlink()
        with self.assertRaises(AssertionError):
            self.verify()
        self.path.symlink_to(SOURCE)
        with self.assertRaisesRegex(AssertionError,'linked'):
            self.verify()

    def test_archive_without_plugin_descriptor_is_rejected(self):
        with ZipFile(self.path,'w') as archive:
            archive.writestr('unrelated.txt','not a plugin')
        raw=self.path.read_bytes()
        with self.assertRaisesRegex(AssertionError,'plugin descriptor'):
            oracle.verify_historical_ie2_jar(
                self.path,len(raw),hashlib.sha256(raw).hexdigest())


if __name__=='__main__':
    unittest.main()
