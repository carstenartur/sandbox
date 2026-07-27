#!/usr/bin/env python3
"""Regression tests for the lexical static test inventory scanner."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from generate_test_report_lexical import TestScanner, mask_non_code


class JavaMaskTest(unittest.TestCase):
    def test_preserves_lines_and_masks_literals_and_comments(self) -> None:
        source = '''class Sample {
String value = "@Disabled @Test void fake()";
String block = """
@Disabled("inside data")
@Test void embedded() {}
""";
// @Disabled @Test void commented() {}
@Test void real() {}
}
'''
        masked = mask_non_code(source)
        self.assertEqual(source.count("\n"), masked.count("\n"))
        self.assertNotIn("embedded", masked)
        self.assertNotIn("commented", masked)
        self.assertIn("@Test void real", masked)

    def test_masks_escaped_literals_and_block_comments(self) -> None:
        source = r'''class Sample {
String escaped = "prefix \" @Disabled @Test void fake() \" suffix";
char quote = '\'';
/*
@Disabled("block comment")
@Test void commentedBlock() {}
*/
@Test void real() {}
}
'''
        masked = mask_non_code(source)
        self.assertNotIn("fake", masked)
        self.assertNotIn("commentedBlock", masked)
        self.assertIn("@Test void real", masked)

    def test_does_not_close_text_block_at_escaped_delimiter(self) -> None:
        source = r'''class Sample {
String block = """
escaped delimiter: \"""
@Disabled("fixture only")
@Test void embedded() {}
""";
@Test void real() {}
}
'''
        masked = mask_non_code(source)
        self.assertNotIn("embedded", masked)
        self.assertIn("@Test void real", masked)


class ScannerTest(unittest.TestCase):
    def scan(self, source: str):
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            java_file = root / "demo_test" / "src" / "demo" / "SampleTest.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(source, encoding="utf-8")
            scanner = TestScanner(root)
            scanner.scan_all()
            return scanner.tests

    def test_ignores_annotated_methods_inside_text_blocks(self) -> None:
        tests = self.scan(
            '''package demo;
public class SampleTest {
    String fixture = """
        @Disabled("fixture only")
        @Test
        void embedded() {}
        """;

    @Test
    void realTest() {}
}
'''
        )
        self.assertEqual(["realTest"], [test.method_name for test in tests])
        self.assertFalse(tests[0].is_disabled)

    def test_reports_real_disabled_reason(self) -> None:
        tests = self.scan(
            '''package demo;
public class SampleTest {
    @Disabled("needs implementation")
    @Test
    void disabledTest() {}
}
'''
        )
        self.assertEqual(1, len(tests))
        self.assertTrue(tests[0].is_disabled)
        self.assertEqual("needs implementation", tests[0].disabled_reason)

    def test_ignores_commented_disabled_annotation(self) -> None:
        tests = self.scan(
            '''package demo;
public class SampleTest {
    // @Disabled("not real")
    @Test
    void enabledTest() {}
}
'''
        )
        self.assertEqual(1, len(tests))
        self.assertFalse(tests[0].is_disabled)

    def test_disabled_annotation_does_not_leak_to_following_test(self) -> None:
        tests = self.scan(
            '''package demo;
public class SampleTest {
    @Disabled("first only")
    @Test
    void disabledTest() {}

    @Test
    void enabledTest() {}
}
'''
        )
        self.assertEqual(["disabledTest", "enabledTest"], [test.method_name for test in tests])
        self.assertTrue(tests[0].is_disabled)
        self.assertFalse(tests[1].is_disabled)


if __name__ == "__main__":
    unittest.main()
