#!/usr/bin/env python3
"""Convert legacy /*!key: value*/ per-rule directives in .sandbox-hint files
into proper per-rule annotations supported by HintFileParser:

  /*!id: foo*/             -> @id: foo
  /*!severity: info*/      -> @severity: info
  /*!description: blah*/   -> "blah":   (must precede the source pattern)

Run from the repository root or pass a path as argv[1].

Background: HintFileParser strips C-style /* ... */ block comments before
parsing, so the legacy /*!...*/ directives were silently discarded, losing
the rule id, description and severity. This script converts them in-place
to the supported per-rule annotation syntax.
"""
from __future__ import annotations
import os, re, sys

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(__file__) or ".")

LEGACY_RE = re.compile(r"^(\s*)/\*!\s*(id|severity|description)\s*:\s*(.*?)\s*\*/\s*$")

def convert_file(path: str) -> int:
    with open(path, "r", encoding="utf-8", newline="") as f:
        original = f.read()
    # Preserve newline style
    nl = "\r\n" if "\r\n" in original else "\n"
    lines = original.splitlines()

    # Group consecutive legacy directives -> emit them as
    #   @id: <value>
    #   @severity: <value>
    #   "description":
    # in the parser-required order: annotations first, description last.
    out: list[str] = []
    i = 0
    converted = 0
    while i < len(lines):
        line = lines[i]
        m = LEGACY_RE.match(line)
        if not m:
            out.append(line)
            i += 1
            continue
        # Collect a contiguous block of legacy directives (skipping blank
        # lines between them is NOT done — the bot always emits them
        # adjacently).
        indent = m.group(1)
        block: dict[str, str] = {}
        block_order: list[str] = []
        while i < len(lines):
            mm = LEGACY_RE.match(lines[i])
            if not mm:
                break
            key = mm.group(2)
            val = mm.group(3)
            if key not in block:
                block_order.append(key)
            block[key] = val
            converted += 1
            i += 1
        # Emit annotations first (id, severity), then description prefix.
        for k in ("id", "severity"):
            if k in block:
                out.append(f"{indent}@{k}: {block[k]}")
        if "description" in block:
            desc = block["description"]
            # Escape: the parser requires the line to start with " and end
            # with ": — internal quotes would prematurely close it. Replace
            # any double quote inside the description with single quote.
            if '"' in desc:
                desc = desc.replace('"', "'")
            out.append(f'{indent}"{desc}":')
    if converted == 0:
        return 0
    new_text = nl.join(out)
    # Preserve trailing newline
    if original.endswith(("\n", "\r\n")):
        new_text += nl
    if new_text != original:
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(new_text)
        print(f"  converted {converted} directives in {os.path.relpath(path, ROOT)}")
    return converted

def main() -> None:
    total = 0
    for r, _, files in os.walk(ROOT):
        if any(seg in r for seg in (os.sep + "target", os.sep + ".git", os.sep + "bin")):
            continue
        for f in files:
            if f.endswith(".sandbox-hint"):
                total += convert_file(os.path.join(r, f))
    print(f"\nTotal directives converted: {total}")

if __name__ == "__main__":
    main()
