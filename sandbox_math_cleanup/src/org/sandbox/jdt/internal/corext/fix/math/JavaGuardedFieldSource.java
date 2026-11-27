/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram.Arm;
import de.regelsuche.math.algorithms.modular.QuadraticRootProgram.Step;

/** Extracts a bounded, two-exit scalar program. Canonicality is tracked separately from the field DAG:
 * a Java equals or a factory argument may observe an unreduced integer. */
final class JavaGuardedFieldSource {
    static final String BIG = "java.math.BigInteger"; //$NON-NLS-1$
    record Scalar(int register, boolean canonical, int bits) {
        Scalar { require(bits <= 16_384); }
    }
    record Ref(String spelling, String key, BigInteger value) {}
    final CompilationUnit unit;
    final JavaValueSourceSummary.Work work;
    final MethodDeclaration method;
    final String source;
    final boolean swappedInputs;
    final List<Ref> inputs = new ArrayList<>();
    final Map<String,Ref> constants = new LinkedHashMap<>();
    final Set<String> constantOwners = new HashSet<>();
    final Set<String> definitions = new java.util.TreeSet<>();
    final Ref modulus, modulusField;
    final boolean localModulus;
    final MethodInvocation sink;
    final int start, end;
    final QuadraticRootProgram original;

    JavaGuardedFieldSource(CompilationUnit unit, String source, MethodDeclaration method, boolean replay,
            boolean swappedInputs, JavaValueSourceSummary.Work work) {
        this.unit = unit; this.method = method; this.source = source; this.work = work; this.swappedInputs = swappedInputs;
        require(method.getBody() != null && method.parameters().size() == 3 && method.resolveBinding() != null);
        List<?> list = method.getBody().statements();
        require(list.size() >= 5 && list.size() <= 32);
        var prime = fragment(list.getFirst());
        var initial = variable(prime.getInitializer());
        var value = constant(initial, 0);
        require(value.signum() > 0 && value.bitLength() <= 4096);
        localModulus = initial.getDeclaringClass().isEqualTo(method.resolveBinding().getDeclaringClass());
        modulusField = new Ref(((Name)unwrap(prime.getInitializer())).getFullyQualifiedName(), key(initial), value);
        modulus = new Ref(prime.getName().getIdentifier(), key(prime.resolveBinding()), value);
        var zero = requireType(list.get(1), IfStatement.class);
        var equality = requireType(unwrap(zero.getExpression()), InfixExpression.class);
        require(equality.getOperator() == InfixExpression.Operator.EQUALS && !equality.hasExtendedOperands()
                && integer(equality.getRightOperand()) == 0 && zero.getElseStatement() == null);
        var sign = requireType(unwrap(equality.getLeftOperand()), MethodInvocation.class);
        require(jdk(sign, "signum") && exits(zero.getThenStatement()));
        String imaginary = key(variable(sign.getExpression()));
        var nonImaginary = new ArrayList<Ref>();
        for (Object obj : method.parameters()) {
            var parameter = requireType(obj, SingleVariableDeclaration.class);
            require(big(parameter.resolveBinding().getType()));
            var ref = new Ref(parameter.getName().getIdentifier(), key(parameter.resolveBinding()), null);
            if (ref.key().equals(imaginary)) inputs.add(ref);
            else nonImaginary.add(ref);
        }
        require(inputs.size() == 1 && nonImaginary.size() == 2);
        // Either remaining parameter can be the real coordinate. Only the independently proven
        // assignment of roles explains both original ordered exits for all admissible inputs.
        inputs.addFirst(nonImaginary.get(swappedInputs ? 1 : 0));
        inputs.add(nonImaginary.get(swappedInputs ? 0 : 1));

        int first = replay ? 3 : 2;
        start = ((Statement)list.get(first)).getStartPosition();
        var last = requireType(list.getLast(), ReturnStatement.class);
        end = last.getStartPosition() + last.getLength();
        sink = requireType(unwrap(last.getExpression()), MethodInvocation.class);
        var factory = sink.resolveMethodBinding();
        require(staticCall(sink) && sink.arguments().size() == 2 && !factory.isVarargs()
                && big(factory.getParameterTypes()[0]) && big(factory.getParameterTypes()[1]));
        var prefix = new Lane();
        for (int i = 0; i < 3; i++) prefix.locals.put(inputs.get(i).key(), new Scalar(i, true, value.bitLength()));
        int branch = first;
        while (branch < list.size() - 1 && !(list.get(branch) instanceof IfStatement))
            prefix.statement(list.get(branch++));
        require(branch > first && branch < list.size() - 1);
        var choice = (IfStatement) list.get(branch);
        Expression condition = unwrap(choice.getExpression());
        boolean reversed = condition instanceof PrefixExpression not && not.getOperator() == PrefixExpression.Operator.NOT;
        if (reversed) condition = unwrap(((PrefixExpression) condition).getOperand());
        var compare = requireType(condition, MethodInvocation.class);
        require(jdk(compare, "equals", "java.lang.Object"));
        int left = prefix.canonical(compare.getExpression()), right = prefix.canonical(arg(compare, 0));
        var yes = new Lane(prefix);
        yes.body(choice.getThenStatement());
        var no = new Lane(prefix);
        if (choice.getElseStatement() != null) no.body(choice.getElseStatement());
        Arm onTrue = yes.finish(list, branch + 1, prefix.steps.size());
        Arm onFalse = no.finish(list, branch + 1, prefix.steps.size());
        original = new QuadraticRootProgram(List.copyOf(prefix.steps), left, right, reversed ? onFalse : onTrue, reversed ? onTrue : onFalse);
        new JavaGuardedFieldEffects(this).check(factory);
        new JavaFieldValueSink(this).check(factory);
    }

