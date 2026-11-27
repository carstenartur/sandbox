/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.jdt.internal.corext.fix.math.FieldCallEvidence.Input;

/** Reaching values before a direct call, with one optional conditional update. All source calls remain in place. */
record JavaFieldGuardFacts(List<Input> inputs, String witnessName, String witnessKey) {
    private static final Input UNKNOWN = new Input(false, false, false);
    private static final Input POSITIVE_MAGNITUDE = new Input(true, true, false);

    private static Input both(Input left, Input right) {
        return new Input(left.exactClass() && right.exactClass(), left.nonnegative() && right.nonnegative(),
                left.belowModulus() && right.belowModulus());
    }
    static JavaFieldGuardFacts forNormalization(JavaGuardedFieldSource site, JavaFieldCallSite call) {
        try { return inspect(site, call, call.statement()); }
        catch (JavaValueSourceSummary.DeadlineExceeded expired) { throw expired; }
        catch (IllegalArgumentException unsupported) { return new JavaFieldGuardFacts(List.of(UNKNOWN, UNKNOWN, UNKNOWN), "", ""); }
    }
    static JavaFieldGuardFacts inspect(JavaGuardedFieldSource site, JavaFieldCallSite call, Statement before) {
        var definitions = new HashMap<String,Expression>();
        var values = new HashMap<String,Input>();
        var moduli = new HashSet<String>();
        var proof = new Returns(site);
        String witness = "", witnessKey = "";
        List<?> prefix = call.caller().getBody().statements();
        int end = prefix.indexOf(before);
        require(end > 0 && end <= 48);
        for (int i = 0; i < end; i++) {
            site.work.charge(1);
            if (prefix.get(i) instanceof VariableDeclarationStatement decl) {
                declare(decl, definitions, values, moduli, proof);
                continue;
            }
            // Only the FALSE continuation of a bound predicate can have changed the roles.
            require(i == end - 1);
            var branch = requireType(prefix.get(i), IfStatement.class);
            require(branch.getElseStatement() == null);
            var not = requireType(unwrap(branch.getExpression()), PrefixExpression.class);
            require(not.getOperator() == PrefixExpression.Operator.NOT);
            var flag = requireType(unwrap(not.getOperand()), SimpleName.class);
            require(JavaFieldCallSite.local(flag));
            String id = key(flag.resolveBinding());
            var roles = call.arguments().stream().map(n -> key(n.resolveBinding())).toList();
            if (values.getOrDefault(roles.get(0), UNKNOWN).exactClass() && values.getOrDefault(roles.get(1), UNKNOWN).exactClass()
                    && norm(definitions.get(id), roles, definitions, site)) {
                witness = flag.getIdentifier();
                witnessKey = id;
            }
            var alternate = new HashMap<>(values);
            var alternateModuli = new HashSet<>(moduli);
            for (Object item : requireType(branch.getThenStatement(), Block.class).statements()) {
                if (item instanceof VariableDeclarationStatement decl) {
                    declare(decl, new HashMap<>(), alternate, alternateModuli, proof);
                } else {
                    var assignment = requireType(requireType(item, ExpressionStatement.class).getExpression(), Assignment.class);
                    var target = requireType(assignment.getLeftHandSide(), SimpleName.class);
                    require(assignment.getOperator() == Assignment.Operator.ASSIGN && JavaFieldCallSite.local(target));
                    String dest = key(target.resolveBinding());
                    if (dest.equals(id)) { witness = ""; witnessKey = ""; }
                    noWrites(assignment.getRightHandSide(), site);
                    alternate.put(dest, proof.value(assignment.getRightHandSide(), alternate, alternateModuli));
                    alternateModuli.remove(dest);
                }
            }
            values.replaceAll((name, input) -> both(input, alternate.getOrDefault(name, UNKNOWN)));
        }
        var facts = call.arguments().stream().map(arg -> values.getOrDefault(key(arg.resolveBinding()), UNKNOWN)).toList();
        require((site.localModulus || !moduli.isEmpty()) && facts.stream().anyMatch(f -> !f.equals(UNKNOWN)));
        return new JavaFieldGuardFacts(facts, witness, witnessKey);
    }

    private static void declare(VariableDeclarationStatement decl, Map<String,Expression> definitions, Map<String,Input> values, Set<String> moduli, Returns proof) {
        for (Object item : decl.fragments()) {
            var fragment = (VariableDeclarationFragment)item;
            var expr = fragment.getInitializer();
            require(expr != null);
            noWrites(expr, proof.site);
            String name = key(fragment.resolveBinding());
            definitions.put(name, expr);
            values.put(name, big(fragment.resolveBinding().getType()) ? proof.value(expr, values, moduli) : UNKNOWN);
            if (expr instanceof Name ref && ref.resolveBinding() instanceof IVariableBinding binding
                    && key(binding).equals(proof.site.modulusField.key())
                    && (!(ref instanceof QualifiedName q) || q.getQualifier().resolveBinding() instanceof ITypeBinding)
                    && proof.site.checkedVariable(ref).isEqualTo(binding)) moduli.add(name);
        }
    }

