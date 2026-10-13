/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class SourceComputationSliceBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings={"int", "long", "final int"})
    void connectsIntegralValuesWithoutLosingExplicitFinalOrWidth(String parameterType) throws Exception {
        String type=parameterType.replace("final ", "");
        String source="class Sample { static void audit(){} static " + type + " f(" + parameterType
                + " a," + parameterType + " b," + parameterType + " c) {" + parameterType
                + " left=a*b; audit(); " + parameterType + " right=a*c; return left+right; }}";
        var analysis=SourceComputationSliceTest.analyze(source,false);
        assertTrue(analysis.evidence().stream().anyMatch(item->item.sourcePlan()!=null),analysis.diagnostics().toString());
        var doc=new Document(source);var undo=analysis.newEdit().apply(doc,TextEdit.CREATE_UNDO);
        SourceComputationSliceTest.parse(doc.get());undo.apply(doc);assertEquals(source,doc.get());
    }

    @Test void duplicateMathematicalValuesRetainDistinctSourceOccurrences() {
        String source="class Sample { static void audit(){} int f(int x,int y){int a=x*y; audit(); int b=x*y; return a+b;} }";
        var result=SourceComputationSliceTest.analyze(source,false);
        var plan=result.evidence().getFirst().sourcePlan();assertNotNull(plan);
        assertEquals(3,plan.occurrenceOrigins().size());
        assertEquals(3,plan.occurrenceOrigins().keySet().stream().distinct().count());
        assertTrue(plan.occurrenceOrigins().values().stream().distinct().count()>=2);
    }

    @Test void selectionOnlyAnalysisDoesNotReachAcrossUnselectedCall() {
        String source="class Sample { static void audit(){} int f(int a,int b,int c){int left=a*b; audit(); int right=a*c; return left+right;} }";
        var result=MathematicalAnalysis.analyze(SourceComputationSliceTest.parse(source),source,
                SourceComputationSliceTest.options(false),null,source.indexOf("int left"),13);
        assertTrue(result.evidence().stream().allMatch(item->item.sourcePlan()==null));
    }

    @Test void observedOutputRemainsLiveAfterTheConnectedGroup() throws Exception {
        String source="class Sample { static void audit(){} static void consume(int x){} int f(int a,int b,int c){"
                + "int left=a*b; audit(); int right=a*c; int sum=left+right; consume(left); return sum;} }";
        var result=SourceComputationSliceTest.analyze(source,false);
        var doc=new Document(source);result.newEdit().apply(doc);SourceComputationSliceTest.parse(doc.get());
        assertTrue(doc.get().contains("int left"));assertTrue(doc.get().contains("consume(left);"));
    }

    @Test void unsupportedCommentRewriteRetainsUsefulOrdinaryCandidates() throws Exception {
        String source="class Sample { static void audit(){} int f(int a,int b,int c){"
                + "int left=(a & b) | (~a & c); audit(); return left + /* retained */ 0;} }";
        var result=SourceComputationSliceTest.analyze(source,false);
        assertTrue(result.changed(),result.diagnostics().toString());
        var doc=new Document(source);var undo=result.newEdit().apply(doc,TextEdit.CREATE_UNDO);
        assertTrue(doc.get().contains("/* retained */"));SourceComputationSliceTest.parse(doc.get());undo.apply(doc);assertEquals(source,doc.get());
    }

    @Test void originalRegionsRetryAfterUnchangedJointCandidate() {
        String source="class Sample { static void audit(){} int f(int a,int b,int c){int left=a*b; audit(); return left+c;} }";
        var result=SourceComputationSliceTest.analyze(source,false);
        assertTrue(result.diagnostics().stream().anyMatch(d->d.code().equals("SOURCE_SLICE_LOCAL_RETRY")),result.diagnostics().toString());
        assertFalse(result.diagnostics().stream().anyMatch(d->d.code().equals("COMBINED_EDITS_INVALID")));
    }

    @Test void severalMethodsWithTheSameLocalNamesNeverShareValues() throws Exception {
        String method="int %s(int a,int b,int c){int left=a*b; audit(); int right=a*c; return left+right;}";
        String source="class Sample { static void audit(){} "+method.formatted("f")+method.formatted("g")+"}";
        var result=SourceComputationSliceTest.analyze(source,false);
        assertEquals(2,result.evidence().stream().filter(item->item.sourcePlan()!=null).count(),result.diagnostics().toString());
        var doc=new Document(source);var undo=result.newEdit().apply(doc,TextEdit.CREATE_UNDO);
        SourceComputationSliceTest.parse(doc.get());undo.apply(doc);assertEquals(source,doc.get());
    }
}
