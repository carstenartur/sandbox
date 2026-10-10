/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** The helper bodies, not a supplied mathematical target, define the search input. */
class ScalarMethodExpansionTest {
    @TempDir Path temporary;

    @Test void distributionAcrossAHelperCallUsesTheOrdinaryMathSearch() throws Exception {
        String helper = "private static int merge(int p,int q) { int value=p+q; return value; }";
        qualify(helper, "return x*merge(a,b)-x*a;", true);
    }

    @Test void nestedCallsAndParameterAssignmentsHaveIndependentFrames() throws Exception {
        String helpers = """
            private static int join(int x,int a) { x += a; return x; }
            private static int multiply(int x,int a) { int value=x; value *= a; return value; }
            private static int total(int x,int a,int b) { return multiply(x,join(a,b)); }
            """;
        qualify(helpers, "return total(x,a,b)-multiply(x,a);", true);
    }

    @Test void repeatedCallsDoNotReuseTheFirstCallsArguments() throws Exception {
        qualify("private static int product(int x,int a) { return x*a; }",
                "return product(x,a)+product(x,b);", true);
    }

    @Test void bindingResolutionDistinguishesOverloads() throws Exception {
        qualify("""
            private static int combine(int p,int q) { return p+q; }
            private static long combine(long p,long q) { return p-q; }
            """, "return x*combine(a,b)-x*a;", true);
    }

    @Test void zeroArgumentHelperAndDifferentAlgebraicShapeNeedNoNamedTask() throws Exception {
        qualify("private static int offset() { return 1; } private static int subtract(int p,int q) { return p-q; }",
                "return subtract(x,offset())+offset();", true);
    }

    @Test void promotionsAndHelperReturnNarrowingArePreserved() throws Exception {
        qualify("private static byte convert(int p) { return (byte)p; }",
                "return convert(x)+a+b-(b+a);", true);
    }

    @Test void wideningOccursAfterTheOriginalIntAddition() throws Exception {
        qualify("private static long sum(int p,int q) { return p+q; }",
                "return (int)(sum(x,a)*b-sum(x,a)*b);", true);
    }

    @Test void unusedThrowingArgumentCannotDisappear() throws Exception {
        qualify("private static int keep(int ignored,int value) { return value; }",
                "return keep(10/a,x)+b-b;", false);
    }

    @Test void unusedThrowingLocalCannotDisappear() throws Exception {
        qualify("private static int keep(int p,int q) { int discarded=10/q; return p; }",
                "return keep(x,a)+b-b;", false);
    }

    @Test void mutableStateDispatchAndControlFlowAreNotAssumedPure() {
        for (String helper : List.of(
                "private static int state; private static int fn(int p,int q) { return state+p; }",
                "private static int state; private static int fn(int p,int q) { state++; return p+q; }",
                "private static synchronized int fn(int p,int q) { return p+q; }",
                "private static native int fn(int p,int q);",
                "private static int fn(int p,int q) { if(p==0)return q; return p+q; }",
                "private static int fn(int p,int q) { return fn(p,q); }",
                "private static int fn(int p,int q) { return other(p,q); } private static int other(int p,int q) { return fn(p,q); }")) {
            String source=source(helper,"return x*fn(a,b)-x*a;");
            var result=analyzeCaller(source);
            assertFalse(result.changed(),helper+": "+result);
            assertFalse(result.diagnostics().isEmpty(),helper);
        }
    }

    @Test void effectfulArgumentAndExpressionQualifierAreRejected() {
        for(String expression:List.of("fn(a++,b)", "receiver().fn(a,b)")) {
            String source=source("private static Calculation receiver(){ return null; } private static int fn(int p,int q){return p+q;}",
                    "return x*"+expression+"-x*a;");
            assertFalse(analyzeCaller(source).changed());
        }
    }

    @Test void helperDefinitionCommentsAreNotMovedIntoTheCaller() throws Exception {
        qualify("private static int sum(int p,int q) { /* Meaning belongs here. */ return p+q; }",
                "return x*sum(a,b)-x*a;", true);
    }

    @Test void constantHelpersCannotBypassTheConstantSourcePolicy() {
        for(String body:List.of("return factors();", "return x*factors();")) {
            String input=source("private static int factors() { return 5*7*11; }",body);
            assertFalse(analyzeCaller(input).changed(),body);
        }
    }

    @Test void decodedWideningAndNarrowingAreCheckedBeforeAnySearch() throws Exception {
        checkDecoded("private static long sum(int p,int q) { return p+q; }",
                "return (int)(sum(x,a)/2);");
        checkDecoded("private static byte narrow(int p) { return (byte)p; }",
                "return narrow(x)+a;");
    }

    @Test void generatedHelperProgramsHaveNoPreconfiguredMathematicalTask() throws Exception {
        SplittableRandom random=new SplittableRandom(20261010);
        String helpers="""
            private static int plus(int first,int second) { return first+second; }
            private static int minus(int first,int second) { int r=first; r-=second; return r; }
            private static int times(int first,int second) { return first*second; }
            """;
        for(int i=0;i<20;i++) checkDecoded(helpers,"return "+program(random,3)+";");
    }