    private final class Lane {
        final List<Step> steps = new ArrayList<>();
        final Map<String,Scalar> locals = new HashMap<>();
        final Set<String> unread = new HashSet<>();
        Lane() {}
        Lane(Lane base) { steps.addAll(base.steps); locals.putAll(base.locals); unread.addAll(base.unread); }
        int add(Step step) {
            int index = steps.indexOf(step);
            if (index >= 0) return index + 3;
            require(steps.size() < 64);
            steps.add(step);
            return steps.size() + 2;
        }
        void body(Statement statement) {
            if (statement instanceof Block block) for (Object child : block.statements()) statement(child);
            else statement(statement);
        }
        void statement(Object node) {
            work.charge(1);
            if (node instanceof VariableDeclarationStatement) {
                var var = fragment(node);
                require(big(var.resolveBinding().getType()));
                String dest = key(var.resolveBinding());
                locals.put(dest, expr(var.getInitializer(), 0));
                require(unread.add(dest));
            } else {
                var expr = requireType(node, ExpressionStatement.class);
                var assign = requireType(expr.getExpression(), Assignment.class);
                require(assign.getOperator() == Assignment.Operator.ASSIGN && assign.getLeftHandSide() instanceof SimpleName);
                String dest = key(variable(assign.getLeftHandSide()));
                require(locals.containsKey(dest) && inputs.stream().noneMatch(i -> i.key().equals(dest)));
                locals.put(dest, expr(assign.getRightHandSide(), 0));
                require(unread.add(dest));
            }
        }
        Arm finish(List<?> list, int at, int prefix) {
            for (int i = at; i < list.size() - 1; i++) statement(list.get(i));
            int real = canonical(arg(sink, 0)), imag = canonical(arg(sink, 1));
            require(unread.isEmpty());
            return new Arm(List.copyOf(steps.subList(prefix, steps.size())), real, imag, false);
        }
        int canonical(Expression node) {
            Scalar value = expr(node, 0);
            require(value.canonical());
            return value.register();
        }
        Scalar expr(Expression expression, int depth) {
            work.charge(1);
            require(depth < 24);
            Expression node = unwrap(expression);
            if (node instanceof Name name) {
                var binding = variable(name);
                Scalar local = locals.get(key(binding));
                if (local != null) { unread.remove(key(binding)); return local; }
                var ref = guardConstant(name, binding);
                require(ref.value().signum() >= 0 && ref.value().compareTo(modulus.value()) < 0);
                return new Scalar(add(Step.constant(ref.value())), true, ref.value().bitLength());
            }
            var call = requireType(node, MethodInvocation.class);
            Scalar left = expr(call.getExpression(), depth + 1);
            int a = left.register();
            if (jdk(call, "mod", BIG) || jdk(call, "modInverse", BIG)) {
                require(modulus.key().equals(key(variable(arg(call, 0)))));
                return new Scalar(call.getName().getIdentifier().equals("mod") ? a : add(Step.inverse(a)), true, modulus.value().bitLength());
            }
            if (jdk(call, "modPow", BIG, BIG)) {
                require(modulus.key().equals(key(variable(arg(call, 1)))));
                var exponent = requireType(unwrap(arg(call, 0)), Name.class);
                BigInteger e = guardConstant(exponent, variable(exponent)).value();
                require(e.signum() >= 0);
                return new Scalar(add(Step.power(a, e)), true, modulus.value().bitLength());
            }
            if (jdk(call, "shiftLeft", "int")) {
                int shift = integer(arg(call, 0));
                require(shift >= 0 && shift <= 32);
                int scale = add(Step.constant(BigInteger.ONE.shiftLeft(shift).mod(modulus.value())));
                return new Scalar(add(Step.multiply(a, scale)), false, left.bits() + shift);
            }
            require(jdk(call, "multiply", BIG) || jdk(call, "add", BIG) || jdk(call, "subtract", BIG));
            Scalar right = expr(arg(call, 0), depth + 1);
            int b = right.register();
            var step = switch (call.getName().getIdentifier()) {
                case "add" -> Step.add(a, b);
                case "subtract" -> Step.subtract(a, b);
                default -> a == b ? Step.square(a) : Step.multiply(a, b);
            };
            int bits = call.getName().getIdentifier().equals("multiply") ? left.bits() + right.bits()
                    : Math.max(left.bits(), right.bits()) + 1;
            return new Scalar(add(step), false, bits);
        }
    }

