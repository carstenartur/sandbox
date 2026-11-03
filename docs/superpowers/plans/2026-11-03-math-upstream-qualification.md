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
- [ ] Run original upstream builds/tests, ordinary cleanup, and identical rebuilt
      tests wherever dependencies and baseline prerequisites are available.
- [x] Independently review the additions and run the inventory regressions; add a
      native CI path and require positive execution in both SWT test modules.
- [ ] Record exact commands, versions, results and remaining blocked stages in
      `docs/qa/math-upstream-qualification-20261103.md`.

## Acceptance

New tests exercise the actual transformed production methods and detect a wrong
replacement. The report distinguishes locally executed checks from earlier CI
records. A before/after success requires the same scope and test inventory; a
clean compile alone is not an all-tests claim. No performance claim is accepted
without representative measurements including the actual call path, guards and
fallbacks, with the JVM and source identities retained.
