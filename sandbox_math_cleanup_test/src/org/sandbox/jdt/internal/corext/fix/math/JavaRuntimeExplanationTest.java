/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class JavaRuntimeExplanationTest extends JavaContextTestSupport {
    static MathCleanUpOptions mode(MathCleanUpOptions.ExplanationMode mode) {
        var o=options();return new MathCleanUpOptions(o.enabled(),o.kinds(),o.safety(),o.goal(),o.workBudget(),o.maxStates(),false,17,List.of(),false,mode);
    }
    static MathematicalAnalysis.Analysis analyze(String source,MathCleanUpOptions options) {
        return JavaEarlyExitGuardFactsTest.analyze(JavaEarlyExitGuardFactsTest.parse(source),source,options,null,-1,0);
    }
    static String apply(String source,MathematicalAnalysis.Analysis analysis) throws Exception {
        var document=new Document(source);var undo=analysis.newEdit().apply(document,TextEdit.CREATE_UNDO);
        String result=document.get();undo.apply(document);assertEquals(source,document.get());return result;
    }
    @ParameterizedTest @EnumSource(MathCleanUpOptions.ExplanationMode.class)
    void secondInvocationPreservesWholeRuntimeEnvelope(MathCleanUpOptions.ExplanationMode mode) throws Exception {
        for(String loop:List.of("for(int i=0;i<=n;i+=3)sum+=1;","for(int i=0;i<n;i++)sum+=i;",
                "for(int i=0;i<n;i++)sum+=1;")) {
            String source="public class Sample {public static int run(int n){int sum=0;"+loop+"return sum;}}";
            var first=analyze(source,mode(mode));assertTrue(first.evidence().stream().anyMatch(e->e.runtimeIteration()!=null),first.diagnostics().toString());
            String generated=apply(source,first);var second=analyze(generated,mode(mode));
            assertEquals(generated,apply(generated,second),mode+" "+loop+" "+second.diagnostics());
            assertFalse(second.changed());
        }
    }
    @ParameterizedTest @EnumSource(MathCleanUpOptions.ExplanationMode.class)
    void explanationsDescribeActualContractAndStrategy(MathCleanUpOptions.ExplanationMode mode) throws Exception {
        for(String loop:List.of("for(int i=0;i<n;i++){/* authored */sum+=1;}","for(int i=0;i<=n;i+=3)sum+=1;",
                "for(int i=0;i<n;i++)sum+=i;")) {
            String source="public class Sample {public static int run(int n){int sum=0;"+loop+"return sum;}}";
            var result=analyze(source,mode(mode));String generated=apply(source,result);
            if(mode==MathCleanUpOptions.ExplanationMode.NONE)assertFalse(generated.contains("// Verified runtime"));
            else {
                assertTrue(generated.contains("// Verified runtime"),generated);
                assertTrue(generated.contains("sum") && generated.contains("modulo 2^32"),generated);
                boolean unit=result.evidence().stream().anyMatch(e->e.runtimeIteration()!=null && e.runtimeIteration().strategy()==RuntimeIterationEvidence.EmissionStrategy.PRIMITIVE_UNIT);
                String comments=generated.lines().filter(l->l.stripLeading().startsWith("//")).collect(java.util.stream.Collectors.joining("\n"));
                assertEquals(!unit,comments.contains("fallback"),comments);
                assertEquals(!unit,comments.contains("crossover"),comments);
            }
            if(loop.contains("authored"))assertTrue(generated.contains("/* authored */"));
        }
    }
    @ParameterizedTest @EnumSource(MathCleanUpOptions.ExplanationMode.class)
    void streamCommentsStayInsideSupplierAndReapplicationIsStable(MathCleanUpOptions.ExplanationMode mode) throws Exception {
        for(String width:List.of("Int","Long")) {
            String source="public class Sample {public static "+width.toLowerCase()+" run(int n){return java.util.stream."+width+"Stream.rangeClosed(1,n).map(i->i*i).sum();}}";
            String generated=apply(source,analyze(source,mode(mode)));
            assertTrue(generated.contains("Supplier"),generated);
            assertEquals(generated,apply(generated,analyze(generated,mode(mode))));
            if(mode!=MathCleanUpOptions.ExplanationMode.NONE) {
                assertTrue(generated.indexOf("// Verified runtime")>generated.indexOf("getAs"));
                assertFalse(generated.contains("// The final counter"));
                assertTrue(generated.contains(width.equals("Int")?"modulo 2^32":"modulo 2^64"));
            }
            try(var before=compile(source);var after=compile(generated)) {
                for(int n:new int[]{-1,0,1,5000})assertEquals(before.loadClass("Sample").getMethod("run",int.class).invoke(null,n),after.loadClass("Sample").getMethod("run",int.class).invoke(null,n));
            }
        }
    }
    @Test void envelopesDoNotProtectNearbyCalculationsAndNamesAloneHaveNoAuthority() throws Exception {
        String source="public class Sample {public static int run(int n){int sum=0;for(int i=0;i<=n;i+=3)sum+=1;return sum;}}";
        String generated=apply(source,analyze(source,mode(MathCleanUpOptions.ExplanationMode.NONE)));
        String nearby=generated.replace("return sum;","int extra=(n+1)-(n-1);return sum+extra;");
        var result=analyze(nearby,options());assertTrue(result.changed(),result.diagnostics().toString());
        var range=JavaRuntimeIterationTemplates.ranges(JavaEarlyExitGuardFactsTest.parse(nearby),n->{}).getFirst();
        assertTrue(result.replacements().stream().noneMatch(r->r.offset()<range.getOffset()+range.getLength() && range.getOffset()<r.offset()+r.length()));
        assertFalse(JavaEarlyExitGuardFactsTest.analyze(JavaEarlyExitGuardFactsTest.parse(generated),generated,options(),null,range.getOffset(),range.getLength()).changed());
        String named="public class Sample {public static int run(int n){/* Verified runtime */int $series_start=(n+1)-(n-1);return $series_start;}}";
        assertTrue(JavaRuntimeIterationTemplates.ranges(JavaEarlyExitGuardFactsTest.parse(named),n->{}).isEmpty());
        assertTrue(analyze(named,options()).changed());
    }
    @Test void replayRejectsCommentAndModeTamperingAndRenderingHonorsBudget() throws Exception {
        String source="public class Sample {public static int run(int n){int sum=0;for(int i=0;i<n;i++)sum+=1;return sum;}}";
        var options=mode(MathCleanUpOptions.ExplanationMode.ALL);var result=analyze(source,options);
        var receipt=result.evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow();
        String generated=apply(source,result);var before=JavaEarlyExitGuardFactsTest.parse(source);var after=JavaEarlyExitGuardFactsTest.parse(generated);
        assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationOptimizer.verify(before,after,source,generated,receipt,result.replacements(),mode(MathCleanUpOptions.ExplanationMode.NONE),500000,()->false));
        String changed=generated.replace("modulo 2^32","exact integers");
        assertNotEquals(generated,changed);
        String replacement=receipt.replacement().replace("modulo 2^32","exact integers");
        var plan=new JavaSourceRewritePlan("Source.java",JavaFiniteSumOptimizer.hash(source),source.length(),receipt.offset(),receipt.length(),List.of(),List.of(),java.util.Map.of(),
                List.of(new JavaSourceRewritePlan.Change(receipt.offset(),receipt.length(),JavaFiniteSumOptimizer.hash(receipt.original()),replacement)));
        var forged=new MathematicalAnalysis.VerifiedRegion(receipt.offset(),receipt.length(),null,receipt.cost(),receipt.original(),replacement,null,plan,null,null,receipt.runtimeIteration());
        var edit=new MathematicalAnalysis.Replacement(receipt.offset(),receipt.length(),replacement,"tampered explanation");
        assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationOptimizer.verify(before,JavaEarlyExitGuardFactsTest.parse(changed),source,changed,forged,List.of(edit),options,500000,()->false));
        var context=JavaRuntimeIterationAnalysis.inspect(before,org.eclipse.jdt.core.dom.NodeFinder.perform(before,receipt.offset(),receipt.length()),new JavaLoopInvariantAnalysis.Work(100000,()->false));
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->MathExplanation.renderRuntime(context,receipt.runtimeIteration(),new JavaLoopInvariantAnalysis.Work(20,()->false)));
        assertThrows(JavaLoopInvariantAnalysis.Limit.class,()->MathExplanation.renderRuntime(context,receipt.runtimeIteration(),new JavaLoopInvariantAnalysis.Work(100000,()->true)));
        assertThrows(IllegalArgumentException.class,()->JavaRuntimeIterationTemplates.ranges(after,n->{throw new IllegalArgumentException("budget");}));
    }
    @Test void checkedFamiliesDescribeOnlyTheirActualGuardsAndRemainStable() throws Exception {
        for(String loop:List.of("for(int i=0;i<n;i++)sum*=3;",
                "for(int i=0;i<n;i++)for(int j=0;j<=i;j++)sum+=j*j;",
                "for(int i=0;i<n;i++){if(i%2==0)sum+=i;else sum+=2*i;}",
                "for(int i=0;i<n;i++){if(i<100)sum+=i;else sum+=2*i;}")) {
            String source="public class Sample {public static int run(int n){int sum=0;"+loop+"return sum;}}";
            var result=analyze(source,mode(MathCleanUpOptions.ExplanationMode.ALL));
            assertTrue(result.evidence().stream().anyMatch(e->e.runtimeIteration()!=null),result.diagnostics().toString());
            String generated=apply(source,result);
            assertTrue(generated.contains("modulo 2^32"),generated);
            assertEquals(loop.contains("i%2"),generated.contains("// Periodic fast path"));
            assertEquals(loop.contains("int j"),generated.contains("// Inner counts"));
            assertFalse(generated.contains("// BigInteger inputs"));
            assertEquals(generated,apply(generated,analyze(generated,mode(MathCleanUpOptions.ExplanationMode.ALL))));
        }
        String exact="public class Sample {public static int run(int n){java.math.BigInteger sum=java.math.BigInteger.ONE;for(int i=0;i<n;i++)sum=sum.multiply(java.math.BigInteger.valueOf(2));return sum.bitLength();}}";
        String generated=apply(exact,analyze(exact,mode(MathCleanUpOptions.ExplanationMode.ALL)));
        assertTrue(generated.contains("exact BigInteger values") && generated.contains("// BigInteger inputs") && generated.contains("// The matrix growth"),generated);
        assertEquals(generated,apply(generated,analyze(generated,mode(MathCleanUpOptions.ExplanationMode.ALL))));
    }
}