    private Ref guardConstant(Name name, IVariableBinding binding) {
        // These fields can be read before the mutable suffix without initializing a foreign class.
        require(binding.isField() && binding.getDeclaringClass().isEqualTo(method.resolveBinding().getDeclaringClass())
                || binding.isField() && big(binding.getDeclaringClass()));
        String id = key(binding);
        return constants.computeIfAbsent(id, unused -> new Ref(name.getFullyQualifiedName(), id, constant(binding, 0)));
    }

    BigInteger constant(IVariableBinding binding, int depth) {
        work.charge(1);
        definitions.add(key(binding));
        constantOwners.add(key(binding.getDeclaringClass()));
        require(definitions.size() <= 128 && depth < 12 && binding.isField() && Modifier.isFinal(binding.getModifiers())
                && Modifier.isStatic(binding.getModifiers()) && big(binding.getType()));
        if (big(binding.getDeclaringClass())) return switch (binding.getName()) {
            case "ZERO" -> BigInteger.ZERO;
            case "ONE" -> BigInteger.ONE;
            case "TWO" -> BigInteger.TWO;
            case "TEN" -> BigInteger.TEN;
            default -> throw unavailable();
        };
        require(binding.getDeclaringClass().isFromSource());
        var definition = JavaSourceContext.definition(unit, binding);
        require(definition != null);
        var declaration = requireType(definition.declaration(), VariableDeclarationFragment.class);
        Expression initializer = unwrap(declaration.getInitializer());
        if (initializer instanceof ClassInstanceCreation creation) {
            require(big(creation.resolveTypeBinding()) && creation.getAnonymousClassDeclaration() == null);
            var leaf = JavaSourceContext.constant(unit, binding);
            require(leaf != null);
            return leaf.value();
        }
        return constantExpr(initializer, depth + 1);
    }

