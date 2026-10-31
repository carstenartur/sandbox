/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import de.regelsuche.ast.*;
import de.regelsuche.ast.iteration.*;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.iteration.*;
import de.regelsuche.symbol.SymbolId;

class JavaFiniteSumBudgetTest extends JavaContextTestSupport {
    private static final OptimizationBudget AMPLE=new OptimizationBudget(200000,256,64,20000);

    @Test void evidenceReportsTheWorkAndCostOfEveryProof() {
        var proofs=List.of(proof(1),proof(4),proof(2));
        var evidence=evidence(proofs);
        long expectedWork=proofs.stream().mapToLong(JavaFiniteSumBudgetTest::verificationWork).sum();
        var checked=evidence.verify(AMPLE,()->false);
        assertTrue(checked.verified());
        assertEquals(expectedWork,checked.work(),"The public receipt must report aggregate verification work");
        assertEquals(evidence.totalCost(),checked.cost());
        assertTrue(checked.work()<=AMPLE.maximumWork());
        assertEquals(expectedWork,evidence.verify(budget(expectedWork),()->false).work());
        assertThrows(IllegalArgumentException.class,()->evidence.verify(budget(expectedWork-1),()->false));
    }

    @Test void tightEvidenceAllowanceCannotBeReusedForEveryExpensiveOutput() {
        var proof=proof(4);
        long single=verificationWork(proof);
        var evidence=evidence(java.util.Collections.nCopies(8,proof));
        var tight=budget(single+100);
        assertThrows(IllegalArgumentException.class,()->evidence.verify(tight,()->false));
        assertTrue(evidence.verify(budget(8*single+1000),()->false).work()<=8*single+1000);
    }

    @Test void anExpensiveLaterOutputCannotStartWithTheOriginalAllowanceAgain() {
        var cheap=proof(1);var expensive=proof(6);
        long first=verificationWork(cheap),later=verificationWork(expensive);
        assertTrue(later>first);
        var allowance=budget(later+first/2);
        assertTrue(first+later>allowance.maximumWork());
        assertThrows(IllegalArgumentException.class,()->evidence(List.of(cheap,expensive)).verify(allowance,()->false));
    }

    @Test void cancellationBetweenProofsStopsBeforeAnyLaterProofWork() {
        var first=proof(1);var later=proof(6);
        int[] firstPolls={0};
        var standalone=new FiniteIterationVerifier().verify(first.source(),first.emitted(),first.certificate(),AMPLE,()->{firstPolls[0]++;return false;});
        assertTrue(standalone.verified());
        int[] polls={0};
        assertThrows(IllegalArgumentException.class,()->evidence(List.of(first,later)).verify(AMPLE,()->++polls[0]>firstPolls[0]));
        assertEquals(firstPolls[0]+1,polls[0]);
    }

    @Test void eightOutputsCannotAuthorizeEightIndependentSearchAndProofShares() {
        String source=multipleOutputs();
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();
        var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        long remaining=JavaFiniteSumOptimizer.optimize(JavaEarlyExitGuardFactsTest.parse(source),source,options(),new org.eclipse.core.runtime.NullProgressMonitor(),-1,0,
                java.util.Map.of(),null,edits,new ArrayList<>(),diagnostics,300000);
        assertTrue(edits.isEmpty(),"Eight expensive outputs must fit one aggregate search/check allocation");
        assertTrue(remaining>=0 && remaining<=300000);
    }

