/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaExpressionNormalizerTest {
    @Test void unchangedGeneratedChainRetainsEveryNodeInsteadOfCloningEverySubtree() {
        Expression generated = parse("x" + ".multiply(x).mod(P)".repeat(40));
        Map<ASTNode, ASTNode> before = parents(generated);
        assertSame(generated, JavaExpressionNormalizer.normalize(generated, Map.of()));
        Map<ASTNode, ASTNode> after = parents(generated);
        assertEquals(before.size(), after.size());
        before.forEach((node, parent) -> {
            assertTrue(after.containsKey(node), "Unchanged generated nodes must be reused");
            assertSame(parent, after.get(node));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "x.multiply(y)", "new java.math.BigInteger(\"4\")", "42", "\"text\""})
    void removesRedundantParenthesesWithoutCopyingTheRetainedExpression(String code) {
        var outer = (ParenthesizedExpression) parse("(((" + code + ")))" );
        Expression inner = outer.getExpression();
        while (inner instanceof ParenthesizedExpression parentheses) inner = parentheses.getExpression();
        Expression result = JavaExpressionNormalizer.normalize(outer, Map.of());
        assertSame(inner, result);
        assertNull(result.getParent(), "A returned root must be detached before its caller attaches it");
        assertTrue(parse(code).subtreeMatch(new ASTMatcher(), result));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(x + y) * z", "((T) x).toString()", "(flag ? x : y).toString()", "(-1)", "(x = y)"})
    void retainsParenthesesRequiredForPrecedenceOrOutsideTheExistingWhitelist(String code) {
        Expression input = parse(code);
        Expression expected = (Expression) ASTNode.copySubtree(input.getAST(), input);
        Expression result = JavaExpressionNormalizer.normalize(input, Map.of());
        assertTrue(expected.subtreeMatch(new ASTMatcher(), result), result.toString());
        assertSame(input, result);
    }

    @Test void literalReplacementCopiesFromSourceWithoutChangingItsAstOrParent() {
        var sourceParent = (MethodInvocation) parse("x.add(BigInteger.valueOf(4L))");
        Expression sourceLiteral = (Expression) sourceParent.arguments().getFirst();
        String original = sourceParent.toString();
        var generated = (MethodInvocation) parse("x.add(new java.math.BigInteger(\"4\"))");
        Expression result = JavaExpressionNormalizer.normalize(generated, Map.of(BigInteger.valueOf(4), sourceLiteral));
        Expression replacement = (Expression) ((MethodInvocation) result).arguments().getFirst();
        assertNotSame(sourceLiteral, replacement);
        assertSame(generated.getAST(), replacement.getAST());
        assertSame(sourceParent, sourceLiteral.getParent());
        assertEquals(original, sourceParent.toString());
        assertTrue(sourceLiteral.subtreeMatch(new ASTMatcher(), replacement));
        assertSame(generated, result);
    }

    @Test void changesInChildListsKeepOrderAndCorrectParentLinks() {
        var generated = (MethodInvocation) parse("f((x), ((y.multiply(z))), new java.math.BigInteger(\"4\"))");
        Expression sourceLiteral = parse("BigInteger.valueOf(4)");
        Expression result = JavaExpressionNormalizer.normalize(generated, Map.of(BigInteger.valueOf(4), sourceLiteral));
        assertTrue(parse("f(x, y.multiply(z), BigInteger.valueOf(4))").subtreeMatch(new ASTMatcher(), result));
        assertSame(generated, result);
        parents(result).forEach((node, parent) -> {
            assertSame(result.getAST(), node.getAST());
            assertSame(parent, node.getParent());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"new java.math.BigInteger(\"not-a-number\")",
            "new java.math.BigInteger(\"ff\", 16)", "new BigInteger(\"4\")",
            "new java.math.BigInteger(\"4\") {}"})
    void unsupportedLiteralFormsStayUnchanged(String code) {
        Expression input = parse(code);
        Expression expected = (Expression) ASTNode.copySubtree(input.getAST(), input);
        Expression result = JavaExpressionNormalizer.normalize(input,
                Map.of(BigInteger.valueOf(4), parse("BigInteger.valueOf(4)")));
        assertTrue(expected.subtreeMatch(new ASTMatcher(), result));
    }

    @Test void depthAllowanceIsNotIncreased() {
        assertThrows(IllegalArgumentException.class,
                () -> JavaExpressionNormalizer.normalize(parse("(".repeat(129) + "x" + ")".repeat(129)), Map.of()));
        assertEquals("x", JavaExpressionNormalizer.normalize(
                parse("(".repeat(128) + "x" + ")".repeat(128)), Map.of()).toString());
    }

    private static Map<ASTNode, ASTNode> parents(ASTNode root) {
        Map<ASTNode, ASTNode> result = new IdentityHashMap<>();
        root.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                assertFalse(result.containsKey(node), "A node must appear only once in the generated tree");
                result.put(node, node.getParent());
            }
        });
        return result;
    }

    private static Expression parse(String code) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_EXPRESSION);
        parser.setSource(code.toCharArray());
        ASTNode result = parser.createAST(null);
        assertInstanceOf(Expression.class, result, code);
        assertEquals(0, result.getFlags() & ASTNode.MALFORMED, code);
        return (Expression) result;
    }
}
