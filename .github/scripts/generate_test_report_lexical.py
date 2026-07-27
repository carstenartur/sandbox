#!/usr/bin/env python3
"""Generate the static test inventory without scanning Java literals/comments."""

from __future__ import annotations

import os
import re
from pathlib import Path

from generate_test_report import TestMethod, TestScanner as LegacyTestScanner


def mask_non_code(source: str) -> str:
    """Replace Java comments and literals with spaces while preserving newlines."""
    result = list(source)
    index = 0
    state = "code"
    while index < len(source):
        char = source[index]
        next_char = source[index + 1] if index + 1 < len(source) else ""
        next_two = source[index : index + 3]

        if state == "code":
            if next_two == '"""':
                result[index : index + 3] = "   "
                index += 3
                state = "text_block"
                continue
            if char == '"':
                result[index] = " "
                index += 1
                state = "string"
                continue
            if char == "'":
                result[index] = " "
                index += 1
                state = "character"
                continue
            if char == "/" and next_char == "/":
                result[index : index + 2] = "  "
                index += 2
                state = "line_comment"
                continue
            if char == "/" and next_char == "*":
                result[index : index + 2] = "  "
                index += 2
                state = "block_comment"
                continue
            index += 1
            continue

        if state == "line_comment":
            if char == "\n":
                state = "code"
            else:
                result[index] = " "
            index += 1
            continue

        if state == "block_comment":
            if char == "*" and next_char == "/":
                result[index : index + 2] = "  "
                index += 2
                state = "code"
            else:
                if char != "\n":
                    result[index] = " "
                index += 1
            continue

        if state == "text_block":
            if char == "\\":
                result[index] = " "
                if index + 1 < len(source):
                    if source[index + 1] != "\n":
                        result[index + 1] = " "
                    index += 2
                else:
                    index += 1
                continue
            if next_two == '"""':
                result[index : index + 3] = "   "
                index += 3
                state = "code"
            else:
                if char != "\n":
                    result[index] = " "
                index += 1
            continue

        if state in {"string", "character"}:
            if char == "\\":
                result[index] = " "
                if index + 1 < len(source):
                    if source[index + 1] != "\n":
                        result[index + 1] = " "
                    index += 2
                else:
                    index += 1
                continue
            terminator = '"' if state == "string" else "'"
            if char == terminator:
                result[index] = " "
                state = "code"
            elif char != "\n":
                result[index] = " "
            index += 1
            continue

    return "".join(result)


def annotation_parenthesis_delta(line: str) -> int:
    """Return the parenthesis balance for one already-masked annotation line."""
    return line.count("(") - line.count(")")


class TestScanner(LegacyTestScanner):
    """Legacy report generator with a lexical Java-code view for discovery."""

    def scan_java_file(self, file_path: Path, plugin_name: str):
        try:
            content = file_path.read_text(encoding="utf-8")
        except Exception as exception:  # pragma: no cover - diagnostic path
            print(f"Error reading {file_path}: {exception}")
            return

        code = mask_non_code(content)
        lines = code.split("\n")
        source_lines = content.split("\n")
        package_match = re.search(r"package\s+([\w.]+);", code)
        class_match = re.search(r"(?:public\s+)?class\s+(\w+)", code)
        if not class_match:
            return

        class_name = class_match.group(1)
        full_class_name = (
            f"{package_match.group(1)}.{class_name}" if package_match else class_name
        )
        pending_code: list[str] = []
        pending_source: list[str] = []
        annotation_depth = 0

        for index, line in enumerate(lines):
            stripped = line.strip()
            original = source_lines[index].strip()
            if not stripped:
                continue

            if annotation_depth > 0:
                pending_code.append(stripped)
                pending_source.append(original)
                annotation_depth = max(
                    0, annotation_depth + annotation_parenthesis_delta(stripped)
                )
                continue

            if stripped.startswith("@"):
                pending_code.append(stripped)
                pending_source.append(original)
                annotation_depth = max(0, annotation_parenthesis_delta(stripped))
                continue

            method_match = re.match(
                r"\s*(?:public|private|protected)?\s+(?:static\s+)?(?:void|\w+)\s+(\w+)\s*\(",
                line,
            )
            if method_match:
                annotation_code = "\n".join(pending_code)
                annotation_source = "\n".join(pending_source)
                test_annotation = None
                if re.search(r"@ParameterizedTest\b", annotation_code):
                    test_annotation = "ParameterizedTest"
                elif re.search(r"@RepeatedTest\b", annotation_code):
                    test_annotation = "RepeatedTest"
                elif re.search(r"@Test(?![a-zA-Z])", annotation_code):
                    test_annotation = "Test"

                if test_annotation:
                    is_disabled = bool(re.search(r"@Disabled\b", annotation_code))
                    reason_match = re.search(
                        r"@Disabled\s*\(\s*[\"']([^\"']+)[\"']\s*\)",
                        annotation_source,
                        re.DOTALL,
                    )
                    disabled_reason = (
                        reason_match.group(1)
                        if reason_match
                        else "No reason specified" if is_disabled else ""
                    )
                    self.tests.append(
                        TestMethod(
                            plugin=plugin_name,
                            file_path=str(file_path.relative_to(self.repo_root)),
                            class_name=full_class_name,
                            method_name=method_match.group(1),
                            is_disabled=is_disabled,
                            disabled_reason=disabled_reason,
                            test_type=test_annotation,
                            line_number=index + 1,
                        )
                    )
                pending_code.clear()
                pending_source.clear()
                continue

            # A declaration or statement boundary ends the contiguous annotation block.
            pending_code.clear()
            pending_source.clear()


def main() -> None:
    repo_root = Path(__file__).parent.parent.parent
    scanner = TestScanner(repo_root)
    scanner.scan_all()

    output_dir = Path(os.environ.get("GITHUB_WORKSPACE", repo_root))
    markdown_path = output_dir / "test-report.md"
    json_path = output_dir / "test-report.json"
    markdown_report = scanner.generate_markdown_report()
    markdown_path.write_text(markdown_report, encoding="utf-8")
    json_path.write_text(scanner.generate_json_report(), encoding="utf-8")

    print(f"\nMarkdown report written to: {markdown_path}")
    print(f"JSON report written to: {json_path}")
    print("\n" + "=" * 60)
    print(markdown_report)


if __name__ == "__main__":
    main()
