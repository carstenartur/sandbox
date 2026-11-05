# Mining verification and closed review repair

Replay the five unmodified candidate records from mining/candidates commit
`c34752745558b335813d6d161f3066fe2e5bf2c7` without calling an LLM.

1. Add JUnit regressions for the exact source evidence and the actual verifier
   boundaries, plus public-type filename, multiple-public-type, and negative controls.
2. Infer the Java unit name from the parsed public top-level declaration. Continue
   checking every compile error and require exactly one positive replacement.
3. Report unresolved imports as invalid source or unavailable dependencies; retain
   a failed result, unestablished validity, and every compile/type/behavior/source gate.
4. Align the generation prompt with the existing strict container and runtime:
   no file metadata, JDK-only compiling examples, one positive occurrence, no stubs.
5. Validate every record in an explicit, paginated GitHub issue snapshot before any
   candidate/report/index writes. Require actual creator metadata and trust only
   github-actions[bot] (GitHub user ID 41898282, type Bot), then match the exact
   leading candidate marker and current revision. Emit the same trusted association
   as a temporary Java-produced creation-deduplication index. The existing Python
   adapter consumes that index instead of searching untrusted issue bodies.
   Closed not_planned becomes REJECTED; closed completed becomes
   historical SUPERSEDED, without inferring approval or promotion. Preserve existing
   terminal decisions and leave newer revisions available for their own review.
6. Publish separate ready/other-failure/unresolved-import/rejected/historical counts through Java and remove
   the legacy Python candidate summary. Keep shell as process adapters.
7. Run targeted red/green and complete common-core/mining Maven verification,
   replay all recorded candidates, check the policy line budget, commit locally.

The current-head review also requires independent filename compilation errors to
retain BEFORE_PARSE precedence when an unresolved import appears first. Verifier
version 9 remains diagnostic metadata in this unmerged change; it does not control
verification caching, and this repair changes neither success nor admission gates.

No live inference, additional budget, candidate-branch writes, issue edits, pushes,
or productive DSL promotions are part of this change.
