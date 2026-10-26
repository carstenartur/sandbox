/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import de.regelsuche.sdk.optimization.*;

class JavaArrayStoreTest {
    @TempDir Path temporary;
    private static final String SOURCE = """
        public class Sample {
          public static long f(int[] a, int[] b, int i, long carry, int u, int v, int w) {
            a[i] = (u & v) | (~u & w);
            long sum = (a[i] & 0xffffffffL) + (b[i] & 0xffffffffL) + carry;
            b[i] = (int)((((((sum + 1L) - 1L) + 2L) - 2L) + 3L) - 3L);
            return sum >>> 32;
          }
        }
        """;

    @Test void orderedStoresPreserveAliasingCarriesExceptionsAndUndo() throws Exception {
        var analysis = analyze(SOURCE);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().stream().anyMatch(e -> e.original().contains("a[i] =")), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().stream().anyMatch(e -> e.original().contains("b[i] =")
                && e.original().contains("long sum =")), analysis.diagnostics().toString());
        var document = new Document(SOURCE); var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(SOURCE, document.get());
        try (var original = compile(SOURCE, "original"); var optimized = compile(generated, "optimized")) {
            var parameters = new Class<?>[] {int[].class, int[].class, int.class, long.class, int.class, int.class, int.class};
            var before = original.loadClass("Sample").getMethod("f", parameters);
            var after = optimized.loadClass("Sample").getMethod("f", parameters);
            var random = new Random(917322);
            for (int n = 0; n < 150; n++) {
                int[] a = n % 11 == 0 ? null : new int[] {random.nextInt(), random.nextInt()};
                int[] b = n % 13 == 0 ? null : n % 2 == 0 ? a : new int[] {random.nextInt(), random.nextInt()};
                int i = n % 4 - 1, u = random.nextInt(), v = random.nextInt(), w = random.nextInt();
                long carry = n % 3 == 0 ? -1L : random.nextLong();
                assertEquals(outcome(before, a, b, i, carry, u, v, w), outcome(after, a, b, i, carry, u, v, w));
            }
        }
    }
    @Test void unsafeStoreShapesRemainBoundaries() {
        for (String target : List.of("a[i++]", "get()[i]", "a[i / divisor]")) {
            String source = "class Sample { int[] get(){return null;} void f(int[] a,int i,int divisor,int u,int v,int w){ "
                    + target + " = (u & v) | (~u & w); } }";
            assertFalse(analyze(source).changed(), target);
        }
        assertFalse(analyze("class Sample { void f(int[] a,int i,int u,int v,int w){ a[i] += (u & v) | (~u & w); } }").changed());
    }
    @Test void storesTerminateRegionsEvenForEqualOrDifferentSourceNames() {
        var extraction = new JavaComputationExtractor().extractRuntime(parse(SOURCE), SOURCE, options());
        int write = SOURCE.indexOf("a[i] ="), end = SOURCE.indexOf(';', write) + 1;
        assertTrue(extraction.regions().stream().anyMatch(r -> r.start() <= write && r.start() + r.length() == end));
        assertTrue(extraction.regions().stream().noneMatch(r -> r.start() <= write && r.start() + r.length() > end));
    }
    private static String outcome(java.lang.reflect.Method method, int[] a, int[] b, int i, long c, int u, int v, int w) throws Exception {
        int[] aa = a == null ? null : a.clone(), bb = b == a ? aa : b == null ? null : b.clone(); Object result;
        try { result = method.invoke(null, aa, bb, i, c, u, v, w); }
        catch (InvocationTargetException e) { result = e.getCause().getClass().getName(); }
        return result + ":" + Arrays.toString(aa) + ":" + Arrays.toString(bb);
    }
    private URLClassLoader compile(String source, String name) throws Exception {
        Path dir = Files.createDirectory(temporary.resolve(name)), file = dir.resolve("Sample.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", dir.toString(), file.toString()));
        return new URLClassLoader(new java.net.URL[] {dir.toUri().toURL()}, getClass().getClassLoader());
    }
    static MathematicalAnalysis.Analysis analyze(String source) {
        return MathematicalAnalysis.analyze(parse(source), source, options(), new NullProgressMonitor(), -1, 0);
    }
    static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", options); parser.setCompilerOptions(options);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString()); return ast;
    }
}
