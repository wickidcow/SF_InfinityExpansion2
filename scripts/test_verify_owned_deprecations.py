#!/usr/bin/env python3
"""Exercise the real IE2 own-source deprecation gate with exact compiler-shaped logs.

All synthetic source and log files live in a temporary directory. Nothing in
production sources is modified, and no compatibility deprecation is suppressed.
"""
from __future__ import annotations

import os
from pathlib import Path
import tempfile
import unittest

from verify_owned_deprecations import verify


CONFIG = "me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config"
REASON = "'class Config : Any' is deprecated. Deprecated in Java."
BOW = "net/guizhanss/infinityexpansion2/utils/events/BowDamageEvents.java"


class OwnedDeprecationsTests(unittest.TestCase):
    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="ie2-warnings-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, self.previous)
        self.log = self.root / "candidate-compile.log"
        self.kotlin = "net/guizhanss/infinityexpansion2/implementation/items/generators/EnergyGenerator.kt"
        self.write("src/main/kotlin/" + self.kotlin,
                   "@Deprecated(\"Legacy compiled descriptor\")\n"
                   f"override fun getGeneratedOutput(l: Location, data: {CONFIG}): Int = 0\n")
        self.write("src/main/java/" + BOW,
                   "class BowDamageEvents {\n"
                   "var base = EntityDamageEvent.DamageModifier.BASE;\n"
                   "}\n")

    def write(self, name: str, content: str) -> None:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def check(self, lines: list[str]) -> set[tuple[str, int]]:
        self.log.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return verify(self.log)

    def kotlin_warning(self, source: str | None = None, row: int = 2, reason: str = REASON) -> str:
        path = source or self.kotlin
        return f"w: file://{self.root}/src/main/kotlin/{path}:{row}:103 {reason}"

    def java_warning(self, source: str = BOW, row: int = 2, reason: str =
                     "DamageModifier in EntityDamageEvent has been deprecated") -> str:
        return f"{self.root}/src/main/java/{source}:{row}: warning: [deprecation] {reason}"

    def test_exact_legacy_bridges_and_damage_preflight_are_allowed(self) -> None:
        observed = self.check([self.kotlin_warning(), self.java_warning()])
        self.assertEqual({("EnergyGenerator.kt", 2), ("BowDamageEvents.java", 2)}, observed)

    def test_missing_warnings_are_not_fabricated(self) -> None:
        self.assertEqual(set(), self.check(["BUILD SUCCESSFUL", "w: other ordinary warning"]))

    def test_new_kotlin_deprecation_is_rejected(self) -> None:
        self.write("src/main/kotlin/net/example/New.kt", "val newCode = 1\n")
        with self.assertRaisesRegex(AssertionError, "Unexpected Kotlin deprecation"):
            self.check([self.kotlin_warning("net/example/New.kt", row=1)])

    def test_new_java_deprecation_is_rejected(self) -> None:
        self.write("src/main/java/net/example/New.java", "class New {}\n")
        with self.assertRaisesRegex(AssertionError, "Unexpected Java deprecation"):
            self.check([self.java_warning("net/example/New.java", row=1)])

    def test_changed_deprecated_api_is_rejected_even_in_allowed_file(self) -> None:
        with self.assertRaisesRegex(AssertionError, "Unexpected Kotlin deprecation"):
            self.check([self.kotlin_warning(reason="'Other API' is deprecated.")])

    def test_deprecation_on_unrecognized_source_line_is_rejected(self) -> None:
        with self.assertRaisesRegex(AssertionError, "Unexpected Kotlin deprecation"):
            self.check([self.kotlin_warning(row=1)])

    def test_unparsed_production_diagnostic_fails_closed(self) -> None:
        line = f"w: file://{self.root}/src/main/kotlin/{self.kotlin} unknown deprecation warning"
        with self.assertRaisesRegex(AssertionError, "Unparsed production diagnostic"):
            self.check([line])

    def test_file_suppression_is_rejected_without_any_compiler_warning(self) -> None:
        self.write("src/main/kotlin/net/example/Suppressed.kt",
                   '@file:Suppress("DEPRECATION")\npackage net.example\n')
        with self.assertRaisesRegex(AssertionError, "Production deprecation suppression"):
            self.check([])

    def test_java_suppression_is_rejected_without_any_compiler_warning(self) -> None:
        self.write("src/main/java/net/example/Suppressed.java",
                   '@SuppressWarnings("deprecation")\nclass Suppressed {}\n')
        with self.assertRaisesRegex(AssertionError, "Production deprecation suppression"):
            self.check([])

    def test_non_deprecation_warnings_are_not_misreported(self) -> None:
        text = self.kotlin_warning(reason="Unnecessary safe call on a non-null receiver")
        self.assertEqual(set(), self.check([text]))


if __name__ == "__main__":
    unittest.main()
