/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Unknown Java source supplies the problem; assertions never inject a target into the optimizer. */
class MathematicalSearchDerivationTest {
    @TempDir Path temporary;

    @Test void actualSelectedStepsReachGeneratedJavaAndCanBeUndone() throws Exception {
        qualify(source("return x*(a+b)-x*a;"), "ALL", true);
    }
    @Test void disabledSourceCommentsKeepTheCompletePathInDiagnostics() throws Exception {
        qualify(source("return x*(a+b)-x*a;"), "NONE", false);
    }
    @Test void sourceHelpersUseActualBindingsNotNamedMathematicalTasks() throws Exception {
        String source = """
                public class Calculation {
                    private static int blend(int p, int q) { return p+q; }
                    public static int compute(int distance, int scale, int bias) {
                        // Preserve this explanation of the input.
                        return distance*blend(scale,bias)-distance*scale;
                    }
                }
                """;
        String generated = qualify(source, "ALL", true);
        assertTrue(generated.contains("Preserve this explanation of the input."));
        assertTrue(generated.contains("distance"));
        assertTrue(generated.contains("bias"));
        assertFalse(generated.contains("RFC 9380"));
    }
    @Test void multiStatementCalculationsKeepEverySelectedStep() throws Exception {
        qualify(source("int u=x*(a+b)-x*a; int v=a*(x+b)-a*x; return u+v;"), "ALL", true);
    }
    private String qualify(String source, String mode, boolean comments) throws Exception {
        var properties = new HashMap<>(new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.READABILITY, 4_000_000, 20_000, false, 17, List.of()).toMap());
        properties.put(MathCleanUpOptions.EXPLANATIONS, mode);
        var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source,
                MathCleanUpOptions.parse(properties, 17), new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
        for (var replacement : result.replacements()) verifyPathNumbering(replacement.description());
        Document document = new Document(source);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertEquals(comments, generated.contains("// Search step "), generated);
        undo.apply(document); assertEquals(source, document.get());
        compare(source, generated);
        System.out.println("ACTUAL_JAVA_SEARCH_PATH=" + generated);
        return generated;
    }
    private static void verifyPathNumbering(String text) {
        assertTrue(text.contains("Recorded search path:"), text);
        var matcher = Pattern.compile("Search step ([0-9]+)/([0-9]+) \\[([^\\r\\n]*)\\]:").matcher(text);
        int found = 0, total = -1;
        while (matcher.find()) {
            assertEquals(++found, Integer.parseInt(matcher.group(1)), text);
            int declared = Integer.parseInt(matcher.group(2));
            if (total < 0) total = declared;
            assertEquals(total, declared, text);
            assertFalse(matcher.group(3).isBlank());
        }
        assertTrue(found > 0, text);
        assertEquals(total, found, "No selected step may be silently omitted: " + text);
        assertTrue(text.contains("compound proposal"), text);
    }
    private static String source(String body) {
        return "public class Calculation { public static int compute(int x,int a,int b) { " + body + " }}";
    }
    private void compare(String source, String generated) throws Exception {
        try (var original = compile(source); var replacement = compile(generated)) {
            var before = original.loadClass("Calculation").getMethod("compute", int.class, int.class, int.class);
            var after = replacement.loadClass("Calculation").getMethod("compute", int.class, int.class, int.class);
            int[] edges = {0,1,-1,17,Integer.MIN_VALUE,Integer.MAX_VALUE,1<<30};
            for (int x:edges) for (int a:edges) for (int b:edges)
                assertEquals(before.invoke(null,x,a,b), after.invoke(null,x,a,b));
        }
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",directory.toString(),file.toString()), source);
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},null);
    }
}
