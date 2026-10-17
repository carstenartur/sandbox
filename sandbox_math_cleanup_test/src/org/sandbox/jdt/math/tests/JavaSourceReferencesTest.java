/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.JavaSourceReferences;
import org.sandbox.jdt.internal.corext.fix.math.JavaSourceRewritePlan;

class JavaSourceReferencesTest {
    private static final String SOURCE = "class Example { int f(int a, int b) { return (a+b)+(a+b); } }";

    @Test void resolvesTypedNodesAndMultipleOriginsWithoutUncheckedCasts() throws Exception {
        var unit = parse();
        try (var references = JavaSourceReferences.open(plan(), unit, SOURCE)) {
            JavaSourceReferences.Reference<InfixExpression> handle = references.reference("first", InfixExpression.class);
            InfixExpression sum = references.get(handle);
            assertEquals(InfixExpression.Operator.PLUS, sum.getOperator());
            assertSame(unit, sum.getRoot());
            var origins = references.origins("shared");
            assertEquals(2, origins.size());
            assertNotSame(references.get(origins.get(0)), references.get(origins.get(1)));
            assertEquals(2, references.size());
        }
    }
    @Test void rejectsWrongTypesAndUnknownIds() throws Exception {
        try (var references = JavaSourceReferences.open(plan(), parse(), SOURCE)) {
            assertThrows(IllegalArgumentException.class, () -> references.reference("first", MethodInvocation.class));
            assertThrows(IllegalArgumentException.class, () -> references.reference("missing", ASTNode.class));
            assertThrows(IllegalArgumentException.class, () -> references.origins("missing"));
        }
    }
    @Test void rejectsHandlesFromAnotherScopeEvenForTheSameSource() throws Exception {
        var plan = plan();
        try (var first = JavaSourceReferences.open(plan, parse(), SOURCE);
                var second = JavaSourceReferences.open(plan, parse(), SOURCE)) {
            var handle = first.reference("first", InfixExpression.class);
            assertThrows(IllegalArgumentException.class, () -> second.get(handle));
        }
    }
    @Test void rejectsDifferentSourceSnapshots() throws Exception {
        var plan = plan();
        var unit = parse();
        assertThrows(IllegalArgumentException.class, () -> JavaSourceReferences.open(plan, unit, SOURCE + " "));
    }
    @Test void rejectsAstMutationAfterResolution() throws Exception {
        try (var references = JavaSourceReferences.open(plan(), parse(), SOURCE)) {
            var handle = references.reference("first", InfixExpression.class);
            references.get(handle).setOperator(InfixExpression.Operator.MINUS);
            assertThrows(IllegalStateException.class, () -> references.get(handle));
        }
    }
    @Test void closeReleasesTheTableAndInvalidatesOutstandingHandles() throws Exception {
        var references = JavaSourceReferences.open(plan(), parse(), SOURCE);
        var handle = references.reference("first", InfixExpression.class);
        references.close();
        references.close();
        assertEquals(0, references.size());
        assertThrows(IllegalStateException.class, () -> references.get(handle));
        assertThrows(IllegalStateException.class, () -> references.origins("shared"));
    }
    private static CompilationUnit parse() {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(SOURCE.toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }
    private static JavaSourceRewritePlan plan() throws Exception {
        int first = SOURCE.indexOf("a+b"), second = SOURCE.lastIndexOf("a+b");
        var anchors = List.of(
                new JavaSourceRewritePlan.Node("first", first, 3, ASTNode.INFIX_EXPRESSION, "", hash("a+b")),
                new JavaSourceRewritePlan.Node("second", second, 3, ASTNode.INFIX_EXPRESSION, "", hash("a+b")));
        return new JavaSourceRewritePlan("Example.java", hash(SOURCE), SOURCE.length(), 0, SOURCE.length(),
                anchors, List.of(), Map.of("shared", List.of("first", "second")), List.of());
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
