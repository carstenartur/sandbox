/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueRuntimeTest {
    @TempDir Path directory;
    @Test void explicitRuntimeRoundTripsWithoutInferringItFromSourceLevel() {
        for(int level:new int[]{17,25}) {
            var defaults=MathCleanUpOptions.defaults(level);
            assertEquals(MathCleanUpOptions.ValueRuntime.UNSPECIFIED,defaults.valueRuntime());
            assertEquals(defaults,MathCleanUpOptions.parse(defaults.toMap(),level));
        }
        var qualified=JavaValueReuseTest.options();assertEquals(17,qualified.targetJava());
        assertEquals(qualified,MathCleanUpOptions.parse(qualified.toMap(),17));
        var unknown=new HashMap<>(qualified.toMap());unknown.put(MathCleanUpOptions.VALUE_RUNTIME,"ANY_JAVA_25");
        assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(unknown,17));
        assertNotEquals(qualified.toMap(),qualified.withValueRuntime(MathCleanUpOptions.ValueRuntime.UNSPECIFIED).toMap());
    }
    @Test void unspecifiedRuntimeRejectsObjectReuse() throws Exception {
        String source=JavaValueReuseTest.fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueCallTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertFalse(generated.contains("Mathematics value reuse"));
        assertTrue(result.diagnostics().stream().anyMatch(item->item.message().equals("VALUE_RUNTIME_UNSPECIFIED")));
    }
    @Test void unspecifiedRuntimeDoesNotSpendTheOtherFamiliesWorkAllowance() throws Exception {
        String source=JavaValueReuseTest.fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var options=JavaValueCallTest.options();var work=new JavaValueSourceSummary.Work(1,options,()->false);
        var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        JavaValueReuseOptimizer.optimize(helper.parse(source),source,options,null,-1,0,Map.of(),helper.environment(),
            new ArrayList<>(),new ArrayList<>(),diagnostics,work,new MathematicalJavadoc());
        assertEquals(0,work.used,"A disabled runtime-qualified family must not scan the entire source against other families' allowance");
        assertTrue(diagnostics.stream().anyMatch(item->item.message().equals("VALUE_RUNTIME_UNSPECIFIED")),diagnostics.toString());
    }
    @Test void mismatchedRuntimeReceiptRevokesMatchesAndApply() throws Exception {
        String source=JavaValueReuseTest.fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        var region=result.evidence().stream().filter(item->item.sourcePlan()!=null && item.sourcePlan().valueRuntime()!=null).findFirst().orElseThrow();
        var plan=region.sourcePlan();var runtime=plan.valueRuntime();assertTrue(runtime.current());assertFalse(runtime.factoryProofs().isEmpty());
        assertEquals(1,result.sourceDependencies().valueRuntimes().size());
        assertEquals(runtime.contract(),result.sourceDependencies().valueRuntimes().getFirst().contract());
        assertTrue(result.sourceDependencyReceipts().stream().noneMatch(receipt->receipt.identity().startsWith("runtime:")));
        var changed=new ValueRuntimeEvidence(runtime.contract(),"0".repeat(64),runtime.auditedSourceSha256(),runtime.factoryProofs());
        assertFalse(changed.current());
        var badPlan=new JavaSourceRewritePlan(plan.unitName(),plan.sourceSha256(),plan.sourceLength(),plan.start(),plan.length(),plan.nodes(),plan.pinned(),plan.occurrenceOrigins(),plan.changes(),changed);
        var badRegion=new MathematicalAnalysis.VerifiedRegion(region.offset(),region.length(),region.proof(),region.cost(),region.original(),region.replacement(),region.explanation(),badPlan,
            region.scheduling(),region.finiteSum(),region.runtimeIteration(),region.sourceNumericBranch(),region.fixedPower());
        var evidence=new ArrayList<>(result.evidence());evidence.set(evidence.indexOf(region),badRegion);
        var drift=new MathematicalAnalysis.Analysis(result.replacements(),result.diagnostics(),result.sourceDigest(),result.compilerOptions(),evidence,result.documentation(),result.sourceDependencies());
        assertFalse(drift.matches(source,Map.of()));assertThrows(IllegalArgumentException.class,drift::newEdit);
    }
}
