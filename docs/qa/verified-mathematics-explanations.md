# Verified mathematics explanations

This follow-up starts from main 403f0aefa10aba33821c997180ae02ece00950b5 after #1676
was merged. PR #1689 targets main directly, without a dependent PR stack. The review
fixes also integrate main b07652c572960e41cff503164af7a901cc0eefc5.

Sandbox formats independently reverified SDK value plans, using the actual Java
input/output bindings and the existing typed Java emitter. No mathematical identity,
source-name selector, prepared target formula, or withdrawn constructor recognizer is
added. Original-value and replacement-value graphs are **not** a complete sequence of
search steps, nor do they replace the retained source evaluation trace.

`cleanup.mathematics.explanations` accepts `NONE`, `NONTRIVIAL`, or `ALL`. New/default
profiles choose NONTRIVIAL. Existing direct nine-argument Java options construction
retains comment-free output for source compatibility. NONTRIVIAL adds comments for
non-preserving profiles, explicit mathematical premises, multiple outputs, or at least
eight original estimated operation-work units. This is a presentation policy, not an
optimization rule or acceptance gate. Full explanations remain in diagnostics and the
JSON report regardless of the comment setting. The source-comment language is English,
matching the current plugin interface and upstream review text.

The Java UI exposes these modes as Source explanations. Existing comments and line
endings are retained. Source comments break raw Unicode escape sequences because Java
processes them before lexing comments. Presentation is bounded to 16,384 characters;
an overlarge presentation is diagnosed, never silently presented as a complete derivation.
Cancellation from the renderer propagates OperationCanceledException. A thread interrupt
aborts the entire analysis, including any already accumulated replacements, and leaves
the interrupt flag set. Ordinary progress-monitor cancellation retains the existing
empty cancelled-analysis result. Cancellation is never classified as an unsupported
mathematical candidate and must not publish partial edits.

The independent numeric checker and Java emission verifier remain mandatory, with
explicit checked-contract/fallback warnings. Estimated operation work is not a measured
speedup or a constant-time qualification. The SDK and mathematical search are unchanged.

## Reproducible Help configuration

The native mathematics capture begins with all registered mathematics defaults and
then applies explicit fixture settings, including the optional `explanations` setting.
Before analysis it reads the project's effective mathematics options back and requires
exact equality with the profile that will be recorded. All four provenance files now
contain all ten effective options, including `cleanup.mathematics.explanations`.
The complete original and replacement source, loaded adapter fingerprint, SDK identity,
screenshot digest, native preview, Apply and byte-exact Undo remain independently checked.

## Executed qualification

The initial test-only revision cebce220e376a402be1d4c0680f80c97ddadcced in run
[38039248065](https://github.com/carstenartur/sandbox/actions/runs/38039248065)
ran 204 tests: the previous 196 passed and eight new tests failed because the
explanation option did not exist. The initial implementation was subsequently qualified
with 204 standard tests and all nine native workbench scenarios in run 38040451091;
canonical Help reproduction succeeded in run 38041389066. Those are historical results,
not a substitute for qualification of later source changes.

Review-regression run
[38043177189](https://github.com/carstenartur/sandbox/actions/runs/38043177189)
at test-only 868076b95a050179121050c5fe5b323b4290fea7 reproduced the defects:
215 standard tests, five cancellation-related failures, no errors or skipped tests;
the independent native profile test also failed because the effective explanation mode
was missing. The policy-trigger tests already passed and protect the intended behavior.

Fix revision **39255de0bfb17f4721475f2f47a1c07dd6e925d3**, integrating main b07652c,
was qualified before non-force publication in
[38043632317](https://github.com/carstenartur/sandbox/actions/runs/38043632317):

- Complete standard mathematics module: **216 tests, zero failures/errors/skips**;
  both mathematics-module SpotBugs checks report zero findings.
- Eight focused renderer/policy tests, three whole-analysis interruption tests and
  nine source-explanation integration tests are included in those 216, not additional
  totals. Policy tests cover each trigger and the exact 7/8-work boundary. The boundary
  integration test checks actual generated comments, compiled Java equivalence and Undo.
- The two native mathematics Help tests passed twice from clean workspaces on the
  canonical Ubuntu 24.04/X11/Java 25 runtime. Each capture exercised all four complete
  Bouncy Castle fixtures. All four PNGs remain byte-identical to the committed images;
  repeated PNGs and regenerated provenance are byte-identical. No tolerance was relaxed.
- Downloaded JUnit XML, ten-key profiles and screenshot hashes were independently checked.

Qualification artifact: 11667016938, SHA-256
`a637c22d79f3e82fd0935c49f8f6fe4c48093c519e5ff999a3ae7c1bb680ee4a`.
The executable-source test tree is `d7b8a51d8a6b57d2e282d8befda643744c56adc5`.
The published tree is `ec84903aa8572bbb07631593b970b0d7c0427ba2`, after accepting only
provenance generated by the successful native captures. Diagnostic orchestration files
and workflows are not part of the product PR.

Normal whole-repository PR checks and independent review remain required. This record
does not claim #1689 has merged or that general loop inference, a full search-step
explanation, or the complete mathematical optimizer has been delivered.
