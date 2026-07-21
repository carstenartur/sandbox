# Open pull request review — 2026-07-21

## Scope

Repositories reviewed:

- `carstenartur/sandbox`, target branch `main`;
- `carstenartur/eclipse.jdt.ui`, target branch `master`.

Each open pull request was evaluated against the current target branch, its last available CI results, changed-file scope, overlap with already merged work, semantic safety, test quality, and suitability for later upstream contribution.

## Result

After the review, both repositories have **zero open pull requests**.

## Sandbox

| PR | Decision | Rationale |
|---|---|---|
| #1237 | Squash-merged | Documentation/metadata-only QA correction. Final head passed Maven/Tycho, CodeQL, Codacy, and test-report workflows. It aligned Tycho 5.0.3 references, release wording, cleanup capability boundaries, mining badges, and QA documentation. |

Earlier multi-file cleanup work remained valid:

- #1207 was already squash-merged after full reactor/security/test validation;
- the dependent JDT UI scope patch #94 was already squash-merged in the fork.

## Eclipse JDT UI fork

Two related but different JUnit capabilities must not be conflated:

1. Upstream #2907 merged adding/removing `@Disabled` or `@Ignore` for an entire test method. Its code includes an intended path for parameterized method nodes, but this is still a whole-method toggle.
2. Excluding or re-including one individual invocation value from `@EnumSource` changes the annotation's `mode` and `names`. That separate feature was proposed in the closed upstream PR #2744 and remains unmerged under issue #2774.

| PR | Decision | Technical reason |
|---|---|---|
| #86 | Closed as superseded for whole-method toggle | The whole-method disable/enable action was merged upstream through #2907 and is present on current fork `master`. This decision does not cover per-value `@EnumSource` filtering. |
| #89 | Closed as superseded for whole-method toggle | Same landed whole-method functionality as #86, combined with a formatting experiment that should be proposed independently. It did not replace the unmerged per-enum-value feature. |
| #81 | Closed as superseded foundation | Older annotation-modification base for the whole-method functionality that later landed upstream; not a valid base for the remaining per-value EnumSource work. |
| #44 | Closed as obsolete monolith | 31 files/65 commits combined whole-method toggling, per-value EnumSource filtering, quick assists, model changes, metadata extraction, labels, and context-menu source mutation. The per-value requirement was not merged; the corresponding upstream PR #2744 was closed and issue #2774 remains the design track. |
| #92 | Closed as duplicate | Substantially duplicated #93 and the same still-unmerged EnumSource exclusion/re-inclusion design. It was not closed because #2907 supplied this feature. |
| #93 | Closed after semantic review | The requirement remains valid, but the branch mixed feature and fork workflows; matched methods by simple name rather than binding/signature; inferred enum constants from display names; did not preserve all INCLUDE/EXCLUDE semantics; performed repeated AST parsing in UI label/menu paths; and lacked exact source, compilation, undo, dirty-editor, overload, and custom-display-name tests. |
| #72 | Closed despite green historical CI | The proposed `WeakHashMap<IDocument, Tracker>` retained its key through the value, had no production disposal path, did not make document changes plus region conversion atomic, and lacked an end-to-end reproduction of upstream issue #2499. |
| #66 | Closed after semantic review | Rejected any same-name/equal-arity/different-type super method without comparing actual invocation bindings. This would create false positives and still miss many binding-change scopes. Upstream issue #2984 is the appropriate generic design track for #1865-class bugs. |
| #41 | Closed as stale upstream-port branch | The corresponding upstream PR #2213 was closed unmerged with unresolved semantics and I-build test concerns. The fork branch also mixed 45 feature/build/workflow files and had failed Maven/CodeQL status. The maintained implementation remains in Sandbox. |

## Retained issue tracks

Closing a PR did not dismiss the underlying user need:

- closed upstream PR `eclipse-jdt/eclipse.jdt.ui#2744` and open issue #2774: staged, binding-safe EnumSource/test-runner workflow, including individual value exclusion/re-inclusion;
- `eclipse-jdt/eclipse.jdt.ui#2499`: deterministic save-action region regression and focused fix;
- `eclipse-jdt/eclipse.jdt.ui#1865` and #2984: pre/post refactoring binding stability;
- Sandbox #1209–#1229: product delivery, headless transactions, lifecycle tests, minimal scope discovery, diagnostics, compatibility, and release evidence.

## Review principles established

A future JDT contribution branch should:

1. start from current upstream `master`;
2. contain no fork CI, README, Maven-parent, or sync-workflow changes;
3. identify Java declarations and invocations by bindings/signatures rather than labels or simple names;
4. use Eclipse working-copy/refactoring conventions instead of directly saving from UI rendering/actions;
5. include exact transformed-source and semantic compilation tests;
6. test undo, dirty editors, custom names, overloads, cancellation, and UI-thread cost where applicable;
7. remain small enough to review as one coherent feature or fix.

## Conclusion

The pull-request backlog is now clean. No technically unsafe or duplicate branch was merged merely because it compiled. Valid completed work was merged; remaining requirements—including individual `@EnumSource` value filtering—are preserved as issues or closed upstream design references for new, focused implementations.
