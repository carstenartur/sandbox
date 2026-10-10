from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
control = Path(__file__).resolve().parent

def replace(path, before, after):
    path = root / path
    text = path.read_text()
    if text.count(before) != 1:
        raise RuntimeError(f'Expected exactly one source anchor in {path}: {before!r}')
    path.write_text(text.replace(before, after))

base = 'sandbox_math_cleanup/src/org/sandbox/jdt/internal/'
math = base + 'corext/fix/math/'
options = math + 'MathCleanUpOptions.java'
replace(options, 'int targetJava, List<String> exclusions) {', 'int targetJava, List<String> exclusions, ExplanationMode explanations) {\n public enum ExplanationMode { NONE, NONTRIVIAL, ALL }\n public static final String EXPLANATIONS = "cleanup.mathematics.explanations";')
replace(options, 'CHECKED_OPT_IN,EXCLUSIONS,UNDERFLOW);', 'CHECKED_OPT_IN,EXCLUSIONS,UNDERFLOW,EXPLANATIONS);')
replace(options, ' public MathCleanUpOptions {', ''' /** Existing programmatic callers retain their explicitly comment-free output. */
 public MathCleanUpOptions(boolean enabled, Set<NumericKind> kinds, SafetyProfile safety,
   OptimizationGoal goal, long workBudget, int maxStates, boolean checkedOptIn,
   int targetJava, List<String> exclusions) {
  this(enabled,kinds,safety,goal,workBudget,maxStates,checkedOptIn,targetJava,exclusions,ExplanationMode.NONE);
 }
 public MathCleanUpOptions {''')
replace(options, '  Objects.requireNonNull(kinds,"kinds");', '  Objects.requireNonNull(explanations,"explanations");\n  Objects.requireNonNull(kinds,"kinds");')
replace(options, 'OptimizationGoal.LOWER_ESTIMATED_RUNTIME,100_000,2000,false,targetJava,List.of());', 'OptimizationGoal.LOWER_ESTIMATED_RUNTIME,100_000,2000,false,targetJava,List.of(),ExplanationMode.NONTRIVIAL);')
replace(options, 'paths.isEmpty()?List.of():Arrays.stream(paths.split(";",-1)).map(String::trim).toList());', 'paths.isEmpty()?List.of():Arrays.stream(paths.split(";",-1)).map(String::trim).toList(),\n    ExplanationMode.valueOf(values.getOrDefault(EXPLANATIONS,defaults.explanations.name())));')
replace(options, '  values.put(UNDERFLOW,"false");return Map.copyOf(values);', '  values.put(UNDERFLOW,"false");values.put(EXPLANATIONS,explanations.name());return Map.copyOf(values);')
shutil.copyfile(control / 'MathExplanation.java', root / math / 'MathExplanation.java')
analysis = math + 'MathematicalAnalysis.java'
replace(analysis, 'import de.regelsuche.sdk.optimization.ComputationOptimizer;', 'import de.regelsuche.sdk.optimization.ComputationOptimizer;\nimport de.regelsuche.sdk.optimization.ComputationExplanations;')
replace(analysis, '                  if (!(optimizer.reverify(request, candidate, cancellation) instanceof Verified)) {', '                  var explanation = ComputationExplanations.describe(request, candidate, cancellation);\n                  if (!(explanation.verification() instanceof Verified)) {')
replace(analysis, '                  JavaEmissionVerifier.verify(request, candidate, plain, region, options, cancellation);', '''                  JavaEmissionVerifier.verify(request, candidate, plain, region, options, cancellation);
                  MathExplanation explanationText = MathExplanation.render(explanation.explanation().orElseThrow(),
                     request, candidate, region, plain, options, cancellation);''')
replace(analysis, '                  checkGeneratedSource(\n                     ast,', '''                  if (options.explanations() == MathCleanUpOptions.ExplanationMode.ALL
                        || options.explanations() == MathCleanUpOptions.ExplanationMode.NONTRIVIAL && explanationText.nontrivial()) {
                     replacement = indentGenerated(explanationText.sourceComment(), source, region.start()) + replacement;
                  }
                  checkGeneratedSource(
                     ast,''')
