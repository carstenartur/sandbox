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

class JavaFiniteSumDeadlineTest extends JavaContextTestSupport {
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
}
