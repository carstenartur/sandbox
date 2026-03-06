## Mining Examples

These examples show how commits should be evaluated and what good
DSL rules look like. Based on actual Eclipse Platform 2025 commits.

### Example 1: GREEN — String.replaceAll() → String.replace()

**Commit:** `7bb8891b` from eclipse.platform.ui
**Message:** "JDT Clean-up Use String.replace() instead of String.replaceAll() when possible"

**Analysis:** When `replaceAll()` is called with a literal (non-regex) pattern,
it should be replaced with `replace()` which avoids regex compilation overhead.

**Expected evaluation:**
- relevant: true
- trafficLight: GREEN
- reusability: 9
- codeImprovement: 6
- implementationEffort: 2
- category: "String API Modernization"

**DSL rule:**
```
// String.replaceAll with literal → String.replace
$str.replaceAll($literal, $replacement) :: sourceVersionGE(9) && isStringLiteral($literal) && !containsRegexChars($literal)
=> $str.replace($literal, $replacement)
;;
```

### Example 2: GREEN — Platform.run() → SafeRunner.run()

**Commit:** `b4b98c6e` from eclipse.platform.ui
**Message:** "Stop using deprecated Platform.run method — Use SafeRunner.run directly"

**Analysis:** `Platform.run(ISafeRunnable)` is deprecated in favor of `SafeRunner.run()`.
Direct call replacement with import change.

**Expected evaluation:**
- relevant: true
- trafficLight: GREEN
- reusability: 7
- codeImprovement: 5
- implementationEffort: 2
- category: "Eclipse API Migration"

**DSL rule:**
```
// Platform.run → SafeRunner.run
org.eclipse.core.runtime.Platform.run($runnable) :: sourceVersionGE(11)
=> org.eclipse.core.runtime.SafeRunner.run($runnable)
;;
```

### Example 3: YELLOW — Performance: Cache method calls in loops

**Commit:** `343fd57b` from eclipse.platform.ui
**Message:** "Performance: Cache method calls in loops and optimize string concatenation"

**Analysis:** Caching `list.size()` or `list.getChildren()` in loop variables is a
performance optimization. The transformation depends on flow analysis to prove the
list is not modified inside the loop — beyond current DSL capability.

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW
- reusability: 8
- codeImprovement: 4
- implementationEffort: 7
- category: "Performance Optimization"
- languageChangeNeeded: "Requires loop body analysis to verify collection is not modified during iteration"

### Example 4: YELLOW — JUnit 4 → JUnit 5 Migration

**Commit:** `0e238b7c` from eclipse.platform.ui
**Message:** "Migrate org.eclipse.ui.tests.navigator from JUnit 4 to JUnit 5"

**Analysis:** Large-scale JUnit migration involves annotations (@Test, @Before/@BeforeEach),
assertion methods, runner/extension replacement. Some patterns are expressible in DSL
but the full migration requires cross-method analysis.

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW
- reusability: 9
- codeImprovement: 7
- implementationEffort: 6
- category: "JUnit Migration"
- languageChangeNeeded: "Full JUnit migration needs lifecycle annotation replacement plus cross-method analysis for setUp/tearDown"

### Example 5: RED — Refactor CleanUp to jdt.core.manipulation

**Commit:** `c60b1056` from eclipse.jdt.ui
**Message:** "Refactor StringCleanUp to jdt.core.manipulation"

**Analysis:** Moving classes between modules is an architectural refactoring that cannot
be expressed as a pattern-based DSL rule. It involves package moves, import updates,
extension point changes, and build configuration updates.

**Expected evaluation:**
- relevant: true
- trafficLight: RED
- reusability: 3
- codeImprovement: 6
- implementationEffort: 9
- category: "Architecture Migration"
- languageChangeNeeded: "Requires cross-module refactoring with extension point and build system changes"

### Example 6: NOT_APPLICABLE — Version bumps

**Commit:** `5fe841fb` from eclipse.platform.ui
**Message:** "Version bump(s) for 4.39 stream"

**Expected evaluation:**
- relevant: false
- irrelevantReason: "Version bump only, no code transformation pattern"
- trafficLight: NOT_APPLICABLE

