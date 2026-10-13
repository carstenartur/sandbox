/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class SourceSliceBudgetBoundaryTest {
    @Test void boundedSearchNeverDuplicatesOriginalFallbackRegionsOrReturnsOverlappingEdits() throws Exception {
        String expression="a*b";
        for(int i=0;i<9;i++) expression="("+expression+"+a*b)";
        String source="class Sample { static void audit(){} int f(int a,int b,int c){int left="
                +expression+"; audit(); int right=a*c; return left+right;}}";
        for(String limit : new String[]{"1000", "5000", "20000"}) {
            var values=new java.util.HashMap<>(SourceComputationSliceTest.options(false).toMap());
            values.put(MathCleanUpOptions.WORK_BUDGET,limit);
            var result=MathematicalAnalysis.analyze(SourceComputationSliceTest.parse(source),source,
                    MathCleanUpOptions.parse(values,17),null,-1,0);
            assertFalse(result.diagnostics().stream().anyMatch(d->d.code().equals("COMBINED_EDITS_INVALID")),result.diagnostics().toString());
            var doc=new Document(source);var undo=result.newEdit().apply(doc,org.eclipse.text.edits.TextEdit.CREATE_UNDO);
            SourceComputationSliceTest.parse(doc.get());undo.apply(doc);assertEquals(source,doc.get());
            var accepted=result.diagnostics().stream().filter(d->d.code().equals("VERIFIED_CANDIDATE")).toList();
            assertEquals(accepted.size(),accepted.stream().map(d->d.offset()+":"+d.length()).distinct().count());
        }
    }
}
