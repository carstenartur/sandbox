/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueAlgebraProofTest {
    @TempDir Path directory;
    static String source(String multiply,String square,String identity) {
        return JavaImmutableValueTest.fixture(multiply).replace("public BigInteger[] parts()",
            "public static final Calculation ONE=new Calculation("+identity+"); public Calculation square(){"+square+"} public BigInteger[] parts()");
    }
    static String square(){return "return mul(this);";}
    static MathCleanUpOptions options(){return new MathCleanUpOptions(true,Set.of(NumericKind.BIG_INTEGER),SafetyProfile.PRESERVE_JAVA,
        OptimizationGoal.LOWER_ESTIMATED_RUNTIME,20_000_000,20_000,false,17,List.of(),true);}
    JavaValueAlgebraProof.Receipt prove(String source) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);var session=new JavaValueSourceSummary.Session(ast,options,work);
            var type=((TypeDeclaration)ast.types().getFirst()).resolveBinding();
            var multiply=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("mul")).findFirst().orElseThrow();
            var square=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("square")).findFirst().orElseThrow();
            var identity=Arrays.stream(type.getDeclaredFields()).filter(f->f.getName().equals("ONE")).findFirst().orElseThrow();
            return JavaValueAlgebraProof.prove(session,session.shape(type),multiply,square,
                (JavaValueSourceSummary.Value)session.constant(identity),work,options,1_000_000);
        }
    }
    @Test void provesSourceDefinedComplexResidueAlgebra(){
        var result=assertDoesNotThrow(()->prove(source(JavaImmutableValueTest.multiplication(),square(),"BigInteger.ONE,BigInteger.ZERO")));
        assertTrue(result.totalAssociativeMultiplicationWithIdentity());assertTrue(result.squareConsistent());assertEquals(381,result.modulus().bitLength());
    }
    @Test void rejectsNonassociativeMultiplicationWrongIdentityAndSquare() throws Exception {
        for(String source:List.of(source(JavaImmutableValueTest.multiplication().replace("a.subtract(b)","a.subtract(b).add(BigInteger.ONE)"),square(),"BigInteger.ONE,BigInteger.ZERO"),
            source(JavaImmutableValueTest.multiplication(),square(),"BigInteger.ZERO,BigInteger.ZERO"),
            source(JavaImmutableValueTest.multiplication(),square(),"P.add(BigInteger.ONE),BigInteger.ZERO"),
            source(JavaImmutableValueTest.multiplication(),"return new Calculation(amber,blue);","BigInteger.ONE,BigInteger.ZERO")))
            assertThrows(IllegalArgumentException.class,()->prove(source));
    }
    @Test void rejectsFreshOuterResultsWhoseNestedIdentityDependsOnTheSchedule()throws Exception {
        String source="import java.math.BigInteger;public final class Calculation{private final Leaf a,b;private Calculation(Leaf a,Leaf b){this.a=a;this.b=b;}public static final Calculation ONE=new Calculation(Leaf.ZERO,Leaf.ZERO);public Calculation mul(Calculation other){return new Calculation(Leaf.ZERO,Leaf.ZERO);}public Calculation square(){return new Calculation(Leaf.of(BigInteger.ZERO),Leaf.of(BigInteger.ZERO));}}final class Leaf{private final BigInteger value;private Leaf(BigInteger value){this.value=value;}public static final Leaf ZERO=of(BigInteger.ZERO);public static Leaf of(BigInteger value){return new Leaf(value.mod(BigInteger.ONE));}}";
        assertEquals("VALUE_ALGEBRA_FRESH_TREE",assertThrows(IllegalArgumentException.class,()->prove(source)).getMessage());
    }
    @Test void rejectsForeignInitializationFromSquareOnlyCallsAndUnusedConstants()throws Exception {
        String foreign="final class Foreign{static final java.math.BigInteger VALUE=java.math.BigInteger.ONE;static java.math.BigInteger touch(java.math.BigInteger x){return x;}}";
        for(String source:List.of(source(JavaImmutableValueTest.multiplication(),"BigInteger unused=Foreign.touch(amber);"+square(),"BigInteger.ONE,BigInteger.ZERO")+foreign,
            source("BigInteger unused=Foreign.VALUE;"+JavaImmutableValueTest.multiplication(),square(),"BigInteger.ONE,BigInteger.ZERO")+foreign))
            assertEquals("VALUE_ALGEBRA_INITIALIZATION_OWNER",assertThrows(IllegalArgumentException.class,()->prove(source)).getMessage());
    }
    @Test void actualCompleteFp12SourceLawsAreIndependentlyVerified() throws Exception {
        Path binary=Path.of(System.getProperty("sandbox.math.bc.jar")), archive=Path.of(System.getProperty("sandbox.math.bc.sourceArchive"));
        assertEquals("b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        Path root=directory.resolve("tower");Files.createDirectories(root.resolve("org/bouncycastle/crypto/bls"));
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            for(String name:List.of("Fp2Element","Fp6Element","Fp12Element","BLS12_381G1")) {
                String relative="org/bouncycastle/crypto/bls/"+name+".java";
                Files.write(root.resolve(relative),zip.getInputStream(zip.getEntry(relative)).readAllBytes());
            }
        }
        Path file=root.resolve("org/bouncycastle/crypto/bls/Fp12Element.java");String source=Files.readString(file);
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(source.toCharArray());parser.setUnitName(file.toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{binary.toString()},new String[]{root.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null);for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());
        var environment=new MathematicalAnalysis.SourceEnvironment(file.toString(),List.of(binary.toString()),List.of(root.toString()),List.of("UTF-8"));
        var options=options();
        try(var context=JavaSourceContext.open(ast,source,environment,null,options)) {
            var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);var session=new JavaValueSourceSummary.Session(ast,options,work);
            var type=((TypeDeclaration)ast.types().getFirst()).resolveBinding();
            var mul=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("mul")).findFirst().orElseThrow();
            var square=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("square")).findFirst().orElseThrow();
            var identity=Arrays.stream(type.getDeclaredFields()).filter(f->f.getName().equals("ONE")).findFirst().orElseThrow();
            var result=JavaValueAlgebraProof.prove(session,session.shape(type),mul,square,(JavaValueSourceSummary.Value)session.constant(identity),work,options);
            assertTrue(result.totalAssociativeMultiplicationWithIdentity());assertEquals(381,result.modulus().bitLength());
            assertTrue(work.used<=20_000_000);context.checkpoint().requireCurrent();
        }
    }

}