### Example 7: NOT_APPLICABLE — Fix race condition

**Commit:** `a3fa0658` from eclipse.platform.ui
**Message:** "Fix flaky PartRenderingEngineTests.ensureCleanUpAddonCleansUp race condition"

**Expected evaluation:**
- relevant: false
- irrelevantReason: "Bug fix for race condition — no reusable transformation pattern"
- trafficLight: NOT_APPLICABLE

### Example 8: YELLOW — Remove unnecessary @SuppressWarnings("deprecation")

**Commit:** `741d08e0` from eclipse.jdt.ui
**Message:** "Removed unnecessary @SuppressWarnings(\"deprecation\") from jdt.ui"

**Actual diff:** Removed `@SuppressWarnings("deprecation")` annotations from 3 JDT UI
preference cleanup classes: `CleanUpConfigurationBlock.java`, `CleanUpTabPage.java`,
`CodeFormatingTabPage.java` (sic — historical Eclipse class name; 6 line deletions, all annotation-only changes).

**Analysis:** Determining whether a `@SuppressWarnings("deprecation")` is unnecessary
requires type-resolution analysis — the tool must check whether the annotated code
actually references deprecated APIs. This is beyond current DSL pattern-matching capability.

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW (NOT GREEN — no DSL rule possible without type resolution)
- reusability: 7
- codeImprovement: 4
- implementationEffort: 3
- category: "Unnecessary Annotation Removal"
- languageChangeNeeded: "Requires type-resolution analysis to determine which @SuppressWarnings annotations no longer suppress actual deprecation warnings"

**Why not GREEN:** A GREEN evaluation requires `canImplementInCurrentDsl: true` and a
valid `dslRule`. This pattern needs semantic analysis (is the referenced API still
deprecated?) that the DSL cannot express.

### Example 9: YELLOW — Remove unused NLS entry

**Commit:** `6c82a838` from eclipse.platform.ui
**Message:** "Remove unused NLS entry"

**Actual diff:** Removed unused NLS constant from `WorkbenchMessages.java` and its
corresponding entry in `messages.properties` (3 line deletions total across 2 files).

**Analysis:** Detecting unused NLS entries requires cross-file reference analysis to
verify no code references the constant. The DSL operates on single-file patterns
and cannot determine field usage across compilation units.

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW (NOT GREEN — requires cross-file reference analysis)
- reusability: 6
- codeImprovement: 3
- implementationEffort: 2
- category: "Dead Code Removal"
- languageChangeNeeded: "Requires cross-file reference analysis to identify unreferenced NLS message constants"

### Example 10: YELLOW — Remove unused JUnit imports and fields

**Commit:** `191a1c77` from eclipse.platform.ui
**Message:** "Remove unused JUnit Rule and TestName imports"

**Actual diff:** Removed unused JUnit 4 `TestName` rule imports and `@Rule` field
declarations from 3 test classes: `PartRenderingEngineTests.java`,
`TestUnitRegistrationWindows.java`, `TextEditorPluginTest.java`
(16 line deletions). Also removed debug `println` statements.

**Analysis:** While "remove unused imports" is a well-known cleanup, detecting
which imports and fields are unreferenced requires usage analysis across the
compilation unit. Eclipse JDT has built-in cleanups for this, but the DSL
cannot express reference-counting logic.

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW (NOT GREEN — requires usage analysis)
- reusability: 8
- codeImprovement: 3
- implementationEffort: 1
- category: "Unused Import Removal"
- languageChangeNeeded: "Requires usage analysis to determine which imports and field declarations are unreferenced"

### Example 11: RED — Fix existing cleanup bug (NLS markers)

**Commit:** `3702d32e` from eclipse.jdt.ui
**Message:** "Fix Unnecessary Array clean-up to not remove NLS markers"

**Actual diff:** Fixed the JDT "Unnecessary Array Creation" cleanup to preserve `//$NON-NLS-n$`
markers when removing redundant array creation in varargs calls. The bug caused NLS markers
to be lost, breaking internationalization.

