/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jdt.core.dom.*;

/** Tracks each exact but possibly differently allocated coordinate until its reference is erased.
 * JavaGuardedFieldEffects separately audits the whole factory, including all code after erasure. */
final class JavaFieldValueSink {
    private final JavaGuardedFieldSource site;
    private final JavaValueSourceSummary.Work work;
    private final Set<String> checked = new HashSet<>(), active = new HashSet<>();

    JavaFieldValueSink(JavaGuardedFieldSource site) { this.site = site; this.work = site.work; }

    void check(IMethodBinding factory) {
        parameter(factory, 0);
        parameter(factory, 1);
    }

    private void parameter(IMethodBinding target, int position) {
        String id = key(target.getMethodDeclaration()) + "#" + position;
        site.definitions.add(id);
        if (checked.contains(id)) return;
        require(active.size() < 8 && checked.size() < 64 && active.add(id) && Modifier.isStatic(target.getModifiers())
                && target.getDeclaringClass().isFromSource() && !target.isVarargs() && big(target.getParameterTypes()[position]));
        var definition = site.definition(target.getMethodDeclaration());
        require(definition != null);
        var method = requireType(definition.declaration(), MethodDeclaration.class);
        require(method.getBody() != null);
        var formal = (SingleVariableDeclaration) method.parameters().get(position);
        uses(method, formal.resolveBinding(), new HashSet<>());
        active.remove(id);
        checked.add(id);
    }

    private void uses(MethodDeclaration method, IVariableBinding local, Set<String> followed) {
        String binding = key(local);
        require(followed.size() < 16 && followed.add(binding) && (local.isEffectivelyFinal()
                || Modifier.isFinal(local.getModifiers())));
        method.getBody().accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.charge(1); }
            @Override public boolean visit(SimpleName name) {
                if (name.isDeclaration() || !local.isEqualTo(name.resolveBinding())) return true;
                ASTNode parent = name.getParent();
                while (parent != method) {
                    if (parent == null) throw unavailable();
                    require(!(parent instanceof LambdaExpression) && !(parent instanceof MethodDeclaration)
                            && !(parent instanceof AnonymousClassDeclaration) && !(parent instanceof AbstractTypeDeclaration));
                    parent = parent.getParent();
                }
                use(method, name, followed);
                return true;
            }
        });
    }

    private void use(MethodDeclaration owner, Expression original, Set<String> followed) {
        Expression value = original;
        while (value.getParent() instanceof ParenthesizedExpression wrapper) value = wrapper;
        if (value.getParent() instanceof VariableDeclarationFragment alias && alias.getInitializer() == value) {
            require(alias.getParent() instanceof VariableDeclarationStatement && big(alias.resolveBinding().getType()));
            uses(owner, alias.resolveBinding(), followed);
            return;
        }
        var call = requireType(value.getParent(), MethodInvocation.class);
        if (call.getExpression() == value) {
            if (jdk(call, "toByteArray") || jdk(call, "longValueExact") || jdk(call, "intValueExact")
                    || jdk(call, "longValue") || jdk(call, "intValue") || jdk(call, "signum")) return;
            // BigInteger.mod may return the receiver by identity. Its result therefore stays tainted.
            require(jdk(call, "mod", BIG));
            site.constant(site.checkedVariable(arg(call, 0)), 0);
            use(owner, call, followed);
        } else {
            int position = call.arguments().indexOf(value);
            require(position >= 0 && site.checkedStaticCall(call));
            parameter(call.resolveMethodBinding(), position);
        }
    }
}
