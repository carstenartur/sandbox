/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import de.regelsuche.sdk.optimization.*;

class JavaSourceNumericBranchWorkTest {
    @Test void failedCandidateCannotHashLargeNonAstTextOutsideItsAllowance() {
        long small=remainingForFailedBranch(0),large=remainingForFailedBranch(1_000_000);
        assertTrue(small<20_000,"The small unequal branch reaches bounded numerical verification");
        assertTrue(large>39_000,"The large fingerprint must be refused before hashing or starting the failed numerical proof: "+large);
    }
    @Test void whitespaceBeforeParenthesisAndBodyConsumesOnlyAdmittedCharacterWork() {
        for(boolean beforeOpen:List.of(false,true)) for(String condition:List.of("mode>0","test()"))
            assertEquals(0,remaining(whitespaceSource(beforeOpen,condition),new NullProgressMonitor()),
                    "Long whitespace scans must stop at the shared allowance");
    }
    @Test void cancellationIsPolledDuringWhitespaceBeforeParenthesisAndBody() {
        for(boolean beforeOpen:List.of(false,true)) {
            int[] polls={0};
            var monitor=new NullProgressMonitor() {
                @Override public boolean isCanceled() { return ++polls[0]>=1000; }
            };
            long left=remaining(whitespaceSource(beforeOpen,"test()"),monitor);
            assertEquals(1000,polls[0],"Cancellation must be polled inside the character scan");
            assertTrue(left>38_000,"Cancellation must stop the scan before spending its allowance");
        }
    }
    @Test void escapedKeywordCharactersConsumeAdmittedWorkAndPollCancellation() {
        String source="class Sample { int f(int a,int b,int mode) { int value; "
                +"\\"+"u".repeat(1_000_000)+"0069f(mode>0) value=a+b; else value=a-b; return value; } }";
        assertEquals(0,remaining(source,new NullProgressMonitor()),"The raw keyword scan must stop at its allowance");
        int[] polls={0};
        var monitor=new NullProgressMonitor() {
            @Override public boolean isCanceled() { return ++polls[0]>=1000; }
        };
        assertTrue(remaining(source,monitor)>38_000,"Cancellation must stop inside the raw keyword scan");
        assertEquals(1000,polls[0]);
    }
    private static String whitespaceSource(boolean beforeOpen,String condition) {
        String gap=" ".repeat(1_000_000);
        return "class Sample { boolean test(){return true;} int f(int a,int b,int mode) { int value; if"
                +(beforeOpen?gap:"")+"("+condition+")"+(beforeOpen?"":gap)
                +" value=a+b; else value=a-b; return value; } }";
    }
    private static long remainingForFailedBranch(int padding) {
        String source="class Sample { int f(int a,int b,int mode) { int value; if(mode>0) value=a+b; else value=a-b; return value; } } /*"
                +"x".repeat(padding)+"*/";
        return remaining(source,new NullProgressMonitor());
    }
    private static long remaining(String source,NullProgressMonitor monitor) {
        var parser=ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0],new String[0],null,true); parser.setResolveBindings(true);
        var compiler=new HashMap<String,String>(); JavaCore.setComplianceOptions("17",compiler); parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null); for(var problem:ast.getProblems()) assertFalse(problem.isError(),problem.toString());
        var options=new MathCleanUpOptions(true,Set.of(NumericKind.INT),SafetyProfile.PRESERVE_JAVA,OptimizationGoal.READABILITY,
                1_000_000,20_000,false,17,List.of());
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();
        var evidence=new ArrayList<MathematicalAnalysis.VerifiedRegion>();
        long remaining=JavaSourceNumericBranches.optimize(ast,source,options,monitor,-1,0,compiler,null,
                edits,evidence,new ArrayList<>(),new ArrayList<>(),40_000);
        assertTrue(edits.isEmpty()); assertTrue(evidence.isEmpty());
        return remaining;
    }
}
