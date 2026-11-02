/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueInverseSourceTest {
    @TempDir Path directory;
    static String fixture() {
        return JavaValueCallTest.fixture().replace("public Leaf combine(Leaf other)",
            "public boolean rejected(){return amber.signum()==0 && blue.signum()==0;}"
            +"public Leaf reciprocal(){if(rejected()){throw new ArithmeticException(\"zero\");}"
            +"BigInteger n=amber.multiply(amber).add(blue.multiply(blue)).mod(P);BigInteger r=n.modInverse(P);"
            +"return new Leaf(amber.multiply(r).mod(P),P.subtract(blue).multiply(r).mod(P));}public Leaf combine(Leaf other)");
    }
    @Test void projectsActualInverseContinuationWithoutAssumingItsAlgebraicLaw() throws Exception {
        var result=inspect(fixture());
        assertEquals(NumericOperation.MOD_INVERSE,JavaExpressions.operationOf(result.inverse().expression()).orElseThrow());
        var inputs=Map.<String,Object>of("valueInput0",BigInteger.ONE,"valueInput1",BigInteger.TWO);
        var values=ComputationOptimizer.prepare(result.summary().plan()).execute(inputs);
        assertEquals(BigInteger.valueOf(81),values.get("component0"));assertEquals(BigInteger.valueOf(40),values.get("component1"));
        assertNotNull(result.rejection());
    }
    @Test void ordinaryNamedLookalikeAndTransitiveEffectDoNotSupplyAnInverseReceipt() throws Exception {
        assertThrows(IllegalArgumentException.class,()->inspect(fixture().replace("n.modInverse(P)","n.add(P)")));
        assertThrows(IllegalArgumentException.class,()->inspect(fixture().replace("return amber.signum()==0", "System.out.print(amber);return amber.signum()==0")));
    }
    private JavaValueInverseSource.Projection inspect(String source) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaValueCallTest.options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var type=(TypeDeclaration)ast.types().get(1);var method=Arrays.stream(type.getMethods()).filter(m->m.getName().getIdentifier().equals("reciprocal")).findFirst().orElseThrow();
            var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);var session=new JavaValueSourceSummary.Session(ast,options,work);
            return JavaValueInverseSource.project(ast,session,method.resolveBinding(),session.symbolic(session.shape(type.resolveBinding()),"operand"),work);
        }
    }
}
