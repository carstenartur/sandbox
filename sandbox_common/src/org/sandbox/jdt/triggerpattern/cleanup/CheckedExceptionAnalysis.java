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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.util.AbstractExceptionAnalyzer;

/** Remaining exception flow, excluding only the converted invocation's own effect. */
final class CheckedExceptionAnalysis extends AbstractExceptionAnalyzer {
    private static final String CONVERTED_CALLS= CheckedExceptionAnalysis.class.getName();
    private final Set<ASTNode> converted;
    private final ITypeBinding target;
    private final ASTRewrite rewrite;
    private final boolean proveDeclaredThrows;
    private boolean uncertain;

    private CheckedExceptionAnalysis(Set<ASTNode> converted, ITypeBinding target, ASTRewrite rewrite) {
        this(converted, target, rewrite, false);
    }

    private CheckedExceptionAnalysis(Set<ASTNode> converted, ITypeBinding target, ASTRewrite rewrite, boolean proveDeclaredThrows) {
        this.converted= converted;
        this.target= target;
        this.rewrite= rewrite;
        this.proveDeclaredThrows= proveDeclaredThrows;
    }

    static boolean hasCheckedException(ASTNode scope) {
        CheckedExceptionAnalysis analysis= new CheckedExceptionAnalysis(Set.of(), null, ASTRewrite.create(scope.getAST()));
        scope.accept(analysis);
        return analysis.uncertain || analysis.getCurrentExceptions().stream().anyMatch(CheckedExceptionAnalysis::isChecked);
    }

    private static boolean isChecked(ITypeBinding exception) {
        return !Bindings.isRuntimeException(exception) && Bindings.findTypeInHierarchy(exception, Error.class.getName()) == null;
    }

    static boolean hasSurvivingDeclaredThrows(MethodDeclaration method, ITypeBinding removed, ASTRewrite previousRewrite) {
        IMethodBinding declaration= method.resolveBinding();
        if (method.getBody() == null || declaration == null || declaration.isRecovered()) return false;
        ITypeBinding[] declared= declaration.getExceptionTypes();
        if (Arrays.stream(declared).anyMatch(ITypeBinding::isRecovered)
                || Arrays.stream(declared).noneMatch(removed::isAssignmentCompatible)) return false;
        List<ITypeBinding> affected= Arrays.stream(declared)
                .filter(type -> removed.isAssignmentCompatible(type) || type.isAssignmentCompatible(removed)).toList();
        ASTRewrite rewrite= previousRewrite == null ? ASTRewrite.create(method.getAST()) : previousRewrite;
        Set<ASTNode> earlierCalls= Collections.newSetFromMap(new IdentityHashMap<>());
        convertedCalls(rewrite).values().forEach(earlierCalls::addAll);
        CheckedExceptionAnalysis analysis= new CheckedExceptionAnalysis(earlierCalls, null, rewrite, true);
        method.getBody().accept(analysis);
        return !analysis.uncertain && affected.stream().allMatch(declaredType -> analysis.getCurrentExceptions().stream()
                .filter(CheckedExceptionAnalysis::isChecked).anyMatch(thrown -> thrown.isAssignmentCompatible(declaredType)));
    }

    static void copyConvertedCalls(ASTRewrite source, ASTRewrite candidate, String exceptionFQN) {
        if (source != null) {
            convertedCalls(candidate, exceptionFQN).addAll(convertedCalls(source, exceptionFQN));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<ASTNode>> convertedCalls(ASTRewrite rewrite) {
        Map<String, Set<ASTNode>> converted= (Map<String, Set<ASTNode>>) rewrite.getProperty(CONVERTED_CALLS);
        if (converted == null) {
            converted= new HashMap<>();
            rewrite.setProperty(CONVERTED_CALLS, converted);
        }
        return converted;
    }

    private static Set<ASTNode> convertedCalls(ASTRewrite rewrite, String exceptionFQN) {
        return convertedCalls(rewrite).computeIfAbsent(exceptionFQN, key -> Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings("unchecked")
    static boolean canRemove(ASTNode scope, ASTNode visited, String exceptionFQN, ASTRewrite rewrite) {
        Set<ASTNode> converted= convertedCalls(rewrite, exceptionFQN);
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

    /** A wider handler must not intercept effects from unrelated surviving expressions. */
    static boolean canWidenCatch(TryStatement statement, CatchClause clause,
            Collection<? extends ASTNode> changedCalls, ITypeBinding added) {
        Set<ASTNode> converted= Collections.newSetFromMap(new IdentityHashMap<>());
        converted.addAll(changedCalls);
        CheckedExceptionAnalysis analysis= new CheckedExceptionAnalysis(converted, null,
                ASTRewrite.create(statement.getAST()));
        statement.getBody().accept(analysis);
        for (Object resource : statement.resources()) ((ASTNode) resource).accept(analysis);
        Type caught= clause.getException().getType();
        List<?> alternatives= caught instanceof UnionType union ? union.types() : List.of(caught);
        return !analysis.uncertain && analysis.getCurrentExceptions().stream().noneMatch(exception ->
                (exception.isAssignmentCompatible(added) || added.isAssignmentCompatible(exception))
                && alternatives.stream().map(Type.class::cast).map(Type::resolveBinding)
                        .noneMatch(type -> type != null && exception.isAssignmentCompatible(type)));
    }

    @Override
    public boolean preVisit2(ASTNode node) {
        if (proveDeclaredThrows && !isDirectEffect(node)) return false;
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

    private static boolean isDirectEffect(ASTNode node) {
        // A nested handler or finally can consume an exception. Use only straight-line witnesses.
        if (node instanceof Statement) {
            return node instanceof Block || node instanceof ExpressionStatement || node instanceof VariableDeclarationStatement
                    || node instanceof ReturnStatement || node instanceof ThrowStatement;
        }
        if (node instanceof InfixExpression expression) {
            InfixExpression.Operator operator= expression.getOperator();
            return operator != InfixExpression.Operator.CONDITIONAL_AND && operator != InfixExpression.Operator.CONDITIONAL_OR;
        }
        return !(node instanceof ConditionalExpression || node instanceof SwitchExpression);
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
        if (proveDeclaredThrows && (!(node instanceof MethodInvocation || node instanceof SuperMethodInvocation)
                || binding.getDeclaringClass().isFromSource())) return;
        for (ITypeBinding exception : binding.getExceptionTypes()) {
            if (exception.isRecovered()) uncertain= true;
            // Receiver and argument expressions are still visited even for a converted call.
            else if (node == null || !converted.contains(node) || target != null && !exception.isEqualTo(target))
                addException(exception, rewrite.getAST());
        }
    }
}
