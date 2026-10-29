"""Offline contracts for the native IE2 SQLite oracle; no Paper process or database mutation."""
from __future__ import annotations

import base64
import contextlib
import importlib.util
from pathlib import Path
import sqlite3
import tempfile
import unittest

SOURCE=Path(__file__).with_name('run.py')
SPEC=importlib.util.spec_from_file_location('ie2_release_runtime_oracle',SOURCE)
assert SPEC is not None and SPEC.loader is not None
oracle=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(oracle)

MARKER='preserve-unknown-record'
ENCODED=base64.b64encode(MARKER.encode('utf-8')).decode('ascii')


class NativeSqliteEncodingOracleTests(unittest.TestCase):
    def setUp(self):
        directory=tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.runtime=Path(directory.name)
        self.config=self.runtime/'plugins/Slimefun/block-storage.yml'
        self.config.parent.mkdir(parents=True)
        self.database=self.runtime/'data-storage/Slimefun/block-storage.db'
        self.database.parent.mkdir(parents=True)
        with contextlib.closing(sqlite3.connect(self.database)) as connection, connection:
            connection.execute('CREATE TABLE block_record(loc TEXT, chunk TEXT, sf_id TEXT)')
            connection.execute('CREATE TABLE block_data(loc TEXT, data_key TEXT, data_val TEXT)')
            connection.execute('INSERT INTO block_record VALUES (?, ?, ?)',
                ('world:1,2,3','world:0,0','IE2_NATIVE_UNKNOWN_ID'))
            connection.execute('INSERT INTO block_data VALUES (?, ?, ?)',
                ('world:1,2,3','probe.marker',ENCODED))
        self.set_config('true')

    def set_config(self,value):
        self.config.write_text('storageType: SQLITE\nbase64EncodeVal: '+value+'\n',encoding='utf-8')

    def set_marker(self,value):
        with contextlib.closing(sqlite3.connect(self.database)) as connection, connection:
            connection.execute('UPDATE block_data SET data_val=? WHERE data_key=?',
                (value,'probe.marker'))

    def test_default_base64_mode_preserves_exact_raw_bytes(self):
        before=self.database.read_bytes()
        result=oracle.stored_unknown_snapshot(self.runtime)
        self.assertEqual(result['record'][2],'IE2_NATIVE_UNKNOWN_ID')
        self.assertEqual(result['payload'],[('probe.marker',ENCODED)])
        self.assertTrue(result['base64_encoded'])
        self.assertEqual(self.database.read_bytes(),before)

    def test_raw_mode_accepts_only_raw_bytes(self):
        self.set_config('false')
        self.set_marker(MARKER)
        result=oracle.stored_unknown_snapshot(self.runtime)
        self.assertEqual(result['payload'],[('probe.marker',MARKER)])
        self.assertFalse(result['base64_encoded'])

    def test_base64_mode_rejects_raw_bytes(self):
        self.set_marker(MARKER)
        with self.assertRaisesRegex(AssertionError,'configured encoding'):
            oracle.stored_unknown_snapshot(self.runtime)

    def test_raw_mode_rejects_base64_bytes(self):
        self.set_config('false')
        with self.assertRaisesRegex(AssertionError,'configured encoding'):
            oracle.stored_unknown_snapshot(self.runtime)

    def test_different_encoded_payload_is_not_accepted(self):
        self.set_marker(base64.b64encode(b'different-payload').decode('ascii'))
        with self.assertRaisesRegex(AssertionError,'configured encoding'):
            oracle.stored_unknown_snapshot(self.runtime)

    def test_missing_marker_is_never_a_pass(self):
        with contextlib.closing(sqlite3.connect(self.database)) as connection, connection:
            connection.execute('DELETE FROM block_data')
        with self.assertRaisesRegex(AssertionError,'marker rows=0'):
            oracle.stored_unknown_snapshot(self.runtime)

    def test_duplicate_marker_does_not_pass(self):
        with contextlib.closing(sqlite3.connect(self.database)) as connection, connection:
            connection.execute('INSERT INTO block_data VALUES (?, ?, ?)',
                ('world:1,2,3','probe.marker',ENCODED))
        with self.assertRaisesRegex(AssertionError,'marker rows=2'):
            oracle.stored_unknown_snapshot(self.runtime)

    def test_missing_unknown_record_is_a_failure(self):
        with contextlib.closing(sqlite3.connect(self.database)) as connection, connection:
            connection.execute('DELETE FROM block_record')
        with self.assertRaisesRegex(AssertionError,'preserved exactly once'):
            oracle.stored_unknown_snapshot(self.runtime)

    def test_missing_or_ambiguous_config_is_not_assumed(self):
        for text in ('storageType: SQLITE\n',
                     'base64EncodeVal: true\nbase64EncodeVal: false\n',
                     'base64EncodeVal: yes\n',
                     'base64EncodeVal: \n'):
            with self.subTest(text=text):
                self.config.write_text(text,encoding='utf-8')
                with self.assertRaises(AssertionError):
                    oracle.stored_unknown_snapshot(self.runtime)

    def test_changed_encoding_mode_changes_snapshot_identity(self):
        before=oracle.stored_unknown_snapshot(self.runtime)
        self.set_config('false')
        self.set_marker(MARKER)
        after=oracle.stored_unknown_snapshot(self.runtime)
        self.assertNotEqual(before,after)

    def test_snapshot_rejects_unrelated_data_drift_across_restart(self):
        before=oracle.stored_unknown_snapshot(self.runtime)
        with contextlib.closing(sqlite3.connect(self.database)) as connection, connection:
            connection.execute('INSERT INTO block_data VALUES (?, ?, ?)',
                ('world:1,2,3','other',ENCODED))
        self.assertNotEqual(before,oracle.stored_unknown_snapshot(self.runtime))


if __name__=='__main__':
    unittest.main()
