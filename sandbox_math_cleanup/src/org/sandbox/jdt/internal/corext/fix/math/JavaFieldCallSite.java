/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.*;

/** A single call with effect-free local arguments and an effect-free destination address. */
record JavaFieldCallSite(MethodDeclaration caller, Statement statement, MethodInvocation call, List<SimpleName> arguments) {
    static List<JavaFieldCallSite> locate(MethodDeclaration root, int selection, int length, JavaValueSourceSummary.Work work) {
        var result = new ArrayList<JavaFieldCallSite>();
        var owner = requireType(root.getParent(), TypeDeclaration.class);
        for (var caller : owner.getMethods()) {
            work.charge(1);
            if (caller == root || caller.getBody() == null || !overlaps(caller, selection, length)) continue;
            for (Object item : caller.getBody().statements()) {
                var stmt = (Statement)item;
                work.charge(1);
                if (!overlaps(stmt, selection, length)) continue;
                Expression rhs = destination(stmt);
                if (rhs instanceof MethodInvocation call && matches(root, call))
                    result.add(new JavaFieldCallSite(caller, stmt, call, List.of()));
            }
        }
        return result;
    }

    static boolean identifierUsesPrefix(JavaGuardedFieldSource site, String prefix) {
        boolean[] used = { false };
        site.unit.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) { site.work.charge(1); return !used[0]; }
            @Override public boolean visit(SimpleName name) {
                used[0] = name.getIdentifier().startsWith(prefix);
                return false;
            }
        });
        return used[0];
    }

    static boolean alreadyHasFallback(CompilationUnit ast, String source, MethodDeclaration root, MathCleanUpOptions options,
            JavaValueSourceSummary.Work work) {
        for (var caller : ((TypeDeclaration)root.getParent()).getMethods()) {
            if (caller.getBody() == null) continue;
            Statement previous = null;
            for (Object item : caller.getBody().statements()) {
                work.charge(1);
                var statement = (Statement)item;
                if (destination(statement) instanceof ConditionalExpression choice
                        && choice.getElseExpression() instanceof MethodInvocation fallback && staticCall(fallback)
                        && root.resolveBinding().isEqualTo(fallback.resolveMethodBinding())
                        && previous instanceof VariableDeclarationStatement decl && decl.fragments().size() == 1
                        && decl.fragments().getFirst() instanceof VariableDeclarationFragment temp
                        && temp.getInitializer() instanceof MethodInvocation attempt && staticCall(attempt)
                        && Modifier.isPrivate(attempt.resolveMethodBinding().getModifiers())
                        && choice.getThenExpression() instanceof SimpleName result && temp.resolveBinding().isEqualTo(result.resolveBinding())
                        && choice.getExpression() instanceof InfixExpression test && test.getOperator() == InfixExpression.Operator.NOT_EQUALS
                        && test.getRightOperand() instanceof NullLiteral && test.getLeftOperand() instanceof SimpleName n
                        && temp.resolveBinding().isEqualTo(n.resolveBinding())
                        && JavaFieldCallOptimizer.isEmittedCandidate(ast, source, root, caller, decl, statement, attempt, fallback, options, work))
                    return true;
                previous = statement;
            }
        }
        return false;
    }

    static Expression destination(Statement statement) {
        if (statement instanceof VariableDeclarationStatement decl && decl.fragments().size() == 1)
            return ((VariableDeclarationFragment)decl.fragments().getFirst()).getInitializer();
        if (statement instanceof ExpressionStatement stmt && stmt.getExpression() instanceof Assignment assign
                && assign.getOperator() == Assignment.Operator.ASSIGN && assign.getLeftHandSide() instanceof ArrayAccess array
                && array.getArray() instanceof SimpleName name && local(name)
                && array.getIndex() instanceof NumberLiteral n && n.resolveConstantExpressionValue() instanceof Integer)
            return assign.getRightHandSide();
        return null;
    }

    static boolean matches(MethodDeclaration root, MethodInvocation call) {
        if (!staticCall(call) || !root.resolveBinding().isEqualTo(call.resolveMethodBinding()) || call.arguments().size() != 3) return false;
        for (Object arg : call.arguments()) if (!(arg instanceof SimpleName name) || !local(name)) return false;
        return true;
    }

    static JavaFieldCallSite bind(JavaGuardedFieldSource site, MethodDeclaration caller, Statement statement, MethodInvocation call) {
        require(matches(site.method, call) && terminalContinuation(site, caller, statement));
        var formals = new ArrayList<String>();
        for (Object item : site.method.parameters()) formals.add(key(((SingleVariableDeclaration)item).resolveBinding()));
        var args = site.inputs.stream().map(role -> (SimpleName)arg(call, formals.indexOf(role.key()))).toList();
        return new JavaFieldCallSite(caller, statement, call, args);
    }

    /** A generated caller local can change the slot number in a later helpful NPE message. */
    private static boolean terminalContinuation(JavaGuardedFieldSource site, MethodDeclaration caller, Statement statement) {
        site.work.charge(3);
        List<?> body = caller.getBody().statements();
        if (body.getLast() == statement) return true;
        if (body.size() < 2 || body.get(body.size() - 2) != statement || !(body.getLast() instanceof ReturnStatement ret)) return false;
        var method = caller.resolveBinding();
        if (method == null || method.isRecovered()) return false;
        var expected = method.getReturnType();
        if (expected == null || expected.isRecovered()) return false;
        var value = ret.getExpression();
        if (value == null) return method.isConstructor() || "void".equals(expected.getName());
        if (value instanceof NullLiteral) return !expected.isPrimitive();
        if (value.resolveBoxing() || value.resolveUnboxing() || !expected.isEqualTo(value.resolveTypeBinding())) return false;
        if (value instanceof SimpleName name) {
            if (!(name.resolveBinding() instanceof IVariableBinding variable) || variable.isRecovered() || variable.isField()
                    || !method.isEqualTo(variable.getDeclaringMethod()) || !expected.isEqualTo(variable.getType())) return false;
            site.work.charge(1);
            var declaration = site.unit.findDeclaringNode(variable);
            if (declaration instanceof SingleVariableDeclaration parameter)
                return parameter.getParent() == caller && !parameter.getType().isVar();
            return declaration instanceof VariableDeclarationFragment fragment && fragment.getParent() instanceof VariableDeclarationStatement local
                    && !local.getType().isVar();
        }
        return value instanceof BooleanLiteral || value instanceof CharacterLiteral || value instanceof NumberLiteral
                || value instanceof StringLiteral || value instanceof TextBlock;
    }

    static boolean nonnullFactory(JavaGuardedFieldSource site) {
        var declaration = JavaSourceContext.definition(site.unit, site.sink.resolveMethodBinding());
        if (declaration == null || !(declaration.declaration() instanceof MethodDeclaration method)) return false;
        var returns = returns(method, site.work);
        return !returns.isEmpty() && returns.stream().allMatch(ret -> ret.getExpression() instanceof ArrayCreation
                || ret.getExpression() instanceof ClassInstanceCreation);
    }

    static List<ReturnStatement> returns(MethodDeclaration method, JavaValueSourceSummary.Work work) {
        var result = new ArrayList<ReturnStatement>();
        if (method.getBody() != null) method.getBody().accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.charge(1); }
            @Override public boolean visit(ReturnStatement ret) { result.add(ret); return false; }
            @Override public boolean visit(AnonymousClassDeclaration nested) { return false; }
            @Override public boolean visit(TypeDeclarationStatement nested) { return false; }
            @Override public boolean visit(LambdaExpression nested) { return false; }
        });
        return result;
    }

    static boolean local(SimpleName name) {
        return name.resolveBinding() instanceof IVariableBinding var && !var.isRecovered() && !var.isField();
    }

    static String text(String source, ASTNode node) {
        return source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
    }
    static boolean overlaps(ASTNode node, int start, int length) {
        return start < 0 || start < node.getStartPosition() + (long)node.getLength()
                && node.getStartPosition() < start + (long)Math.max(1, length);
    }
}