**Analysis:** This is a bug fix for an existing JDT cleanup, not a new transformation pattern.
It requires understanding of NLS marker semantics and array creation context — it's fixing
incorrect behavior in existing infrastructure, not discovering a new pattern.

**Expected evaluation:**
- relevant: true
- trafficLight: RED (NOT GREEN/YELLOW — this is a bug fix, not a new pattern)
- reusability: 2
- codeImprovement: 7
- implementationEffort: 8
- category: "Clean-up Bug Fix"
- languageChangeNeeded: "Bug fix for existing JDT cleanup — requires understanding of NLS marker renumbering and array creation context"

**Why RED (not YELLOW or GREEN):** Bug fixes to existing cleanup implementations are RED
because they fix incorrect behavior rather than define new transformations. The DSL cannot
express "fix the implementation of cleanup X" — that requires changes to the cleanup's Java code.

### Example 12: NOT_APPLICABLE — Apply existing Eclipse JDT cleanups

**Commit:** `c9f1e026` from eclipse.platform.ui
**Message:** "Perform clean code of org.eclipse.ui.workbench"

**Analysis:** Commits with messages like "Perform clean code" or "Apply code cleanup" typically
apply existing Eclipse JDT cleanups (add `final`, remove unnecessary casts, organize imports, etc.).
These are NOT new patterns — they use already-built-in Eclipse cleanup actions.

**Expected evaluation:**
- relevant: false
- irrelevantReason: "Applies multiple built-in Eclipse JDT cleanups — these are already implemented as JDT cleanups, no new pattern needed"
- trafficLight: NOT_APPLICABLE

### Example 13: NOT_APPLICABLE — Mark deprecated API for removal

**Commit:** `a629637e` from eclipse.platform.ui
**Message:** "Marks several unused deprecated methods/constants for removal"

**Analysis:** Adding `@Deprecated(forRemoval=true)` annotations to already-deprecated methods
is API lifecycle management. It does NOT change code behavior and is NOT a transformation pattern.
**Do NOT confuse this with "removing deprecated usage"** (which IS a transformation pattern).

**Expected evaluation:**
- relevant: false
- irrelevantReason: "Adding @Deprecated(forRemoval=true) annotations — API lifecycle management, not a code transformation pattern"
- trafficLight: NOT_APPLICABLE

### Example 14: YELLOW — URL deprecation (Java 20+)

**Commit:** `6361505f` from eclipse.platform.ui
**Message:** "Fix URL deprecation in tests and snippets"

**Analysis:** Replacing `new URL(String)` (deprecated in Java 20) with URI-based alternatives.
The replacement changes exception semantics: `URL(String)` throws `MalformedURLException` (checked),
while `URI.create(String)` throws `IllegalArgumentException` (unchecked). A simple DSL rule cannot
safely capture this distinction.

**Expected evaluation:**
- relevant: true
- trafficLight: YELLOW (NOT GREEN — exception semantics change)
- reusability: 8
- codeImprovement: 5
- implementationEffort: 4
- category: "Java API Deprecation"
- languageChangeNeeded: "Requires understanding of checked vs unchecked exception semantics"

### Common Mistakes to Avoid

1. **Do NOT use XML tags** in dslRule:
   - Wrong: `<trigger>method_invocation(...)</trigger>`
   - Right: `trigger: method_invocation(...)`

2. **Do NOT use isType()**:
   - Wrong: `guard: isType($var, "String")`
   - Right: `guard: instanceof($var, "String")`

3. **Do NOT mark version bumps, CI changes, or documentation as relevant**

4. **Do NOT propose rules that only apply to a single specific codebase**

5. **Do NOT mark architectural refactoring (moving classes between modules) as GREEN** —
   these are RED because they cannot be expressed as pattern-based rules

6. **Do NOT confuse "adding @Deprecated" with "removing deprecated usage"** —
   adding annotations to newly-deprecated methods is NOT a refactoring pattern

7. **Do NOT mark commits as GREEN when `canImplementInCurrentDsl` is false** —
   GREEN means a valid DSL rule exists. If the pattern requires semantic analysis
   (type resolution, cross-file references, usage counting), it must be YELLOW.
   GREEN evaluations MUST have `dslRule` set and `dslValidationResult == "VALID"`.

