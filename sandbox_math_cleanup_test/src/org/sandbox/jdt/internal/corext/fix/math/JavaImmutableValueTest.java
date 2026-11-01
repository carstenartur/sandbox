/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaImmutableValueTest {
    @TempDir Path directory;
    static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true,Set.of(NumericKind.BIG_INTEGER),SafetyProfile.PRESERVE_JAVA,
            OptimizationGoal.LOWER_ESTIMATED_RUNTIME,1_000_000,20_000,false,17,List.of(),true);
    }
    static String fixture(String body) {
        return "import java.math.BigInteger; public final class Calculation {"
            + "private final BigInteger amber, blue; private static final BigInteger P=new BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\");"
            + "private Calculation(BigInteger a,BigInteger b){this.amber=a;this.blue=b;}"
            + "public static Calculation of(BigInteger a,BigInteger b){return new Calculation(a,b);}"
            + "public BigInteger[] parts(){return new BigInteger[]{amber,blue};}"
            + "private static BigInteger reduce(BigInteger x){return x.mod(P);}"
            + "public Calculation mul(Calculation other){"+body+"}}";
    }
    static String multiplication() {
        return "BigInteger a=amber.multiply(other.amber); BigInteger b=blue.multiply(other.blue);"
            + "BigInteger c=amber.multiply(other.blue); BigInteger d=blue.multiply(other.amber);"
            + "return new Calculation(reduce(a.subtract(b)),reduce(c.add(d)));";
    }
    CompilationUnit parse(String source) throws Exception {
        Files.writeString(directory.resolve("Calculation.java"), source, StandardCharsets.UTF_8);
        var parser=ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray());
        parser.setUnitName(directory.resolve("Calculation.java").toString()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[0],new String[]{directory.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null);
        for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());return ast;
    }
    MathematicalAnalysis.SourceEnvironment environment() {
        return new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(),List.of(),List.of(directory.toString()),List.of("UTF-8"));
    }
    MathematicalAnalysis.Analysis analyze(String source) throws Exception {
        return MathematicalAnalysis.analyze(parse(source),source,options(),null,-1,0,environment());
    }
    String apply(String source,MathematicalAnalysis.Analysis analysis) throws Exception {
        var document=new Document(source);var undo=analysis.newEdit().apply(document);String generated=document.get();
        undo.apply(document);assertEquals(source,document.get());return generated;
    }
    @Test void wholeMethodSharesBilinearProductsAndPreservesFallbackBehavior() throws Exception {
        String source=fixture(multiplication());var analysis=analyze(source);String generated=apply(source,analysis);
        assertTrue(generated.contains("Mathematics value path"),analysis.diagnostics().toString());
        String branch=generated.substring(generated.indexOf("Mathematics value path"),generated.indexOf(JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER));
        assertEquals(3,branch.split("\\.multiply\\(",-1).length-1,branch);
        assertTrue(generated.contains(multiplication()));
        assertEquals(execute(source),execute(generated));
    }
    @Test void scalarIdentityChangesRequireMathematicalOptInAndDocumentTheContract() throws Exception {
        String source=fixture("BigInteger a=amber.multiply(other.amber); BigInteger b=blue.multiply(other.blue);"
            +"BigInteger c=amber.multiply(other.blue); BigInteger d=blue.multiply(other.amber);"
            +"return new Calculation(reduce(a.subtract(b)),reduce(a.subtract(b).add(c).subtract(c).add(d).subtract(d)));")
            .replace("public Calculation mul", "public static boolean run(BigInteger x){"
                +"Calculation left=of(x,BigInteger.TWO),right=of(BigInteger.valueOf(3),BigInteger.valueOf(5));"
                +"Calculation result=left.mul(right);if(result==left||result==right)throw new AssertionError();"
                +"BigInteger[] leaves=result.parts();return leaves[0]==leaves[1];} public Calculation mul");
        var base=options();var strict=new MathCleanUpOptions(base.enabled(),base.kinds(),base.safety(),base.goal(),
            base.workBudget(),base.maxStates(),base.checkedOptIn(),base.targetJava(),base.exclusions());
        assertFalse(strict.mathematicalOptIn());assertFalse(MathCleanUpOptions.defaults(17).mathematicalOptIn());
        var strictAnalysis=MathematicalAnalysis.analyze(parse(source),source,strict,null,-1,0,environment());
        String preserved=apply(source,strictAnalysis);assertFalse(preserved.contains("Mathematics value path"));
        assertEquals(executeScalar(source),executeScalar(preserved));
        var analysis=analyze(source);String generated=apply(source,analysis);
        assertTrue(generated.contains("Mathematics value path"),analysis.diagnostics().toString());
        assertEquals(execute(source),execute(generated));
        // This is an observed counterexample on the qualification runtime, not a portable allocation premise.
        assertNotEquals(executeScalar(source),executeScalar(generated));
        assertEquals(List.of("true","true","true","true"),executeScalar(generated));
        assertTrue(MathCleanUpOptions.MATHEMATICAL_WARNING.contains("BigInteger result reference identity"));
        assertFalse(analysis.documentation().isEmpty(),"the opted-in identity contract must be attached to the method");
        assertTrue(generated.contains("BigInteger component reference identity may change"),generated);
        assertTrue(analysis.replacements().stream().anyMatch(edit->edit.description().contains("fresh wrapper")));
    }
    @Test void initializerAssignmentCannotDisappearDuringProjection() throws Exception {
        String body="BigInteger a=other.amber; BigInteger first=a=amber.multiply(a); BigInteger second=a.add(a);"
            + "return new Calculation(first.subtract(first),second.add(first).subtract(first));";
        String source=fixture(body);String generated=apply(source,analyze(source));
        assertFalse(generated.contains("Mathematics value path"));
        assertTrue(execute(source).contains("[0, 42]"));
        assertEquals(execute(source),execute(generated));
    }
    @Test void observableConstructorsAndNonFinalClassesStayOutsideValueAdmission() throws Exception {
        for(String source:List.of(fixture(multiplication()).replace("final class","class"),
            fixture(multiplication()).replace("this.amber=a;","System.out.print(a);this.amber=a;"),
            fixture(multiplication()).replace("public Calculation mul", "protected void finalize(){} public Calculation mul"),
            fixture(multiplication()).replace("public Calculation mul", "public synchronized Calculation mul"))) {
            String generated=apply(source,analyze(source));assertFalse(generated.contains("Mathematics value path"),generated);
        }
    }
    @Test void transitiveHelperWritesCannotBeProjectedAway() throws Exception {
        for(String helper:List.of("BigInteger first=x=x.add(x); return first.mod(P);",
            "int shifts=1; return x.shiftLeft(shifts++).mod(P);", "int shifts=1; return x.shiftLeft(--shifts).mod(P);")) {
            String source=fixture(multiplication()).replace("return x.mod(P);",helper);
            String generated=apply(source,analyze(source));assertFalse(generated.contains("Mathematics value path"));
            assertEquals(execute(source),execute(generated));
        }
    }
    static String wrappedMutation(String wrapper) {
        String body="BigInteger k="+wrapper+";"+multiplication().replace("a.subtract(b)","a.subtract(b).add(k.subtract(BigInteger.TWO))");
        return fixture(body).replace("public Calculation mul", "private static BigInteger bad(BigInteger x){"
            +"BigInteger first=x=x.add(x);BigInteger second=x.add(x);return first.subtract(first).add(second);} public Calculation mul");
    }
    @Test void castAndParenthesisWrappedHelperWritesPreserveCompiledOrdinaryApply() throws Exception {
        for(String wrapper:List.of("(BigInteger)bad(BigInteger.ONE)","((BigInteger)(bad(BigInteger.ONE)))","(bad(BigInteger.ONE))")) {
            String source=wrappedMutation(wrapper),generated=apply(source,analyze(source));
            assertEquals("[2, 0]",execute(source).getFirst());
            assertEquals(execute(source),execute(generated));
            assertFalse(generated.contains("Mathematics value path"));
        }
    }
    @Test void scalarHelperInitializerWriteIsNotExpandedThroughOrdinaryApply() throws Exception {
        String source=fixture("return new Calculation(bad(amber),blue);").replace("public Calculation mul", "private static BigInteger bad(BigInteger x){"
            +"BigInteger first=x=x.add(x);BigInteger second=x.add(x);return first.subtract(first).add(second);}"
            +"public static BigInteger run(BigInteger x){return bad(x).subtract(x);} public Calculation mul");
        String generated=apply(source,analyze(source));
        assertEquals(executeScalar(source),executeScalar(generated));
        assertEquals(List.of("0","3","21","-21"),executeScalar(generated));
    }
    private List<String> executeScalar(String source) throws Exception {
        Path output=Files.createTempDirectory(directory,"scalarCompiled");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var method=loader.loadClass("Calculation").getMethod("run",BigInteger.class);var results=new ArrayList<String>();
            for(int value:new int[]{0,1,7,-7})results.add(method.invoke(null,BigInteger.valueOf(value)).toString());return results;
        }
    }
    @Test void unsupportedScalarOperationDoesNotAbortTheRestOfOrdinaryAnalysis() throws Exception {
        String source=fixture(multiplication()).replace("public Calculation mul", "public Calculation unsupported(){return new Calculation(amber.nextProbablePrime(),blue);} public Calculation mul");
        var analysis=assertDoesNotThrow(()->analyze(source));String generated=apply(source,analysis);
        assertTrue(generated.contains("Mathematics value path"),analysis.diagnostics().toString());
        assertTrue(generated.contains("return new Calculation(amber.nextProbablePrime(),blue);"));
        assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("VALUE_UNAVAILABLE")));
    }
    @Test void requestExpiryAfterValuePassDiscardsPreviouslyVerifiedValueEdits() throws Exception {
        String source=fixture(multiplication());assertTrue(apply(source,analyze(source)).contains("Mathematics value path"));
        var base=options();var shortRequest=new MathCleanUpOptions(base.enabled(),base.kinds(),base.safety(),base.goal(),base.workBudget(),base.maxStates(),base.checkedOptIn(),
            base.targetJava(),base.exclusions(),base.mathematicalOptIn(),base.explanations(),5);
        var afterValue=new java.util.concurrent.atomic.AtomicBoolean();
        var monitor=new org.eclipse.core.runtime.NullProgressMonitor(){@Override public void done(){
            afterValue.set(true);assertDoesNotThrow(()->Thread.sleep(5200));
        }};
        var result=MathematicalAnalysis.analyze(parse(source),source,shortRequest,monitor,-1,0,environment());
        assertTrue(afterValue.get());assertFalse(result.changed(),"the expired file request must discard the completed value path");
        assertTrue(result.evidence().isEmpty());assertTrue(result.documentation().isEmpty());
    }
    @Test void sourceModulusDependencyIsRecheckedBeforeApply() throws Exception {
        String source=fixture(multiplication());String declaration=source.substring(source.indexOf("new BigInteger(\""),source.indexOf(";private Calculation"));
        Files.writeString(directory.resolve("Modulus.java"),
				("final class Modulus {static final java.math.BigInteger VALUE="
						+ declaration.replace("new BigInteger", "new java.math.BigInteger") + ";}"),
				StandardCharsets.UTF_8);
        source=source.replace(declaration,"Modulus.VALUE");var result=analyze(source);
        assertTrue(apply(source,result).contains("Mathematics value path"),result.diagnostics().toString());
        assertEquals(1,result.sourceDependencies().receipts().size());
        Files.writeString(directory.resolve("Modulus.java"),
				"final class Modulus {static final java.math.BigInteger VALUE=java.math.BigInteger.ONE;}",
				StandardCharsets.UTF_8);
        assertFalse(result.matches(source,Map.of()));assertThrows(IllegalArgumentException.class,result::newEdit);
    }
    @Test void sourceSummaryAndAllSdkAllocationsShareTheOriginalDeadline() throws Exception {
        String source=fixture(multiplication());var ast=parse(source);var clock=new java.util.concurrent.atomic.AtomicLong(0);
        var work=new JavaValueSourceSummary.Work(1_000_000,2_000_000_000L,()->false,clock::get);
        try(var context=JavaSourceContext.open(ast,source,environment(),null,options())) {
            var methods=((TypeDeclaration)ast.types().getFirst()).getMethods();
            var method=Arrays.stream(methods).filter(m->m.getName().getIdentifier().equals("mul")).findFirst().orElseThrow();
            var summary=new JavaValueSourceSummary.Session(ast,options(),work).method(method,method.getBody());
            var request=summary.request(options(),options().optimizationBudget(6000));
            clock.set(1_500_000_000L);assertEquals(500,work.allocate(6000,options()).timeoutMillis());
            assertSame(work.token(),MathematicalAnalysis.deadline(work.token(),options()));
            assertEquals(500,MathematicalAnalysis.remainingTime(request,work.token()).budget().timeoutMillis());
            long used=work.used;assertEquals(6000,work.allocate(6000,options()).maximumWork());assertEquals(used+6000,work.used);
            clock.set(2_000_000_001L);
            assertThrows(IllegalArgumentException.class,()->new JavaValueSourceSummary.Session(ast,options(),work).method(method,method.getBody()));
            assertThrows(IllegalArgumentException.class,()->work.allocate(6000,options()));
            assertThrows(IllegalArgumentException.class,()->MathematicalAnalysis.remainingTime(request,work.token()));
        }
    }
    @Test void completeActualFp2MulUsesThreeProductsAndKeepsExceptionalInputs() throws Exception {
        Path archive=Path.of(System.getProperty("sandbox.math.bc.sourceArchive")),binary=Path.of(System.getProperty("sandbox.math.bc.jar"));
        assertEquals("b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        Path root=directory.resolve("actual");Files.createDirectories(root.resolve("org/bouncycastle/crypto/bls"));String source;
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            for(String name:List.of("Fp2Element","BLS12_381G1")) {
                String relative="org/bouncycastle/crypto/bls/"+name+".java";
                Files.write(root.resolve(relative),zip.getInputStream(zip.getEntry(relative)).readAllBytes());
            }
        }
        Path file=root.resolve("org/bouncycastle/crypto/bls/Fp2Element.java");source=Files.readString(file, StandardCharsets.UTF_8);
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(source.toCharArray());parser.setUnitName(file.toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{binary.toString()},new String[]{root.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null);for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());
        var environment=new MathematicalAnalysis.SourceEnvironment(file.toString(),List.of(binary.toString()),List.of(root.toString()),List.of("UTF-8"));
        var result=MathematicalAnalysis.analyze(ast,source,options(),null,-1,0,environment);String generated=apply(source,result);
        String mul=generated.substring(generated.indexOf("public Fp2Element mul("),generated.indexOf("public Fp2Element mulFp("));
        assertTrue(mul.contains("Mathematics value path"),result.diagnostics().toString());
        String branch=mul.substring(0,mul.indexOf(JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER));assertEquals(3,branch.split("\\.multiply\\(",-1).length-1,branch);
        assertEquals(executeActual(source,binary),executeActual(generated,binary));
    }
    private List<String> executeActual(String source,Path binary) throws Exception {
        Path output=Files.createTempDirectory(directory,"actualCompiled");Path file=output.resolve("Fp2Element.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-cp",binary.toString(),"-d",output.toString(),file.toString()));
        try(var loader=new IsolatedBC(output,binary)) {
            var type=loader.loadClass("org.bouncycastle.crypto.bls.Fp2Element");var constructor=type.getDeclaredConstructor(BigInteger.class,BigInteger.class);constructor.setAccessible(true);
            var multiply=type.getMethod("mul",type);var c0=type.getMethod("c0");var c1=type.getMethod("c1");var outcomes=new ArrayList<String>();
            BigInteger[] values={BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(-7),BigInteger.ONE.shiftLeft(5000),null,new Derived("9")};
            for(BigInteger a:values)for(BigInteger b:values) {
                Object left=constructor.newInstance(a,b),right=constructor.newInstance(BigInteger.valueOf(3),BigInteger.valueOf(5));
                try{Object result=multiply.invoke(left,right);assertNotSame(left,result);outcomes.add(c0.invoke(result)+":"+c1.invoke(result));}
                catch(java.lang.reflect.InvocationTargetException failure){outcomes.add(failure.getCause().getClass().getName());}
            }
            Object left=constructor.newInstance(BigInteger.ONE,BigInteger.TWO);
            try{multiply.invoke(left,new Object[]{null});fail();}catch(java.lang.reflect.InvocationTargetException failure){outcomes.add(failure.getCause().getClass().getName());}
            return outcomes;
        }
    }
    private List<String> execute(String source) throws Exception {
        Path output=Files.createTempDirectory(directory,"compiled");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var type=loader.loadClass("Calculation");var factory=type.getMethod("of",BigInteger.class,BigInteger.class);
            var multiply=type.getMethod("mul",type);var parts=type.getMethod("parts");var outcomes=new ArrayList<String>();
            BigInteger[] values={BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(-7),BigInteger.valueOf(3),BigInteger.valueOf(7),BigInteger.ONE.shiftLeft(5000),null,new Derived("9")};
            for(BigInteger a:values)for(BigInteger b:values) {
                Object left=factory.invoke(null,a,b),right=factory.invoke(null,BigInteger.valueOf(3),BigInteger.valueOf(5));
                try {Object result=multiply.invoke(left,right);assertNotSame(left,result);outcomes.add(Arrays.toString((BigInteger[])parts.invoke(result)));}
                catch(java.lang.reflect.InvocationTargetException failure){outcomes.add(failure.getCause().getClass().getName());}
            }
            Object left=factory.invoke(null,BigInteger.ONE,BigInteger.TWO);
            try{multiply.invoke(left,new Object[]{null});fail("null receiver argument must fail");}
            catch(java.lang.reflect.InvocationTargetException failure){outcomes.add(failure.getCause().getClass().getName());}
            return outcomes;
        }
    }
    public static class Derived extends BigInteger {
        private static final long serialVersionUID=1L;
        Derived(String value){super(value);}
        @Override public BigInteger multiply(BigInteger value){return super.multiply(value).add(BigInteger.TEN);}
    }
    static final class IsolatedBC extends ClassLoader implements AutoCloseable {
        private final Path classes;
        private final java.util.jar.JarFile dependency;
        IsolatedBC(Path classes,Path binary) throws java.io.IOException {
            super(ClassLoader.getPlatformClassLoader());this.classes=classes;
            dependency=new java.util.jar.JarFile(binary.toFile(),false,java.util.jar.JarFile.OPEN_READ,Runtime.version());
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            String relative=name.replace('.','/')+".class";
            try {
                byte[] bytes;Path file=classes.resolve(relative);
                if(Files.isRegularFile(file))bytes=Files.readAllBytes(file);
                else {var entry=dependency.getJarEntry(relative);if(entry==null)throw new ClassNotFoundException(name);
                    try(var input=dependency.getInputStream(entry)){bytes=input.readAllBytes();}}
                return defineClass(name,bytes,0,bytes.length);
            } catch(java.io.IOException failure){throw new ClassNotFoundException(name,failure);}
        }
        @Override public void close() throws java.io.IOException {dependency.close();}
    }
}
