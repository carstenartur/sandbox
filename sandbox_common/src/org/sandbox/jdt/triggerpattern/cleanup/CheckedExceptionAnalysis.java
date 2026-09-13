/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.util.AbstractExceptionAnalyzer;

/** Remaining exception flow, excluding only the converted invocation's own effect. */
final class CheckedExceptionAnalysis extends AbstractExceptionAnalyzer {
    private final Set<ASTNode> converted;
    private final ITypeBinding target;
    private final ASTRewrite rewrite;
    private boolean uncertain;

    private CheckedExceptionAnalysis(Set<ASTNode> converted, ITypeBinding target, ASTRewrite rewrite) {
        this.converted= converted;
        this.target= target;
        this.rewrite= rewrite;
    }

    @SuppressWarnings("unchecked")
    static boolean canRemove(ASTNode scope, ASTNode visited, String exceptionFQN, ASTRewrite rewrite) {
        String key= CheckedExceptionAnalysis.class.getName() + ':' + exceptionFQN;
        Set<ASTNode> converted= (Set<ASTNode>) rewrite.getProperty(key);
        if (converted == null) {
            converted= Collections.newSetFromMap(new IdentityHashMap<>());
            rewrite.setProperty(key, converted);
        }
        ASTNode invocation= visited;
        while (invocation != null && !(invocation instanceof MethodInvocation)
                && !(invocation instanceof ClassInstanceCreation) && !(invocation instanceof SuperMethodInvocation)
                && !(invocation instanceof ConstructorInvocation) && !(invocation instanceof SuperConstructorInvocation)) {
            invocation= invocation.getParent();
        }
        if (invocation == null) return false;
        converted.add(invocation);
        List<Type> types= scope instanceof MethodDeclaration method ? method.thrownExceptionTypes()
                : ((List<CatchClause>) ((TryStatement) scope).catchClauses()).stream()
                        .flatMap(c -> c.getException().getType() instanceof UnionType union
                                ? ((List<Type>) union.types()).stream() : java.util.stream.Stream.of(c.getException().getType()))
                        .toList();
        ITypeBinding target= types.stream().map(Type::resolveBinding)
                .filter(t -> t != null && !t.isRecovered() && exceptionFQN.equals(t.getErasure().getQualifiedName()))
                .findFirst().orElse(null);
        if (target == null) return false;
        CheckedExceptionAnalysis analysis= new CheckedExceptionAnalysis(converted, target, rewrite);
        if (scope instanceof TryStatement statement) {
            statement.getBody().accept(analysis);
            for (Object resource : statement.resources()) ((ASTNode) resource).accept(analysis);
        } else if (((MethodDeclaration) scope).getBody() != null) {
            ((MethodDeclaration) scope).getBody().accept(analysis);
        }
        return !analysis.uncertain && analysis.getCurrentExceptions().stream()
                .noneMatch(t -> t.isAssignmentCompatible(target) || target.isAssignmentCompatible(t));
    }

    @Override
    public boolean preVisit2(ASTNode node) {
        if (node instanceof CatchClause clause && !rewrite.getListRewrite(clause.getParent(),
                TryStatement.CATCH_CLAUSES_PROPERTY).getRewrittenList().contains(clause)) return false;
        if (node instanceof Expression resource && node.getParent() instanceof TryStatement statement
                && statement.resources().contains(resource)) {
            ITypeBinding type= resource.resolveTypeBinding();
            check(null, type == null || type.isRecovered() ? null
                    : Bindings.findMethodInHierarchy(type, "close", new ITypeBinding[0])); //$NON-NLS-1$
        }
        return true;
    }

    // The resource's implicit close is checked for both declarations and Java 9 resource references above.
    @Override
    public boolean visit(VariableDeclarationExpression node) { return true; }

    @Override
    public boolean visit(RecordDeclaration node) { return false; }

    @Override
    public boolean visit(MethodInvocation node) { check(node, node.resolveMethodBinding()); return true; }

    @Override
    public boolean visit(SuperMethodInvocation node) { check(node, node.resolveMethodBinding()); return true; }

    @Override
    public boolean visit(ClassInstanceCreation node) { check(node, node.resolveConstructorBinding()); return true; }

    @Override
    public boolean visit(ConstructorInvocation node) { check(node, node.resolveConstructorBinding()); return true; }

    @Override
    public boolean visit(SuperConstructorInvocation node) { check(node, node.resolveConstructorBinding()); return true; }

    @Override
    public boolean visit(ThrowStatement node) {
        ITypeBinding type= node.getExpression().resolveTypeBinding();
        if (type == null || type.isRecovered()) uncertain= true;
        else addException(type, node.getAST());
        return true;
    }

    private void check(ASTNode node, IMethodBinding binding) {
        if (binding == null || binding.isRecovered()) {
            uncertain= true;
            return;
        }
        for (ITypeBinding exception : binding.getExceptionTypes()) {
            if (exception.isRecovered()) uncertain= true;
            // Receiver and argument expressions are still visited even for a converted call.
            else if (node == null || !converted.contains(node) || !exception.isEqualTo(target))
                addException(exception, rewrite.getAST());
        }
    }
}
