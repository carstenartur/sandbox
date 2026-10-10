from pathlib import Path
import sys

root = Path(sys.argv[1])
math = root / 'sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math'

def replace(path, old, new, count=1):
    text = path.read_text()
    assert text.count(old) == count, (path, old, text.count(old))
    path.write_text(text.replace(old, new))

replace(math / 'MathExplanation.java', 'import java.util.List;\n',
        'import java.util.List;\nimport org.eclipse.core.runtime.OperationCanceledException;\n')
replace(math / 'MathExplanation.java', 'throw new IllegalArgumentException("EXPLANATION_CANCELLED");',
        'throw new OperationCanceledException("EXPLANATION_CANCELLED");')
path = math / 'MathematicalAnalysis.java'
text = path.read_text()
assert text.count('monitor.isCanceled()') >= 5
text = text.replace('monitor.isCanceled()', 'isCancelled(monitor)')
text = text.replace('CancellationToken cancellation = monitor::isCanceled;',
                    'CancellationToken cancellation = () -> isCancelled(monitor);')
assert 'monitor::isCanceled' not in text
text = text.replace('import org.eclipse.core.runtime.NullProgressMonitor;\n',
                    'import org.eclipse.core.runtime.NullProgressMonitor;\nimport org.eclipse.core.runtime.OperationCanceledException;\n')
anchor = '   private static String rewriteValues('
assert text.count(anchor) == 1
text = text.replace(anchor, '''   /** Interrupts abort the entire invocation, never just the current candidate. */
   private static boolean isCancelled(IProgressMonitor monitor) {
      if (Thread.currentThread().isInterrupted()) {
         throw new OperationCanceledException("Mathematics analysis interrupted");
      }
      return monitor.isCanceled();
   }

''' + anchor)
path.write_text(text)

path = root / 'sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views/MathematicsHelpScreenshots.java'
replace(path, 'import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;\n',
        'import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;\nimport org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;\n')
replace(path, 'Map<String, String> values= new TreeMap<>(); values.put(PREFIX, "true");', '''Map<String, String> values= new TreeMap<>();
        JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS)
                .getMap().forEach((key, value) -> {
                    if (key.equals(PREFIX) || key.startsWith(PREFIX + ".")) values.put(key, value); //$NON-NLS-1$
                });
        values.put(PREFIX, "true");''')
replace(path, 'values.put(PREFIX + ".underflowChecks", "false"); return values;', '''values.put(PREFIX + ".underflowChecks", "false");
        values.put(PREFIX + ".explanations", properties.getProperty("explanations", values.get(PREFIX + ".explanations"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return values;''')
replace(path, 'IJavaProject project= createProject(resource); Map<String, String> profile= profile(properties); persist(project, profile);', '''IJavaProject project= createProject(resource); Map<String, String> profile= profile(properties); persist(project, profile);
            Map<String, String> effective= new TreeMap<>();
            CleanUpPreferenceUtil.loadOptions(new ProjectScope(project.getProject())).forEach((key, value) -> {
                if (key.equals(PREFIX) || key.startsWith(PREFIX + ".")) effective.put(key, value); //$NON-NLS-1$
            });
            assertEquals(effective, profile, "Provenance must contain every effective mathematics option"); //$NON-NLS-1$''')
replace(path, 'private static Map<String, String> profile(Properties properties)',
        'static Map<String, String> profile(Properties properties)')

path = root / 'sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views/MathematicsHelpScreenshotsSWTBotTest.java'
text = path.read_text()
start = text.index('    @SuppressWarnings("unchecked")')
text = text[:start] + '}\n'
text = text.replace('profile(properties)', 'MathematicsHelpScreenshots.profile(properties)')
path.write_text(text)

path = root / 'sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/MathematicalExplanationTest.java'
replace(path, '    private static String source(String expression) {', '''    @Test void nontrivialModeActuallyControlsTheGeneratedSourceAtItsBoundary() throws Exception {
        for (int work : new int[] {7, 8}) {
            String source = source("x" + " + 0".repeat(work));
            var result = analyze(source, "NONTRIVIAL", SafetyProfile.PRESERVE_JAVA);
            assertTrue(result.changed(), result.diagnostics().toString());
            assertEquals(work, result.evidence().getFirst().cost().sourceCost().operationWork());
            String generated = applyAndUndo(source, result);
            assertEquals(work >= 8, generated.contains("// Verified mathematics"), generated);
            assertTrue(result.replacements().getFirst().description().contains("Original values:"));
            compare(source, generated);
        }
    }

    private static String source(String expression) {''')

path = root / 'sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/corext/fix/math/MathExplanationReviewTest.java'
replace(path, 'import de.regelsuche.sdk.optimization.*;', '''import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationExplanations;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SemanticAssumption;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.sdk.optimization.VerificationResult;''')

print('Staged PR 1689 cancellation/profile fixes and policy regression coverage; mathematical rules and SDK unchanged.')
