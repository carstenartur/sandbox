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
`CodeFormatingTabPage.java` (6 line deletions, all annotation-only changes).

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
