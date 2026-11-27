/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import static org.sandbox.jdt.internal.corext.fix.math.JavaFieldCallSite.text;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.math.algorithms.modular.QuadraticRootNormalization;

/** Proves the complete lifetime of a fresh pair and both of its coordinates in the consuming caller. */
final class JavaFieldNormalizationSite {
    private JavaFieldNormalizationSite() {}

    static boolean pairType(ITypeBinding type) {
        return type != null && type.isArray() && big(type.getComponentType());
    }
    private static boolean eligible(MethodDeclaration method) {
        return Modifier.isStatic(method.getModifiers()) && !Modifier.isSynchronized(method.getModifiers());
    }
    private static JavaSourceContext.Definition definition(JavaGuardedFieldSource site, IMethodBinding binding) {
        var found = site.definition(binding.getMethodDeclaration());
        if (found == null || !(found.declaration() instanceof MethodDeclaration)) throw unavailable();
        site.definitions.add(key(binding.getMethodDeclaration()));
        return found;
    }
    static void freshFactory(JavaGuardedFieldSource site) {
        var binding = site.sink.resolveMethodBinding();
        var factory = (MethodDeclaration)definition(site, binding).declaration();
        require(site.localModulus && eligible(site.method) && eligible(factory) && factory.getParent() == site.method.getParent()
                && pairType(site.method.resolveBinding().getReturnType()) && pairType(binding.getReturnType())
                && !binding.isGenericMethod() && factory.getBody() != null && factory.getBody().statements().size() == 1);
        checkBigIntegerTypes(site, site.method);
        checkBigIntegerTypes(site, factory);
        var ret = requireType(factory.getBody().statements().getFirst(), ReturnStatement.class);
        var array = requireType(ret.getExpression(), ArrayCreation.class);
        require(pairType(array.resolveTypeBinding()) && array.dimensions().isEmpty() && array.getInitializer() != null);
        List<?> elements = array.getInitializer().expressions();
        require(elements.size() == 2);
        for (int i = 0; i < 2; i++) {
            var formal = (SingleVariableDeclaration)factory.parameters().get(i);
            require(formal.resolveBinding().isEqualTo(variable((Expression)elements.get(i))));
        }
    }

