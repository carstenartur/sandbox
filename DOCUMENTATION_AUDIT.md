# Documentation Audit — Correctness & Completeness

**Date:** 2026-06-27
**Scope:** All components (plugin modules, infrastructure modules, root-level and `docs/` documentation).
**Objective:** Verify that documentation is a *complete and correct* representation of what
the code actually does. This audit is about factual accuracy, not marketing or style.

## Methodology

Each module's `README.md`, `ARCHITECTURE.md`, `TODO.md` (and any other module-level
markdown) was compared against the actual source code and configuration:

- Do referenced class names, packages and constants exist in the code?
- Are described features actually implemented?
- Do versions (Java, Tycho, Eclipse release) match `pom.xml` / `MANIFEST.MF` /
  `*.product` / `*.target`?
- Do internal documentation links point to files that exist?
- Are there significant public components with no documentation, or documentation
  describing components that no longer exist?

Every finding below was independently verified against the source before being recorded.

---

## Summary

| Area | Result |
|------|--------|
| Modules whose documentation was accurate | encoding_quickfix, junit_cleanup, css_cleanup, use_general_type, common, common_core, triggerpattern, ast-api, coverage, web, benchmarks, mining_core |
| Modules with factual errors **corrected in this PR** | cleanup_application, platform_helper, tools, jface_cleanup, functional_converter, oomph, target, product, test_commons, extra_search, usage_view + root README/inventory/pom |
| Outstanding gaps (recommendations, not yet fixed) | Missing READMEs (mining_cli, jgit modules, maven-plugin, ast-api-jdt); one code/doc behavior mismatch (xml_cleanup); aspirational "future" features |

---

## Corrections applied in this PR

### sandbox_cleanup_application
- `ARCHITECTURE.md` referenced the wrong package
  `org.sandbox.jdt.cleanup.application.*`. The actual classes live in
  `org.sandbox.jdt.core.cleanupapp.*` (`plugin.xml` `<run class="…cleanupapp.CodeCleanupApplicationWrapper"/>`).
  **Fixed.**
- *Verified correct (no change):* the application id
  `sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup` is **correct** —
  Eclipse composes it as `Bundle-SymbolicName` (`sandbox_cleanup_application`) +
  `.` + the `<extension id>` (`org.sandbox.jdt.core.JavaCleanup`).

### sandbox_platform_helper
- `ARCHITECTURE.md` documented a class `StatusCleanUp` at
  `org.sandbox.jdt.internal.corext.fix.StatusCleanUp` that does not exist. The real
  cleanup is `SimplifyPlatformStatusCleanUpCore`
  (`org.sandbox.jdt.internal.ui.fix.SimplifyPlatformStatusCleanUpCore`). **Fixed.**
- The documented cleanup constant `MYCleanUpConstants.PLATFORM_STATUS_CLEANUP` does not
  exist; the real constant is `MYCleanUpConstants.SIMPLIFY_STATUS_CLEANUP`
  (`sandbox_common/.../fix2/MYCleanUpConstants.java:76`). **Fixed.**

### sandbox_tools
- `README.md` and `ARCHITECTURE.md` documented a class `WhileToForConverter` at
  `org.sandbox.jdt.internal.corext.fix.WhileToForConverter` that does not exist. The
  real class is `WhileToForEach`
  (`org.sandbox.jdt.internal.corext.fix.helper.WhileToForEach`). **Fixed.**

### sandbox_functional_converter
- `README.md` and `FAQ.md` linked to `COMMENT_PRESERVATION.md` and `EXAMPLES.md`,
  neither of which exists in the module. Links were redirected to the existing
  `ARCHITECTURE.md` (comment-preservation details) and `README.md` (examples). **Fixed.**

### sandbox_oomph / sandbox_target / sandbox_product
- Multiple "current state" claims referenced **Eclipse 2025-09**, but the actual
  configuration targets **2025-12** (`sandbox_oomph/sandbox.setup`,
  `sandbox_target/eclipse.target`, `sandbox_product/sandbox.product`). The stale
  current-state references were updated to 2025-12. **Fixed.**
- *Left intentionally unchanged:* example/instructional references such as
  "change from 2025-12 to 2025-09" and the multi-release build scenarios in
  `sandbox_target/TODO.md` (these are deliberately illustrative, not current-state claims).

