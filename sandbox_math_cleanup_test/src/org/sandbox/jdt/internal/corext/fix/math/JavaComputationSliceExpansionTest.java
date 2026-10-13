/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import de.regelsuche.sdk.optimization.CancellationToken;

class JavaComputationSliceExpansionTest {
    @Test void expandedSharingCannotEscapeTheSourceDiscoveryWorkBound() {
        var text=new StringBuilder("class Sample { void audit(){} int f(int a,int b){ int v0=a+b;");
        for(int i=1;i<20;i++) {
            if(i%5==0) text.append("audit();");
            text.append("int v").append(i).append("=v").append(i-1).append("+v").append(i-1).append(';');
        }
        text.append("return v19;} }");String source=text.toString();
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setUnitName("Sample.java");parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[0],new String[0],null,true);parser.setResolveBindings(true);
        var settings=new HashMap<String,String>();JavaCore.setComplianceOptions("17",settings);parser.setCompilerOptions(settings);
        var ast=(CompilationUnit)parser.createAST(null);
        for(var problem:ast.getProblems()) assertFalse(problem.isError(),problem.toString());
        var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP,"true");values.put(MathCleanUpOptions.KINDS,"INT");
        values.put(MathCleanUpOptions.WORK_BUDGET,"1000000");
        var options=MathCleanUpOptions.parse(values,17);
        var original=new JavaComputationExtractor().extractRuntime(ast,source,options).regions();
        assertEquals(4,original.size());
        var discovery=JavaComputationSlices.discover(ast,original,options,CancellationToken.NONE);
        assertTrue(discovery.slices().isEmpty(),"An expanded expression must be bounded before hashing a composed region");
        assertTrue(discovery.diagnostics().stream().anyMatch(d->d.code().equals("SOURCE_SLICE_LIMIT")));
        assertEquals(original,discovery.regions());assertTrue(discovery.work()<=62500);
    }
}
