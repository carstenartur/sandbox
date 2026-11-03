# Mathematics qualification on real upstream projects

## Goal and scope

Continue the Bouncy Castle and SWT qualification and exercise the ordinary
mathematics cleanup on actual Eclipse JDT Core and UI source checkouts. Establish
separate evidence for numerical equivalence, compilation, upstream test outcomes,
static bytecode changes, and measured runtime. A shorter expression or method is
not itself a runtime improvement.

The starting Sandbox revision is `95bfbddc067d0033a86fb2af97baf6b5cd81e297`.
Use isolated source trees. Preserve existing proof checks, source identities,
runtime qualifications, optimizer budgets, and test selection. Existing full
upstream failures must remain visible rather than being reclassified as passes.

## Design

1. Reuse ordinary analysis and Apply, including its proof and Java-emission
   verification. Do not introduce hand-written replacement formulas for a corpus.
2. Close the known SWT execution gaps: the changed JPEG `extractData`/`storeData`,
   TIFF `getColorMap`, and the renderer's changed scrolling calculation. Compile
   complete original and generated classes from exactly hashed archives. Execute
   identical cases in isolated class loaders or separate GTK/Xvfb JVMs. Require
   targeted wrong-result mutations to be detected.
3. Keep archive-based behavior checks separate from the existing live Git
   provenance test. The former can be reproducible offline; it does not prove the
   latter was run or that an entire upstream test suite passed.
4. For JDT Core/UI, use pristine release checkouts compatible with the installed
   Eclipse target, record exact commits, and run the same official build/test
   command before and after ordinary cleanup. Record all test identities and
   skipped/error/failure outcomes. Dependency or infrastructure failures are
   blocked stages, not successful comparison results.
5. Compare executable bytecode with the same compiler, target, flags and classpath.
   Resolve constant-pool references symbolically. Report instruction count, code
   bytes, branches/calls/allocations and maximum stack separately. Do not infer
   throughput, latency, or cryptographic constant-time behavior from these counts.
6. Reuse existing source explanations (`NONE`, `NONTRIVIAL`, `ALL`). Keep the
   verified contract, premises and operation-work estimate in source comments.
   Put compiler/test/benchmark observations in revision-bound QA evidence because
   ordinary Apply has not executed those qualifications.

## Implementation and verification sequence

- [x] Inspect current Oomph provisioning and the existing native math application.
- [x] Obtain independent bytecode observations for actual BC/SWT replacements.
- [x] Add meaningful SWT changed-method behavior coverage and verify its headless
      mutations; retain the real native renderer gate for the Linux CI run.
- [x] Acquire a matching materialized Sandbox product and pristine JDT checkouts.
- [x] Complete both official SWT Linux/GTK reactors with identical inventories:
      5,012 tests, 4,550 passed, 462 unchanged skips, zero failures/errors.
- [x] Apply the native mathematics plugin to all 4,287 JDT production source units;
      retain the five accepted changes, UI zero-change result and warning delta.
- [x] Reproduce Tycho's replacement of changed Core bytecode with release bytecode;
      use the same separately recorded build-only correction in both variants.
- [x] Run original upstream builds/tests, ordinary cleanup, and identical rebuilt
      tests wherever dependencies and baseline prerequisites are available.
- [x] Independently review the additions and run the inventory regressions; add a
      native CI path and require positive execution in both SWT test modules.
- [x] Record exact commands, versions, results and remaining blocked stages in
      `docs/qa/math-upstream-qualification-20261103.md`.

## Acceptance

New tests exercise the actual transformed production methods and detect a wrong
replacement. The report distinguishes locally executed checks from earlier CI
records. A before/after success requires the same scope and test inventory; a
clean compile alone is not an all-tests claim. No performance claim is accepted
without representative measurements including the actual call path, guards and
fallbacks, with the JVM and source identities retained.

## JDT execution extension

Build the native product from the same CI checkout and verify its commit, adapter
and embedded SDK hashes. Reuse the existing official-reactor wrapper and pinned
JGit fixture. Run Core and UI independently on native Linux CI with JavaSE
1.8/17/21/25 toolchains, no compliance/test filters, and a frozen dependency cache
for the second phase. Preserve raw receipts, command failures and interrupted
reports outside the disposable checkouts. Stream large Apply JSON rather than
loading its complete sources and proof trees into the JUnit heap.

The Maven/JUnit gate must reject stale, incomplete and duplicate source receipts,
unsuccessful Apply, missing/all-skipped test modules, released JARs in place of
reactor output, mismatched class inventories, and changes to source embedded only
in a prebuilt jar-in-jar-loader ZIP. A complete zero-change UI scan is valid.
Regression fixtures must first demonstrate the relevant false-green cases.
The generated Java is retained exactly; new warnings and source growth are
reported instead of silently repairing the output before measuring it.

Completed on tested head `55a98f69cd59fbcdb67813091ea2af3a3cc92374`: both native
Core 20-module reactors pass with identical 290,650 passing executions and zero
skips/failures/errors per phase. Both native UI 25-module reactors pass with
identical 9,577 cases, 9,423 passed and 154 skipped per phase. Core repeats the
five local edits and 13 warnings; UI again accepts no edits. The separately
recorded interrupted/local environment failures remain visible as history.

## Targeted MethodInfo usefulness measurement

The actual local Apply output adds 13 warnings and expands the complete
`decodeCodeAttribute` method from 81 to 189 JVM instructions. While the native
Core regression comparison runs, measure this concrete unresolved usefulness
question with the already-qualified original/generated compiler JARs. Keep the
experiment isolated from the product sources and the running CI checkout.

Use JMH for complete fresh ClassFileReader/name-decoding calls, including the
generated threshold, guards and fallback. Bind loaded classes to the retained
JAR/Class hashes. Before measurement, JUnit must validate complete JVM-verifiable
input classfiles and expected parameter names, so a preinitialized or cached
MethodInfo cannot silently bypass the changed method. Cover ordinary small
exception tables and both sides of the 4,096-entry threshold, plus a bounded
large-table stress case. Separate synthetic boundaries from any real-class
control and preserve identical input bytes in both variants.

Use the same bounded initial JMH protocol as the retained BC comparison: two
forks, three 500 ms warmups and five 500 ms measurements per fork, with time and
normalized allocation results. Preserve code, commands, hashes, raw samples and
limitations as analysis evidence. This is a steady-state measurement, not a
cold-start claim, and does not substitute for the full native Core tests.

Completed: ten JUnit gates pass; all 18 JMH cases and 36 forks complete. The
four valid synthetic inputs at or above 4,096 handlers require 2.16–2.49 times
the full operation time and 2.82–3.05 times the allocated bytes after cleanup,
with separate 99.9% intervals. Smaller cases and the single real-class control
have overlapping intervals. Independent read-only review confirms the measured
path, warm-cache scope, exact reachable branches and loaded product identities.
The generated code remains unchanged for qualification; this result rejects a
usefulness claim for its present form rather than changing the running experiment.
Compact receipts are retained in the repository results directory and complete
analysis sources, fixtures and raw measurements in the evidence archive.
