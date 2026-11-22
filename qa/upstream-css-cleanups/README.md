# Pinned upstream CSS cleanup qualification

This standalone Maven/JUnit gate invokes the installed Sandbox `PrettierRunner`
and `StylelintRunner` in a display-free Equinox runtime. It enumerates every CSS,
SCSS, and LESS source in each pinned repository and operates on workspace copies.

| Repository | Ref | Exact commit | CSS files |
| --- | --- | --- | ---: |
| `eclipse-jdt/eclipse.jdt.core` | `R4_41` | `6725c16c24d94c83302346dc384bb915a0f2fe1a` | 2 |
| `eclipse-jdt/eclipse.jdt.ui` | `R4_41` | `54bd63e6453a7eb5db0bfc1d60f17e9568095619` | 3 |
| `eclipse-platform/eclipse.platform.swt` | `R4_41` | `3e3b24b4691c4f18a7d5e22653fe28d4c47385d7` | 8 |

The reusable JGit fixture checks out and verifies each advertised tag and exact
commit. Node 24.19.0 and npm 11.17.0 are installed through the existing frontend
Maven plug-in. The CSS test module's unchanged `package-lock.json` installs
Prettier 3.6.2, Stylelint 17.14.1, and their parser dependencies.

## Run

Use Java 25 and a Linux Sandbox product whose `sandbox_css_cleanup` sources match
the current branch. Supply the source commit used to build that product and a
fresh output directory:

```sh
MAVEN_OPTS=-Xmx256m mvn -f qa/upstream-css-cleanups/pom.xml \
  -Dsandbox.css.product=/absolute/path/to/product \
  -Dsandbox.css.productCommit=FULL_PRODUCT_SOURCE_COMMIT \
  -Dsandbox.css.output=/absolute/path/to/new-evidence \
  test
```

Surefire and the nested Equinox process each have a 512 MiB heap limit and run
the three repository cases sequentially.

The product's CSS bundle SHA-256, harness commit, upstream identity, helper source,
tool lockfile, and process command are recorded. A product source commit with a
different CSS production tree is rejected before execution.

For repeated local runs, `-Dsandbox.css.mirrors=/absolute/path/to/upstream` may name
existing `core`, `ui`, and `swt` checkouts. JGit first verifies each origin URL and
tag commit; the same pinned checkout fixture then fetches the verified local
repository. Evidence records both the canonical upstream identity and actual
fetch URI. Without this option, it fetches directly from GitHub.

## What the gate checks

For twelve ordinary CSS files, the gate applies Prettier formatting and the
Stylelint fix path independently, verifies that each returned result is
idempotent, parses before/after structures, and compares all selected Stylelint
diagnostics before and after. Diagnostic identity retains severity, rule,
message arguments, and occurrence counts. Only embedded source-line references
in two known Stylelint rules are excluded because formatting moves those lines.
The original diagnostic messages and locations remain in the evidence files.

The parser adapter uses the pinned PostCSS, selector, and value parsers. Its
fingerprint preserves selectors, values, string contents, declaration and rule
order, important flags, at-rules, and comment text with whitespace normalized.
It excludes layout metadata,
equivalent quote delimiters, and hexadecimal color letter case. JUnit tests
verify both accepted formatting differences and rejected structural changes.
This is a conservative structure guard; it does not execute a browser or GTK
renderer.

The explicit Stylelint configuration selects eleven applicable correctness rules.
It supports Eclipse and GTK property/selector names by leaving web-platform
name-vocabulary rules unselected. Existing lint findings are retained as baseline
data; the gate rejects additional occurrences or changed severity/meaning.

### Explicit GTK syntax rejection

SWT's `swt_functional_gtk_3_20.css` contains GTK's documented
[`@binding-set` and `bind` syntax](https://docs.gtk.org/gtk3/key-bindings.html).
The pinned Prettier and Stylelint parsers reject this syntax. The test invokes
both real runners for this exact pinned source and requires the observed syntax
diagnostic, Prettier rejection, and byte-identical source/fix result.

The file is reported as `REJECTED_UNSUPPORTED_SYNTAX`, with `rejectedFiles=1`
and `PASS_WITH_REJECTIONS` for SWT. It is included in the thirteen attempted
files and excluded from the twelve successfully formatted files. Any different
rejection, changed source, missing diagnostic, or unexpected rejected file fails
the gate. No source file or JUnit test is skipped.

## Evidence

Each family retains `source-identity.txt`, `fetch-uri.txt`, the tested CSS bundle
hash, `eclipse.log`, `eclipse-command.txt`, and its complete helper inputs. Under
`corpus`, a receipt and per-file table identify every result. Per-file directories
contain original, formatted, and fixed content; parser fingerprints; full
Stylelint diagnostics; and any rejection or failure details. The GTK rejection
has no fabricated formatted output.

This gate covers the CSS runners and their return values. The preference-store
defaults regression belongs to `CSSPreferenceDefaultsTest`; workbench dialog and
action interaction need their own UI tests.

## Recorded execution

The [run receipt](results/r4_41-run.properties) and
[per-file hashes and diagnostics](results/r4_41-corpus.tsv) record an actual
Maven run against product source `daefef7c27f42a48ad9f88d55bba041bf9563688`:
ten JUnit cases passed, with zero failures, errors, or skipped tests.

| Corpus | Attempted | Formatted | Explicitly rejected | Existing diagnostics after formatting |
| --- | ---: | ---: | ---: | ---: |
| JDT Core | 2 | 2 | 0 | 0 |
| JDT UI | 3 | 3 | 0 | 14 |
| SWT | 8 | 7 | 1 | 4, plus the retained GTK syntax error |

All twelve formatted files changed and passed the structure, repeat-run, and
diagnostic-count checks. Stylelint's independent fix result changed one file's
final newline. The eighteen ordinary preexisting findings remained eighteen;
the separate rejected GTK file retained its one syntax diagnostic and bytes.