### sandbox_test_commons
- `README.md` described ~14 classes that do not exist (`AbstractCleanUpTest`,
  `AbstractQuickFixTest`, `ASTTestHelper`, `ASTMatcher`, `ASTNodeFactory`,
  `WorkspaceHelper`, `ProjectHelper`, `CompilationUnitHelper`, `CleanUpAssertions`,
  `ASTAssertions`, `SourceAssertions`, `MockCompilationUnit`, `MockIFile`,
  `MockIProject`, `MockProgressMonitor`). The module actually provides the JUnit 5
  extension `AbstractEclipseJava` (package `org.sandbox.jdt.ui.tests.quickfix.rules`),
  version-specific subclasses `EclipseJava8/9/10/17/18/22`, and `TestOptions`.
  The README was rewritten to describe the real infrastructure (`ARCHITECTURE.md` was
  already accurate). **Fixed.**

### sandbox_extra_search
- `ARCHITECTURE.md` documented a non-existent `ExtraSearchView`
  (`org.sandbox.jdt.internal.ui.views.ExtraSearchView`). The plugin actually contributes
  two `ISearchPage` implementations — `UpdateNeededSearchPage` and
  `SemanticCodeSearchPage` (package `org.sandbox.jdt.internal.ui.search`) — plus a
  `gitindex` subsystem (`GitSearchView`, `JavaTypeHistoryView`, `CommitAnalyticsView`,
  `EmbeddedSearchService`, `IncrementalIndexer`, `RepositoryIndexService`,
  `EGitRepositoryTracker`). The section was rewritten to describe the real components.
  **Fixed.**

### sandbox_usage_view
- `ARCHITECTURE.md` documented a non-existent `NamingAnalyzer` class. The naming-conflict
  detection is actually performed by `VariableBindingVisitor`, `NamingConflictFilter`
  and `VariableNameSuggester`. The section was rewritten accordingly. **Fixed.**

### sandbox_jface_cleanup
- *Clarified:* `ARCHITECTURE.md` presented `JFaceCleanUp` as the main implementation;
  it is actually a thin wrapper (`AbstractCleanUpCoreWrapper<JFaceCleanUpCore>`) and the
  logic lives in `JFaceCleanUpCore`. (This mirrors the wrapper/core pattern used
  throughout the repository.)

### Root-level documentation
- `README.md` — the Usage View entry attributed `AstProcessorBuilder` to
  `sandbox_common`; the class is actually in `sandbox_common_core`
  (`…/internal/common/AstProcessorBuilder.java`). **Fixed.**
- `pom.xml` — the Java-version enforcer message stated "This project uses Tycho 5.0.1";
  the project uses `tycho-version = 5.0.2`. Message text corrected (no build-logic
  change). **Fixed.**
- `DOCUMENTATION_INVENTORY.md` — referenced a `DOCUMENTATION_VERIFICATION.md` that does
  not exist (row removed), and presented stale module counts; a note was added pointing
  to this audit and clarifying the counts. **Fixed.**

---

## Outstanding gaps & recommendations (not changed in this PR)

These require either new content or a code change and are recorded here for follow-up:

1. **Missing module READMEs.** The following modules ship no `README.md` and are only
   covered (if at all) by the root README: `sandbox_mining_cli`,
   `sandbox-jgit-storage-hibernate`, `sandbox-jgit-server-webapp`,
   `sandbox-maven-plugin`, `sandbox-ast-api-jdt`. Adding a short README to each would
   close the completeness gap.

2. **xml_cleanup — documented preference does not function (code/doc mismatch).**
   `ARCHITECTURE.md` states that indentation is *optional* and controlled by the
   `XML_CLEANUP_INDENT` preference (default `indent="no"`). In
   `SchemaTransformationUtils.transform(Path, boolean enableIndent)` the `enableIndent`
   parameter is accepted but never read — line 76 unconditionally sets
   `OutputKeys.INDENT = "yes"`. The preference is wired up
   (`XMLCleanUpCore` reads `XML_CLEANUP_INDENT`) but has no effect on the transform.
   Resolution should be a **code fix** (honour `enableIndent`) rather than rewording the
   docs, since the documented behaviour matches the plugin's stated purpose.

3. **Aspirational "future" features.** Some documents list planned capabilities that are
   not implemented (e.g. machine-learning pattern detection in
   `sandbox_method_reuse/README.md`; the conceptual similarity algorithms in
   `sandbox_usage_view`). `sandbox_int_to_enum` correctly and explicitly marks its
   transformation logic as a placeholder. Where such items are not clearly labelled as
   future/planned, they should be moved under an explicit "Planned / Not yet implemented"
   heading to avoid implying current functionality.

---

## Modules verified accurate (no changes needed)

`sandbox_encoding_quickfix`, `sandbox_junit_cleanup`, `sandbox_css_cleanup`,
`sandbox_use_general_type`, `sandbox_common`, `sandbox_common_core`,
`sandbox_triggerpattern`, `sandbox-ast-api`, `sandbox_coverage`, `sandbox_web`,
`sandbox-benchmarks`, `sandbox_mining_core` — documented classes, packages, features,
versions and links were checked and match the code.
