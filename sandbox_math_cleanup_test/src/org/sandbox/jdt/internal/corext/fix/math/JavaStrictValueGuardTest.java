/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;

class JavaStrictValueGuardTest {
    @TempDir Path directory;

    static Stream<Arguments> fixtures() {
        String residue=JavaValueCallTest.fixture()
            .replace("BigInteger.valueOf(101)","new BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\")")
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}",
                "public static Leaf scale(BigInteger x,BigInteger y){BigInteger a=x.mod(Leaf.P);BigInteger b=y.mod(Leaf.P);return Leaf.of(a,b);}");
        return Stream.of(JavaCore.WARNING,JavaCore.ERROR).flatMap(severity->Stream.of(
            Arguments.of(severity,"Mathematics value path",JavaImmutableValueTest.fixture(JavaImmutableValueTest.multiplication()),JavaImmutableValueTest.options()),
            Arguments.of(severity,"Mathematics constant value",JavaValueCallTest.fixture(),JavaValueCallTest.options()),
            Arguments.of(severity,"Mathematics factory residue",residue,JavaValueCallTest.options()),
            Arguments.of(severity,"Mathematics value reuse",JavaValueReuseTest.fixture(),JavaValueReuseTest.options()),
            Arguments.of(severity,"Mathematics inverse reuse",JavaValueInverseReuseTest.fixture(),JavaValueReuseTest.options()),
            Arguments.of(severity,"Mathematics fixed power",JavaFixedPowerOptimizerTest.source(),JavaValueAlgebraProofTest.options())));
    }

    @ParameterizedTest(name="{0}: {1}") @MethodSource("fixtures")
    void valueGuardsRemainUsefulWithNullDiagnostics(String severity,String marker,String source,MathCleanUpOptions options) throws Exception {
        var compiler=StrictCompilerOptions.options(17);
        for(String key:List.of(JavaCore.COMPILER_PB_NULL_REFERENCE,JavaCore.COMPILER_PB_POTENTIAL_NULL_REFERENCE,JavaCore.COMPILER_PB_REDUNDANT_NULL_CHECK))
            compiler.put(key,severity);
        var environment=new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(),
            List.of(),List.of(directory.toString()),List.of("UTF-8"),compiler);
        var analysis=MathematicalAnalysis.analyze(parse(source,compiler),source,options,null,-1,0,environment);
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        String generated=helper.apply(source,analysis);
        assertTrue(generated.contains(marker),analysis.diagnostics().toString());
        assertEquals(environment.compilerOptions(),analysis.compilerOptions());
        parse(generated,compiler);
    }

    @Test void sourceGetterNamedNonNullRemainsEligible() throws Exception {
        valueGuardsRemainUsefulWithNullDiagnostics(JavaCore.WARNING,"Mathematics constant value",
            JavaValueCallTest.fixture().replace("first()","nonNull()"),JavaValueCallTest.options());
    }

    private CompilationUnit parse(String source,Map<String,String> compiler) throws Exception {
        Files.writeString(directory.resolve("Calculation.java"), source, StandardCharsets.UTF_8);
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(source.toCharArray());
        parser.setUnitName(directory.resolve("Calculation.java").toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[0],new String[]{directory.toString()},new String[]{"UTF-8"},true);
        parser.setCompilerOptions(compiler);var ast=(CompilationUnit)parser.createAST(null);
        for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());return ast;
    }

    @Test void actualJdkPredicateThroughValueQualifierIsRejectedEvenWithoutStaticAccessWarnings() throws Exception {
        String source="public class Calculation {"
            +"public boolean f(J java,Object x){return java.util.Objects.nonNull(x);}}"
            +"class J {U util;} class U {java.util.Objects Objects;}";
        var compiler=StrictCompilerOptions.options(17);compiler.put(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER,JavaCore.IGNORE);
        var ast=parse(source,compiler);var calls=new ArrayList<MethodInvocation>();
        ast.accept(new ASTVisitor(){@Override public boolean visit(MethodInvocation call){calls.add(call);return true;}});
        var call=calls.getFirst();assertEquals("java.util.Objects",call.resolveMethodBinding().getDeclaringClass().getBinaryName());
        var options=JavaValueCallTest.options();var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);
        var failure=assertThrows(IllegalArgumentException.class,()->JavaValueGuardBindings.verifyNonNullCalls(call,work));
        assertEquals("VALUE_GUARD_BINDING_NON_NULL_METHOD",failure.getMessage());
    }

    @Test void inheritedPackageShadowRejectsCoreReceiverGuardByBinding() throws Exception {
        Files.writeString(directory.resolve("Base.java"),
				"public class Base {public J java;} class J {public U util;} class U {public java.util.Objects Objects;}",
				StandardCharsets.UTF_8);
        String source="import java.math.BigInteger; public class Calculation extends Base {"
            +"public BigInteger f(BigInteger x){return x.add(BigInteger.ONE).subtract(BigInteger.ONE);}}";
        var compiler=StrictCompilerOptions.options(17);compiler.put(JavaCore.COMPILER_PB_STATIC_ACCESS_RECEIVER,JavaCore.IGNORE);
        var environment=new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(),
            List.of(),List.of(directory.toString()),List.of("UTF-8"),compiler);
        var analysis=MathematicalAnalysis.analyze(parse(source,compiler),source,JavaValueCallTest.options(),null,-1,0,environment);
        assertFalse(analysis.changed(),analysis.diagnostics().toString());
        assertTrue(analysis.diagnostics().stream().anyMatch(d->"VALUE_GUARD_BINDING_NON_NULL_METHOD".equals(d.message())
            && d.stage()==MathematicalAnalysis.Stage.EMISSION),analysis.diagnostics().toString());
    }

    @Test void coreReceiverGuardDoesNotValidateUnrelatedAuthoredPredicate() throws Exception {
        String authored="public boolean callback(Object x){return Callback.nonNull(x);}";
        String source="import java.math.BigInteger; public class Calculation {"+authored
            +"public BigInteger f(BigInteger x){return x.add(BigInteger.ONE).subtract(BigInteger.ONE);}}"
            +"class Callback {static boolean nonNull(Object x){return x!=null;}}";
        var compiler=StrictCompilerOptions.options(17);
        var environment=new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(),
            List.of(),List.of(directory.toString()),List.of("UTF-8"),compiler);
        var analysis=MathematicalAnalysis.analyze(parse(source,compiler),source,JavaValueCallTest.options(),null,-1,0,environment);
        assertTrue(analysis.changed(),analysis.diagnostics().toString());
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        assertTrue(helper.apply(source,analysis).contains(authored));
    }
}
