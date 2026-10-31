/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaBranchExpressionTest extends JavaContextTestSupport {
    @Test void conditionalArmsStayUnderTheirOriginalCondition() throws Exception {
        String source = "public class Sample { static int checks; static boolean test(int x){checks++;return x>0;}"
                + " public static long run(int x){ checks=0; int y=test(x) ? (x+1)-1 : (x-1)+1; return y+checks; }}";
        String generated = changed(source);
        assertTrue(generated.contains("test(x) ?"), generated);
        equivalent(source, generated);
    }
    @Test void conditionalNarrowingAndOverloadSelectionArePreserved() throws Exception {
        String source = "public class Sample { static long pick(byte x){return 1;} static long pick(int x){return 2;}"
                + " public static long run(int n){byte x=(byte)n; return pick(n>0 ? (byte)((x+1)-1) : (byte)7); }}";
        equivalent(source, changed(source, "(byte)((x+1)-1)"));
    }
    @Test void makingAnArmConstantCannotSilentlyNarrowTheConditionalAndChangeAnOverload() {
        String source = "class Sample { static int pick(byte x){return 1;} static int pick(int x){return 2;}"
                + " int run(int n){return pick(n>0 ? n-n : (byte)7);}}";
        var analysis = analyze(source);
        assertFalse(analysis.changed(), analysis.diagnostics().toString());
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.message().contains("EXPRESSION_CONTEXT_TYPE_CHANGED")),
                analysis.diagnostics().toString());
    }
    @Test void intArmIsNotWidenedBeforeItsArithmeticExecutes() throws Exception {
        String source = "public class Sample { public static long run(int x){return x>0 ? (x+1)-1 : 0L;}}";
        equivalent(source, changed(source, "(x+1)-1"));
    }
    @Test void switchYieldsRetainTheSelectorAndAllArms() throws Exception {
        String source = "public class Sample { public static long run(int x){return switch(x){"
                + " case 1 -> (x+1)-1; case 2 -> { /* keep */ yield (x-1)+1; } default -> (x+2)-2; }; }}";
        String generated = changed(source);
        assertTrue(generated.contains("switch(x)"), generated);
        assertTrue(generated.contains("/* keep */"), generated);
        equivalent(source, generated);
    }
    @ParameterizedTest @ValueSource(strings={
            "if(x>0) return (x+1)-1; else return (x-1)+1;",
            "while(x>0) return (x+1)-1; return (x-1)+1;",
            "for(int i=0;i<1;i++) return (x+1)-1; return x;",
            "do return (x+1)-1; while(x>0);"})
    void unbracedReturnsKeepTheirControlFlow(String body) throws Exception {
        String source = "public class Sample { public static long run(int x){"+body+"}}";
        equivalent(source, changed(source, "(x+1)-1"));
    }
    @Test void unbracedAssignmentRetainsItsTargetAndExecutionPoint() throws Exception {
        String source = "public class Sample { public static long run(int x){int y=0;"
                + " if(x>0) y=(x+1)-1; return y; }}";
        equivalent(source, changed(source, "(x+1)-1"));
    }
    @Test void armCommentsAndConstantOnlyExpressionsRemainUntouched() {
        String source = "class Sample { int run(int x){return x>0 ? (x /* keep */ +1)-1 : (2+1)-1; }}";
        assertFalse(analyze(source).changed());
    }
    @Test void divisionOnAnUnchosenArmNeverExecutes() throws Exception {
        String source = "public class Sample { public static long run(int x){ return x==0 ? (x+1)-1 : 20/x; }}";
        equivalent(source, changed(source));
    }
}
