#!/usr/bin/env python3
"""Read-only class-header gate for the entire shipped InfinityExpansion2 JAR."""
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import struct
import sys
import zipfile


def verify_jar(path: Path) -> Counter[int]:
    majors: Counter[int] = Counter()
    with zipfile.ZipFile(path) as archive:
        for member in archive.namelist():
            if not member.endswith(".class"):
                continue
            with archive.open(member) as stream:
                header = stream.read(8)
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                raise ValueError(f"Invalid class header in {member}")
            minor, major = struct.unpack(">HH", header[4:8])
            if not 45 <= major <= 65:
                raise ValueError(f"Class exceeds Java 21 or has invalid major: {member} ({major}.{minor})")
            # JVMS 4.1: modern stable class files have minor 0. Minor 65535
            # is release-specific preview and cannot ship without --enable-preview.
            if major >= 56 and minor != 0:
                kind = "preview" if minor == 65535 else "invalid minor"
                raise ValueError(f"Non-distributable {kind} class: {member} ({major}.{minor})")
            majors[major] += 1
        if 65 not in majors:
            raise ValueError(f"Expected Java 21 plugin classes; observed majors: {sorted(majors)}")
    return majors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    args = parser.parse_args()
    try:
        majors = verify_jar(args.jar)
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f"Java 21 distribution check FAILED: {exc}", file=sys.stderr)
        return 1
    print(f"Verified Java 21-compatible plugin classes: {dict(sorted(majors.items()))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
