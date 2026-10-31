/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.iteration.*;

class JavaSeriesFinalReviewTest extends JavaContextTestSupport {
    private static final String CLASSES="public static class J {public M math; public L lang;} public static class M {public java.math.BigInteger BigInteger;} public static class L {public java.lang.Math Math;} ";
    private static String source(boolean field) {
        return "public class Sample {"+CLASSES+(field?"public static J java;":"")
                +"public static int run(int n"+(field?"":",J java")+"){int sum=0;for(int i=1;i<=n;i++)sum+=i;return sum;}}";
    }
    private static String uncheckedTemplate(String source) {
        var ast=JavaEarlyExitGuardFactsTest.parse(source);int start=source.indexOf("for("),end=source.indexOf("return sum");
        var work=new JavaLoopInvariantAnalysis.Work(1000000,()->false);
        var context=JavaRuntimeIterationAnalysis.inspect(ast,NodeFinder.perform(ast,start,end-start),work);
        var plans=context.sums().stream().map(model->new PolynomialSumOptimizer().search(model,options().optimizationBudget(100000),()->false).plan().orElseThrow()).toList();
        var linear=context.linear()==null?null:new LinearIterationOptimizer().search(context.linear(),options().optimizationBudget(100000),()->false).candidate().orElseThrow();
        var proof=new RuntimeIterationEvidence(JavaFiniteSumOptimizer.hash(source),context.bindings(),plans,linear,null);
        return source.substring(0,start)+JavaRuntimeIterationEmitter.emit(ast,source,context,proof,work)+source.substring(end);
    }
    private static List<MathematicalAnalysis.Replacement> directRuntime(String source) {
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();
        JavaRuntimeIterationOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(source),source,options(),new NullProgressMonitor(),-1,0,Map.of(),null,edits,new ArrayList<>(),new ArrayList<>(),1000000);
        return edits;
    }
    @Test void evaluatedJdkQualifierMustNotIntroduceNullDereference() throws Exception {
        for(boolean field:List.of(false,true)) {
            String source=source(field),unsafe=uncheckedTemplate(source);
            try(var before=compile(source);var oldTemplate=compile(unsafe)) {
                for(var loader:List.of(before,oldTemplate)) {
                    var cls=loader.loadClass("Sample");
                    var method=field?cls.getMethod("run",int.class):cls.getMethod("run",int.class,loader.loadClass("Sample$J"));
                    for(int n:new int[]{0,1,50000}) {
                        Object[] args=field?new Object[]{n}:new Object[]{n,null};
                        if(loader==before)assertEquals(n==50000?1250025000:n,method.invoke(null,args));
                        else assertInstanceOf(NullPointerException.class,assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,args)).getCause());
                    }
                }
            }
            assertTrue(directRuntime(source).isEmpty(),"Expression qualifiers must bind to package/type names");
            var result=analyze(source);assertTrue(result.evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
            var document=new Document(source);result.newEdit().apply(document);
            try(var loader=compile(document.get())) {
                var cls=loader.loadClass("Sample");var method=field?cls.getMethod("run",int.class):cls.getMethod("run",int.class,loader.loadClass("Sample$J"));
                assertEquals(1250025000,method.invoke(null,field?new Object[]{50000}:new Object[]{50000,null}));
            }
        }
    }
    @Test void ordinaryNamesStillPermitQualifiedJdkAccess() throws Exception {
        String source=source(false).replace("J java)","J ordinary)");
        assertFalse(directRuntime(source).isEmpty());String generated=changed(source);
        try(var loader=compile(generated)) {
            assertEquals(1250025000,loader.loadClass("Sample").getMethod("run",int.class,loader.loadClass("Sample$J")).invoke(null,50000,null));
        }
    }
    @Test void correctJdkMemberOwnerIsInsufficientForMathAndConstantReceivers() {
        String source="import java.math.BigInteger;public class Sample {"+CLASSES+"public static int run(int n,J java){BigInteger sum=BigInteger.ONE;for(int i=0;i<n;i++)sum=sum.multiply(BigInteger.valueOf(2));return sum.bitLength();}}";
        int[] staticMethods={0},staticConstants={0},mathMethods={0};
        JavaEarlyExitGuardFactsTest.parse(uncheckedTemplate(source)).accept(new ASTVisitor() {
            @Override public boolean visit(MethodInvocation call) {
                if(call.getExpression() instanceof Name name && Set.of("java.math.BigInteger","java.lang.Math").contains(name.getFullyQualifiedName())) {
                    assertInstanceOf(IVariableBinding.class,name.resolveBinding());
                    assertEquals(name.getFullyQualifiedName(),call.resolveMethodBinding().getDeclaringClass().getQualifiedName());
                    staticMethods[0]++;if(name.getFullyQualifiedName().equals("java.lang.Math"))mathMethods[0]++;
                }
                return true;
            }
            @Override public boolean visit(QualifiedName name) {
                if(name.getFullyQualifiedName().equals("java.math.BigInteger.ONE")) {
                    assertInstanceOf(IVariableBinding.class,name.getQualifier().resolveBinding());
                    assertEquals("java.math.BigInteger",((IVariableBinding)name.resolveBinding()).getDeclaringClass().getQualifiedName());staticConstants[0]++;
                }
                return true;
            }
        });
        assertTrue(staticMethods[0]>0);assertTrue(staticConstants[0]>0);assertTrue(mathMethods[0]>0);
        assertTrue(directRuntime(source).isEmpty(),"Math and BigInteger static receivers may evaluate source fields despite JDK member bindings");
        assertTrue(analyze(source).evidence().stream().noneMatch(e->e.runtimeIteration()!=null));
    }
    private static MathCleanUpOptions oneSecond() {
        var config=new HashMap<>(options().toMap());config.put(MathCleanUpOptions.TIMEOUT_SECONDS,"1");return MathCleanUpOptions.parse(config,17);
    }
    private static final String FINITE="public class Sample {public static int run(int n){int a=n,b=n;for(int i=0;i<100;i++){a+=i;b+=i*i;}return a+b;}}";
    private record Receipt(String source,String generated,CompilationUnit before,CompilationUnit after,MathematicalAnalysis.VerifiedRegion proof,List<MathematicalAnalysis.Replacement> edits) { }
    private static Receipt finiteReceipt() {
        var before=JavaEarlyExitGuardFactsTest.parse(FINITE);var edits=new ArrayList<MathematicalAnalysis.Replacement>();var proofs=new ArrayList<MathematicalAnalysis.VerifiedRegion>();
        JavaFiniteSumOptimizer.optimize(before,FINITE,options(),new NullProgressMonitor(),-1,0,Map.of(),null,edits,proofs,new ArrayList<>(),1000000);
        var proof=proofs.getFirst();assertEquals(1,proof.finiteSum().additional().size());
        String generated=FINITE.substring(0,proof.offset())+proof.replacement()+FINITE.substring(proof.offset()+proof.length());
        return new Receipt(FINITE,generated,before,JavaEarlyExitGuardFactsTest.parse(generated),proof,edits);
    }
    /** Controlled time injection at child boundaries; no source line dependency or elapsed-time assertion. */
    private static final class SlowChildren implements CancellationToken {
        private boolean inside;int delayed;
        @Override public boolean isCancelled() {
            boolean now=StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals(FiniteIterationVerifier.class.getName())&&f.getMethodName().equals("verify")));
            if(now&&!inside) {inside=true;delayed++;try {Thread.sleep(650);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return true;}}
            else if(!now)inside=false;
            return false;
        }
    }
    @Test void constantSourceReplaySharesDeadlineAcrossChildren() {
        var r=finiteReceipt();var options=oneSecond();
        assertDoesNotThrow(()->JavaFiniteSumOptimizer.verify(r.before(),r.after(),r.source(),r.generated(),r.proof(),r.edits(),options,100000,()->false));
        assertThrows(RuntimeException.class,()->JavaFiniteSumOptimizer.verify(r.before(),r.after(),r.source(),r.generated(),r.proof(),r.edits(),options,100000,()->true));
        var slow=new SlowChildren();
        assertThrows(RuntimeException.class,()->JavaFiniteSumOptimizer.verify(r.before(),r.after(),r.source(),r.generated(),r.proof(),r.edits(),options,100000,slow));
        assertEquals(2,slow.delayed,"The first child completes before the later child crosses the shared deadline");
    }
    @Test void constantOptimizationSharesDeadlineAcrossOutputsAndImmediateReplay() {
        var slow=new SlowChildren();var monitor=new NullProgressMonitor(){@Override public boolean isCanceled(){return slow.isCancelled();}};
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();
        JavaFiniteSumOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(FINITE),FINITE,oneSecond(),monitor,-1,0,Map.of(),null,edits,new ArrayList<>(),new ArrayList<>(),1000000);
        assertEquals(2,slow.delayed);assertTrue(edits.isEmpty(),"Expired aggregate must not publish a candidate");
    }
    @Test void runtimeReplayDeadlineIncludesAdmissionBeforeItsChildProofs() {
        String source="public class Sample {public static int run(int n){int a=0,b=1;for(int i=0;i<n;i++){a+=i;b+=i*i;}return a+b;}}";
        var result=analyze(source);var receipt=result.evidence().stream().filter(e->e.runtimeIteration()!=null).findFirst().orElseThrow();
        assertEquals(2,receipt.runtimeIteration().sums().size());
        String generated=source.substring(0,receipt.offset())+receipt.replacement()+source.substring(receipt.offset()+receipt.length());
        var before=JavaEarlyExitGuardFactsTest.parse(source);var after=JavaEarlyExitGuardFactsTest.parse(generated);
        assertDoesNotThrow(()->JavaRuntimeIterationOptimizer.verify(before,after,source,generated,receipt,result.replacements(),oneSecond(),500000,()->false));
        var delayed=new HashSet<String>();
        CancellationToken slow=()->{
            var phase=StackWalker.getInstance().walk(frames->frames.map(StackWalker.StackFrame::getClassName)
                    .filter(name->name.equals(JavaRuntimeIterationAnalysis.class.getName())||name.equals(PolynomialSumVerifier.class.getName())).findFirst());
            if(phase.isPresent()&&delayed.add(phase.orElseThrow()))try {Thread.sleep(650);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return true;}
            return false;
        };
        assertThrows(RuntimeException.class,()->JavaRuntimeIterationOptimizer.verify(before,after,source,generated,receipt,result.replacements(),oneSecond(),500000,slow));
        assertEquals(2,delayed.size());
    }
}