    @Test void originalTraceRetainsUnusedArgumentBeforeUnusedCalleeLocal() {
        String input=source("private static int keep(int unused,int p) { int dead=7/p; return p; }",
                "return keep(10/a,x)+b-b;");
        var extraction=new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(input),input,options());
        var region=extraction.regions().stream().filter(r->r.start()>input.indexOf("public static int compute")).findFirst().orElseThrow();
        var divisions=region.trace().occurrences().stream().filter(o->JavaExpressions.operationOf(o.expression()).orElse(null)==NumericOperation.DIVIDE).toList();
        assertEquals(2,divisions.size());
        assertTrue(divisions.getFirst().sourceId().startsWith("operation@"+input.indexOf("10/a")+":"));
        assertTrue(divisions.getLast().sourceId().startsWith("operation@"+input.indexOf("7/p")+":"));
    }

    @Test void anotherClassInitializationCannotBeLost() {
        String input=source("""
            private static int calls;
            private static class Other {
                static { calls++; }
                private static int sum(int p,int q) { return p+q; }
            }
            ""","return x*Other.sum(a,b)-x*a;");
        assertFalse(analyzeCaller(input).changed());
    }

    @Test void expandingDeepAcyclicCallsStopsAtTheSourceBudget() {
        StringBuilder helpers=new StringBuilder();
        for(int i=0;i<24;i++) helpers.append("private static int step").append(i)
                .append("(int p,int q){return ").append(i==23?"p+q":"step"+(i+1)+"(p,q)").append(";}");
        String input=source(helpers.toString(),"return x*step0(a,b)-x*a;");
        var result=analyzeCaller(input);
        assertFalse(result.changed());
        assertTrue(result.diagnostics().stream().anyMatch(d->d.code().contains("DEPTH")||d.code().contains("LIMIT")));
    }

    private static String program(SplittableRandom random,int depth) {
        if(depth==0) return List.of("x","a","b").get(random.nextInt(3));
        return List.of("plus","minus","times").get(random.nextInt(3))
                +"("+program(random,depth-1)+","+program(random,depth-1)+")";
    }

    private void checkDecoded(String helpers,String body)throws Exception {
        String original=source(helpers,body);
        var ast=MathTestSupport.parse(original);
        var extraction=new JavaComputationExtractor().extractRuntime(ast,original,options());
        int start=original.indexOf("public static int compute");
        var regions=extraction.regions().stream().filter(region->region.start()>start).toList();
        assertEquals(1,regions.size(),extraction.diagnostics().toString());
        var region=regions.getFirst();
        assertEquals(1,region.plan().outputs().size());
        var decoded=ComputationOptimizer.prepare(region.plan());
        Method method=compile(original);
        int[] edges={0,1,-1,255,Integer.MIN_VALUE,Integer.MAX_VALUE};
        for(int x:edges)for(int a:edges)for(int b:edges) {
            Map<String,Integer> values=Map.of("x",x,"a",a,"b",b);
            Map<String,Integer> inputs=new HashMap<>();
            region.inputNames().forEach((id,name)->inputs.put(id,values.get(name)));
            assertEquals(method.invoke(null,x,a,b),decoded.execute(inputs).get(region.plan().outputs().getFirst().name()),original);
        }
    }

    private static String source(String helpers,String body) {
        return "public class Calculation {\n"+helpers+"\npublic static int compute(int x,int a,int b) {\n"+body+"\n}}";
    }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true,EnumSet.of(NumericKind.BYTE,NumericKind.SHORT,NumericKind.CHAR,NumericKind.INT,NumericKind.LONG),
                SafetyProfile.PRESERVE_JAVA,OptimizationGoal.LOWER_ESTIMATED_RUNTIME,2_000_000L,20_000,false,17,List.of());
    }
    private static MathematicalAnalysis.Analysis analyzeCaller(String source) {
        var options=options();
        int start=source.indexOf("public static int compute");
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source),source,options,new NullProgressMonitor(),start,source.length()-start);
    }
    private void qualify(String helpers,String body,boolean improvementRequired)throws Exception {
        String original=source(helpers,body);
        var result=analyzeCaller(original);
        if(improvementRequired) assertTrue(result.changed(),result.diagnostics().toString());
        Document document=new Document(original);
        var undo=result.newEdit().apply(document,TextEdit.CREATE_UNDO);
        String generated=document.get();
        System.out.println("SCALAR_METHOD_GENERATED="+generated);
        undo.apply(document);
        assertEquals(original,document.get());
        assertTrue(generated.contains(helpers),generated);
        assertFalse(generated.contains("de.regelsuche"));
        if(improvementRequired) {
            assertFalse(result.evidence().isEmpty());
            String caller=generated.substring(generated.indexOf("public static int compute"));
            assertFalse(caller.contains("merge(")||caller.contains("total(")||caller.contains("product("),caller);
        }
        Method before=compile(original),after=compile(generated);
        int[] edges={0,1,-1,17,255,256,Integer.MIN_VALUE,Integer.MAX_VALUE,1<<30};
        for(int x:edges)for(int a:edges)for(int b:edges) assertEquals(outcome(before,x,a,b),outcome(after,x,a,b),generated);
        SplittableRandom random=new SplittableRandom(432761);
        for(int i=0;i<128;i++) {
            int x=random.nextInt(),a=random.nextInt(),b=random.nextInt();
            assertEquals(outcome(before,x,a,b),outcome(after,x,a,b),generated);
        }
    }
    private static Object outcome(Method method,int x,int a,int b)throws Exception {
        try {return method.invoke(null,x,a,b);}
        catch(InvocationTargetException failure) {return failure.getCause().getClass();}
    }
    private Method compile(String source)throws Exception {
        Path output=Files.createTempDirectory(temporary,"compiled-");
        Path file=output.resolve("Calculation.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()),source);
        try(var loader=new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},null)) {
            return loader.loadClass("Calculation").getMethod("compute",int.class,int.class,int.class);
        }
    }
}
