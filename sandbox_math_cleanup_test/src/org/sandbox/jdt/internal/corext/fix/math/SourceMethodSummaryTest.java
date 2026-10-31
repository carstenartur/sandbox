/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import de.regelsuche.sdk.optimization.*;
import java.nio.file.*;
import java.util.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceMethodSummaryTest {
    @TempDir Path directory;
    private static final String SOURCE = "public class Calculation { public static int initialized; public static boolean fail; "
            + "public static int run(int x,int y,int[] z) { int a=Helper.add(x,y); z[0]=a; return a-x; } }";
    private static final String HELPER = "public class Helper { static { Calculation.initialized++; if(Calculation.fail) throw new IllegalStateException(); } "
            + "public static int add(int x,int y) { return x+y; } }";
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private MathematicalAnalysis.Analysis analyze(String source) throws Exception {
        Files.writeString(directory.resolve("Calculation.java"), source);
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray());
        parser.setUnitName(directory.resolve("Calculation.java").toString()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[0], new String[]{directory.toString()}, new String[]{"UTF-8"}, true);
        var compiler = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", compiler); parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit)parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return MathematicalAnalysis.analyze(ast, source, options(), null, -1, 0,
                new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(), List.of(), List.of(directory.toString()), List.of("UTF-8")));
    }
    @Test void sourceSummaryAcrossFilesRetainsInitializationAndCall() throws Exception {
        Files.writeString(directory.resolve("Helper.java"), HELPER);
        var result = analyze(SOURCE);
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("VERIFIED_SOURCE_TASK")), result.diagnostics().toString());
        var document = new Document(SOURCE); result.newEdit().apply(document);
        assertTrue(document.get().contains("int a=Helper.add(x,y); z[0]=a; return y;"), document.get());
        assertEquals(1, result.sourceDependencies().receipts().size());
        execute(SOURCE, "original", false); execute(document.get(), "generated", false);
        execute(SOURCE, "original-failure", true); execute(document.get(), "generated-failure", true);
        Files.writeString(directory.resolve("Helper.java"), HELPER.replace("x+y", "x-y"));
        assertFalse(result.matches(SOURCE, Map.of()));
        assertThrows(IllegalArgumentException.class, result::newEdit);
    }
    @Test void sameClassNonprivateHelperIsSummarizedWithoutRemovingItsCall() throws Exception {
        String source = SOURCE.replace("Helper.add", "add").replace("public static int run", "static int add(int x,int y){return x+y;} public static int run");
        var result = analyze(source); var document = new Document(source); result.newEdit().apply(document);
        assertTrue(document.get().contains("int a=add(x,y); z[0]=a; return y;"), document.get());
    }
    @Test void mutableFieldsAndVirtualMethodsAreNotPureSummaries() throws Exception {
        for (String helper : List.of(HELPER.replace("return x+y;", "return x+y+Calculation.initialized;"),
                HELPER.replace("static int add", "int add"))) {
            Files.writeString(directory.resolve("Helper.java"), helper);
            String source = helper.contains("static int add") ? SOURCE : SOURCE.replace("Helper.add", "new Helper().add");
            var result = analyze(source); var document = new Document(source); result.newEdit().apply(document);
            assertTrue(document.get().contains("return a-x;"), document.get());
        }
    }
    @Test void reextractionCannotAcquireANewFullFileSourceAllowance() throws Exception {
        Files.writeString(directory.resolve("Helper.java"), HELPER + "/*" + " ".repeat(64_000) + "*/");
        Files.writeString(directory.resolve("Calculation.java"), SOURCE);
        var environment = new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(), List.of(), List.of(directory.toString()), List.of("UTF-8"));
        var ast = MathematicalAnalysis.checkGeneratedSource(AST.newAST(AST.getJLSLatest(), false).newCompilationUnit(), SOURCE,
                Map.of(), 17, new org.eclipse.core.runtime.NullProgressMonitor(), environment);
        try (var context = JavaSourceContext.open(ast, SOURCE, environment, null, options(), 100)) {
            var tasks = new JavaComputationExtractor().extractTasks(ast, SOURCE, options());
            assertTrue(context.takeWork() <= 100);
            assertTrue(tasks.tasks().stream().noneMatch(t -> t.start() == SOURCE.indexOf("int a=Helper.add")));
        }
    }
    private void execute(String source, String name, boolean fail) throws Exception {
        Path output = Files.createDirectories(directory.resolve(name));
        Path file = output.resolve("Calculation.java"); Files.writeString(file, source);
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", output.toString(), file.toString(), directory.resolve("Helper.java").toString()));
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var type = loader.loadClass("Calculation"); type.getField("fail").set(null, fail);
            var method = type.getMethod("run", int.class, int.class, int[].class); int[] array = {17};
            if (fail) {
                var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(null, 1, 2, array));
                assertInstanceOf(ExceptionInInitializerError.class, error.getCause()); assertEquals(17, array[0]);
            } else {
                for (int x : new int[]{0, -1, Integer.MAX_VALUE}) for (int y : new int[]{0, 1, Integer.MIN_VALUE}) {
                    assertEquals(y, method.invoke(null, x, y, array)); assertEquals(x+y, array[0]);
                }
            }
            assertEquals(1, type.getField("initialized").get(null));
        }
    }
}
