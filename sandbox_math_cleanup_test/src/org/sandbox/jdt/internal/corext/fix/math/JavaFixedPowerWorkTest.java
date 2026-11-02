/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaFixedPowerWorkTest {
    @TempDir Path directory;
    private record Spent(long common, long source, long remaining) { }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void rejectedSitesChargeDiscoveryBeforeAnyAlgebraSession(boolean rejectConstant) throws Exception {
        String dependency=rejectConstant
            ? "public final class PowerData {static final java.math.BigInteger VALUE=java.math.BigInteger.ZERO;}"
            : "public final class PowerData {public PowerData raise(java.math.BigInteger n){return this;}}";
        Files.writeString(directory.resolve("PowerData.java"),dependency+"/*"+" ".repeat(16384)+"*/");
        String source="import java.math.BigInteger; public final class Calculation {static final BigInteger FIXED="
            +(rejectConstant?"PowerData.VALUE; public Calculation raise(BigInteger n){return this;} static Calculation call(Calculation value)"
                :"BigInteger.TEN; static PowerData call(PowerData value)")+"{return value.raise(FIXED);}}";
        var ordinary=inspect(source,false,rejectConstant);
        var alreadyAttached=inspect(source,true,rejectConstant);
        assertTrue(ordinary.source()>500,"The rejected site must really load the external source");
        assertEquals(alreadyAttached.source(),ordinary.source());
        assertAll(
            ()->assertEquals(alreadyAttached.common(),ordinary.common(),"Discovery must charge the same common meter without requiring a preceding session"),
            ()->assertEquals(alreadyAttached.remaining(),ordinary.remaining(),"Later passes must receive the remainder after all rejected-site source work"));
    }

    private Spent inspect(String source,boolean attached,boolean rejectConstant) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);
        var options=JavaValueAlgebraProofTest.options();var work=new JavaValueSourceSummary.Work(50000,options,()->false);
        var edits=new ArrayList<MathematicalAnalysis.Replacement>();var evidence=new ArrayList<MathematicalAnalysis.VerifiedRegion>();
        var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            if(attached)JavaSourceContext.valueWork(ast,work);
            JavaFixedPowerOptimizer.optimize(ast,source,options,null,-1,0,Map.of(),fixture.environment(),edits,evidence,diagnostics,work);
            assertTrue(edits.isEmpty());assertTrue(evidence.isEmpty());
            if(!rejectConstant)assertTrue(diagnostics.stream().anyMatch(d->d.message().contains("VALUE_FIXED_POWER_SOURCE_SHAPE")),diagnostics.toString());
            long sourceWork=context.takeWork(),remaining=work.remaining();
            assertEquals(0,context.takeWork(),"Source work is acknowledged once, as in MathematicalAnalysis");
            var result=new Spent(work.used,sourceWork,remaining);
            System.out.println("FIXED_DISCOVERY_WORK constant="+rejectConstant+" attached="+attached+" "+result);
            assertThrows(IllegalArgumentException.class,()->work.allocate(remaining+1,options));
            assertEquals(remaining,work.allocate(remaining,options).maximumWork());assertEquals(0,work.remaining());
            assertNotNull(JavaSourceContext.definition(ast,((org.eclipse.jdt.core.dom.TypeDeclaration)ast.types().getFirst()).resolveBinding()),
                "The optimizer must detach its exhausted meter before later independent source work");
            return result;
        }
    }
}
