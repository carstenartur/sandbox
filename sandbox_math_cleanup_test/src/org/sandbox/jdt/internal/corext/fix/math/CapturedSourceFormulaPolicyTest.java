/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;

class CapturedSourceFormulaPolicyTest {
    @Test void constantFormulaInsideAWholeSourceReadDoesNotExcludeTheRuntimeStatement() {
        assertFalse(preserve("return words[2 * 3] + value;"));
        assertFalse(preserve("return buffer.get(2 * 3) + value;"));
    }
    @Test void ordinaryConstantFormulasRemainProtected() {
        assertTrue(preserve("return value + (2 * 3);"));
        assertTrue(preserve("return 2 * 3;"));
        assertTrue(preserve("int constant = 2 * 3; return value;"));
        assertFalse(preserve("return value * -2;"));
    }
    private static boolean preserve(String body) {
        String source = "class Calculation { int f(int[] words, java.nio.IntBuffer buffer, int value) {" + body + "}}";
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setUnitName("Calculation.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        var options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        var type = (TypeDeclaration) unit.types().getFirst();
        return RuntimeCalculationPolicy.preserve((Statement) type.getMethods()[0].getBody().statements().getFirst(), Map.of());
    }
}