8. **Do NOT mark bug fixes to existing cleanups as GREEN or YELLOW** —
   commits that fix bugs in JDT cleanup implementations (NLS marker handling,
   lambda edge cases, generic type issues) are RED. They improve existing
   infrastructure rather than defining new transformation patterns.

9. **Do NOT mark "Perform clean code" commits as relevant** —
   commits that apply existing built-in Eclipse JDT cleanups (add final, organize
   imports, remove unnecessary casts) are NOT_APPLICABLE. No new pattern is needed.

10. **Do NOT mark API removal (no replacement) as relevant** —
    when an entire API is removed (e.g., `IPageLayout.addFastView`, keybinding API)
    with no replacement, this is NOT_APPLICABLE. It's deletion, not transformation.

11. **Do NOT mark concurrency refactoring as GREEN or YELLOW** —
    replacing one synchronization mechanism with another (e.g., Phaser → CountDownLatch)
    requires understanding of the concurrency model and cannot be expressed as a
    pattern-based rule. Mark as NOT_APPLICABLE or RED.

12. **Do NOT confuse "cleanup bug fix" with "cleanup enhancement"** —
    if a commit message says "Fix cleanup to ..." and changes the implementation of
    an existing JDT cleanup (fixing edge cases, preserving comments/markers), this is
    RED. Enhancements that add new functionality (e.g., "Add cleanup to use module imports")
    may be YELLOW or GREEN depending on pattern expressibility.

### Example 15: NOT_APPLICABLE — Concurrency refactoring

**Commit:** `4391448b` from eclipse.platform.ui
**Message:** "Replace Phaser with CountDownLatch for reliable synchronization"

**Analysis:** Replacing a concurrency mechanism with another is a design decision that depends
on the specific synchronization requirements. This is not a reusable pattern — each case
requires understanding of what is being synchronized and why.

**Expected evaluation:**
- relevant: false
- irrelevantReason: "Concurrency mechanism replacement — requires understanding of synchronization model, not a reusable transformation"
- trafficLight: NOT_APPLICABLE

### Example 16: RED — Fix cleanup to handle edge case

**Commit:** `ce67be20` from eclipse.jdt.ui
**Message:** "Fix cleanup to replace java.specification.version property"

**Analysis:** This commit fixes the ConstantsForSystemProperties cleanup to correctly
handle `java.specification.version` by adding a Java 10 version check and converting
the result via `String.valueOf()`. This is fixing existing cleanup logic, not defining
a new pattern.

**Expected evaluation:**
- relevant: true
- trafficLight: RED (NOT GREEN — this is fixing a bug in existing cleanup infrastructure)
- reusability: 3
- codeImprovement: 5
- implementationEffort: 6
- category: "Clean-up Bug Fix"
- languageChangeNeeded: "Requires modifying existing cleanup Java code to add version-dependent behavior"

### Example 17: RED — Fix quick-fix to check for diamond context

**Commit:** `90ff95a0` from eclipse.jdt.ui
**Message:** "Do not offer to add type arguments where diamond operator would do"

**Analysis:** This commit restricts when Eclipse offers to add explicit type arguments — only
in positions where the diamond operator would not suffice (assignments, variable declarations).
This is a correction to quick-fix behavior, requiring AST context analysis.

**Expected evaluation:**
- relevant: true
- trafficLight: RED (NOT YELLOW — this is fixing quick-fix logic, not a pattern)
- reusability: 2
- codeImprovement: 6
- implementationEffort: 7
- category: "Clean-up Bug Fix"

### Example 18: RED — Fix deprecation highlighting for records

**Commit:** `f9770112` from eclipse.jdt.ui
**Message:** "Highlight deprecation of canonical record constructor"

**Analysis:** A single-line change to fix how semantic highlighting handles the canonical
constructor of a deprecated record type. This is a very narrow bug fix in the JDT
semantic highlighting engine.

**Expected evaluation:**
- relevant: true
- trafficLight: RED (single-line bug fix in existing infrastructure)
- reusability: 1
- codeImprovement: 4
- implementationEffort: 2
- category: "Deprecation Handling"
