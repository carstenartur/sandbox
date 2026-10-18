# TriggerPattern: change to reviewed project rule

This implements the milestone requested on 18 October 2026 against `65fb1e0`.
The acceptance path is an ordinary Java change, a lossless strict rule draft,
an actual project rewrite preview, and a reusable project hint.

## Independently reviewable changes

1. Safety: remove state-changing examples; recursively and conservatively
   check effects; preserve unknown semantic information. Correct the bundled
   String cleanup rules using binding-aware guards and Unicode counterexamples.
2. Matching: give indexed and individual execution the same matcher, original
   nodes, sequence ranges, constraints and binding behavior.
3. Authoring: retain multiline selections and the complete DSL document;
   expose before/after drafts without silently guessing placeholder scope.
4. Project trial: execute the production rewrite backend with the project's
   classpath and compiler options; show bounded guard/binding diagnostics and
   actual Eclipse refactoring diffs, with cancellation, stale-source checks,
   syntax/type checking, coherent file changes and normal undo.
5. Evidence: JUnit regressions, real JDT substring/isBlank comparison cases,
   documentation distinguishing language checks from semantic evidence.

## Constraints and verification

- Java 25; Maven/JUnit are the executable authority. Existing workbench test
  fixtures cover integration; core tests must run without a workbench.
- Missing required bindings never authorize a rewrite. Side-effect freedom
  alone does not authorize duplication, omission or argument reordering.
- Preserve Charset import and checked-exception follow-up processing by using
  `HintFileFixCore` rather than a second text-substitution implementation.
- Project trials do not execute embedded Java or silently ignore includes.
  Unsupported draft capabilities receive an explicit diagnostic.
- Keep review slices within repository limits; split implementation into
  dependent PRs rather than enlarging an unrelated migration branch.
- Do not claim measured speed, complete JDT parity, successful UI interaction
  or maintainer acceptance without corresponding evidence.

## Subsequent work requiring separate evidence

Automatic inference from multiple positive/negative examples, packaged API
migration contracts, a minimal upstream dependency extraction and performance
comparisons are distinct deliverables. The implementation report records which
parts are complete and which still need validation or implementation.
