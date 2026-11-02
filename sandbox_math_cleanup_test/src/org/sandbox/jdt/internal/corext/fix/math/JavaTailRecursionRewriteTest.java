/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaTailRecursionRewriteTest {
    @TempDir Path temporary;
    private static final long BUDGET = 200_000;
    private static String source(String method) { return "public class Sample { " + method + " }"; }
    private static String sum() { return source("public static long step(final int n, final long a) { if (n <= 0) return a; return step(n - 1, a + n); }"); }
    private static JavaTailRecursionRewrite.Result prepare(String source) {
        return JavaTailRecursionRewrite.prepare("Sample.java", source, source.indexOf("public static"),
                JavaTailRecursionRewrite.StackPolicy.ALLOW_FRAME_ELISION, BUDGET, () -> false);
    }

    @Test void strictPolicyCannotAuthorizeAChangedStack() {
        var result = JavaTailRecursionRewrite.prepare("Sample.java", sum(), sum().indexOf("public static"),
                JavaTailRecursionRewrite.StackPolicy.PRESERVE_STACK, BUDGET, () -> false);
        assertTrue(result.plan().isEmpty());
        assertEquals("STACK_CHANGE_NOT_ACCEPTED", result.reason());
    }

    @Test void nativeBridgeRetainsAuthoredDocumentationAndBothLoopFormsExecuteEqually() throws Exception {
        String before=sum().replace("public static", "/** Authored sum. @param n count */ public static");
        var ast=bound(before);int offset=((TypeDeclaration)ast.types().getFirst()).getMethods()[0].getStartPosition();
        for(String goal:List.of("READABILITY","LOWER_ESTIMATED_RUNTIME")) {
            var values=nativeOptions();values.put(MathCleanUpOptions.GOAL,goal);
            var result=JavaTailRecursionRewrite.prepare(ast,before,offset,MathCleanUpOptions.parse(values,17),new NullProgressMonitor());
            var plan=result.plan().orElseThrow(()->new AssertionError(result.reason()));
            assertTrue(plan.preview().contains("Authored sum."));assertTrue(plan.preview().contains("ALLOW_FRAME_ELISION"));
            assertTrue(plan.preview().contains(goal.equals("READABILITY")?"while":"for ("));
            assertInstanceOf(Block.class,NodeFinder.perform(bound(plan.preview()),plan.bodyOffset(),plan.bodyLength()));
            compare(before,plan.preview(),new Class<?>[]{int.class,long.class},
                List.of(new Object[]{-1,Long.MIN_VALUE},new Object[]{0,7L},new Object[]{30,Long.MAX_VALUE}));
        }
    }

    @Test void nativeBridgeRejectsStaleSourceMissingConsentKindsAndExhaustedWork() {
        String before=sum();var ast=bound(before);int offset=before.indexOf("public static");var values=nativeOptions();
        var stale=JavaTailRecursionRewrite.prepare(ast,before.replace("a + n","a - n"),offset,MathCleanUpOptions.parse(values,17),new NullProgressMonitor());
        assertTrue(stale.plan().isEmpty());assertEquals("STALE_RECURSION_SOURCE",stale.reason());
        for(String key:List.of(MathCleanUpOptions.TAIL_RECURSION,MathCleanUpOptions.CLEANUP,MathCleanUpOptions.KINDS,MathCleanUpOptions.WORK_BUDGET)) {
            var changed=new HashMap<>(values);changed.put(key,key.equals(MathCleanUpOptions.KINDS)?"INT":key.equals(MathCleanUpOptions.WORK_BUDGET)?"1":"false");
            assertTrue(JavaTailRecursionRewrite.prepare(ast,before,offset,MathCleanUpOptions.parse(changed,17),new NullProgressMonitor()).plan().isEmpty(),key);
        }
        var cancelled=new NullProgressMonitor();cancelled.setCanceled(true);
        assertEquals("CANCELLED",JavaTailRecursionRewrite.prepare(ast,before,offset,MathCleanUpOptions.parse(values,17),cancelled).reason());
    }
    private static HashMap<String,String> nativeOptions() {
        var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());values.put(MathCleanUpOptions.CLEANUP,"true");
        values.put(MathCleanUpOptions.TAIL_RECURSION,"true");values.put(MathCleanUpOptions.KINDS,"INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET,Long.toString(BUDGET));return values;
    }
    private static CompilationUnit bound(String source) {
        var parser=ASTParser.newParser(AST.getJLSLatest());var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);
        parser.setCompilerOptions(compiler);parser.setSource(source.toCharArray());parser.setUnitName("Sample.java");
        parser.setEnvironment(null,null,null,true);parser.setResolveBindings(true);var ast=(CompilationUnit)parser.createAST(null);
        for(var problem:ast.getProblems()) assertFalse(problem.isError(),problem.toString());return ast;
    }

    @Test void finalParametersRemainFinalAndActualGeneratedCodeMatches() throws Exception {
        String before = sum();
        var plan = prepare(before).plan().orElseThrow();
        assertTrue(plan.preview().contains("final int n, final long a"));
        assertTrue(plan.preview().contains("while"));
        compare(before, plan.preview(), new Class<?>[]{int.class, long.class},
                List.of(new Object[]{-4, 7L}, new Object[]{0, Long.MIN_VALUE}, new Object[]{30, Long.MAX_VALUE}, new Object[]{100, -1L}));
    }

    @Test void swappedStatesUseOldValuesAndCounterNeedNotBeFirst() throws Exception {
        String before = source("public static long step(long left, int remaining, long right) { if (remaining <= 0) return left * 31 + right; return step(right, remaining - 1, left + right); }");
        var plan = prepare(before).plan().orElseThrow();
        compare(before, plan.preview(), new Class<?>[]{long.class, int.class, long.class},
                List.of(new Object[]{2L, 1, 3L}, new Object[]{5L, 18, -7L}, new Object[]{Long.MAX_VALUE, 25, Long.MIN_VALUE}));
    }

    @Test void intOverflowOccursBeforeWideningIntoLongParameter() throws Exception {
        String before = source("public static long step(int n, int a, long value) { if (n <= 0) return value; return step(n - 1, a + 1, a + 1); }");
        var plan = prepare(before).plan().orElseThrow();
        compare(before, plan.preview(), new Class<?>[]{int.class, int.class, long.class},
                List.of(new Object[]{1, Integer.MAX_VALUE, 0L}, new Object[]{8, Integer.MAX_VALUE - 3, 4L}));
    }

    @Test void shiftedAndBitwiseArgumentsRetainJavaWidthRules() throws Exception {
        String before = source("public static int step(int n, int bits) { if (n <= 0) return bits; return step(n - 1, (bits << n) ^ (bits >>> 33)); }");
        var plan = prepare(before).plan().orElseThrow();
        compare(before, plan.preview(), new Class<?>[]{int.class, int.class},
                List.of(new Object[]{33, -1}, new Object[]{32, Integer.MIN_VALUE}, new Object[]{6, Integer.MAX_VALUE}));
    }

    @Test void generatedNamesCannotCaptureExistingParameterNames() throws Exception {
        String before = source("public static int step(int _state0, int _next0) { if (_state0 <= 0) return _next0; return step(_state0 - 1, _next0 + _state0); }");
        var plan = prepare(before).plan().orElseThrow();
        compare(before, plan.preview(), new Class<?>[]{int.class, int.class}, List.<Object[]>of(new Object[]{20, 4}));
    }

    @Test void snapshotApplyAndUndoRejectStaleWholeDocuments() {
        String before = sum(); var plan = prepare(before).plan().orElseThrow();
        String after = plan.applyTo(before);
        assertEquals(plan.preview(), after);
        assertEquals(before, plan.undo(after));
        assertThrows(IllegalStateException.class, () -> plan.applyTo(before + " "));
        assertThrows(IllegalStateException.class, () -> plan.undo(after + " "));
    }

    @Test void cancellationAndBudgetEndReturnNoPartialPlan() {
        String before = sum();
        var cancelled = JavaTailRecursionRewrite.prepare("Sample.java", before, before.indexOf("public static"),
                JavaTailRecursionRewrite.StackPolicy.ALLOW_FRAME_ELISION, BUDGET, () -> true);
        assertTrue(cancelled.plan().isEmpty()); assertEquals("CANCELLED", cancelled.reason());
        var exhausted = JavaTailRecursionRewrite.prepare("Sample.java", before, before.indexOf("public static"),
                JavaTailRecursionRewrite.StackPolicy.ALLOW_FRAME_ELISION, 1, () -> false);
        assertTrue(exhausted.plan().isEmpty()); assertEquals("WORK_LIMIT", exhausted.reason());
    }

    @Test void unsupportedEffectsAndCommentsAreNotMadeIntoLoops() {
        for (String statement : List.of("a++", "a / n", "Math.abs(a)")) {
            assertTrue(prepare(source("public static long step(int n, long a) { if (n <= 0) return a; return step(n - 1, " + statement + "); }")).plan().isEmpty());
        }
        assertTrue(prepare(sum().replace("return a;", "return a; /* retain */")).plan().isEmpty());
    }

    @Test void wrongMethodPositionAndCompilationErrorsFailClosed() {
        var missing = JavaTailRecursionRewrite.prepare("Sample.java", sum(), -1,
                JavaTailRecursionRewrite.StackPolicy.ALLOW_FRAME_ELISION, BUDGET, () -> false);
        assertTrue(missing.plan().isEmpty());
        assertTrue(prepare(sum().replace("a + n", "unknown + n")).plan().isEmpty());
    }

    @Test void generatedLoopDoesNotRecurAndHandlesDeepCountdownAfterOptIn() throws Exception {
        var plan = prepare(sum()).plan().orElseThrow();
        try (var loader = compile(plan.preview(), "deep")) {
            var method = loader.loadClass("Sample").getMethod("step", int.class, long.class);
            assertEquals(5_000_050_000L, method.invoke(null, 100_000, 0L));
        }
        // Deliberately do not claim equality of StackOverflowError behavior.
    }

    @Test void oversizedStateIdentityReturnsNoPlan() {
        String name = "step" + "x".repeat(4097);
        String before = source("public static long " + name
                + "(int n, long a) { if (n <= 0) return a; return " + name + "(n - 1, a); }");
        var result = assertDoesNotThrow(() -> prepare(before));
        assertTrue(result.plan().isEmpty());
        assertEquals("OUTSIDE_DIRECT_TAIL_FRAGMENT", result.reason());
    }

    @Test void rewritesTheExactSelectedMethodInsideALocalClass() throws Exception {
        String before = source("""
            public static long step(int n, long a) {
                class Local {
                    static long step(int n, long a) {
                        if (n <= 0) return a;
                        return step(n - 1, a + n);
                    }
                    static long step(long n, long a) { return a - n; }
                }
                return Local.step(n, a);
            }
            """);
        int offset = before.indexOf("static long step(int n, long a)", before.indexOf("class Local"));
        var result = JavaTailRecursionRewrite.prepare("Sample.java", before, offset,
                JavaTailRecursionRewrite.StackPolicy.ALLOW_FRAME_ELISION, BUDGET, () -> false);
        var plan = result.plan().orElseThrow(() -> new AssertionError(result.reason()));
        assertEquals(before.substring(0, offset), plan.preview().substring(0, offset));
        assertTrue(plan.preview().contains("static long step(long n, long a) { return a - n; }"));
        compare(before, plan.preview(), new Class<?>[]{int.class, long.class},
                List.of(new Object[]{-4, 7L}, new Object[]{0, Long.MIN_VALUE}, new Object[]{30, Long.MAX_VALUE}));
        assertEquals(before, plan.undo(plan.applyTo(before)));
        var inexact = JavaTailRecursionRewrite.prepare("Sample.java", before, offset + 1,
                JavaTailRecursionRewrite.StackPolicy.ALLOW_FRAME_ELISION, BUDGET, () -> false);
        assertTrue(inexact.plan().isEmpty());
        assertEquals("METHOD_NOT_FOUND", inexact.reason());
    }

    private void compare(String before, String after, Class<?>[] parameters, List<Object[]> values) throws Exception {
        try (var original = compile(before, "original"); var generated = compile(after, "generated")) {
            var left = original.loadClass("Sample").getMethod("step", parameters);
            var right = generated.loadClass("Sample").getMethod("step", parameters);
            for (Object[] input : values) assertEquals(left.invoke(null, input), right.invoke(null, input));
        }
    }
    private URLClassLoader compile(String source, String label) throws Exception {
        Path directory = Files.createTempDirectory(temporary, label);
        Path file = directory.resolve("Sample.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler, "A JDK is required");
        assertEquals(0, compiler.run(null, null, null, "-d", directory.toString(), file.toString()), source);
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}
