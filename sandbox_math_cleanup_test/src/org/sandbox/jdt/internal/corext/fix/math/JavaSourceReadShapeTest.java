/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;

class JavaSourceReadShapeTest {
    @Test void establishedHawkScalarWindowRemainsAvailableBeforeNewQualifiedArrayReads() throws Exception {
        String root=System.getProperty("sandbox.math.bc.mavenSources");
        String binary=System.getProperty("sandbox.math.bc.mavenBinary");
        String path="org/bouncycastle/pqc/crypto/hawk/HawkEngine.java", source;
        try(var jar=new java.util.jar.JarFile(java.util.Objects.requireNonNull(root))) {
            try(var input=jar.getInputStream(jar.getJarEntry(path))) {source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        }
        var parser=ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName(path); parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{java.util.Objects.requireNonNull(binary)},new String[0],null,true);
        var compiler=new HashMap<String,String>(); JavaCore.setComplianceOptions("17",compiler); parser.setCompilerOptions(compiler);
        var unit=(CompilationUnit)parser.createAST(null);
        for(var problem:unit.getProblems()) assertFalse(problem.isError(),problem.toString());
        var options=new MathCleanUpOptions(true,java.util.EnumSet.allOf(de.regelsuche.sdk.optimization.NumericKind.class),de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA,
                de.regelsuche.sdk.optimization.OptimizationGoal.READABILITY,1_000_000,20_000,false,17,List.of());
        int start=source.indexOf("int logn = lognTop - depth;"), end=source.indexOf("int slen = prof.maxBlSmall[depth];",start);
        var primary=new JavaComputationExtractor().extractRuntimeWithoutStores(unit,source,options);
        var window=primary.regions().stream().filter(r -> r.start()==start).findFirst().orElseThrow();
        assertEquals(source.substring(start,end).stripTrailing(),source.substring(window.start(),window.start()+window.length()));
        assertTrue(window.arrayReads().isEmpty());
        var rich=new JavaComputationExtractor().extractRuntime(unit,source,options);
        assertTrue(rich.regions().stream().anyMatch(r -> r.start()==start && r.arrayReads().size()==2));
    }
    @Test void supportsLocalAndNonvolatileFieldArraysWithTotalIndices() {
        for (String expression : List.of("values[i]", "data[i]", "this.data[i]", "owner.data[i]", "owner.parent.data[i]", "values[CONST * 2]", "values[Calculation.CONST << 1]", "values[(int)((long)i + 1)]"))
            assertTrue(supported(expression), expression);
        for (String expression : List.of("volatileData[i]", "this.volatileData[i]", "values[i++]", "values[next()]", "values[i / j]", "values[i % j]", "values[volatileIndex]", "values[STATIC_FINAL]", "values[owner.CONST]"))
            assertFalse(supported(expression), expression);
    }
    @Test void recognizesOnlyTheBoundJdkAbsoluteGetSignature() {
        for (String name : List.of("bytes", "shorts", "chars", "ints", "longs")) {
            assertTrue(supported(name + ".get(CONST * 2)"));
            assertFalse(supported(name + ".get()"));
            assertFalse(supported(name + ".get(i++)"));
            assertFalse(supported(name + ".get(volatileIndex)"));
        }
        assertFalse(supported("owner.get(i)"));
        assertFalse(supported("buffers.get(i)")); // a field receiver, not a fixed local/parameter
    }
    @Test void nonvolatilePrimitiveFieldsKeepTheOriginalReceiverOrClassAccess() {
        for (String expression : List.of("dynamic", "this.dynamic", "owner.dynamic", "owner.parent.dynamic", "mutable", "owner.mutable", "STATIC_FINAL", "Calculation.STATIC_FINAL"))
            assertTrue(supported(expression), expression);
        for (String expression : List.of("volatileIndex", "nextOwner().dynamic", "CONST", "owner.CONST"))
            assertFalse(supported(expression), expression);
    }
    @Test void producingAnIndexBeforeTheCaptureRequiresARegionBoundary() {
        Expression read = expression("ints.get(i)");
        MethodInvocation call = (MethodInvocation) read;
        String key = ((IVariableBinding) ((SimpleName) call.arguments().getFirst()).resolveBinding()).getKey();
        assertFalse(JavaSourceReadShape.supported(read, Set.of(key)));
        assertTrue(JavaSourceReadShape.requiresBoundary((Statement) read.getParent(), Set.of(key), true));
        assertTrue(JavaSourceReadShape.requiresBoundary((Statement) read.getParent(), Set.of(), false));
        assertFalse(JavaSourceReadShape.requiresBoundary((Statement) read.getParent(), Set.of(), true));
    }
    @Test void producingAnObjectReceiverBeforeTheCaptureRequiresARegionBoundary() {
        Expression read = expression("owner.dynamic");
        String key = ((IVariableBinding) ((QualifiedName) read).getQualifier().resolveBinding()).getKey();
        assertFalse(JavaSourceReadShape.supported(read, Set.of(key)));
        assertTrue(JavaSourceReadShape.requiresBoundary((Statement) read.getParent(), Set.of(key), true));
    }
    private static boolean supported(String expression) {
        return JavaSourceReadShape.supported(expression(expression), Set.of());
    }
    private static Expression expression(String expression) {
        String source = "class Calculation { static final int CONST=4; static final int STATIC_FINAL=Integer.parseInt(\"7\");"
                + " final int dynamic=Integer.parseInt(\"9\"); int mutable; static volatile int volatileIndex; int[] data; volatile int[] volatileData;"
                + " Calculation parent; java.nio.IntBuffer buffers; int get(int x){return x;} int next(){return 0;} Calculation nextOwner(){return this;}"
                + " long calculate(int[] values, int i, int j, Calculation owner, java.nio.ByteBuffer bytes, java.nio.ShortBuffer shorts,"
                + " java.nio.CharBuffer chars, java.nio.IntBuffer ints, java.nio.LongBuffer longs) { return " + expression + "; }}";
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName("Calculation.java"); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        var type = (TypeDeclaration) unit.types().getFirst();
        MethodDeclaration method = java.util.Arrays.stream(type.getMethods())
                .filter(m -> m.getName().getIdentifier().equals("calculate")).findFirst().orElseThrow();
        return ((ReturnStatement) method.getBody().statements().getFirst()).getExpression();
    }
}
