/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;

import org.eclipse.jdt.core.dom.*;

import de.regelsuche.math.algorithms.modular.MontgomerySchedule.Parameters;

/** Two bounded source grammars with the same array accesses and the same exact observation. */
final class JavaMontgomeryDotPattern {
    private JavaMontgomeryDotPattern() { }

    record Site(MethodDeclaration method, Type sumType, MethodInvocation reducer, Expression product,
            Expression observed, String reducerKey, Parameters parameters, int count) { }

    static boolean candidate(MethodDeclaration method) {
        var binding = method.resolveBinding();
        // Static entry has already triggered initialization of the reducer owner. A default
        // interface method can run before its interface is initialized during recursive initialization.
        return binding != null && !binding.isRecovered() && Modifier.isStatic(binding.getModifiers())
                && type(binding.getReturnType(), "int") //$NON-NLS-1$
                && binding.getParameterTypes().length == 2 && type(binding.getParameterTypes()[0], "short[]") //$NON-NLS-1$
                && type(binding.getParameterTypes()[1], "short[]") && method.getBody() != null //$NON-NLS-1$
                && method.getBody().statements().size() == 3 && method.getBody().getLength() <= 4096
                && method.getBody().statements().get(1) instanceof ForStatement;
    }

    /**
     * Whole-block matching excludes extra reads/writes, guards, calls, exit paths and observations.
     * Bindings identify the actual reducer declaration and JDK observer. The two short[] parameters
     * may alias and may be short or null: both loops evaluate a[i], then b[i], at each index.
     */
    static Site read(CompilationUnit unit, MethodDeclaration method, boolean delayed) {
        require(candidate(method));
        var body = method.getBody();
        var sum = as(body.statements().getFirst(), VariableDeclarationStatement.class);
        var accumulator = fragment(sum.fragments());
        var loop = as(body.statements().get(1), ForStatement.class);
        var initial = as(one(loop.initializers()), VariableDeclarationExpression.class);
        var index = fragment(initial.fragments());
        var count = as(as(loop.getExpression(), InfixExpression.class).getRightOperand(), NumberLiteral.class);
        int terms = integer(count);
        require(terms >= 2 && terms <= 64);
        var statement = as(one(as(loop.getBody(), Block.class).statements()), ExpressionStatement.class);
        var addition = as(statement.getExpression(), Assignment.class);
        var returned = as(body.statements().getLast(), ReturnStatement.class);
        var canonical = as(returned.getExpression(), MethodInvocation.class);
        var canonicalBinding = canonical.resolveMethodBinding();
        require(canonical.arguments().size() == 2 && canonical.getExpression() instanceof SimpleName receiver
                && receiver.resolveBinding() instanceof ITypeBinding math && !math.isFromSource()
                && type(math, "java.lang.Math") && canonicalBinding != null && !canonicalBinding.isRecovered() //$NON-NLS-1$
                && math.isEqualTo(canonicalBinding.getDeclaringClass()) && Modifier.isStatic(canonicalBinding.getModifiers())
                && canonicalBinding.getParameterTypes().length == 2 && type(canonicalBinding.getParameterTypes()[0], "int") //$NON-NLS-1$
                && type(canonicalBinding.getParameterTypes()[1], "int") && type(canonicalBinding.getReturnType(), "int")); //$NON-NLS-1$ //$NON-NLS-2$
        var modulus = as(canonical.arguments().get(1), NumberLiteral.class);
        var observed = (Expression)canonical.arguments().getFirst();
        var reducer = as(delayed ? observed : addition.getRightHandSide(), MethodInvocation.class);
        var reduceArgument = as(one(reducer.arguments()), Expression.class);
        var product = delayed ? addition.getRightHandSide() : reduceArgument;
        var reduceBinding = reducer.resolveMethodBinding();
        require(reduceBinding != null && !reduceBinding.isRecovered()
                && reduceBinding.getDeclaringClass().isEqualTo(method.resolveBinding().getDeclaringClass())
                && Modifier.isStatic(reduceBinding.getModifiers()) && !Modifier.isSynchronized(reduceBinding.getModifiers())
                && reduceBinding.getParameterTypes().length == 1 && type(reduceBinding.getParameterTypes()[0], "long") //$NON-NLS-1$
                && type(reduceBinding.getReturnType(), "int")); //$NON-NLS-1$
        var helper = as(unit.findDeclaringNode(reduceBinding.getMethodDeclaration()), MethodDeclaration.class);
        require(helper.getParent() == method.getParent());
        Parameters parameters = parameters(helper, modulus);

        String sumName = accumulator.getName().getIdentifier(), i = index.getName().getIdentifier();
        String red = reducer.getName().getIdentifier();
        String multiply = "(long)" + parameter(method, 0) + "[" + i + "] * " + parameter(method, 1) + "[" + i + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        String template = """
                %s %s = 0;
                for (int %s = 0; %s < %s; ++%s) { %s += %s; }
                return Math.floorMod(%s, %s);
                """.formatted(delayed ? "long" : "int", sumName, i, i, count.getToken(), i, sumName, //$NON-NLS-1$ //$NON-NLS-2$
                        delayed ? multiply : red + "(" + multiply + ")", //$NON-NLS-1$ //$NON-NLS-2$
                        delayed ? red + "(" + sumName + ")" : sumName, modulus.getToken()); //$NON-NLS-1$ //$NON-NLS-2$
        match(body, template);
        return new Site(method, sum.getType(), reducer, product, observed, reduceBinding.getMethodDeclaration().getKey(),
                parameters, terms);
    }

    private static Parameters parameters(MethodDeclaration helper, NumberLiteral modulus) {
        var body = helper.getBody();
        require(body != null && body.getLength() <= 1024 && body.statements().size() == 2);
        var low = fragment(as(body.statements().getFirst(), VariableDeclarationStatement.class).fragments());
        var inverse = as(as(low.getInitializer(), InfixExpression.class).getRightOperand(), NumberLiteral.class);
        String argument = parameter(helper, 0);
        match(body, """
                int %s = (int)%s * %s;
                return (int)((%s - (long)%s * %s) >> 32);
                """.formatted(low.getName().getIdentifier(), argument, inverse.getToken(),
                        argument, low.getName().getIdentifier(), modulus.getToken()));
        return new Parameters(32, integer(modulus), Integer.toUnsignedLong(integer(inverse)));
    }

    private static String parameter(MethodDeclaration method, int index) {
        return ((SingleVariableDeclaration)method.parameters().get(index)).getName().getIdentifier();
    }

    private static VariableDeclarationFragment fragment(List<?> fragments) {
        return as(one(fragments), VariableDeclarationFragment.class);
    }

    private static Object one(List<?> values) {
        require(values.size() == 1);
        return values.getFirst();
    }

    private static int integer(NumberLiteral literal) {
        return as(literal.resolveConstantExpressionValue(), Integer.class);
    }

    private static void match(Block block, String statements) {
        var parser = ASTParser.newParser(block.getAST().apiLevel());
        parser.setKind(ASTParser.K_STATEMENTS);
        parser.setSource(statements.toCharArray());
        var pattern = parser.createAST(null);
        require(pattern instanceof Block expected && (expected.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) == 0
                && block.subtreeMatch(new ASTMatcher(), expected));
    }

    private static boolean type(ITypeBinding binding, String name) {
        return binding != null && !binding.isRecovered() && binding.getQualifiedName().equals(name);
    }

    private static <T> T as(Object node, Class<T> expected) {
        require(expected.isInstance(node));
        return expected.cast(node);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("MONTGOMERY_SOURCE_SHAPE"); //$NON-NLS-1$
    }
}
