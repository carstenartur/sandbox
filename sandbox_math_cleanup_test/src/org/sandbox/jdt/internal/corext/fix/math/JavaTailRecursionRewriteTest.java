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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings={JavaCore.ENABLED,JavaCore.DISABLED})
    void nativeBridgeRetainsAuthoredDocumentationAndBothLoopFormsExecuteEqually(String documentation) throws Exception {
        String before=sum().replace("public static", "/** Authored sum. @param n count */ public static");
        var ast=bound(before,documentation);int offset=((TypeDeclaration)ast.types().getFirst()).getMethods()[0].getStartPosition();
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

    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualCountedTailEmissionEntersTheExistingRuntimeProofAndExecutesBothPaths(boolean counterLast) throws Exception {
        String before=counterLast?source("public static long step(final long a,final int n) { if(n<=0)return a+n; return step(a+n,n-1); }"):sum();
        var values=nativeOptions();values.put(MathCleanUpOptions.GOAL,"LOWER_ESTIMATED_RUNTIME");
        var options=MathCleanUpOptions.parse(values,17);
        var prepared=JavaTailRecursionRewrite.prepare(bound(before),before,before.indexOf("public static"),options,new NullProgressMonitor());
        String transferred=prepared.plan().orElseThrow(()->new AssertionError(prepared.reason())).applyTo(before);
        var ast=bound(transferred);var loops=new java.util.ArrayList<ForStatement>();
        ast.accept(new ASTVisitor(){@Override public boolean visit(ForStatement loop){loops.add(loop);return true;}});
        assertEquals(1,loops.size());var loop=loops.getFirst();
        var result=MathematicalAnalysis.analyzeTailLoops(ast,transferred,options,new NullProgressMonitor(),
                List.of(new org.eclipse.jdt.core.SourceRange(loop.getStartPosition(),loop.getLength())));
        var proof=result.evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst()
                .orElseThrow(()->new AssertionError(result.diagnostics().toString()));
        var document=new org.eclipse.jface.text.Document(transferred);var undo=result.newEdit().apply(document);
        String accelerated=document.get();assertTrue(accelerated.contains("$series"),accelerated);
        assertTrue(java.util.Arrays.stream(bound(accelerated).getProblems()).noneMatch(p->p.isWarning()),accelerated);
        int above=Math.toIntExact(proof.runtimeIteration().minimumIterations()+1);
        Class<?>[] parameters=counterLast?new Class<?>[]{long.class,int.class}:new Class<?>[]{int.class,long.class};
        List<Object[]> inputs=List.of(new Object[]{-1,7L},new Object[]{0,7L},new Object[]{1,Long.MAX_VALUE},
                new Object[]{30,Long.MIN_VALUE},new Object[]{above,7L});
        if(counterLast) inputs=inputs.stream().map(input->new Object[]{input[1],input[0]}).toList();
        compare(before,accelerated,parameters,inputs);
        String fallback=transferred.substring(loop.getStartPosition(),loop.getStartPosition()+loop.getLength());
        assertTrue(accelerated.contains(fallback));
        String probe=accelerated.replace(fallback,"throw new AssertionError(\"fallback\");");
        compare(before,probe,parameters,List.<Object[]>of(inputs.getLast()));
        undo.apply(document);assertEquals(transferred,document.get());
    }

    @Test void mathematicalOptInKeepsTheProtectedTailLoopAndCannotEnterStrictAcceleration() throws Exception {
        String before=sum();var values=nativeOptions();values.put(MathCleanUpOptions.GOAL,"LOWER_ESTIMATED_RUNTIME");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN,"true");var options=MathCleanUpOptions.parse(values,17);
        var prepared=JavaTailRecursionRewrite.prepare(bound(before),before,before.indexOf("public static"),options,new NullProgressMonitor());
        var plan=prepared.plan().orElseThrow(()->new AssertionError(prepared.reason()));String transferred=plan.applyTo(before);
        assertTrue(transferred.contains("while"));assertFalse(transferred.contains("$series"));
        var body=new org.eclipse.jdt.core.SourceRange(plan.bodyOffset(),plan.bodyLength());
        var rejected=assertThrows(IllegalArgumentException.class,()->MathematicalAnalysis.analyzeTailLoops(bound(transferred),transferred,options,null,List.of(body)));
        assertEquals("TAIL_ITERATION_PROFILE_REQUIRED",rejected.getMessage());
        var ordinary=MathematicalAnalysis.analyze(bound(transferred),transferred,options,null,-1,0,null,List.of(body));
        var document=new org.eclipse.jface.text.Document(transferred);ordinary.newEdit().apply(document);
        assertFalse(ordinary.changed(),ordinary.diagnostics().toString());assertEquals(transferred,document.get());
        compare(before,document.get(),new Class<?>[]{int.class,long.class},List.of(new Object[]{-1,7L},new Object[]{0,7L},new Object[]{30,Long.MAX_VALUE}));
    }

    @ParameterizedTest
    @ValueSource(strings={"/* retain */","// retain   "})
    void nativeBridgeRejectsDetachedCommentsAddedToTheSource(String comment) throws Exception {
        String padding=" ".repeat(comment.length());
        String before=sum().replace("{ if", "{ "+padding+"\nif");
        String current=before.replace(padding,comment);
        var ast=bound(before);var currentAst=bound(current);int offset=before.indexOf("public static");
        assertEquals(before.length(),current.length());assertTrue(ast.getCommentList().isEmpty());
        assertEquals(1,currentAst.getCommentList().size());
        var options=MathCleanUpOptions.parse(nativeOptions(),17);
        assertTrue(JavaTailRecursionRewrite.prepare(currentAst,current,offset,options,new NullProgressMonitor()).plan().isEmpty());
        var result=JavaTailRecursionRewrite.prepare(ast,current,offset,options,new NullProgressMonitor());
        if(result.plan().isPresent()) compare(current,result.plan().orElseThrow().applyTo(current),
                new Class<?>[]{int.class,long.class},List.<Object[]>of(new Object[]{30,7L}));
        assertTrue(result.plan().isEmpty(),()->"Stale AST admitted and rewrote the current comment: "+result.plan().orElseThrow().preview());
        assertEquals("STALE_RECURSION_SOURCE",result.reason());
    }

    @ParameterizedTest
    @ValueSource(strings={"// retain   \n    ","    /* retain */\n"})
    void nativeBridgeRejectsChangedDetachedCommentKindsAndRanges(String prefix) {
        String before="/* retain */\n    "+sum();String current=prefix+sum();
        var ast=bound(before);int offset=before.indexOf("public static");
        var options=MathCleanUpOptions.parse(nativeOptions(),17);
        var unchanged=JavaTailRecursionRewrite.prepare(ast,before,offset,options,new NullProgressMonitor());
        assertTrue(unchanged.plan().orElseThrow(()->new AssertionError(unchanged.reason())).preview().contains("/* retain */"));
        assertEquals(before.length(),current.length());bound(current);
        var result=JavaTailRecursionRewrite.prepare(ast,current,offset,options,new NullProgressMonitor());
        assertTrue(result.plan().isEmpty(),()->"Stale detached comment accepted: "+prefix);
        assertEquals("STALE_RECURSION_SOURCE",result.reason());
    }

    @Test void nativeBridgeRejectsAnAstWithoutCommentMetadata() {
        String before=sum();var ast=AST.newAST(AST.getJLSLatest(),false).newCompilationUnit();
        var result=assertDoesNotThrow(()->JavaTailRecursionRewrite.prepare(ast,before,before.indexOf("public static"),
                MathCleanUpOptions.parse(nativeOptions(),17),new NullProgressMonitor()));
        assertTrue(result.plan().isEmpty());assertEquals("STALE_RECURSION_SOURCE",result.reason());
    }
    private static HashMap<String,String> nativeOptions() {
        var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());values.put(MathCleanUpOptions.CLEANUP,"true");
        values.put(MathCleanUpOptions.TAIL_RECURSION,"true");values.put(MathCleanUpOptions.KINDS,"INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET,Long.toString(BUDGET));return values;
    }
    private static CompilationUnit bound(String source) {
        return bound(source,null);
    }
    private static CompilationUnit bound(String source,String documentation) {
        var parser=ASTParser.newParser(AST.getJLSLatest());var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);
        if(documentation!=null) compiler.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT,documentation);
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
