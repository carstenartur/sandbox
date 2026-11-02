/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaRecursiveValueSummaryTest {
    @TempDir Path directory;
    private static final String PACKAGE="org.bouncycastle.crypto.bls.";
    private static final List<String> NAMES=List.of("Fp2Element","Fp6Element","Fp12Element","BLS12_381G1");
    private Path binary;
    @Test void suppliedFieldProjectionRequiresTheEstablishedReceiver() throws Exception {
        String source=JavaImmutableValueTest.fixture("return new Calculation(amber,other.amber);");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaImmutableValueTest.options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var type=(TypeDeclaration)ast.types().getFirst();
            var method=Arrays.stream(type.getMethods()).filter(m->m.getName().getIdentifier().equals("mul")).findFirst().orElseThrow();
            var creation=(ClassInstanceCreation)((ReturnStatement)method.getBody().statements().getFirst()).getExpression();
            var field=(SimpleName)creation.arguments().getFirst();
            var known=new JavaValueSourceSummary.Scalar(JavaExpressions.literal(BigInteger.ONE));
            Map<String,JavaValueSourceSummary.Term> frame=Map.of(((IVariableBinding)field.resolveBinding()).getKey(),known);
            var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
            assertEquals(known,session.project(field,frame));
            assertThrows(IllegalArgumentException.class,()->session.project((Expression)creation.arguments().get(1),frame));
        }
    }
    @Test void wrappedScalarHelpersStillRequireExplicitSessionEffectAdmission() throws Exception {
        for(String wrapper:List.of("(BigInteger)bad(BigInteger.ONE)","((BigInteger)(bad(BigInteger.ONE)))","(bad(BigInteger.ONE))")) {
            String source=JavaImmutableValueTest.wrappedMutation(wrapper);var fixture=new JavaImmutableValueTest();fixture.directory=directory;
            var ast=fixture.parse(source);var options=JavaImmutableValueTest.options();
            try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
                var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
                var method=Arrays.stream(((TypeDeclaration)ast.types().getFirst()).getMethods()).filter(m->m.getName().getIdentifier().equals("mul")).findFirst().orElseThrow();
                assertThrows(IllegalArgumentException.class,()->session.method(method,method.getBody()));
            }
        }
    }
    private Path sources(boolean renamed) throws Exception {
        Path archive=Path.of(System.getProperty("sandbox.math.bc.sourceArchive"));binary=Path.of(System.getProperty("sandbox.math.bc.jar"));
        assertEquals("b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        Path root=directory.resolve(renamed?"renamed":"actual");Files.createDirectories(root.resolve("org/bouncycastle/crypto/bls"));
        try(var archiveFile=new java.util.zip.ZipFile(archive.toFile())) {
            for(String name:NAMES) {
                String relative="org/bouncycastle/crypto/bls/"+name+".java";
                String source=new String(archiveFile.getInputStream(archiveFile.getEntry(relative)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                if(renamed)source=rename(source);
                Files.writeString(root.resolve("org/bouncycastle/crypto/bls/"+(renamed?rename(name):name)+".java"),source);
            }
        }
        return root;
    }
    private static String rename(String source) {
        return source.replace("Fp12Element","Canopy").replace("Fp6Element","Branch").replace("Fp2Element","Leaf")
            .replace("NON_RESIDUE","TWIST").replace("mulByV","rotate").replace(" mul("," combine(").replace(".mul(",".combine(")
            .replaceAll("\\bc0\\b", "amber").replaceAll("\\bc1\\b","blue").replaceAll("\\bc2\\b","cyan");
    }
    private record Receipt(JavaValueSourceSummary.Summary summary,JavaSourceContext.Checkpoint checkpoint) { }
    private Receipt summary(Path root,boolean renamed) throws Exception {
        String type=renamed?"Canopy":"Fp12Element",name=renamed?"combine":"mul";
        Path file=root.resolve("org/bouncycastle/crypto/bls/"+type+".java");String source=Files.readString(file);
        var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(source.toCharArray());parser.setUnitName(file.toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{binary.toString()},new String[]{root.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null);for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());
        var environment=new MathematicalAnalysis.SourceEnvironment(file.toString(),List.of(binary.toString()),List.of(root.toString()),List.of("UTF-8"));
        var options=JavaImmutableValueTest.options();
        try(var context=JavaSourceContext.open(ast,source,environment,null,options)) {
            var method=Arrays.stream(((TypeDeclaration)ast.types().getFirst()).getMethods()).filter(m->m.getName().getIdentifier().equals(name)).findFirst().orElseThrow();
            var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
            return new Receipt(session.method(method,method.getBody()),context.checkpoint());
        }
    }
    @Test void actualCompleteTowerHasTwentyFourInputsAndTwelveOutputsAndReplays() throws Exception {
        Path root=sources(false);var result=assertDoesNotThrow(()->summary(root,false)).summary();
        assertEquals(24,result.inputs().size());assertEquals(12,result.plan().outputs().size());
        assertEquals(12,result.value().shape().leaves());
        assertTrue(result.staticValues().stream().anyMatch(value->value.field().getName().equals("NON_RESIDUE") && value.value() instanceof JavaValueSourceSummary.Value));
        replay(root,false,result);
    }
    @Test void structuralRenamingDoesNotChangeRecursiveAdmissionOrReplay() throws Exception {
        Path root=sources(true);var result=assertDoesNotThrow(()->summary(root,true)).summary();
        assertEquals(24,result.inputs().size());assertEquals(12,result.plan().outputs().size());replay(root,true,result);
    }
    @Test void transitiveBodyChangeInvalidatesReceiptAndChangesFingerprint() throws Exception {
        Path root=sources(false);var first=summary(root,false);Path helper=root.resolve("org/bouncycastle/crypto/bls/Fp2Element.java");
        Files.writeString(helper,Files.readString(helper).replace("ac.subtract(bd)","ac.add(bd)"));
        assertFalse(first.checkpoint().current());assertNotEquals(first.summary().sourceFingerprint(),summary(root,false).summary().sourceFingerprint());
    }
    @Test void staticInitializerArithmeticIsMetadataRatherThanMethodRuntimeTrace() throws Exception {
        String source="import java.math.BigInteger; final class Calculation {private final BigInteger value;"
            +"private Calculation(BigInteger value){this.value=value;} static final Calculation ONE=make(BigInteger.ONE);"
            +"private static Calculation make(BigInteger x){return new Calculation(x.add(BigInteger.ZERO));}}";
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaImmutableValueTest.options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
            var type=(TypeDeclaration)ast.types().getFirst();var field=Arrays.stream(type.resolveBinding().getDeclaredFields()).filter(f->f.getName().equals("ONE")).findFirst().orElseThrow();
            var summary=session.summary((JavaValueSourceSummary.Value)session.constant(field));
            assertTrue(summary.trace().occurrences().isEmpty(),"a static read must not replay class-initializer arithmetic");
            assertEquals(BigInteger.ONE,ComputationOptimizer.prepare(summary.plan()).execute(Map.of()).get("component0"));
            assertEquals(1,summary.staticValues().size());
        }
    }
    @Test void recursiveShapeBoundsRejectCyclesExcessLeavesAndExcessDepth() throws Exception {
        for(String source:List.of(
            "final class Calculation {private final Calculation next; private Calculation(Calculation next){this.next=next;}}",
            shapeTree(9,1),shapeTree(3,8),shapeTree(1,9))) {
            var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaImmutableValueTest.options();
            try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
                var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
                var type=(TypeDeclaration)ast.types().getLast();
                assertThrows(IllegalArgumentException.class,()->session.shape(type.resolveBinding()));
            }
        }
    }
    @Test void freshDefinitionMustStillBeStaticForAStaticValueReceipt() throws Exception {
        String holder="final class Holder {static final Calculation VALUE=Calculation.of(java.math.BigInteger.ONE,java.math.BigInteger.ZERO);}";
        Files.writeString(directory.resolve("Holder.java"),holder);
        String source=JavaImmutableValueTest.fixture(JavaImmutableValueTest.multiplication()).replace("public BigInteger[] parts()", "public static Calculation constant(){return Holder.VALUE;} public BigInteger[] parts()");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaImmutableValueTest.options();
        var fields=new ArrayList<IVariableBinding>();ast.accept(new ASTVisitor(){@Override public boolean visit(QualifiedName name){
            if(name.toString().equals("Holder.VALUE"))fields.add((IVariableBinding)name.resolveBinding());return true;}});
        Files.writeString(directory.resolve("Holder.java"),holder.replace("static final","final"));
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
            assertThrows(IllegalArgumentException.class,()->session.constant(fields.getFirst()));
        }
    }
    @Test void nestedScalarCallsCannotBypassTheDecoderExpressionDepthLimit() throws Exception {
        String expression="amber"+".add(blue)".repeat(128);
        String source=JavaImmutableValueTest.fixture("return new Calculation("+expression+",blue);");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);var options=JavaImmutableValueTest.options();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var session=new JavaValueSourceSummary.Session(ast,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
            var method=Arrays.stream(((TypeDeclaration)ast.types().getFirst()).getMethods()).filter(m->m.getName().getIdentifier().equals("mul")).findFirst().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->session.method(method,method.getBody()));
        }
    }
    private static String shapeTree(int depth,int width) {
        var source=new StringBuilder("import java.math.BigInteger;");
        for(int level=0;level<depth;level++) {
            String type=level==depth-1?"Calculation":"Node"+level,child=level==0?"BigInteger":"Node"+(level-1);
            source.append("final class ").append(type).append("{");
            for(int field=0;field<width;field++)source.append("private final ").append(child).append(" f").append(field).append(';');
            source.append("private ").append(type).append('(');
            for(int field=0;field<width;field++){if(field>0)source.append(',');source.append(child).append(" p").append(field);}
            source.append("){");for(int field=0;field<width;field++)source.append("this.f").append(field).append("=p").append(field).append(';');source.append("}}");
        }
        return source.toString();
    }
    private void replay(Path root,boolean renamed,JavaValueSourceSummary.Summary summary) throws Exception {
        Path output=Files.createTempDirectory(directory,"compiled");var command=new ArrayList<>(List.of("--release","17","-cp",binary.toString(),"-d",output.toString()));
        for(String name:NAMES.subList(0,3))command.add(root.resolve("org/bouncycastle/crypto/bls/"+(renamed?rename(name):name)+".java").toString());
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,command.toArray(String[]::new)));
        var prepared=ComputationOptimizer.prepare(summary.plan());
        try(var loader=new JavaImmutableValueTest.IsolatedBC(output,binary)) {
            var type=loader.loadClass(PACKAGE+(renamed?"Canopy":"Fp12Element"));var method=type.getMethod(renamed?"combine":"mul",type);
            for(int example=0;example<5;example++) {
                var values=new ArrayList<BigInteger>();var inputs=new LinkedHashMap<String,Object>();
                for(int i=0;i<24;i++) {BigInteger value=example==0?BigInteger.ZERO:BigInteger.valueOf((i+1L)*(example+3)*(i%2==0?1:-1));values.add(value);inputs.put(summary.inputs().get(i).id(),value);}
                Object left=construct(type,values.subList(0,12).iterator()),right=construct(type,values.subList(12,24).iterator());
                var expected=new ArrayList<BigInteger>();flatten(method.invoke(left,right),expected);
                var actual=prepared.execute(inputs);for(int i=0;i<12;i++)assertEquals(expected.get(i),actual.get("component"+i),"case"+example+" leaf"+i);
                if(example==0)assertEquals(Collections.nCopies(12,BigInteger.ZERO),expected);
            }
        }
    }
    private static Object construct(Class<?> type,Iterator<BigInteger> values) throws Exception {
        if(type==BigInteger.class)return values.next();var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var arguments=new ArrayList<Object>();for(var parameter:constructor.getParameterTypes())arguments.add(construct(parameter,values));
        return constructor.newInstance(arguments.toArray());
    }
    private static void flatten(Object value,List<BigInteger> leaves) throws Exception {
        if(value instanceof BigInteger integer){leaves.add(integer);return;}
        for(var field:value.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())){field.setAccessible(true);flatten(field.get(value),leaves);}
    }
}
