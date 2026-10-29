#!/usr/bin/env python3
"""Synthetic headers exercise the same read-only gate used for the raw release JAR."""
from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_java21_distribution import verify_jar


MAIN = "net/guizhanss/infinityexpansion2/InfinityExpansion2.class"


def clazz(major: int = 65, minor: int = 0) -> bytes:
    return struct.pack(">IHH", 0xCAFEBABE, minor, major) + b"\x00\x01"


class Java21DistributionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.jar = Path(self.temp.name) / "SF_InfinityExpansion2.0.17.jar"

    def write(self, classes: dict[str, bytes]) -> None:
        with zipfile.ZipFile(self.jar, "w") as archive:
            for name, contents in classes.items():
                archive.writestr(name, contents)

    def test_stable_java21_passes(self) -> None:
        self.write({MAIN: clazz()})
        self.assertEqual({65: 1}, dict(verify_jar(self.jar)))

    def test_stable_mixed_java8_and_java21_pass(self) -> None:
        self.write({MAIN: clazz(), "net/guizhanss/infinityexpansion2/Older.class": clazz(52)})
        self.assertEqual({52: 1, 65: 1}, dict(verify_jar(self.jar)))

    def test_java25_major_is_rejected(self) -> None:
        self.write({MAIN: clazz(), "library/Newer.class": clazz(69)})
        with self.assertRaisesRegex(ValueError, r"library/Newer.class \(69.0\)"):
            verify_jar(self.jar)

    def test_java21_preview_is_rejected(self) -> None:
        self.write({MAIN: clazz(65, 65535)})
        with self.assertRaisesRegex(ValueError, r"preview class: .* \(65.65535\)"):
            verify_jar(self.jar)

    def test_preview_from_earlier_release_is_rejected(self) -> None:
        for major in (56, 61, 64):
            with self.subTest(major=major):
                self.write({MAIN: clazz(), "library/OldPreview.class": clazz(major, 65535)})
                with self.assertRaisesRegex(ValueError, "preview class"):
                    verify_jar(self.jar)

    def test_invalid_modern_minors_are_rejected(self) -> None:
        for major, minor in ((56, 1), (61, 42), (65, 65534)):
            with self.subTest(major=major, minor=minor):
                self.write({MAIN: clazz(major, minor), "library/New.class": clazz()})
                with self.assertRaisesRegex(ValueError, "invalid minor class"):
                    verify_jar(self.jar)

    def test_shaded_preview_not_ignored(self) -> None:
        self.write({MAIN: clazz(), "net/guizhanss/infinityexpansion2/libs/Shaded.class": clazz(65, 65535)})
        with self.assertRaisesRegex(ValueError, "Shaded.class"):
            verify_jar(self.jar)

    def test_historical_nonzero_minors_are_accepted(self) -> None:
        for major, minor in ((45, 3), (52, 1), (55, 65535)):
            with self.subTest(major=major, minor=minor):
                self.write({MAIN: clazz(), "legacy/Old.class": clazz(major, minor)})
                self.assertEqual(2, sum(verify_jar(self.jar).values()))

    def test_invalid_magic_fails(self) -> None:
        self.write({MAIN: b"notjava!"})
        with self.assertRaisesRegex(ValueError, "Invalid class header"):
            verify_jar(self.jar)

    def test_truncated_header_fails(self) -> None:
        self.write({MAIN: clazz()[:7]})
        with self.assertRaisesRegex(ValueError, "Invalid class header"):
            verify_jar(self.jar)

    def test_no_java21_class_cannot_pass(self) -> None:
        self.write({"legacy/Old.class": clazz(52)})
        with self.assertRaisesRegex(ValueError, "Expected Java 21 plugin classes"):
            verify_jar(self.jar)

    def test_empty_archive_cannot_pass(self) -> None:
        self.write({"plugin.yml": b"name: InfinityExpansion2"})
        with self.assertRaisesRegex(ValueError, "Expected Java 21 plugin classes"):
            verify_jar(self.jar)

    def test_invalid_major_below_jvm_floor_is_rejected(self) -> None:
        self.write({MAIN: clazz(44)})
        with self.assertRaisesRegex(ValueError, "invalid major"):
            verify_jar(self.jar)

    def test_invalid_zip_is_rejected(self) -> None:
        self.jar.write_bytes(b"not a ZIP")
        with self.assertRaises(zipfile.BadZipFile):
            verify_jar(self.jar)

    def test_check_does_not_mutate_artifact(self) -> None:
        self.write({MAIN: clazz()})
        before = hashlib.sha256(self.jar.read_bytes()).digest()
        verify_jar(self.jar)
        self.assertEqual(before, hashlib.sha256(self.jar.read_bytes()).digest())


if __name__ == "__main__":
    unittest.main()
