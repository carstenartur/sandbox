/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFixedPowerSourceTest {
    @TempDir Path directory;
    static String source(){return JavaValueAlgebraProofTest.source(JavaImmutableValueTest.multiplication(),"return new Calculation(amber.multiply(amber).subtract(blue.multiply(blue)).mod(P),amber.multiply(blue).add(amber.multiply(blue)).mod(P));","BigInteger.ONE,BigInteger.ZERO")
        .replace("public BigInteger[] parts()","public Calculation raise(BigInteger n){if(n.signum()<0){throw new IllegalArgumentException();} Calculation r=ONE; Calculation b=this; for(int i=0;i<n.bitLength();++i){if(n.testBit(i)){r=r.mul(b);}b=b.square();}return r;} public BigInteger[] parts()");}
    JavaFixedPowerSource.Loop loop(String source) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaValueAlgebraProofTest.options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)){
            var type=((TypeDeclaration)ast.types().getFirst()).resolveBinding();var power=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("raise")).findFirst().orElseThrow();
            return JavaFixedPowerSource.recognize(ast,power,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
        }
    }
    @Test void recognizesBindingsAndNegativePrefixInTheActualLoopShape(){assertNotNull(assertDoesNotThrow(()->loop(source())));}
    @Test void rejectsChangedControlOrderOrAliasing() throws Exception {
        for(String changed:List.of(source().replace("i<n.bitLength()","i<=n.bitLength()"),source().replace("r=r.mul(b)","r=b.mul(b)"),source().replace("b=b.square()","b=r.square()"),source().replace("Calculation b=this","Calculation b=ONE"),source().replace("i=0","i=1")))
            assertThrows(IllegalArgumentException.class,()->loop(changed));
    }
}