    static void checkBigIntegerTypes(JavaGuardedFieldSource site, ASTNode scope) {
        scope.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                site.work.charge(1);
                if (node instanceof Type type && big(type.resolveBinding())) require(JavaSourceTypeNames.closedType(type, site.work::charge));
            }
        });
    }

    static String identity(String... fields) {
        var parts = new StringBuilder();
        for (String field : fields) parts.append(field.length()).append(':').append(field);
        return MathematicalAnalysis.digest(parts.toString());
    }
    static String rootIdentity(JavaGuardedFieldSource site) {
        return identity(key(site.method.resolveBinding()), text(site.source, site.method));
    }

    static FieldNormalizationEvidence inspect(JavaGuardedFieldSource site, JavaFieldCallSite call, SimpleName assignment,
            String original) {
        var caller = call.caller();
        checkBigIntegerTypes(site, caller);
        require(eligible(caller) && caller.getParent() == site.method.getParent());
        var decl = requireType(call.statement(), VariableDeclarationStatement.class);
        List<?> body = caller.getBody().statements();
        int ordinal = body.indexOf(decl), suffix = ordinal + (assignment == null ? 1 : 2);
        require(ordinal >= 0 && body.size() == suffix + 4);
        var pair = fragment(decl);
        require(decl.getType() instanceof ArrayType && pairType(decl.getType().resolveBinding()) && pair.extraDimensions().isEmpty()
                && (assignment == null ? pair.getInitializer() == call.call() : pair.getInitializer() == null));
        var uses = new HashSet<SimpleName>();
        uses.add(pair.getName());
        if (assignment != null) use(assignment, pair, uses);
        var real = coordinate(body.get(suffix), pair, 0, uses);
        var imaginary = coordinate(body.get(suffix + 1), pair, 1, uses);
        var branch = requireType(body.get(suffix + 2), IfStatement.class);
        var test = unequal(branch.getExpression());
        var bit = requireType(unwrap(test.getLeftOperand()), MethodInvocation.class);
        require(jdk(bit, "testBit", "int") && integer(arg(bit, 0)) == 0 && branch.getElseStatement() == null);
        var select = requireType(unwrap(bit.getExpression()), ConditionalExpression.class);
        var nonzero = unequal(select.getExpression());
        var sign = requireType(unwrap(nonzero.getLeftOperand()), MethodInvocation.class);
        require(jdk(sign, "signum") && integer(nonzero.getRightOperand()) == 0);
        boolean realFirst = real.resolveBinding().isEqualTo(variable(sign.getExpression()));
        var first = realFirst ? real : imaginary;
        use(sign.getExpression(), first, uses);
        use(select.getThenExpression(), first, uses);
        use(select.getElseExpression(), realFirst ? imaginary : real, uses);
        var target = variable(test.getRightOperand());
        stableTarget(site, caller, decl, target);
        List<?> updates = requireType(branch.getThenStatement(), Block.class).statements();
        require(updates.size() == 2);
        negate(site, updates.get(0), real, uses);
        negate(site, updates.get(1), imaginary, uses);
        var sink = requireType(requireType(body.getLast(), ReturnStatement.class).getExpression(), MethodInvocation.class);
        var binding = sink.resolveMethodBinding();
        require(site.checkedStaticCall(sink) && !binding.isGenericMethod() && !binding.isVarargs() && sink.arguments().size() == 2
                && big(binding.getParameterTypes()[0]) && big(binding.getParameterTypes()[1]));
        use(arg(sink, 0), real, uses);
        use(arg(sink, 1), imaginary, uses);
        exactUses(site, caller, Set.of(key(pair.resolveBinding()), key(real.resolveBinding()), key(imaginary.resolveBinding())), uses);
        new JavaGuardedFieldEffects(site).check(binding);
        new JavaFieldValueSink(site).check(binding);
        var factory = definition(site, site.sink.resolveMethodBinding());
        var sinkSource = definition(site, binding);
        String factoryKey = key(site.sink.resolveMethodBinding()), factoryHash = hash(factory);
        String sinkKey = key(binding), sinkHash = hash(sinkSource);
        String pairKey = key(pair.resolveBinding()), realKey = key(real.resolveBinding());
        String imaginaryKey = key(imaginary.resolveBinding()), targetKey = key(target);
        var selector = realFirst ? QuadraticRootNormalization.ParitySelector.realFirst() : QuadraticRootNormalization.ParitySelector.imaginaryFirst();
        String id = identity(rootIdentity(site), key(caller.resolveBinding()), MathematicalAnalysis.digest(original), Integer.toString(ordinal),
                factoryKey, factoryHash, sinkKey, sinkHash, pairKey, realKey, imaginaryKey, targetKey, site.modulusField.key(),
                Integer.toString(selector.mask()));
        return new FieldNormalizationEvidence(new QuadraticRootNormalization.Contract(id, site.modulus.value(), selector),
                ordinal, factoryKey, factoryHash, sinkKey, sinkHash, pairKey, realKey, imaginaryKey, targetKey, site.modulusField.key());
    }

    private static String hash(JavaSourceContext.Definition source) {
        return MathematicalAnalysis.digest(text(source.source(), source.declaration()));
    }
    private static InfixExpression unequal(Expression expression) {
        var test = requireType(unwrap(expression), InfixExpression.class);
        require(test.getOperator() == InfixExpression.Operator.NOT_EQUALS && !test.hasExtendedOperands());
        return test;
    }
    private static void stableTarget(JavaGuardedFieldSource site, MethodDeclaration caller, Statement before, IVariableBinding target) {
        require(!target.isField() && (target.isEffectivelyFinal() || Modifier.isFinal(target.getModifiers()))
                && target.getType().isPrimitive() && "boolean".equals(target.getType().getName()));
        ASTNode declaration = site.unit.findDeclaringNode(target);
        if (declaration instanceof SingleVariableDeclaration parameter) {
            require(parameter.getParent() == caller && parameter.getType().isPrimitiveType());
        } else {
            var fragment = requireType(declaration, VariableDeclarationFragment.class);
            var local = requireType(fragment.getParent(), VariableDeclarationStatement.class);
            require(local.getParent() == caller.getBody() && local.getStartPosition() < before.getStartPosition()
                    && local.getType().isPrimitiveType() && fragment.getInitializer() != null);
        }
    }
    private static VariableDeclarationFragment coordinate(Object declaration, VariableDeclarationFragment pair, int slot, Set<SimpleName> uses) {
        var coord = fragment(declaration);
        require(big(coord.resolveBinding().getType()));
        var read = requireType(coord.getInitializer(), ArrayAccess.class);
        require(integer(read.getIndex()) == slot);
        use(read.getArray(), pair, uses);
        uses.add(coord.getName());
        return coord;
    }
    private static void negate(JavaGuardedFieldSource site, Object statement, VariableDeclarationFragment coord, Set<SimpleName> uses) {
        var assign = requireType(requireType(statement, ExpressionStatement.class).getExpression(), Assignment.class);
        var mod = requireType(assign.getRightHandSide(), MethodInvocation.class);
        require(assign.getOperator() == Assignment.Operator.ASSIGN && jdk(mod, "mod", BIG)
                && key(site.checkedVariable(arg(mod, 0))).equals(site.modulusField.key()));
        var negative = requireType(mod.getExpression(), MethodInvocation.class);
        require(jdk(negative, "negate"));
        use(assign.getLeftHandSide(), coord, uses);
        use(negative.getExpression(), coord, uses);
    }
    private static void use(Expression expression, VariableDeclarationFragment coord, Set<SimpleName> uses) {
        var name = requireType(unwrap(expression), SimpleName.class);
        require(coord.resolveBinding().isEqualTo(name.resolveBinding()) && uses.add(name));
    }
    static void exactUses(JavaGuardedFieldSource site, ASTNode scope, Set<String> keys, Set<SimpleName> allowed) {
        var remaining = new HashSet<>(allowed);
        scope.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { site.work.charge(1); }
            @Override public boolean visit(SimpleName name) {
                var binding = name.resolveBinding();
                if (binding != null && keys.contains(binding.getKey())) require(remaining.remove(name));
                return true;
            }
        });
        require(remaining.isEmpty());
    }
}