replace(analysis, '                  replacements.add(new MathematicalAnalysis.Replacement(region.start(), region.length(), replacement, description));', '                  description += "\\n" + explanationText.detail();\n                  replacements.add(new MathematicalAnalysis.Replacement(region.start(), region.length(), replacement, description));')
ui = base + 'ui/preferences/cleanup/MathematicalCleanUpTabPage.java'
replace(ui, '\t\tNumberPreference budget= createNumberPref', '''        ComboPreference explanations= createComboPref(group, columns, "Source explanations:", MathCleanUpOptions.EXPLANATIONS, //$NON-NLS-1$
                Arrays.stream(MathCleanUpOptions.ExplanationMode.values()).map(Enum::name).toArray(String[]::new),
                new String[] { "No source comments", "Nontrivial changes", "All changes" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        wrappedLabel(columns, group, "Verified value plans remain in the detailed diagnostics and report even without source comments."); //$NON-NLS-1$
\t\tNumberPreference budget= createNumberPref''')
replace(ui, '\t\t\tsetEnabledIfChanged(goal, active);', '\t\t\tsetEnabledIfChanged(goal, active);\n            setEnabledIfChanged(explanations, active);')
initializer = 'sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/ui/preferences/cleanup/MathematicalOptionsInitializerTest.java'
replace(initializer, 'defaultsSupplyExactlyTheNineNormalizedDisabledOptions', 'defaultsSupplyExactlyTheTenNormalizedDisabledOptions')
replace(initializer, 'assertEquals(9,options.getKeys().size());', 'assertEquals(10,options.getKeys().size());\n  assertEquals("NONTRIVIAL",options.getValue(MathCleanUpOptions.EXPLANATIONS));')
newtest = 'sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/MathematicalExplanationTest.java'
replace(newtest, '        compile(generated);', '        try (var compiled = compile(generated)) { assertNotNull(compiled.loadClass("Calculation")); }')
workbench = 'sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/ui/fix/MathematicalWorkbenchSWTBotTest.java'
replace(workbench, '  assertEquals("Only BigInteger",profile.bot().comboBoxWithLabel("Numeric type profile:").getText());', '''  assertEquals("Only BigInteger",profile.bot().comboBoxWithLabel("Numeric type profile:").getText());
  assertEquals("Nontrivial changes",profile.bot().comboBoxWithLabel("Source explanations:").getText());
  profile.bot().comboBoxWithLabel("Source explanations:").setSelection("All changes");''')
replace(workbench, '  Map<String,String> saved=CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE);', '''  Map<String,String> saved=CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE);
  assertEquals("ALL",saved.get(MathCleanUpOptions.EXPLANATIONS));''')
doc = root / 'docs/qa/verified-mathematics-explanations.md'
doc.write_text('''# Verified mathematics explanations

This follow-up starts from main 403f0aefa10aba33821c997180ae02ece00950b5 after #1676
was merged. It must be reviewed directly against main, without a dependent PR stack.

Sandbox formats independently reverified SDK value plans, using the actual Java
input/output bindings and the existing typed Java emitter. No mathematical identity,
source-name selector, prepared target formula, or withdrawn constructor recognizer is
added. Original-value and replacement-value graphs are **not** a complete sequence of
search steps, nor do they replace the retained source evaluation trace.

`cleanup.mathematics.explanations` accepts `NONE`, `NONTRIVIAL`, or `ALL`. New/default
profiles choose NONTRIVIAL. Existing direct nine-argument Java options construction
retains comment-free output for source compatibility. NONTRIVIAL adds comments for
non-preserving profiles, explicit mathematical premises, multiple outputs, or at least
eight original estimated operation-work units. This is a presentation policy, not an
optimization rule or acceptance gate. Full explanations remain in diagnostics and the
JSON report regardless of the comment setting. The source-comment language is English,
matching the current plugin interface and upstream review text.

The Java UI exposes these modes as Source explanations. Existing comments and line
endings are retained. Source comments break raw Unicode escape sequences because Java
processes them before lexing comments. Presentation is bounded to 16,384 characters;
an overlarge or cancelled presentation is diagnosed, never silently presented as a
complete derivation. The independent numeric checker and Java emission verifier remain
mandatory, with explicit checked-contract/fallback warnings. Estimated operation work
is not a measured speedup or a constant-time qualification.

Red-before-implementation: run 38039248065 at test-only cebce220e376a402be1d4c0680f80c97ddadcced
ran 204 tests: all previous 196 passed; the eight new tests failed because the explanation
option was not implemented. This document does not claim the follow-up is already merged
or that its new tests/native UI/whole-repository checks have passed.
''')
helpfile = root / 'sandbox_math_cleanup_help/html/cleanup.html'
text = helpfile.read_text()
assert text.count('</body>') == 1
helpfile.write_text(text.replace('</body>', '''<h2 id="explanations">Explain a verified change</h2>
<p><strong>Source explanations</strong> selects no source comments, comments for nontrivial
changes (the new-profile default), or comments for every change. The description uses
actual Java names and the independently checked original/replacement value graphs.
It identifies premises and numerical policies, not a hand-written example template.
Detailed diagnostics and the JSON report retain these plans even when source comments
are disabled. Existing nine-argument programmatic option construction remains comment-free.</p>
<p>A value-graph comparison is not a complete step-by-step search derivation. Existing
source-trace and Java checks still govern evaluation order and exceptions. Checked
arithmetic is an explicit contract change; operation estimates are not timing measurements.
No source-comment setting re-enables constant-folding-only edits.</p>
</body>'''))
print('Staged general verified value-plan presentation; mathematical search and SDK bytes unchanged.')
