# Useful incoming assignments in SWT and Bouncy Castle

The adapter previously rejected an assignment when its primitive local or
parameter was declared outside the extracted block. This hid arithmetic such as
SWT's `grow = (j - modifyStart) - (modifyEnd - modifyStart);` from the existing
SDK. The new admission exposes each such assignment as an isolated region under
`PRESERVE_JAVA`, without combining separate observable writes.

## Frozen whole-source comparison

Baseline is main `6d7545d38b013a95becdd54437478988f584c1ae` after PR #1707.
Both sides use BC 1.85.2 or SWT GTK 3.135.0, the unchanged SDK, Java 25.0.4.1+1,
JDT 3.47, 1,000,000 work per file and 20,000 states. Source compliance is Java 17
for BC and Java 21 for SWT. `comparison.json` records input and profile hashes,
run timestamps, adapter source hashes, exact additions and retained-region counts.

| Corpus and profile | Before | After | Added |
| --- | ---: | ---: | ---: |
| SWT, strict Java | 7 | 10 | 3 |
| BC, strict Java | 109 | 115 | 6 |
| BC, mathematical opt-in | 118 | 124 | 6 |

All previously accepted regions remain accepted. SWT parses all 473 source
files. BC parses 3,126 files with the same ten multi-release/module errors;
all 3,136 files remain available as shared source context. Counts describe
verified, source-validated edits with exact Apply/Undo, not measured speedups or
whole-library behavioral qualification.

SWT gains two cancellations in the actual `addMerge` overloads and a scrolling
offset simplification, all in `StyledTextRenderer`. The cancellation reduces
three subtractions to one. BC gains bitwise-select rewrites in both CMCE package
copies, a factorization in FAEST challenge decoding, and sharing of `3 * low` in
Hawk verification. The six BC additions include duplicate CMCE implementations;
they are not six independent algebraic discoveries. The SDK already knew all
of these transformations.

## Quality and budget barriers addressed

An intermediate implementation found 138 BC candidates but lost six old ones:
the additional regions diluted existing search allocations. It also expanded
some named helpers or introduced locals merely to share implicit casts.
Those intermediate counts are not the qualified result.

Whole-file analysis now defers incoming primitive assignments until established
regions and their retries have run. Preparation, modular-statement and
array-to-scalar retries apply the same rule when they expose further incoming
assignments. Explicit selections still use the available budget immediately.
The runtime goal additionally requires a reduction in source arithmetic
operators for this new admission. Helper expansion and cast sharing alone do
not pass. This conservative filter can reject equal-operator-count strength
reductions; it does not alter the SDK's cost model or proof requirements.

The review also exposed a fragment-limit boundary bug: after a four-output
retry reset, an incoming assignment could be combined with reads of its old
value. An executed regression demonstrated the wrong result before the fix.
Resetting the builder before classifying the assignment now preserves its
singleton boundary, including on retry paths.

## Validation and limits

The Maven/JUnit core suite passes 406 tests with no failures, errors or skips.
The 30 focused tests include conversions through byte/short/char/int/long,
integer wraparound, observable writes before exceptions, budget starvation,
retry ordering and the executed fragment-limit counterexample.
`SwtAssignmentRegionTest` analyzes and compiles the complete original and
generated renderer and compares both actual `addMerge` overloads across 96
style/range cases. The headless core harness does not replace the native
Maven/Tycho, Eclipse UI, source-receipt or runtime-corpus gates; those remain
required in the pull request's normal CI. Local native qualification is blocked
by the unavailable display and an uncached target-platform artifact.

Larger gains still require better proven receiver/range facts, supported source
dependencies and independently checked proof coverage. Unknown calls, field
effects, array dependencies and long modular chains remain real boundaries.
Their raw diagnostic totals include non-mathematical code and are not counts of
missed useful refactorings. No BC/SWT-specific recognizer, SDK change, budget
increase or relaxed correctness gate was introduced.

Reproduce with `qa/math-optimization/run-project.sh`, freshly compiled adapter
classes, the pinned binary/source archives and the existing `swt-strict`,
`bc-strict` and `bc-mathematical` profiles. Use fresh output directories and the
source hashes and Java levels recorded in `comparison.json`.
