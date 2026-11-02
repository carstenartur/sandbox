/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueResidueTest {
    @TempDir Path directory;
    private static String fixture() {
        return JavaValueCallTest.fixture().replace("BigInteger.valueOf(101)","new BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\")").replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}",
            "public static Leaf scale(BigInteger x,BigInteger y){BigInteger a=x.mod(Leaf.P);BigInteger b=y.mod(Leaf.P);return Leaf.of(a,b);}");
    }
    @Test void factoryReceivesRawValuesOnlyWhenEarlierReductionsAreTotalAndUnobserved() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var analysis=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueCallTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,analysis);
        assertTrue(generated.contains("Mathematics factory residue"));assertEquals(execute(source),execute(generated));
        assertTrue(analysis.documentation().stream().anyMatch(edit->edit.offset()==source.indexOf("public static Leaf scale(")
            && edit.replacement().contains("BigInteger component reference identity may change")),"The enclosing method must retain the mathematical contract");
        assertEquals(generated.split("Mathematics factory residue",-1).length,apply(generated).split("Mathematics factory residue",-1).length,
            "A repeated cleanup must retain the original guarded normalization envelope");
    }
    @Test void rejectedDiscoveryStillChecksTheSharedCancellationToken() throws Exception {
        String source=JavaValueCallTest.fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var checks=new java.util.concurrent.atomic.AtomicInteger();var options=JavaValueCallTest.options();
        var work=new JavaValueSourceSummary.Work(1,options,()->{checks.incrementAndGet();return true;});
        JavaValueResidueOptimizer.optimize(helper.parse(source),source,options,null,-1,0,Map.of(),helper.environment(),
            new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),work,new MathematicalJavadoc());
        assertTrue(checks.get()>0,"An unsuccessful candidate scan still belongs to the shared cancelled request");
    }
    @Test void standaloneSelectionAndDeferredDiscoveryRetainTheSameVerifiedFactoryRewrite() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var options=JavaValueCallTest.options();var ast=helper.parse(source);
        var shared=MathematicalAnalysis.analyze(ast,source,options,null,-1,0,helper.environment());
        var expected=shared.replacements().stream().filter(edit->edit.replacement().contains("Mathematics factory residue")).toList();
        assertEquals(1,expected.size(),shared.diagnostics().toString());
        var selected=MathematicalAnalysis.analyze(ast,source,options,null,source.indexOf("return Leaf.of(a,b)"),1,helper.environment());
        assertEquals(expected,selected.replacements(),selected.diagnostics().toString());
        assertEquals(1,selected.evidence().size());
        var replacements=new ArrayList<MathematicalAnalysis.Replacement>();var evidence=new ArrayList<MathematicalAnalysis.VerifiedRegion>();
        var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);
        try(var context=JavaSourceContext.open(ast,source,helper.environment(),null,options)) {
            JavaValueResidueOptimizer.optimize(ast,source,options,null,-1,0,Map.of(),helper.environment(),
                replacements,evidence,diagnostics,work,new MathematicalJavadoc());
            context.checkpoint().requireCurrent();
        }
        assertEquals(expected,replacements,diagnostics.toString());assertEquals(1,evidence.size());
    }
    @Test void wholeFileFreshnessPrecedesResidueDiscoveryWithTheEnabledProfile() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var ast=helper.parse(source);String changed=source.replace("Leaf.of(a,b)","Leaf.of(b,a)");
        var values=new HashMap<>(JavaValueCallTest.options().toMap());values.put(MathCleanUpOptions.WORK_BUDGET,"1");
        var result=MathematicalAnalysis.analyze(ast,changed,MathCleanUpOptions.parse(values,17),null,-1,0,helper.environment());
        assertFalse(result.changed());assertTrue(result.evidence().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(item->item.code().equals("STALE_AST_SOURCE")),
            "Whole-file residue discovery must not spend the established regions' budget during freshness: "+result.diagnostics());
    }
    @Test void staleFactoryCallsCannotEscapeFreshnessAndDisabledAnalysisStaysDormant() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var ast=helper.parse(source);String changed=source.replace("Leaf.of(a,b)","Leaf.of(b,a)");assertNotEquals(source,changed);
        var options=JavaValueCallTest.options();
        var stale=MathematicalAnalysis.analyze(ast,changed,options,null,-1,0,helper.environment());
        assertFalse(stale.changed());assertTrue(stale.diagnostics().stream().anyMatch(item->item.code().equals("STALE_AST_SOURCE")));
        for(var gate:List.of(Map.entry(MathCleanUpOptions.MATHEMATICAL_OPT_IN,"false"),Map.entry(MathCleanUpOptions.KINDS,"INT"))) {
            var inactive=new HashMap<>(options.toMap());inactive.put(gate.getKey(),gate.getValue());inactive.put(MathCleanUpOptions.WORK_BUDGET,"1");
            var result=MathematicalAnalysis.analyze(ast,changed,MathCleanUpOptions.parse(inactive,17),null,-1,0,helper.environment());
            assertFalse(result.changed());assertTrue(result.diagnostics().stream().anyMatch(item->item.code().equals("STALE_AST_SOURCE")),
                "Inactive residue discovery must not exhaust the source-freshness check: "+result.diagnostics());
        }
        var disabled=new HashMap<>(options.toMap());disabled.put(MathCleanUpOptions.CLEANUP,"false");disabled.put(MathCleanUpOptions.WORK_BUDGET,"1");
        var dormant=MathematicalAnalysis.analyze(ast,changed,MathCleanUpOptions.parse(disabled,17),null,-1,0,helper.environment());
        assertFalse(dormant.changed());assertTrue(dormant.diagnostics().isEmpty(),dormant.diagnostics().toString());
    }
    @Test void selectedResidueBlockUsesHalfOpenSourceIntervals() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var ast=helper.parse(source);var owner=(org.eclipse.jdt.core.dom.TypeDeclaration)ast.types().getFirst();
        var body=Arrays.stream(owner.getMethods()).filter(method->method.getName().getIdentifier().equals("scale")).findFirst().orElseThrow().getBody();
        int start=body.getStartPosition(),length=body.getLength();
        for(int[] selection:new int[][]{{start-1,1},{start+length,0},{start,0},{start+length-1,1}}) {
            var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueCallTest.options(),null,selection[0],selection[1],helper.environment());
            boolean overlaps=selection[0]>=start && selection[0]<start+length;
            assertEquals(overlaps,result.replacements().stream().anyMatch(edit->edit.replacement().contains("Mathematics factory residue")),Arrays.toString(selection));
        }
    }
    @Test void otherConsumersAndDifferentModulusCannotLoseTheirNormalizedValues() throws Exception {
        for(String source:List.of(fixture().replace("BigInteger b=y.mod", "if(a.signum()<0)throw new AssertionError();BigInteger b=y.mod"),
            fixture().replace("x.mod(Leaf.P)","x.mod(BigInteger.valueOf(103))")))assertFalse(apply(source).contains("Mathematics factory residue"));
    }
    @Test void smallModulusRetainsTheOriginalWhenGuardsCostMoreThanDuplicateReduction() throws Exception {
        String source=fixture().replace("new BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\")","BigInteger.valueOf(101)");
        assertFalse(apply(source).contains("Mathematics factory residue"));
    }
    @Test void mixedSubclassFallbackCannotObserveAnotherArgumentsUnreducedValueInsideFactory() throws Exception {
        String source=fixture().replace("new Leaf(a.mod(P),b.mod(P))","new Leaf(a.mod(P),b.add(a).subtract(a).mod(P))");
        assertFalse(apply(source).contains("Mathematics factory residue"));
    }
    @Test void cancellationAndExhaustedSourceBudgetCannotExposePartialEdits() throws Exception {
        for(String source:List.of(fixture(),JavaValueCallTest.fixture(),JavaValueReuseTest.fixture(),JavaValueInverseReuseTest.fixture())) {
            var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var normal=JavaValueReuseTest.options();
            var cancelled=new org.eclipse.core.runtime.NullProgressMonitor();cancelled.setCanceled(true);
            assertTrue(MathematicalAnalysis.analyze(ast,source,normal,cancelled,-1,0,fixture.environment()).replacements().isEmpty());
            var exhausted=new MathCleanUpOptions(true,normal.kinds(),normal.safety(),normal.goal(),1,normal.maxStates(),false,17,List.of(),true).withValueRuntime(normal.valueRuntime());
            assertTrue(MathematicalAnalysis.analyze(ast,source,exhausted,null,-1,0,fixture.environment()).replacements().isEmpty());
        }
    }
    @Test void changedFactoryDependencyInvalidatesTheOrdinaryApplyReceipt() throws Exception {
        for(String source:List.of(fixture(),JavaValueCallTest.fixture())) {
            int split=source.indexOf("final class Leaf");Path dependency=directory.resolve("Leaf.java");Files.writeString(dependency, ("import java.math.BigInteger;" + source.substring(split)),
					StandardCharsets.UTF_8);
            source=source.substring(0,split);var fixture=new JavaImmutableValueTest();fixture.directory=directory;
            var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueCallTest.options(),null,-1,0,fixture.environment());
            assertFalse(result.replacements().isEmpty(),result.diagnostics().toString());
            Files.writeString(dependency,
					Files.readString(dependency).replace("a.mod(P)", "a.mod(P.add(BigInteger.ONE))"),
					StandardCharsets.UTF_8);
            assertFalse(result.matches(source,Map.of()));assertThrows(IllegalArgumentException.class,result::newEdit);
        }
    }
    private String apply(String source) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueCallTest.options(),null,-1,0,fixture.environment());
        return fixture.apply(source,result);
    }
    private List<String> execute(String source) throws Exception {
        Path output=Files.createTempDirectory(directory,"residue");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var owner=loader.loadClass("Calculation");var leaf=loader.loadClass("Leaf");var method=owner.getMethod("scale",BigInteger.class,BigInteger.class);
            var first=leaf.getMethod("first");first.setAccessible(true);var second=leaf.getMethod("second");second.setAccessible(true);var outcomes=new ArrayList<String>();
            BigInteger[] values={BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(-7),BigInteger.valueOf(101),BigInteger.ONE.shiftLeft(5000),null,new Derived("9")};
            for(BigInteger a:values)for(BigInteger b:values)try {Object result=method.invoke(null,a,b);Object fresh=method.invoke(null,a,b);assertNotSame(result,fresh);outcomes.add(first.invoke(result)+":"+second.invoke(result));}
                catch(java.lang.reflect.InvocationTargetException failure){outcomes.add(failure.getCause().getClass().getName());}
            return outcomes;
        }
    }
    public static class Derived extends BigInteger {
        private static final long serialVersionUID=1L;
        Derived(String value){super(value);}
        @Override public BigInteger mod(BigInteger value){throw new IllegalStateException("observable mod");}
    }
}
