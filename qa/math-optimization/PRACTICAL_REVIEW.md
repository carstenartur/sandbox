# Practical mathematics review: Bouncy Castle and SWT

The cleanup is a source-review tool, not an automatic upstream patch approval.
Keep independently verified edits separate from estimated usefulness, actual
program benchmarks and cryptographic timing qualification.

## Advisory report

The existing headless mathematics JSON report adds `reviewCandidates` to each
file. Each entry points to an original source offset/length and contains:

- `estimatedScoreSaving`: the SDK's source score minus its final candidate score;
- `changedLines`: inserted plus deleted lines, ignoring indentation and unchanged
  context; `-1` means the bounded comparison could not be completed;
- `addedBranches` and `addedLocals`: positive differences in syntactic control-flow
  constructs and declared local bindings, not dynamic execution counts;
- `disposition` and `cautions`: review categories, never permission to apply.

The advisory order is compact, guarded, large, contract-sensitive, no estimated
runtime gain, then unassessed. Within a category, greater estimated saving comes
first, followed by smaller diffs and the original offset. More than 40 changed
lines or eight new local bindings triggers large-rewrite review. These thresholds
are review heuristics, not performance measurements or proof limits.

Mathematical BigInteger and checked-mode results require contract review; the
metadata says a contract change is **allowed**, not that every result necessarily
changes observable behavior. Even a compact strict rewrite retains warnings that
runtime and timing behavior have not been measured. Legacy evidence without the
necessary metadata, malformed snippets and analysis bounds are explicitly
unassessed. No verified edit or evidence is removed or reordered for application.

## Existing guards as proven facts

The adapter can remove a duplicate receiver guard when dominating source checks
establish its complete conjunction for the whole selected region: non-null, exact
`BigInteger` class, `bitLength() < 4096` or a stronger integer bound, and positive
sign when required. Only binding-resolved final or effectively final locals and
parameters qualify; the mathematical opt-in contract remains unchanged.

Facts can come from enclosing true `if` branches or an earlier guard clause in
an enclosing block: `if (invalid) return ...;` or `if (invalid) throw ...;`.
The early-exit branch must have no `else` and must consist of a return/throw,
optionally wrapped in single-statement blocks. A negated valid conjunction or
an invalid-condition disjunction is supported. A true `&&` or false `||`
establishes each term; a false `&&` or true `||` does not. Previous guards and
enclosing conditions can contribute different parts of the complete conjunction.

Fields, reassigned variables, `instanceof`, conditional exits, caught throws,
`try/finally`, arbitrary terminating calls and unrelated branches do not establish
continuation facts. An unproved receiver retains its full existing guard, not
merely the unproved individual checks. No fact crosses a lambda, method or
anonymous-class boundary. Fractional limits are not converted to integer bounds.
The existing 4,096-node and 128-ancestor limits include early-exit discovery;
exhaustion or interruption yields no partially established guard proof.

All numerical premises remain in the SDK request with source-bound provenance.
Independent emitted-Java verification remains mandatory. Original guard clauses,
return/throw statements and side effects stay in place. This does not infer
general loop invariants or arbitrary interprocedural facts, permit BigInteger
code motion, or activate mathematical mode implicitly.

## Reproducible archive runs

`run-project.sh` uses the actual production adapter and the advisory report code.
It needs **Java 25** and a classpath file containing the freshly built mathematics
classes/plugin, the pinned standalone SDK JAR, JDT/runtime dependencies and
Jackson. Use the same classpath exported by the existing standalone harness or
native build; do not substitute a different SDK. The runner records the loaded
code origins, all classpath JAR/class hashes and checks them again at completion.
The original historical four-argument `BigIntegerCorpusScan` remains unchanged.

Profiles are explicit. `profiles/bc-strict.properties` and
`profiles/bc-mathematical.properties` retain the all-kinds, one-million-work,
20,000-state comparison settings. Only the latter enables mathematical semantics.
`profiles/swt-strict.properties` selects primitive integral kinds with mathematical
mode off; simply using the cleanup's default BigInteger-only profile is not a SWT
pilot. All profiles retain the existing 64-candidate / 5,000-ms request limits.

Run from a clean checkout, with absolute dependency paths and a fresh output:

```sh
bash qa/math-optimization/run-project.sh "$MATH_CP_FILE" \
  "$BC_BINARY" "$BC_SOURCES" qa/math-optimization/profiles/bc-strict.properties \
  "$OUT/bc-strict" \
  b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f 17

bash qa/math-optimization/run-project.sh "$MATH_CP_FILE" \
  "$BC_BINARY" "$BC_SOURCES" qa/math-optimization/profiles/bc-mathematical.properties \
  "$OUT/bc-mathematical" \
  b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f 17

bash qa/math-optimization/run-project.sh "$MATH_CP_FILE" \
  "$SWT_BINARY" "$SWT_SOURCES" qa/math-optimization/profiles/swt-strict.properties \
  "$OUT/swt-image-data" "$PINNED_SWT_SOURCE_SHA256" "$SWT_TARGET_JAVA" \
  org/eclipse/swt/graphics/ImageData.java
```

The BC hash is the previously qualified **bcprov-jdk18on 1.85.2 Maven source
archive**, not the differently packaged Orbit source bundle. Pin both SWT archives
from the same published platform/version and set `SWT_TARGET_JAVA` from that
project's compiler settings before running; there is deliberately
no invented SWT source hash or assumed current release here. Exact selections
must exist, and unsafe or duplicate archive names are rejected. UTF-8 decoding
is strict. Parse errors remain visible and are not counted as analyzed files.

`inputs.json` records the intended run. Only a complete run with unchanged inputs
writes `review.json` and `COMPLETED`; a partial directory is not successful evidence.
`COMPLETED_WITH_PARSE_ERRORS` explicitly retains excluded sources. For changed
files, `original/`, `generated/` and `evidence/` retain actual code and its proof.
Apply and exact Undo are tested in memory; upstream archives/files are never edited.
The runner does not compile the entire upstream project or execute benchmarks.
No new corpus totals or SWT hits follow merely from adding this runner.

## Selecting upstream proposals

First inspect small verified diffs, then compile and test the actual original and
changed upstream function. For SWT, compare image outputs and relevant scaling
edge cases. For BigInteger, benchmark realistic operand sizes and allocations;
include generated guard/fallback costs. Use the existing JMH harness rather than
interpreting estimated scores or optimizer wall time as generated-program speed.
For cryptographic code, separately establish whether inputs are public and assess
secret-dependent control flow/memory access. Strict Java equivalence is not a
constant-time proof. Keep declined proposals and their reasons in the review.
