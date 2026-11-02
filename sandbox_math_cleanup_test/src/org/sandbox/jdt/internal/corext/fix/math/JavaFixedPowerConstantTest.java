/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class JavaFixedPowerConstantTest {
    @TempDir Path directory;
    static String source(){return "import java.math.BigInteger; public final class Calculation {static final BigInteger A=new BigInteger(\"d201000000010000\",16);static final BigInteger X=A.negate();static final BigInteger E;static{BigInteger n=X.subtract(BigInteger.ONE).pow(2);BigInteger[] q=n.divideAndRemainder(BigInteger.valueOf(3));if(q[1].signum()!=0){throw new IllegalStateException();}E=q[0];}}";}
    BigInteger derive(String source) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaValueAlgebraProofTest.options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)){
            var type=((TypeDeclaration)ast.types().getFirst()).resolveBinding();var field=Arrays.stream(type.getDeclaredFields()).filter(f->f.getName().equals("E")).findFirst().orElseThrow();
            return JavaFixedPowerConstant.derive(ast,field,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
        }
    }
    @Test void derivesBoundedExactDivisionFromStaticSource()throws Exception {assertEquals(new BigInteger("d201000000010000",16).negate().subtract(BigInteger.ONE).pow(2).divide(BigInteger.valueOf(3)),derive(source()));}
    @Test void refusesRemainderFailureEffectsAndUnboundedPower()throws Exception{
        for(String source:List.of(source().replace("valueOf(3)","valueOf(7)"),source().replace("E=q[0]","System.out.print(q);E=q[0]"),source().replace("pow(2)","pow(1000000000)")))assertThrows(IllegalArgumentException.class,()->derive(source));
    }
    @Test void primitiveArgumentsRequireSourceLiterals()throws Exception {
        for(String source:List.of(source().replace("pow(2)","pow(Integer.BYTES-2)"),source().replace("valueOf(3)","valueOf(Integer.BYTES-1)"),source().replace("q[0]","q[Integer.BYTES-4]"),source().replace("!=0","!=Integer.BYTES-4")))assertThrows(IllegalArgumentException.class,()->derive(source));
    }
}
