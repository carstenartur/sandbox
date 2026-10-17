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
    @Test void supportsOnlyOneDimensionalLocalOrUnqualifiedFieldArraysAndTotalIndices() {
        for (String expression : List.of("values[i]", "values[CONST * 2]", "values[Calculation.CONST << 1]", "values[(int)((long)i + 1)]",
                "data[i]", "this.data[i]"))
            assertTrue(supported(expression), expression);
        for (String expression : List.of("owner.data[i]", "values[i++]", "values[next()]", "values[i / j]", "values[i % j]", "values[volatileIndex]", "values[STATIC_FINAL]", "values[owner.CONST]"))
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
    @Test void finalPrimitiveFieldsKeepTheOriginalReceiverOrClassAccess() {
        for (String expression : List.of("dynamic", "this.dynamic", "owner.dynamic", "STATIC_FINAL", "Calculation.STATIC_FINAL"))
            assertTrue(supported(expression), expression);
        for (String expression : List.of("mutable", "owner.mutable", "volatileIndex", "nextOwner().dynamic", "owner.parent.dynamic", "CONST", "owner.CONST"))
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
                + " final int dynamic=Integer.parseInt(\"9\"); int mutable; static volatile int volatileIndex; int[] data;"
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
