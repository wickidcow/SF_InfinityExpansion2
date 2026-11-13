"""Fail-closed source-specific receipts for genuine IE2 GEO Quarry native outputs."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

SOURCE=Path(__file__).with_name('run.py')
SPEC=importlib.util.spec_from_file_location('ie2_geo_native_receipt',SOURCE)
assert SPEC is not None and SPEC.loader is not None
oracle=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(oracle)


def phase_report(phase):
    return ('PASS '+phase+': real SMALL_URANIUM cheat, GEO guide display, '
            'GeoQuarry.process, world entity and inventory transfer preserve '
            'metadata across restart\nfailures: 0\n')


class GeoQuarryReceiptTests(unittest.TestCase):
    def setUp(self):
        temporary=tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.runtime=Path(temporary.name)
        self.capture=(self.runtime/'plugins/NativeModernizationProbe/native-geo-uranium.bin')
        self.capture.parent.mkdir(parents=True)
        self.original=b'Paper native genuine GEO output fixture bytes'
        self.capture.write_bytes(self.original)

    def attest(self,phase='seed',report=None,prior=None):
        return oracle.native_geo_capture_receipt(
            self.runtime,phase,phase_report(phase) if report is None else report,prior)

    def test_exact_seed_and_restart_receipts_preserve_original_bytes(self):
        initial=self.attest()
        self.assertEqual(initial,{
            'filename':'native-geo-uranium.bin',
            'sha256':hashlib.sha256(self.original).hexdigest(),
            'size_bytes':len(self.original)})
        self.assertEqual(self.attest('verify',prior=initial),initial)
        self.assertEqual(self.capture.read_bytes(),self.original)
        self.assertNotIn(self.original.decode(),str(initial))

    def test_missing_empty_or_linked_capture_fails_closed(self):
        self.capture.unlink()
        with self.assertRaisesRegex(AssertionError,'Missing or linked'):
            self.attest()
        self.capture.write_bytes(b'')
        with self.assertRaisesRegex(AssertionError,'Invalid GEO'):
            self.attest()
        self.capture.unlink()
        self.capture.symlink_to(SOURCE)
        with self.assertRaisesRegex(AssertionError,'Missing or linked'):
            self.attest()

    def test_cross_restart_modified_capture_fails_closed(self):
        previous=self.attest()
        self.capture.write_bytes(self.original+b' different')
        with self.assertRaisesRegex(AssertionError,'changed across native restart'):
            self.attest('verify',prior=previous)

    def test_missing_or_duplicate_specific_producer_marker_fails_closed(self):
        for invalid in ('PASS seed: synthetic template comparison\nfailures: 0\n',
                        'PASS seed: real SMALL_URANIUM cheat, GEO guide display, '
                        'GeoQuarry.process,\nfailures: 1\n',
                        phase_report('seed')+phase_report('seed'),
                        phase_report('verify')):
            with self.subTest(report=invalid),self.assertRaisesRegex(
                    AssertionError,'Missing exact native GEO'):
                self.attest(report=invalid)

    def test_invalid_native_phase_fails_closed(self):
        with self.assertRaisesRegex(AssertionError,'Unexpected GEO'):
            self.attest(phase='synthetic')

    def test_huge_capture_is_rejected(self):
        self.capture.write_bytes(b'x'*(1024*1024+1))
        with self.assertRaisesRegex(AssertionError,'Invalid GEO'):
            self.attest()


if __name__=='__main__':
    unittest.main()
