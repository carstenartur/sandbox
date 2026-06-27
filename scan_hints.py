#!/usr/bin/env python3
"""Scan all .sandbox-hint files and report suspicious patterns.

Run from the repository root (or pass a path as argv[1]).
"""
import os, re, sys

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(__file__) or ".")

def find_files():
    out = []
    for r, dirs, files in os.walk(ROOT):
        if any(p in r for p in (r"\target\\", r"\target", r"\.git", r"\bin\\")):
            continue
        # exclude target dirs
        if "\\target\\" in r or r.endswith("\\target"):
            continue
        if "\\.git" in r:
            continue
        for f in files:
            if f.endswith(".sandbox-hint"):
                out.append(os.path.join(r, f))
    return out

def split_rules(text):
    """Yield (line_no_of_rule_start, list_of_lines) for each rule (separated by ;;)."""
    lines = text.splitlines()
    cur = []
    start = None
    in_meta = False
    meta_buf = []
    meta_start = None
    out = []
    i = 0
    while i < len(lines):
        raw = lines[i]
        stripped = raw.strip()
        # strip line comments
        if stripped.startswith("//"):
            i += 1
            continue
        # multi-line metadata directive
        if stripped.startswith("<!") and not in_meta:
            if stripped.endswith(">"):
                # complete metadata line: not part of a rule
                i += 1
                continue
            in_meta = True
            i += 1
            continue
        if in_meta:
            if stripped.endswith(">"):
                in_meta = False
            i += 1
            continue
        if stripped == ";;":
            if cur:
                out.append((start, cur))
            cur = []
            start = None
            i += 1
            continue
        if not stripped:
            i += 1
            continue
        if start is None:
            start = i + 1  # 1-based
        cur.append(stripped)
        i += 1
    if cur:
        out.append((start, cur))
    return out

def analyze(path, text):
    issues = []
    lines = text.splitlines()
    # Issue: per-rule <!id:> overwriting file id
    file_id_lines = [i+1 for i, l in enumerate(lines) if re.match(r"\s*<!id:", l)]
    if len(file_id_lines) > 1:
        issues.append(("MULTI_FILE_ID", f"<!id:> at lines {file_id_lines} (overwrites file id)"))

    # Issue: legacy /*!key: value*/ per-rule directives — silently ignored by parser
    legacy_dir_lines = [i+1 for i, l in enumerate(lines) if re.match(r"\s*/\*!\s*(id|severity|description)\s*:", l)]
    if legacy_dir_lines:
        issues.append(("LEGACY_DIRECTIVE", f"/*!key:value*/ at lines {legacy_dir_lines[:5]}{'...' if len(legacy_dir_lines)>5 else ''}: silently stripped by parser"))

    # Find first <!id:> for the file
    file_id = None
    for l in lines:
        m = re.match(r"\s*<!id:\s*([^>]+)>", l)
        if m:
            file_id = m.group(1).strip()
            break

    rules = split_rules(text)
    for start, rule_lines in rules:
        # parse out @id annotations and source/replacements
        rule_id = None
        src = None
        replacements = []
        had_arrow = False
        had_description_prefix = False
        for ln in rule_lines:
            if ln.startswith("@id:"):
                rule_id = ln[4:].strip()
                continue
            if ln.startswith("@severity:") or ln.startswith("@description:"):
                continue
            if ln.startswith('"') and ln.endswith('":'):
                had_description_prefix = True
                continue
            if ln.startswith("=>"):
                had_arrow = True
                replacements.append(ln[2:].strip())
                continue
            if ln.startswith("::"):
                continue
            if had_arrow:
                # continuation of replacement
                if replacements:
                    replacements[-1] += " " + ln
                continue
            if src is None:
                src = ln
            else:
                src += " " + ln
        if src is None:
            issues.append(("NO_SOURCE", f"rule@line {start}: no source pattern"))
            continue
        if not replacements:
            # Hint-only rules (description prefix + source + ;;) are valid
            if not had_description_prefix:
                issues.append(("NO_REPLACEMENT", f"rule@line {start} ({rule_id}): no '=>' replacement and no description prefix (not a valid hint-only rule)"))
            continue
        # remove guard from source
        src_no_guard = re.split(r"\s*::\s*", src, maxsplit=1)[0].strip().rstrip(";").strip()
        for rep in replacements:
            rep_clean = re.split(r"\s*::\s*", rep, maxsplit=1)[0].strip().rstrip(";").strip()
            if rep_clean == src_no_guard:
                issues.append(("NOOP", f"rule@line {start} ({rule_id}): src==replacement: {src_no_guard!r}"))
        # Detect obviously malformed FQN-with-placeholder mixing like 'java.lang.String.$str.foo()'
        if re.search(r"java\.\w+(?:\.\w+)*\.\$\w+\.", src_no_guard):
            issues.append(("MALFORMED_FQN", f"rule@line {start} ({rule_id}): suspicious FQN.placeholder mix in src: {src_no_guard!r}"))
        for rep in replacements:
            if re.search(r"java\.\w+(?:\.\w+)*\.\$\w+\.", rep):
                issues.append(("MALFORMED_FQN", f"rule@line {start} ({rule_id}): suspicious FQN.placeholder mix in repl: {rep!r}"))
        # Detect placeholders that appear in the replacement but not in the source pattern
        src_phs = set(re.findall(r"\$\w+", src_no_guard))
        for rep in replacements:
            rep_no_guard = re.split(r"\s*::\s*", rep, maxsplit=1)[0]
            rep_phs = set(re.findall(r"\$\w+", rep_no_guard))
            unbound = rep_phs - src_phs
            # $body$ etc. with trailing $ are statement-block placeholders — strip the
            # trailing-$ form for comparison
            unbound = {p for p in unbound if not (p + "$") in src_no_guard}
            if unbound:
                issues.append(("UNBOUND_PLACEHOLDER", f"rule@line {start} ({rule_id}): replacement uses placeholder(s) {sorted(unbound)} not bound in source: {src_no_guard!r}"))

    return file_id, issues

def main():
    files = find_files()
    print(f"Scanning {len(files)} hint files...\n")
    total_issues = 0
    by_type = {}
    for p in files:
        try:
            with open(p, "r", encoding="utf-8") as f:
                text = f.read()
        except Exception as e:
            print(f"ERR reading {p}: {e}")
            continue
        rel = os.path.relpath(p, ROOT)
        fid, issues = analyze(p, text)
        if issues:
            print(f"=== {rel} (id={fid}) ===")
            for kind, msg in issues:
                print(f"  [{kind}] {msg}")
                by_type[kind] = by_type.get(kind, 0) + 1
                total_issues += 1
            print()
    print(f"\nTotal issues: {total_issues}")
    for k, v in sorted(by_type.items()):
        print(f"  {k}: {v}")

if __name__ == "__main__":
    main()