    private static void noWrites(Expression expression, JavaGuardedFieldSource site) {
        expression.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                site.work.charge(1);
                require(!(node instanceof Assignment) && !(node instanceof PostfixExpression)
                        && !(node instanceof LambdaExpression) && !(node instanceof AnonymousClassDeclaration)
                        && !(node instanceof PrefixExpression p && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                                || p.getOperator() == PrefixExpression.Operator.DECREMENT)));
            }
        });
    }

    private static boolean norm(Expression expression, List<String> roles, Map<String,Expression> definitions, JavaGuardedFieldSource site) {
        if (!(expression instanceof MethodInvocation equals) || !jdk(equals, "equals", "java.lang.Object")
                || !(unwrap(equals.getExpression()) instanceof MethodInvocation left) || !jdk(left, "mod", BIG)
                || !(arg(equals, 0) instanceof SimpleName name)
                || !(definitions.get(key(name.resolveBinding())) instanceof MethodInvocation right) || !jdk(right, "mod", BIG)
                || !(arg(left, 0) instanceof SimpleName p) || !(arg(right, 0) instanceof SimpleName q)
                || !p.resolveBinding().isEqualTo(q.resolveBinding()) || !(definitions.get(key(p.resolveBinding())) instanceof Name field)
                || !site.modulusField.key().equals(key(site.checkedVariable(field)))
                || !(right.getExpression() instanceof MethodInvocation sum) || !jdk(sum, "add", BIG)) return false;
        return square(left.getExpression(), roles.get(2))
                && (square(sum.getExpression(), roles.get(0)) && square(arg(sum, 0), roles.get(1))
                    || square(sum.getExpression(), roles.get(1)) && square(arg(sum, 0), roles.get(0)));
    }

    private static boolean square(Expression expr, String role) {
        return unwrap(expr) instanceof MethodInvocation call && jdk(call, "multiply", BIG)
                && call.getExpression() instanceof SimpleName left && arg(call, 0) instanceof SimpleName right
                && role.equals(key(left.resolveBinding())) && role.equals(key(right.resolveBinding()));
    }

    private static final class Returns {
        private final JavaGuardedFieldSource site;
        private final Map<String,Input> summaries = new HashMap<>();
        private final Set<String> active = new HashSet<>();
        Returns(JavaGuardedFieldSource site) { this.site = site; }

        Input value(Expression expr, Map<String,Input> locals, Set<String> moduli) {
            return value(expr, locals, moduli, 0);
        }
        private static boolean fixedDispatch(IMethodBinding method) {
            return Modifier.isPrivate(method.getModifiers()) || Modifier.isFinal(method.getModifiers())
                    || Modifier.isFinal(method.getDeclaringClass().getModifiers());
        }
        private Input value(Expression expr, Map<String,Input> locals, Set<String> moduli, int depth) {
            site.work.charge(1);
            if (expr == null || depth >= 24) return UNKNOWN;
            Expression node = unwrap(expr);
            if (node instanceof SimpleName n) return locals.getOrDefault(key(n.resolveBinding()), UNKNOWN);
            if (node instanceof ClassInstanceCreation creation && big(creation.resolveTypeBinding())
                    && creation.getAnonymousClassDeclaration() == null && creation.resolveConstructorBinding() != null
                    && JavaSourceTypeNames.closedType(creation.getType(), site.work::charge)) {
                if (creation.arguments().size() == 2 && creation.arguments().getFirst() instanceof NumberLiteral sign
                        && Integer.valueOf(1).equals(sign.resolveConstantExpressionValue())
                        && creation.resolveConstructorBinding().getParameterTypes()[1].getQualifiedName().equals("byte[]")) return POSITIVE_MAGNITUDE;
                return new Input(true, false, false);
            }
            if (!(node instanceof MethodInvocation call)) return UNKNOWN;
            // The exact receiver supplies BigInteger.mod's numeric contract. Its result still needs a class guard.
            if (jdk(call, "mod", BIG) && arg(call, 0) instanceof SimpleName modulus && moduli.contains(key(modulus.resolveBinding()))
                    && value(call.getExpression(), locals, moduli, depth + 1).exactClass()) return new Input(false, true, true);
            var binding = call.resolveMethodBinding();
            if (binding == null || binding.isRecovered() || !binding.getDeclaringClass().isFromSource() || !big(binding.getReturnType())) return UNKNOWN;
            if (Modifier.isStatic(binding.getModifiers())) {
                if (!site.closedStaticCall(call)) return UNKNOWN;
            } else if (site.normalized || !fixedDispatch(binding) || !site.directInstanceReceiver(call, binding.getDeclaringClass())
                    || !fixedDispatch(site.checkSourceMethod(binding))) return UNKNOWN;
            String id = key(binding.getMethodDeclaration());
            if (summaries.containsKey(id)) return summaries.get(id);
            if (active.size() >= 12 || summaries.size() >= 48 || !active.add(id)) return UNKNOWN;
            var definition = site.definition(binding.getMethodDeclaration());
            Input result = UNKNOWN;
            if (definition != null && definition.declaration() instanceof MethodDeclaration method) {
                site.definitions.add(id);
                var exits = JavaFieldCallSite.returns(method, site.work);
                // A return-local or an unchecked source call carries no representation fact.
                if (!exits.isEmpty()) {
                    result = value(exits.getFirst().getExpression(), Map.of(), Set.of());
                    for (int i = 1; i < exits.size(); i++) result = both(result, value(exits.get(i).getExpression(), Map.of(), Set.of()));
                }
            }
            summaries.put(id, result);
            active.remove(id);
            return result;
        }
    }
}
