# Verified mathematics explanations

This follow-up starts from main 403f0aefa10aba33821c997180ae02ece00950b5 after #1676
was merged. It must be reviewed directly against main, without a dependent PR stack.

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
an overlarge or cancelled presentation is diagnosed, never silently presented as a
complete derivation. The independent numeric checker and Java emission verifier remain
mandatory, with explicit checked-contract/fallback warnings. Estimated operation work
is not a measured speedup or a constant-time qualification.

Red-before-implementation: run 38039248065 at test-only cebce220e376a402be1d4c0680f80c97ddadcced
ran 204 tests: all previous 196 passed; the eight new tests failed because the explanation
option was not implemented. This document does not claim the follow-up is already merged
or that its new tests/native UI/whole-repository checks have passed.
