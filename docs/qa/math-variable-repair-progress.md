# Mathematics variable-region repair

Tracking #1657; implementation PR #1676 and companion Regelsuche PR #1075.
This ledger distinguishes verified code from the remaining parent-issue work.

## Verified source

Implementation commit: `ed355d95067d713a7b07226762001d143561c06e`.

[CI run 37930392257](https://github.com/carstenartur/sandbox/actions/runs/37930392257)
completed successfully on 2026-10-09. Its retained JUnit XML reports contain:

- the complete default mathematics module: **156 tests, 0 failures, 0 errors,
  0 skipped, 22 suites**;
- a separate focused run of `MathVariableRegionsTest`: **7 tests, 0 failures,
  0 errors, 0 skipped**. Those seven cases also belong to the module total; they
  are not seven additional unique tests.

The workflow tested the source changes before publishing that implementation
commit. Both temporary source patches and the one-time bootstrap workflow were
removed. The PR contains ordinary Java sources and the vendored SDK; no patch
application on main or automated merge is part of delivery.

The SDK remains pinned to qualified source
`655809a500a18eda8dca6ad4e89b82751de0e521`. Its qualification and two-clean-build /
two-fresh-consumer receipts are retained under `sandbox_math_cleanup/lib/`.

## Repair covered by these tests

Numeric return expressions preserve the method return conversion after the
original expression. Output liveness is separate from the full execution trace.
Internal local statements can disappear without losing adjacent comments;
externally used declarations and declarations with embedded comments remain.
Plain emission reuses input names, inlines single-use nodes with grouping, and
retains shared temporaries. Guarded returns keep the original fallback.

The Java emission verifier requires exactly the requested output bindings.
Mutation tests now target actual output IDs and expressions, prove their
unmodified fixture is accepted, and verify the mutation really changes code.
They continue to reject wrong operations, hidden division/exact-arithmetic
exceptions, and incorrect conditional floating-point replacements. Two new
contract tests demonstrated the missing output-binding validation before its
production fix.

The variable regressions cover direct returns, multiline factoring, return
conversions, externally used locals, checked overflow, guarded fallback and
retention of dead throwing operations. Generated Java is compiled and compared
on boundary-value combinations; undo must restore source bytes exactly.

## Boundaries retained

BigInteger object-return identity, unproved parameter magnitude/dispatch, raw
NaN-payload observations and unmodelled side effects remain conservative
rejections. Checked source occurrences, including operations whose value is
internal, remain in the request and exception checks.

## Native UI attempt and remaining gates

A separate local Java-25/Tycho/Xvfb attempt with
`-Pswtbot -Dtest=MathematicalWorkbenchSWTBotTest` aborted before producing PNGs:
`SIGSEGV` in `libgdk-3.so.0`, `gdk_window_get_screen`, called through
`GTK.gtk_im_context_set_client_window` during cleanup-preferences layout.
The cause is not established. This is **not** a passing native UI qualification.

Genuine runtime-variable Help screenshots, BigInteger parameter/guard-cost
coverage, stronger unchanged-production-corpus examples, final p2/product
installation, normal exact-head repository CI and final review remain open.
The default-module result is not a benchmark, cryptographic constant-time
qualification, complete-product approval, or completion of #1657.