    private BigInteger constantExpr(Expression expression, int depth) {
        work.charge(1);
        require(depth < 12);
        var node = unwrap(expression);
        if (node instanceof Name) return constant(variable(node), depth + 1);
        var call = requireType(node, MethodInvocation.class);
        if (jdk(call, "valueOf", "long") && staticCall(call)) {
            var literal = requireType(unwrap(arg(call, 0)), NumberLiteral.class);
            Object value = literal.resolveConstantExpressionValue();
            require(value instanceof Long || value instanceof Integer);
            return BigInteger.valueOf(((Number)value).longValue());
        }
        var x = constantExpr(call.getExpression(), depth + 1);
        if (jdk(call, "shiftRight", "int")) {
            int shift = integer(arg(call, 0)); require(shift >= 0 && shift <= 4096);
            return x.shiftRight(shift);
        }
        require(jdk(call, "add", BIG) || jdk(call, "modInverse", BIG));
        var y = constantExpr(arg(call, 0), depth + 1);
        int words = 1 + Math.max(x.bitLength(), y.bitLength()) / 32;
        work.charge((long)words * words);
        var result = call.getName().getIdentifier().equals("add") ? x.add(y) : x.modInverse(y);
        require(result.bitLength() <= 4096);
        return result;
    }

    private static boolean exits(Statement node) {
        return node instanceof ReturnStatement || node instanceof Block block && !block.statements().isEmpty()
                && block.statements().getLast() instanceof ReturnStatement;
    }
    private static VariableDeclarationFragment fragment(Object node) {
        var declaration = requireType(node, VariableDeclarationStatement.class);
        require(declaration.fragments().size() == 1);
        var fragment = (VariableDeclarationFragment) declaration.fragments().getFirst();
        require(fragment.resolveBinding() != null && !fragment.resolveBinding().isRecovered());
        return fragment;
    }
    static IVariableBinding variable(Expression node) {
        var name = requireType(unwrap(node), Name.class);
        require(!(name instanceof QualifiedName q) || q.getQualifier().resolveBinding() instanceof ITypeBinding);
        return requireType(name.resolveBinding(), IVariableBinding.class).getVariableDeclaration();
    }
    static Expression unwrap(Expression expr) {
        while (expr instanceof ParenthesizedExpression parens) expr = parens.getExpression();
        return expr;
    }
    static int integer(Expression node) {
        require(unwrap(node) instanceof NumberLiteral && node.resolveConstantExpressionValue() instanceof Integer);
        return (Integer) node.resolveConstantExpressionValue();
    }
    static Expression arg(MethodInvocation call, int index) { return (Expression) call.arguments().get(index); }
    static boolean staticCall(MethodInvocation call) {
        var binding = call.resolveMethodBinding();
        return binding != null && !binding.isRecovered() && Modifier.isStatic(binding.getModifiers())
                && (call.getExpression() == null || call.getExpression() instanceof Name n && n.resolveBinding() instanceof ITypeBinding);
    }
    static boolean big(ITypeBinding type) {
        return type != null && !type.isRecovered() && !type.isFromSource() && BIG.equals(type.getQualifiedName());
    }
    static boolean jdk(MethodInvocation call, String name, String... parameters) {
        var method = call.resolveMethodBinding();
        if (method == null || method.isRecovered() || !big(method.getDeclaringClass()) || !method.getName().equals(name)
                || method.getParameterTypes().length != parameters.length) return false;
        for (int i = 0; i < parameters.length; i++)
            if (!parameters[i].equals(method.getParameterTypes()[i].getQualifiedName())) return false;
        return true;
    }
    static String key(IBinding binding) {
        if (binding == null || binding.isRecovered()) throw unavailable();
        String key = binding.getKey();
        if (key == null) throw unavailable();
        return key;
    }
    static <T> T requireType(Object x, Class<T> type) {
        require(type.isInstance(x)); return type.cast(x);
    }
    static void require(boolean valid) { if (!valid) throw unavailable(); }
    static IllegalArgumentException unavailable() { return new IllegalArgumentException("Unproved field source"); }
}
