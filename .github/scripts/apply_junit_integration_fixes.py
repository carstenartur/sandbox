#!/usr/bin/env python3
"""Apply the two classified JUnit integration fixes deterministically."""

from __future__ import annotations

from pathlib import Path
import re


def replace_exactly_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one {description}, found {count}")
    return text.replace(old, new, 1)


def patch_project_cleanup_application() -> None:
    path = Path(
        "sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/"
        "ProjectWideCodeCleanupApplication.java"
    )
    source = path.read_text(encoding="utf-8")
    source = replace_exactly_once(
        source,
        "import java.io.IOException;",
        "import java.io.ByteArrayOutputStream;\nimport java.io.IOException;",
        "ProjectWideCodeCleanupApplication IOException import",
    )

    perform_pattern = re.compile(
        r"(?m)^(\t+)change\.perform\(monitor\);\n"
        r"\1refresh\(sources, monitor\);$"
    )

    def preserve_after_perform(match: re.Match[str]) -> str:
        indent = match.group(1)
        return (
            f"{indent}change.perform(monitor);\n"
            f"{indent}preserveOriginalLineDelimiters(sources);\n"
            f"{indent}refresh(sources, monitor);"
        )

    source, replacement_count = perform_pattern.subn(preserve_after_perform, source)
    if replacement_count != 2:
        raise SystemExit(
            "Expected two project-wide cleanup perform/refresh sequences, "
            f"found {replacement_count}"
        )

    marker = "\n\tprivate static void createParent(Path path) throws IOException {\n"
    helpers = r'''
	private enum OriginalLineDelimiter {
		LF(new byte[] { '\n' }),
		CRLF(new byte[] { '\r', '\n' }),
		CR(new byte[] { '\r' });

		private final byte[] bytes;

		OriginalLineDelimiter(byte[] bytes) {
			this.bytes= bytes;
		}

		private static OriginalLineDelimiter detect(byte[] content) {
			boolean foundLf= false;
			boolean foundCrLf= false;
			boolean foundCr= false;
			for (int index= 0; index < content.length; index++) {
				if (content[index] == '\r') {
					if (index + 1 < content.length && content[index + 1] == '\n') {
						foundCrLf= true;
						index++;
					} else {
						foundCr= true;
					}
				} else if (content[index] == '\n') {
					foundLf= true;
				}
			}
			int styleCount= (foundLf ? 1 : 0) + (foundCrLf ? 1 : 0) + (foundCr ? 1 : 0);
			if (styleCount != 1) {
				return null;
			}
			if (foundCrLf) {
				return CRLF;
			}
			return foundLf ? LF : CR;
		}
	}

	private static void preserveOriginalLineDelimiters(List<SourceSnapshot> sources) throws IOException {
		for (SourceSnapshot source : sources) {
			OriginalLineDelimiter delimiter= OriginalLineDelimiter.detect(source.before());
			if (delimiter == null) {
				continue;
			}
			byte[] current= Files.readAllBytes(source.path());
			byte[] normalized= normalizeLineDelimiters(current, delimiter);
			if (!Arrays.equals(current, normalized)) {
				Files.write(source.path(), normalized);
			}
		}
	}

	private static byte[] normalizeLineDelimiters(byte[] content, OriginalLineDelimiter delimiter) {
		ByteArrayOutputStream normalized= new ByteArrayOutputStream(content.length + 64);
		for (int index= 0; index < content.length; index++) {
			byte current= content[index];
			if (current == '\r') {
				if (index + 1 < content.length && content[index + 1] == '\n') {
					index++;
				}
				normalized.writeBytes(delimiter.bytes);
			} else if (current == '\n') {
				normalized.writeBytes(delimiter.bytes);
			} else {
				normalized.write(current);
			}
		}
		return normalized.toByteArray();
	}
'''
    source = replace_exactly_once(
        source,
        marker,
        "\n" + helpers + marker,
        "ProjectWideCodeCleanupApplication createParent marker",
    )
    path.write_text(source, encoding="utf-8")


def patch_jdt_core_runner() -> None:
    path = Path("qa/upstream-jdt/run-before-after.sh")
    source = path.read_text(encoding="utf-8")
    source = replace_exactly_once(
        source,
        '    "$SANDBOX_ECLIPSE"\n    -nosplash\n',
        '    "$SANDBOX_ECLIPSE"\n    --launcher.suppressErrors\n    -nosplash\n',
        "JDT Core cleanup launcher command",
    )
    path.write_text(source, encoding="utf-8")


def main() -> None:
    patch_project_cleanup_application()
    patch_jdt_core_runner()


if __name__ == "__main__":
    main()