    @Test void sourceReplaySharesItsAllowanceWithAllNumericalProofs() {
        String source=multipleOutputs();
        var before=JavaEarlyExitGuardFactsTest.parse(source);
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();
        var receipts=new ArrayList<MathematicalAnalysis.VerifiedRegion>();
        var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        // Obtain a valid source-qualified receipt independently of the tight replay allowance below.
        JavaFiniteSumOptimizer.optimize(before,source,options(),new org.eclipse.core.runtime.NullProgressMonitor(),-1,0,java.util.Map.of(),null,edits,receipts,diagnostics,1000000);
        assertEquals(1,receipts.size(),diagnostics.toString());
        var receipt=receipts.getFirst();
        String generated=source.substring(0,receipt.offset())+receipt.replacement()+source.substring(receipt.offset()+receipt.length());
        var after=JavaEarlyExitGuardFactsTest.parse(generated);
        var admission=new JavaLoopInvariantAnalysis.Work(50000,()->false);
        JavaFiniteSumAnalysis.inspect(before,org.eclipse.jdt.core.dom.NodeFinder.perform(before,receipt.offset(),receipt.length()),admission);
        var numerical=new ArrayList<FiniteSumEvidence.ReductionProof>();
        var finite=receipt.finiteSum();
        numerical.add(new FiniteSumEvidence.ReductionProof(finite.source(),finite.certificate(),finite.emitted(),finite.bindings(),finite.checkedCost()));
        numerical.addAll(finite.additional());
        long sdkWork=numerical.stream().mapToLong(JavaFiniteSumBudgetTest::verificationWork).sum();
        long allowance=60000;
        assertTrue(admission.used+4L*(source.length()+generated.length())+sdkWork>allowance);
        JavaFiniteSumOptimizer.verify(before,after,source,generated,receipt,edits,options(),100000,()->false);
        var failure=assertThrows(RuntimeException.class,()->JavaFiniteSumOptimizer.verify(before,after,source,generated,receipt,edits,options(),allowance,()->false));
        assertTrue(failure instanceof IllegalArgumentException || failure instanceof JavaLoopInvariantAnalysis.Limit,failure.toString());
    }

    private static OptimizationBudget budget(long maximumWork) {return new OptimizationBudget(maximumWork,256,64,20000);}
    private static long verificationWork(FiniteSumEvidence.ReductionProof proof) {
        var checked=new FiniteIterationVerifier().verify(proof.source(),proof.emitted(),proof.certificate(),AMPLE,()->false);
        assertTrue(checked.verified(),checked.reason());return checked.work();
    }
    private static FiniteSumEvidence.ReductionProof proof(int power) {
        var namespace=new UUID(17,power);var index=new SymbolId(namespace,1);var state=new SymbolId(namespace,2);
        Expr term=new NumberExpr(1);
        for(int exponent=0;exponent<power;exponent++) term=new BinaryExpr(term,BinaryOperator.MUL,VariableExpr.scoped(index));
        var model=new FiniteIteration.Recurrence(new IndexRange(BigInteger.ONE,BigInteger.ONE,BigInteger.valueOf(100)),index,state,Set.of(),
                new NumberExpr(0),new BinaryExpr(VariableExpr.scoped(state),BinaryOperator.ADD,term),IntegerArithmetic.JAVA_INT);
        var candidate=new FiniteIterationOptimizer().search(model,AMPLE,()->false).candidate().orElseThrow();
        return new FiniteSumEvidence.ReductionProof(model,candidate.certificate(),candidate.result(),List.of(),candidate.cost());
    }
    private static FiniteSumEvidence evidence(List<FiniteSumEvidence.ReductionProof> proofs) {
        var first=proofs.getFirst();
        return new FiniteSumEvidence("fixture",first.source(),first.certificate(),first.emitted(),first.bindings(),first.checkedCost(),proofs.subList(1,proofs.size()));
    }
    private static String multipleOutputs() {
        return "class Sample{int run(int n){int a=n,b=n,c=n,d=n,e=n,f=n,g=n,h=n;for(int i=0;i<100;i++){"
                +"a+=i*i*i*i;b+=i*i*i*i;c+=i*i*i*i;d+=i*i*i*i;e+=i*i*i*i;f+=i*i*i*i;g+=i*i*i*i;h+=i*i*i*i;}return a^b^c^d^e^f^g^h;}}";
    }
}
