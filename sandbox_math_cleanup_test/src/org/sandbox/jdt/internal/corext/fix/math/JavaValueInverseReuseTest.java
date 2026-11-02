/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueInverseReuseTest {
    @TempDir Path directory;
    static String fixture() {
        return JavaValueInverseSourceTest.fixture()
            .replace("BigInteger.valueOf(101)","new BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\")")
            .replace("return x.combine(TWIST);","Leaf powered=x.square().combine(x);Leaf weighted=x.square().combine(powered.reciprocal());return weighted.combine(x.reciprocal());")
            .replace("public Leaf reciprocal()", "public static final Leaf UNITY=new Leaf(BigInteger.ONE,BigInteger.ZERO);public Leaf square(){return combine(this);} public Leaf reciprocal()");
    }
    @Test void originalFirstInverseRemainsAndLaterBaseInverseUsesTheVerifiedUnitCertificate() throws Exception {
        String source=fixture();var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
        assertTrue(result.evidence().stream().anyMatch(item->item.valueInverse()!=null && item.valueInverse().unitRoot().verified()));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.evidence());
        assertTrue(json.length()<1_000_000,"export must remain compact");assertFalse(json.contains("unitRootRequest"));
        assertTrue(json.contains("unitRootSourceCommitment"));assertTrue(json.contains("CONDITIONAL_SUCCESSFUL_MODULAR_INVERSE"));
        var exported=new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).findValue("valueInverse");
        assertEquals(4,exported.get("algebraLaws").size());assertEquals(2,exported.get("inverseLaws").size());
        assertTrue(exported.get("retainedScalarOccurrence").isTextual());assertEquals(64,exported.get("unitBaseCommitment").asText().length());
        var execution=new JavaValueCallTest();execution.directory=directory;assertEquals(execution.execute(source),execution.execute(generated));
    }
    @Test void namedInverseWithWrongResultCannotSatisfyTheSourceContract() throws Exception {
        String source=fixture().replace("amber.multiply(r).mod(P)","amber.multiply(r).add(BigInteger.ONE).mod(P)");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        assertFalse(fixture.apply(source,result).contains("Mathematics inverse reuse"));
    }
    @Test void acceptedInverseDocumentsItsRetainedEvaluationAndDeploymentContract() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        assertTrue(result.documentation().stream().anyMatch(edit->edit.offset()==source.indexOf("public static Leaf scale(")
            && edit.replacement().contains("first successful inverse") && edit.replacement().contains("original evaluation position")
            && edit.replacement().contains("audited OpenJDK 25.0.5+8") && edit.replacement().contains("fresh")),result.documentation().toString());
    }
    @Test void repeatedApplyRetainsOneOriginalInverseEnvelope() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
        var repeated=MathematicalAnalysis.analyze(helper.parse(generated),generated,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        assertTrue(repeated.replacements().stream().noneMatch(edit->edit.replacement().contains("Mathematics inverse reuse")),repeated.diagnostics().toString());
    }
    @Test void selectedInverseBlockUsesHalfOpenSourceIntervals() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var ast=helper.parse(source);var owner=(org.eclipse.jdt.core.dom.TypeDeclaration)ast.types().getFirst();
        var body=Arrays.stream(owner.getMethods()).filter(method->method.getName().getIdentifier().equals("scale")).findFirst().orElseThrow().getBody();
        int start=body.getStartPosition(),length=body.getLength();var options=JavaValueReuseTest.options();
        for(int[] selection:new int[][]{{start-1,1},{start+length,0},{start,0},{start+length-1,1}}) {
            ast=helper.parse(source);var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);
            var replacements=new ArrayList<MathematicalAnalysis.Replacement>();var evidence=new ArrayList<MathematicalAnalysis.VerifiedRegion>();
            var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
            try(var context=JavaSourceContext.open(ast,source,helper.environment(),null,options)) {
                // Seed the verified square so cost rejection cannot mask a faulty inverse selection gate.
                JavaValueReuseOptimizer.optimize(ast,source,options,null,-1,0,Map.of(),helper.environment(),replacements,evidence,diagnostics,work,new MathematicalJavadoc());
                assertTrue(replacements.stream().anyMatch(edit->edit.replacement().contains("Mathematics value reuse")),diagnostics.toString());
                JavaValueInverseOptimizer.optimize(ast,source,options,null,selection[0],selection[1],Map.of(),helper.environment(),replacements,evidence,diagnostics,work,new MathematicalJavadoc());
            }
            boolean overlaps=selection[0]>=start && selection[0]<start+length;
            assertEquals(overlaps,evidence.stream().anyMatch(item->item.valueInverse()!=null),Arrays.toString(selection)+diagnostics);
        }
    }
    @Test void rejectedInverseDiscoveryStillChecksSharedCancellation() throws Exception {
        String source=JavaValueCallTest.fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var checks=new java.util.concurrent.atomic.AtomicInteger();var options=JavaValueReuseTest.options();
        var work=new JavaValueSourceSummary.Work(1,options,()->{checks.incrementAndGet();return true;});
        var replacements=new ArrayList<MathematicalAnalysis.Replacement>();
        JavaValueInverseOptimizer.optimize(helper.parse(source),source,options,null,-1,0,Map.of(),helper.environment(),replacements,new ArrayList<>(),new ArrayList<>(),work,new MathematicalJavadoc());
        assertTrue(checks.get()>0,"An unsuccessful candidate scan still belongs to the shared cancelled request");assertTrue(replacements.isEmpty());
    }
    @Test void staticAndInstanceInitializersRemainOutsideInverseReuse() throws Exception {
        for(String initializer:new String[]{"static ",""}) {
            String source=fixture().replace("public final class Calculation {","public final class Calculation {public static String observed;")
                .replace("public static Leaf scale(Leaf x){",initializer+"{Leaf x=Leaf.of(BigInteger.valueOf(8),BigInteger.valueOf(3));")
                .replace("return weighted.combine(x.reciprocal());","Leaf answer=weighted.combine(x.reciprocal());observed=answer.first().toString()+\":\"+answer.second().toString();");
            var execution=new JavaValueReuseTest();execution.directory=directory;String original=execution.executeInitializer(source,initializer.isEmpty());
            var helper=new JavaImmutableValueTest();helper.directory=directory;
            var result=assertDoesNotThrow(()->MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment()));
            String generated=helper.apply(source,result);assertFalse(generated.contains("Mathematics inverse reuse"));
            assertEquals(original,execution.executeInitializer(generated,initializer.isEmpty()));
        }
    }
    @Test void aDetachedInverseFactoryCannotIntroduceForeignStaticInitialization() throws Exception {
        String source=fixture().replace("static final Leaf TWIST=Leaf.of(BigInteger.ONE,BigInteger.ONE);","public static int calls;")
            .replace("public static Leaf scale(Leaf x){","public static int scale(Leaf x){")
            .replace("return weighted.combine(x.reciprocal());","Leaf answer=weighted.combine(x.reciprocal());return calls;")
            .replace("return new Leaf(a.mod(P),b.mod(P));","return new Leaf(a.mod(Foreign.P),b.mod(Foreign.P));")
            +"final class Foreign {static {Calculation.calls++;}public static final java.math.BigInteger P=new java.math.BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\");}";
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);var execution=new JavaValueReuseTest();execution.directory=directory;
        assertEquals(0,execution.executeIdentity(source,false,8,"scale"));assertEquals(0,execution.executeIdentity(generated,false,8,"scale"));
        assertFalse(generated.contains("Mathematics inverse reuse"));
        assertTrue(result.diagnostics().stream().anyMatch(item->item.code().equals("VALUE_INVERSE_UNAVAILABLE")
            && item.message().equals("VALUE_DETACH_FACTORY_STATIC_INITIALIZATION")),result.diagnostics().toString());
    }
    @Test void reusedInverseHasExecutedFreshWrappersAndIndependentScalarCopies() throws Exception {
        String source=fixture().replace("public static Leaf scale(Leaf x){","public static boolean scale(Leaf x){")
            .replace("return weighted.combine(x.reciprocal());","Leaf first=x.reciprocal().copy();Leaf second=x.reciprocal().copy();return first==second || first.first()==second.first() || first.second()==second.second();")
            .replace("public Leaf square()","public Leaf copy(){return new Leaf(amber,blue);}public Leaf square()");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
        var conditions=new ArrayList<String>();helper.parse(generated).accept(new org.eclipse.jdt.core.dom.ASTVisitor(){
            @Override public boolean visit(org.eclipse.jdt.core.dom.ConditionalExpression expression) {
                if(expression.getThenExpression().toString().contains("mathInverseCandidate"))conditions.add(expression.getExpression().toString());return true;
            }});
        assertEquals(2,conditions.size());assertEquals(conditions.getFirst(),conditions.getLast());
        String probe=generated.replace("public static boolean scale", "public static boolean fastGuard(Leaf x){Leaf mathSharedValue0=x.square();Leaf mathRetainedInverse=x.square().combine(x).reciprocal();Leaf mathInverseCandidate;return "+conditions.getFirst()+";}public static boolean scale");
        var execution=new JavaValueReuseTest();execution.directory=directory;
        assertEquals(true,execution.executeIdentity(probe,false,8,"fastGuard"),"The exact emitted guard must exercise detachment, not only fallback");
        assertEquals(false,execution.executeIdentity(source,false,8,"scale"));assertEquals(false,execution.executeIdentity(generated,false,8,"scale"));
    }
    @Test void squareProducerMustRespectTheScalarOriginWhitelist() throws Exception {
        String source=fixture().replace("public Leaf square(){return combine(this);}",
            "public Leaf square(){return new Leaf(amber.pow(2).subtract(blue.pow(2)).mod(P),amber.multiply(blue).shiftLeft(1).mod(P));}");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        assertFalse(helper.apply(source,result).contains("Mathematics inverse reuse"));
        assertTrue(result.diagnostics().stream().anyMatch(item->item.code().equals("VALUE_INVERSE_UNAVAILABLE")
            && item.message().equals("VALUE_SCALAR_ORIGIN_UNSUPPORTED")),result.diagnostics().toString());
    }
    @Test void originalEarlyRejectionStillRunsForTheLaterBase() throws Exception {
        String source=fixture().replace("amber.signum()==0", "amber.equals(BigInteger.valueOf(100))");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
        var execution=new JavaValueCallTest();execution.directory=directory;assertEquals(execution.execute(source),execution.execute(generated));
    }
    @Test void originalInverseExceptionsKeepTheirObservedEvaluationPosition() throws Exception {
        String modulus="4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787";
        String source=fixture().replace(modulus,new java.math.BigInteger(modulus).multiply(java.math.BigInteger.valueOf(15)).toString())
            .replace("public static Leaf scale(Leaf x){","public static String scale(Leaf x){if(x.first().signum()==0)x=Leaf.of(BigInteger.ZERO,BigInteger.ZERO);String order=\"before:\";try{")
            .replace("return weighted.combine(x.reciprocal());","order+=\"retained:\";Leaf answer=weighted.combine(x.reciprocal());return order+\"after:\"+answer.first()+\":\"+answer.second();}catch(ArithmeticException failure){return order+failure.getClass().getSimpleName();}");
        var helper=new JavaImmutableValueTest();helper.directory=directory;var execution=new JavaValueReuseTest();execution.directory=directory;
        for(boolean earlyRejection:new boolean[]{false,true}) {
            String candidate=earlyRejection?source.replace("amber.signum()==0 && blue.signum()==0", "amber.equals(BigInteger.valueOf(100))"):source;
            var result=MathematicalAnalysis.analyze(helper.parse(candidate),candidate,JavaValueReuseTest.options(),null,-1,0,helper.environment());
            String generated=helper.apply(candidate,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
            for(int value:new int[]{0,3,8,100}) {
                Object original=execution.executeIdentity(candidate,false,value,"scale");
                if(value==0 || value==3)assertEquals("before:ArithmeticException",original);
                if(earlyRejection && value==100)assertEquals("before:retained:ArithmeticException",original);
                if(value==8)assertTrue(original.toString().startsWith("before:retained:after:"));
                assertEquals(original,execution.executeIdentity(generated,false,value,"scale"));
            }
        }
    }
    @Test void effectsChangedBaseAndWrongSquareCannotEstablishThePremise() throws Exception {
        for(String source:new String[]{fixture().replace("if(rejected())", "System.out.print(amber);if(rejected())"),
            fixture().replace("return weighted.combine(x.reciprocal());", "x=powered;return weighted.combine(x.reciprocal());"),
            fixture().replace("public Leaf square(){return combine(this);}","public Leaf square(){return new Leaf(amber,blue);}")}) {
            var fixture=new JavaImmutableValueTest();fixture.directory=directory;
            var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
            assertFalse(fixture.apply(source,result).contains("Mathematics inverse reuse"));
        }
    }

    @Test void compositeModulusUsesOnlyTheRetainedSuccessfulUnitPremise() throws Exception {
        String source=fixture().replace("4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787",
            new java.math.BigInteger("4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787").multiply(java.math.BigInteger.valueOf(15)).toString());
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
        var execution=new JavaValueCallTest();execution.directory=directory;assertEquals(execution.execute(source),execution.execute(generated));
    }

    @Test void recomputingThePredecessorMustStillPayItsRealizedCost() throws Exception {
        String source=fixture().replace("Leaf weighted=x.square().combine(powered.reciprocal());","Leaf weighted=x.combine(powered.reciprocal());");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        assertFalse(fixture.apply(source,result).contains("Mathematics inverse reuse"));
        assertTrue(result.diagnostics().stream().anyMatch(item->item.message().startsWith("VALUE_INVERSE_COST:")));
    }
    private static String authoredCache(String initialization) {
        return fixture().replace("public static Leaf scale(Leaf x){","public static Leaf scale(Leaf x){"+initialization
                +"Leaf a=x.combine(x).copy();Leaf b=x.combine(x).copy();")
            .replace("Leaf weighted=x.square().combine(powered.reciprocal());","Leaf weighted=x.combine(powered.reciprocal());")
            .replace("public Leaf square()","public Leaf copy(){return new Leaf(amber,blue);}public Leaf square()");
    }
    @Test void unrelatedReuseCannotQualifyAnUnexecutedNonsquareCache() throws Exception {
        String source=authoredCache("Leaf cache=Leaf.of(BigInteger.valueOf(7),BigInteger.valueOf(11));if(false)cache=x.square();");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertTrue(generated.contains("Mathematics value reuse"),result.diagnostics().toString());
        var execution=new JavaValueCallTest();execution.directory=directory;assertIterableEquals(execution.execute(source),execution.execute(generated));
        assertFalse(generated.contains("Mathematics inverse reuse"),"The unrelated prior receipt supplies no provenance for a non-null authored cache");
    }
    @Test void aNullInitializedConditionalSquareCacheKeepsGuardedFallbackAndFastPath() throws Exception {
        String initialization="Leaf cache=null;if(x.first().compareTo(BigInteger.valueOf(10))>0)cache=x.square();";
        String source=authoredCache(initialization);var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertTrue(generated.contains("Mathematics inverse reuse"),result.diagnostics().toString());
        var conditions=new ArrayList<String>();helper.parse(generated).accept(new org.eclipse.jdt.core.dom.ASTVisitor(){
            @Override public boolean visit(org.eclipse.jdt.core.dom.ConditionalExpression expression) {
                if(expression.getThenExpression().toString().contains("mathInverseCandidate"))conditions.add(expression.getExpression().toString());return true;
            }});
        assertEquals(1,conditions.size());assertTrue(conditions.getFirst().contains("cache != null"));
        String probe=generated.replace("public static Leaf scale", "public static boolean fastGuard(Leaf x){"+initialization
            +"Leaf mathRetainedInverse=x.square().combine(x).reciprocal();Leaf mathInverseCandidate;return "+conditions.getFirst()+";}public static Leaf scale");
        var identity=new JavaValueReuseTest();identity.directory=directory;
        assertEquals(false,identity.executeIdentity(probe,false,8,"fastGuard"));assertEquals(true,identity.executeIdentity(probe,false,17,"fastGuard"));
        var execution=new JavaValueCallTest();execution.directory=directory;assertEquals(execution.execute(source),execution.execute(generated));
    }
    @Test void parenthesizedCacheOverwriteCannotRetainSquareProvenance() throws Exception {
        assertNoInverse(authoredCache("Leaf cache=null;if(false)cache=x.square();(cache)=Leaf.of(BigInteger.valueOf(7),BigInteger.valueOf(11));"));
    }
    @Test void parenthesizedBaseWriteRevokesTheInversePremise() throws Exception {
        assertNoInverse(fixture().replace("return weighted.combine(x.reciprocal());","(x)=Leaf.of(BigInteger.valueOf(7),BigInteger.valueOf(11));return weighted.combine(x.reciprocal());"));
    }
    @Test void parenthesizedPoweredWriteRevokesTheInversePremise() throws Exception {
        assertNoInverse(fixture().replace("Leaf weighted=x.square()","(powered)=Leaf.of(BigInteger.valueOf(7),BigInteger.valueOf(11));Leaf weighted=x.square()"));
    }
    private void assertNoInverse(String source) throws Exception {
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueReuseTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);var execution=new JavaValueCallTest();execution.directory=directory;
        assertIterableEquals(execution.execute(source),execution.execute(generated));assertFalse(generated.contains("Mathematics inverse reuse"));
    }

    @Test void changedInverseDependencyRevokesTheOrdinaryApplyReceipt() throws Exception {
        String source=fixture();int split=source.indexOf("final class Leaf");Path dependency=directory.resolve("Leaf.java");
        java.nio.file.Files.writeString(dependency,"import java.math.BigInteger;"+source.substring(split));source=source.substring(0,split);
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        assertTrue(result.evidence().stream().anyMatch(item->item.valueInverse()!=null),result.diagnostics().toString());
        java.nio.file.Files.writeString(dependency,java.nio.file.Files.readString(dependency).replace("n.modInverse(P)","n.modInverse(P.add(BigInteger.ONE))"));
        assertFalse(result.matches(source,java.util.Map.of()));assertThrows(IllegalArgumentException.class,result::newEdit);
    }

    @Test void foreignHelperInitializationIsRejectedByTheInverseSourceUseProof() throws Exception {
        String source=fixture().replace("BigInteger n=amber.multiply", "BigInteger ignored=InitProbe.touch(amber);BigInteger n=amber.multiply")
            +"final class InitProbe {static {System.setProperty(\"inverse.init.probe\",\"initialized\");} static java.math.BigInteger touch(java.math.BigInteger x){return x;}}";
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueReuseTest.options(),null,-1,0,fixture.environment());
        assertFalse(fixture.apply(source,result).contains("Mathematics inverse reuse"));
        assertTrue(result.diagnostics().stream().anyMatch(item->item.message().contains("VALUE_IDENTITY_FOREIGN_CALL")));
    }

}
