#!/usr/bin/env python3
"""Unit tests for tools/port_guards.py. Run: python3 -I tools/test_port_guards.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import port_guards as g  # noqa: E402


class PathLint(unittest.TestCase):
    def test_clean(self):
        self.assertEqual(g.lint_paths(["core/src/A.kt", "docs/x.md"]), [])

    def test_reserved(self):
        self.assertTrue(g.lint_paths(["a/con.txt"]))
        self.assertTrue(g.lint_paths(["a/NUL"]))
        self.assertTrue(g.lint_paths(["a/com1.kt"]))
        self.assertEqual(g.lint_paths(["a/console.kt"]), [])

    def test_case_collision(self):
        self.assertTrue(g.lint_paths(["a/Foo.kt", "a/foo.kt"]))

    def test_trailing_and_illegal(self):
        self.assertTrue(g.lint_paths(["a/b."]))
        self.assertTrue(g.lint_paths(["a/b "]))
        self.assertTrue(g.lint_paths(["a/b?.kt"]))

    def test_long(self):
        self.assertTrue(g.lint_paths(["a/" + "x" * 200]))


class Purity(unittest.TestCase):
    def test_import_and_system(self):
        src = "import java.util.List\nimport javax.sound.x\nimport android.os.Bundle\nval t = System.nanoTime()\n"
        self.assertEqual([n for n, _ in g.scan_source(src)], [1, 2, 3, 4])

    def test_comments_and_strings_ignored(self):
        src = (
            '// import java.util.List\n/** System.currentTimeMillis() in KDoc */\n'
            'val s = "System.exit and import java.x"\nval r = """\nSystem.out\n"""\n'
            '/* nested /* System.x */ System.y */\nval ok = 1\n'
        )
        self.assertEqual(g.scan_source(src), [])

    def test_identifier_not_flagged(self):
        self.assertEqual(g.scan_source("val a = MySystem.x\nval b = ecosystem.y\nimport kotlinx.java.z\n"), [])

    def test_repo_is_clean(self):
        self.assertEqual(g.lint_purity(), [])


class Projects(unittest.TestCase):
    LISTING = "Root project 'x'\n+--- Project ':core'\n+--- Project ':ui'\n\\--- Project ':backend-ios'\n"

    def test_names(self):
        self.assertEqual(g.project_names(self.LISTING), [":core", ":ui", ":backend-ios"])

    def test_gated(self):
        self.assertEqual(len(g.lint_projects(self.LISTING, {})), 1)
        self.assertEqual(g.lint_projects(self.LISTING, {"ENABLE_IOS": "1"}), [])

    def test_default_ok(self):
        ok = "+--- Project ':core'\n+--- Project ':backend-desktop'\n\\--- Project ':desktopApp'\n"
        self.assertEqual(g.lint_projects(ok, {}), [])


if __name__ == "__main__":
    unittest.main()
