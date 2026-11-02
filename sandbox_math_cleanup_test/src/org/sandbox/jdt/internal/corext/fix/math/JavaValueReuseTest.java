/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaValueReuseTest {
    @TempDir Path directory;
    static MathCleanUpOptions options() {
        return JavaValueCallTest.options().withValueRuntime(MathCleanUpOptions.ValueRuntime.AUDITED_OPENJDK_25_0_5_8);
    }
    static String fixture() {
        return JavaValueCallTest.fixture().replace("return x.combine(TWIST);",
            "Leaf a=x.square().combine(TWIST);Leaf b=x.square().combine(TWIST);return a.combine(b);")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);} public Leaf combine(Leaf other)");
    }
    @Test void repeatedPureValueCallKeepsFirstEvaluationAndSharesOnlyClosedNumericUses() throws Exception {
        String source=fixture();var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);
        assertTrue(generated.contains("Mathematics value reuse"),result.diagnostics().toString());
        var execution=new JavaValueCallTest();execution.directory=directory;assertEquals(execution.execute(source),execution.execute(generated));
    }
    @Test void acceptedReuseDocumentsItsDeploymentContractAndRepeatedApplyIsStable() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        assertTrue(result.documentation().stream().anyMatch(edit->edit.offset()==source.indexOf("public static Leaf scale(")
            && edit.replacement().contains("audited OpenJDK 25.0.5+8")),"The enclosing method must retain the deployment requirement");
        String generated=helper.apply(source,result);
        var repeated=MathematicalAnalysis.analyze(helper.parse(generated),generated,options(),null,-1,0,helper.environment());
        assertTrue(repeated.replacements().stream().noneMatch(edit->edit.replacement().contains("Mathematics value reuse")),
            "The first retained evaluation and guarded fallbacks must not acquire another reuse envelope");
    }
    @Test void selectedReuseBlockUsesHalfOpenSourceIntervals() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var ast=helper.parse(source);var owner=(org.eclipse.jdt.core.dom.TypeDeclaration)ast.types().getFirst();
        var body=Arrays.stream(owner.getMethods()).filter(method->method.getName().getIdentifier().equals("scale")).findFirst().orElseThrow().getBody();
        int start=body.getStartPosition(),length=body.getLength();
        for(int[] selection:new int[][]{{start-1,1},{start+length,0},{start,0},{start+length-1,1}}) {
            var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,selection[0],selection[1],helper.environment());
            boolean overlaps=selection[0]>=start && selection[0]<start+length;
            assertEquals(overlaps,result.replacements().stream().anyMatch(edit->edit.replacement().contains("Mathematics value reuse")),Arrays.toString(selection));
        }
    }
    @Test void rejectedReuseDiscoveryStillChecksSharedCancellation() throws Exception {
        String source=JavaValueCallTest.fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        var checks=new java.util.concurrent.atomic.AtomicInteger();var options=options();
        var work=new JavaValueSourceSummary.Work(1,options,()->{checks.incrementAndGet();return true;});
        JavaValueReuseOptimizer.optimize(helper.parse(source),source,options,null,-1,0,Map.of(),helper.environment(),
            new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),work,new MathematicalJavadoc());
        assertTrue(checks.get()>0,"An unsuccessful candidate scan still belongs to the shared cancelled request");
    }
    @Test void staticAndInstanceInitializersRemainOutsideMethodReuse() throws Exception {
        for(String initializer:new String[]{"static ",""}) {
            String source=fixture().replace("public final class Calculation {","public final class Calculation {public static String observed;")
                .replace("public static Leaf scale(Leaf x){",initializer+"{Leaf x=Leaf.of(BigInteger.valueOf(8),BigInteger.valueOf(3));")
                .replace("return a.combine(b);","Leaf answer=a.combine(b);observed=answer.first().toString()+\":\"+answer.second().toString();");
            String original=executeInitializer(source,initializer.isEmpty());
            var helper=new JavaImmutableValueTest();helper.directory=directory;
            var result=assertDoesNotThrow(()->MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment()));
            String generated=helper.apply(source,result);assertFalse(generated.contains("Mathematics value reuse"));
            assertEquals(original,executeInitializer(generated,initializer.isEmpty()));
        }
    }
    private String executeInitializer(String source,boolean instance) throws Exception {
        Path output=Files.createTempDirectory(directory,"initializer");Path file=output.resolve("Calculation.java");Files.writeString(file,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var owner=Class.forName("Calculation",true,loader);if(instance)owner.getConstructor().newInstance();
            return (String)owner.getField("observed").get(null);
        }
    }
    @Test void identityVisibleResultAndEffectfulOperationCannotBeShared() throws Exception {
        for(String source:new String[]{fixture().replace("Leaf b=x.square().combine(TWIST);","Leaf b=x.square();if(a==b)return a;"),
            fixture().replace("return combine(this);","System.out.print(amber);return combine(this);"),
            fixture().replace("private final BigInteger amber,blue;","private BigInteger amber,blue;")}) {
            var fixture=new JavaImmutableValueTest();fixture.directory=directory;
            var result=MathematicalAnalysis.analyze(fixture.parse(source),source,options(),null,-1,0,fixture.environment());
            assertFalse(fixture.apply(source,result).contains("Mathematics value reuse"));
        }
    }
    @Test void everyProducerInAChainRequiresSupportedScalarOrigins() throws Exception {
        String source=fixture().replace("public Leaf square(){return combine(this);}",
            "public Leaf square(){return new Leaf(amber.pow(2).subtract(blue.pow(2)).mod(P),amber.multiply(blue).shiftLeft(1).mod(P));}")
            .replace("public Leaf combine(Leaf other)","public Leaf copy(){return new Leaf(amber,blue);}public Leaf combine(Leaf other)");
        for(String candidate:new String[]{source,source.replace("x.square().combine(TWIST)","x.square().copy().combine(TWIST)")}) {
            var helper=new JavaImmutableValueTest();helper.directory=directory;
            var result=MathematicalAnalysis.analyze(helper.parse(candidate),candidate,options(),null,-1,0,helper.environment());
            assertFalse(helper.apply(candidate,result).contains("Mathematics value reuse"),result.diagnostics().toString());
            assertTrue(result.diagnostics().stream().anyMatch(item->item.message().equals("VALUE_SCALAR_ORIGIN_UNSUPPORTED")));
        }
    }
    @Test void aFactoryCannotIntroduceForeignStaticInitialization() throws Exception {
        String source=JavaValueCallTest.fixture().replace("static final Leaf TWIST=Leaf.of(BigInteger.ONE,BigInteger.ONE);","public static int calls;")
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static int scale(Leaf x){Leaf a=x.square().copy();Leaf b=x.square().copy();return calls;}")
            .replace("return new Leaf(a.mod(P),b.mod(P));","return new Leaf(a.mod(Foreign.P),b.mod(Foreign.P));")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf copy(){return new Leaf(amber,blue);}public Leaf combine(Leaf other)")
            +"final class Foreign {static {Calculation.calls++;}public static final java.math.BigInteger P=java.math.BigInteger.valueOf(101);}";
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertEquals(0,executeIdentity(source,false,8,"scale"));
        assertEquals(executeIdentity(source,false,8,"scale"),executeIdentity(generated,false,8,"scale"));
        assertFalse(generated.contains("Mathematics value reuse"));
        String established=source.replace(".mod(P)",".mod(Foreign.P)");
        var allowed=MathematicalAnalysis.analyze(helper.parse(established),established,options(),null,-1,0,helper.environment());
        assertTrue(helper.apply(established,allowed).contains("Mathematics value reuse"),allowed.diagnostics().toString());
        assertEquals(1,executeIdentity(established,false,8,"scale"));
        assertEquals(executeIdentity(established,false,8,"scale"),executeIdentity(helper.apply(established,allowed),false,8,"scale"));
    }
    @Test void repeatedSourceTextCannotShareDifferentlyBoundArguments() throws Exception {
        String source=JavaValueCallTest.fixture().replace("static final Leaf TWIST=Leaf.of(BigInteger.ONE,BigInteger.ONE);","public static final BigInteger K=BigInteger.valueOf(17);")
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static String scale(Leaf x){Leaf a=x.stretch(K).copy();BigInteger K=BigInteger.valueOf(23);Leaf b=x.stretch(K).copy();return b.first()+\",\"+b.second();}")
            .replace("public Leaf combine(Leaf other)","public Leaf stretch(BigInteger k){return new Leaf(amber.multiply(amber).multiply(amber).multiply(k).mod(P),blue.multiply(blue).multiply(blue).multiply(k).mod(P));}public Leaf copy(){return new Leaf(amber,blue);}public Leaf combine(Leaf other)");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);assertEquals("60,15",executeIdentity(source,false,8,"scale"));
        assertEquals(executeIdentity(source,false,8,"scale"),executeIdentity(generated,false,8,"scale"));
        assertFalse(generated.contains("Mathematics value reuse"));
    }
    @Test void parenthesizedReceiverAssignmentCannotPreserveAnEarlierValue() throws Exception {
        String source=fixture().replace("Leaf b=x.square()","((x))=Leaf.of(BigInteger.valueOf(7),BigInteger.valueOf(11));Leaf b=x.square()");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);var execution=new JavaValueCallTest();execution.directory=directory;
        assertIterableEquals(execution.execute(source),execution.execute(generated));assertFalse(generated.contains("Mathematics value reuse"));
    }
    @ParameterizedTest
    @ValueSource(strings={"x=x.square().combine(TWIST);","((x))=x.square().combine(TWIST);",
        "Leaf a=(x=x.square().combine(TWIST));"})
    void receiverWriteEnclosingFirstCallCannotReuseItsEarlierValue(String firstStatement) throws Exception {
        String source=fixture().replace("Leaf a=x.square().combine(TWIST);",firstStatement)
            .replace("return a.combine(b);","return b;")
            .replace("public static Leaf scale", "public static String observe(Leaf x){Leaf result=scale(x);return result.first()+\":\"+result.second();}public static Leaf scale");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);
        assertEquals("17:73",executeIdentity(source,false,8,"observe"));
        assertEquals("17:73",executeIdentity(generated,false,8,"observe"),generated);
        var execution=new JavaValueCallTest();execution.directory=directory;
        assertIterableEquals(execution.execute(source),execution.execute(generated));
        assertFalse(generated.contains("Mathematics value reuse"));
    }
    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void receiverWritesOutsideTheReuseIntervalPreserveSharing(boolean beforeFirst) throws Exception {
        String assignment="((x))=x.combine(TWIST);";
        String source=beforeFirst?fixture().replace("Leaf a=x.square()",assignment+"Leaf a=x.square()")
            :fixture().replace("return a.combine(b);",assignment+"return a.combine(b);");
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);
        assertTrue(generated.contains("Mathematics value reuse"),result.diagnostics().toString());
        var execution=new JavaValueCallTest();execution.directory=directory;
        assertIterableEquals(execution.execute(source),execution.execute(generated));
    }
    @Test void nestedRepeatedCallsReplayBothFirstAssignmentsAndCachedOperands() throws Exception {
        String source=fixture().replace("x.square().combine(TWIST)","x.square().combine(x).combine(TWIST)");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);assertTrue(generated.contains("Mathematics value reuse"),result.diagnostics().toString());
        var execution=new JavaValueCallTest();execution.directory=directory;assertEquals(execution.execute(source),execution.execute(generated));
    }
    @Test void freshCopyWrapperCannotExposeSharedComponentIdentity() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static boolean scale(Leaf x){Leaf a=x.square().copy();Leaf b=x.square().copy();return a.first()==b.first();}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf copy(){return new Leaf(amber,blue);}public Leaf combine(Leaf other)");
        assertIdentityPreserved(source,false);
    }
    @Test void scalarIdentityOperationsCannotDisguiseAComponentCopy() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static boolean scale(Leaf x){Leaf a=x.square().copy();Leaf b=x.square().copy();return a.first()==b.first();}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf copy(){return new Leaf(amber.add(BigInteger.ZERO).abs().shiftLeft(0),blue);}public Leaf combine(Leaf other)");
        assertIdentityPreserved(source,false);
    }
    @Test void independentSubclassCallbackCannotObserveSharedOperandIdentity() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static boolean scale(Leaf x,Leaf y){Leaf a=x.square().observe(y);Leaf b=x.square().observe(y);return b.first().equals(BigInteger.ONE);}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf observe(Leaf other){return new Leaf(other.amber.add(amber),blue);}public Leaf combine(Leaf other)");
        assertIdentityPreserved(source,true);
    }
    @Test void callbackCanObserveSharedOperandEvenWhenEveryResultComponentUsesArithmetic() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static boolean scale(Leaf x,Leaf y){Leaf a=x.square().observe(y);Leaf b=x.square().observe(y);return b.first().equals(BigInteger.ONE);}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf observe(Leaf other){return new Leaf(other.amber.add(amber),blue.add(BigInteger.ONE));}public Leaf combine(Leaf other)");
        assertIdentityPreserved(source,true);
    }
    @Test void modulusInTheCopyConsumerPreservesItsOwnScalarOriginBehavior() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static boolean scale(Leaf x){Leaf a=x.square().copy();Leaf b=x.square().copy();return a.first()==b.first();}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf copy(){return new Leaf(amber.mod(P),blue.mod(P));}public Leaf combine(Leaf other)");
        assertIdentityPreserved(source,false);
    }
    private void assertIdentityPreserved(String source,boolean callback) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);
        assertTrue(generated.contains("Mathematics value reuse"),result.diagnostics().toString());
        for(int value:new int[]{5,8}) {assertEquals(false,executeIdentity(source,callback,value,"scale"));
            assertEquals(executeIdentity(source,callback,value,"scale"),executeIdentity(generated,callback,value,"scale"));}
    }
    public static final class IdentityObserver extends java.math.BigInteger {
        private static final long serialVersionUID=1L;private java.math.BigInteger previous;
        public IdentityObserver(){super("7");}
        // Callback history does not participate in BigInteger's numeric equality contract.
        @Override public boolean equals(Object other){return super.equals(other);}
        @Override public int hashCode(){return super.hashCode();}
        @Override public java.math.BigInteger add(java.math.BigInteger argument){boolean same=previous==argument;previous=argument;return same?ONE:ZERO;}
    }
    private Object executeIdentity(String source,boolean callback,int value,String method) throws Exception {
        Path output=Files.createTempDirectory(directory,"identity");Path file=output.resolve("Calculation.java");Files.writeString(file,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var leaf=loader.loadClass("Leaf");var constructor=leaf.getDeclaredConstructor(java.math.BigInteger.class,java.math.BigInteger.class);constructor.setAccessible(true);
            var input=constructor.newInstance(java.math.BigInteger.valueOf(value),java.math.BigInteger.valueOf(3));var owner=loader.loadClass("Calculation");
            return callback?owner.getMethod(method,leaf,leaf).invoke(null,input,constructor.newInstance(new IdentityObserver(),java.math.BigInteger.ONE))
                :owner.getMethod(method,leaf).invoke(null,input);
        }
    }
    @Test void qualifiedRenamedDetachmentHasAnExecutedSatisfiableFastPath() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static boolean scale(Leaf x){Leaf a=x.square().copy();Leaf b=x.square().copy();return a.first()==b.first();}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf copy(){return new Leaf(amber,blue);}public Leaf combine(Leaf other)")
            .replace("amber","leftPart").replace("blue","rightPart").replace("square","selfProduct").replace("combine","product")
            .replace("first","leftScalar").replace("second","rightScalar").replace("of(","manufacture(");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var result=MathematicalAnalysis.analyze(fixture.parse(source),source,options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,result);assertTrue(generated.contains(".toByteArray()"),result.diagnostics().toString());
        var conditions=new ArrayList<String>();fixture.parse(generated).accept(new org.eclipse.jdt.core.dom.ASTVisitor(){
            @Override public boolean visit(org.eclipse.jdt.core.dom.ConditionalExpression expression) {
                if(expression.getThenExpression().toString().contains(".toByteArray()"))conditions.add(expression.getExpression().toString());return true;
            }});
        assertEquals(1,conditions.size());
        // Execute the exact emitted predicate on the original first result, independently of optimizer estimates.
        String probe=generated.replace("public static boolean scale", "public static boolean fastGuard(Leaf x){Leaf mathSharedValue0=x.selfProduct();return "+conditions.getFirst()+";}public static boolean scale");
        assertEquals(true,executeIdentity(probe,false,8,"fastGuard"));
        assertEquals(false,executeIdentity(probe,false,5,"fastGuard"));
        assertEquals(executeIdentity(source,false,8,"scale"),executeIdentity(generated,false,8,"scale"));
    }
    @Test void shadowedJavaPackageCannotInvokeAnUnrelatedGuardCallback() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}","public static int calls;public static int scale(Leaf x){Names java=new Names();Leaf a=x.square().copy();Leaf b=x.square().copy();return calls;}")
            .replace("public Leaf combine(Leaf other)","public Leaf square(){return combine(this);}public Leaf copy(){return new Leaf(amber,blue);}public Leaf combine(Leaf other)")
            +"class Names {public final MathNamespace math=new MathNamespace();}class MathNamespace {public final ProbeInteger BigInteger=new ProbeInteger();}"
            +"class ProbeInteger {public java.math.BigInteger valueOf(long n){Calculation.calls++;return java.math.BigInteger.valueOf(n);}}";
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);
        assertTrue(result.diagnostics().stream().anyMatch(item->item.code().equals("VALUE_REUSE_LIMIT") && item.message().startsWith("VALUE_GUARD_BINDING_")),result.diagnostics().toString());
        assertEquals(0,executeIdentity(source,false,8,"scale"));
        assertEquals(executeIdentity(source,false,8,"scale"),executeIdentity(generated,false,8,"scale"));
    }
    @Test void scalarStaticMethodThroughANullValueQualifierCannotEnterReuseGuard() throws Exception {
        String source=fixture()
            .replace("public static Leaf scale(Leaf x){", "public static int calls;public static int scale(Leaf x){Names java=new Names();")
            .replace("return a.combine(b);", "Leaf answer=a.combine(b);return calls;")
            +"class Names {public final MathNamespace math=null;}class MathNamespace {public final java.math.BigInteger BigInteger=null;}";
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,result);
        assertTrue(result.diagnostics().stream().anyMatch(item->item.code().equals("VALUE_REUSE_LIMIT") && item.message().startsWith("VALUE_GUARD_BINDING_")),result.diagnostics().toString());
        assertEquals(0,executeIdentity(source,false,8,"scale"));
        assertEquals(executeIdentity(source,false,8,"scale"),executeIdentity(generated,false,8,"scale"));
    }
    @Test void completeActualG2SharesTheRepeatedSquaresAndCube() throws Exception {
        Path archive=Path.of(System.getProperty("sandbox.math.bc.sourceArchive")),binary=Path.of(System.getProperty("sandbox.math.bc.jar"));
        assertEquals("b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        Path root=directory.resolve("actual");Files.createDirectories(root.resolve("org/bouncycastle/crypto/bls"));
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            for(String name:List.of("Fp2Element","BLS12_381G2HashToCurve","BLS12_381G1")) {
                String relative="org/bouncycastle/crypto/bls/"+name+".java";Files.write(root.resolve(relative),zip.getInputStream(zip.getEntry(relative)).readAllBytes());
            }
        }
        Path file=root.resolve("org/bouncycastle/crypto/bls/BLS12_381G2HashToCurve.java");String source=Files.readString(file);
        var parser=org.eclipse.jdt.core.dom.ASTParser.newParser(org.eclipse.jdt.core.dom.AST.getJLSLatest());parser.setSource(source.toCharArray());parser.setUnitName(file.toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{binary.toString()},new String[]{root.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();org.eclipse.jdt.core.JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(org.eclipse.jdt.core.dom.CompilationUnit)parser.createAST(null);for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());
        var environment=new MathematicalAnalysis.SourceEnvironment(file.toString(),List.of(binary.toString()),List.of(root.toString()),List.of("UTF-8"));
        var result=MathematicalAnalysis.analyze(ast,source,options(),null,-1,0,environment);
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String generated=fixture.apply(source,result);
        assertTrue(generated.contains("Mathematics value reuse"),result.diagnostics().toString());
        assertTrue(generated.contains("Mathematics factory residue"),result.diagnostics().toString());
        assertEquals(3,generated.split(" = null;",-1).length-source.split(" = null;",-1).length,"three shared values");
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.evidence());
        assertTrue(json.length()<1_000_000,"actual scan evidence must remain compact: "+json.length());assertFalse(json.contains("unitRootRequest"));
        assertTrue(json.contains("factoryProofs"));assertTrue(json.contains(ValueRuntimeEvidence.CONTRACT));
        assertEquals(executeActual(source,binary),executeActual(generated,binary));
    }
    private List<String> executeActual(String source,Path binary) throws Exception {
        Path output=Files.createTempDirectory(directory,"g2reuse");Path file=output.resolve("BLS12_381G2HashToCurve.java");Files.writeString(file,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-cp",binary.toString(),"-d",output.toString(),file.toString()));
        try(var loader=new JavaImmutableValueTest.IsolatedBC(output,binary)) {
            var type=loader.loadClass("org.bouncycastle.crypto.bls.BLS12_381G2HashToCurve");var leaf=loader.loadClass("org.bouncycastle.crypto.bls.Fp2Element");
            var factory=leaf.getMethod("of",long.class,long.class);Object hasher=type.getConstructor(byte[].class).newInstance((Object)new byte[]{1,2,3});
            var map=type.getMethod("mapToCurveAndIso3",leaf);var result=new ArrayList<String>();
            for(int i=0;i<4;i++)result.add(map.invoke(hasher,factory.invoke(null,(long)i,(long)i+1)).toString());
            var hash=type.getMethod("hashToField",byte[].class);
            for(byte[] message:new byte[][]{new byte[0],new byte[]{1},new byte[]{3,1,4,1,5}})result.add(Arrays.toString((Object[])hash.invoke(hasher,(Object)message)));
            try{map.invoke(hasher,new Object[]{null});fail();}catch(java.lang.reflect.InvocationTargetException failure){result.add(failure.getCause().getClass().getName());}
            return result;
        }
    }
}
