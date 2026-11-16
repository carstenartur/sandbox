/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;

final class JavaConstructorGuardOptimizer {
    private static final String BIG_INTEGER = "java.math.BigInteger"; //$NON-NLS-1$
    private static final String SERIALIZABLE = "java.io.Serializable"; //$NON-NLS-1$

    private record Guard(IfStatement statement, List<Expression> prefix, IVariableBinding receiver,
            IVariableBinding constant) {
        int start() { return prefix.getFirst().getStartPosition(); }
        int end() { return JavaConstructorGuardOptimizer.end(prefix.getLast()); }
    }

    private record Site(MethodDeclaration constructor, Statement after) { }

    private JavaConstructorGuardOptimizer() { }

    static long optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String, String> compiler,
            MathematicalAnalysis.SourceEnvironment environment, List<MathematicalAnalysis.Replacement> replacements,
            List<MathematicalAnalysis.VerifiedRegion> evidence, List<MathematicalAnalysis.Diagnostic> diagnostics,
            long remaining) {
        if (!options.mathematicalOptIn() || options.goal() != OptimizationGoal.LOWER_ESTIMATED_RUNTIME
                || !options.kinds().contains(NumericKind.BIG_INTEGER) || remaining < source.length()) return remaining;
        var work = new JavaLoopInvariantAnalysis.Work(remaining, MathematicalAnalysis.cancellation(monitor, options));
        try {
            // Bound discovery and the identifier scan before visiting the unit.
            work.charge(source.length());
            var statements = new ArrayList<IfStatement>();
            var names = new HashSet<String>();
            ast.accept(new ASTVisitor() {
                @Override public boolean visit(SimpleName name) {
                    names.add(name.getIdentifier());
                    return false;
                }
                @Override public boolean visit(IfStatement statement) {
                    if (selectionOffset < 0 || overlaps(statement.getExpression().getStartPosition(),
                            statement.getExpression().getLength(), selectionOffset, Math.max(1, selectionLength)))
                        statements.add(statement);
                    return true;
                }
            });
            for (var statement : statements) {
                work.tick();
                var owner = methodOwner(statement);
                if (owner == null || replacements.stream().anyMatch(edit -> overlaps(owner.getStartPosition(),
                        owner.getLength(), edit.offset(), Math.max(1, edit.length())))) continue;
                var type = owner.resolveBinding();
                if (!resolved(type)) continue;
                Guard guard = guard(ast, statement, type);
                if (guard == null) continue;
                var fields = new HashSet<String>();
                // Adding a private member must not hide an accessible inherited field.
                if (!collectFieldNames(type, fields, new HashSet<>(), work)) continue;
                List<Site> sites = sites(ast, owner, guard.receiver(), work);
                if (sites.isEmpty()) continue;
                int index = 0;
                String name;
                do { name = "mathCachedGuard" + index++; } while (fields.contains(name) || names.contains(name)); //$NON-NLS-1$
                try {
                    var receipt = emit(ast, source, options, monitor, compiler, environment, owner, guard, sites, name, work);
                    int start = receipt.offset(), length = receipt.length();
                    replacements.add(new MathematicalAnalysis.Replacement(start, length, receipt.replacement(),
                            receipt.constructorGuard().description()));
                    evidence.add(receipt);
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("VERIFIED_CONSTRUCTOR_GUARD", //$NON-NLS-1$
                            receipt.constructorGuard().description(), guard.start(), guard.end() - guard.start(),
                            MathematicalAnalysis.Stage.PROOF));
                } catch (IllegalArgumentException rejected) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("CONSTRUCTOR_GUARD_NOT_REALIZED", //$NON-NLS-1$
                            rejected.getMessage(), guard.start(), guard.end() - guard.start(), MathematicalAnalysis.Stage.EMISSION));
                }
            }
        } catch (JavaLoopInvariantAnalysis.Limit exhausted) {
            // Already replayed candidates may be used. The caller checks the shared deadline.
        }
        return remaining - work.used;
    }

    private static TypeDeclaration methodOwner(IfStatement statement) {
        ASTNode node = statement.getParent();
        while (node != null && !(node instanceof MethodDeclaration)) {
            if (node instanceof LambdaExpression || node instanceof AbstractTypeDeclaration
                    || node instanceof AnonymousClassDeclaration) return null;
            node = node.getParent();
        }
        if (node instanceof MethodDeclaration method && !method.isConstructor() && !Modifier.isStatic(method.getModifiers())
                && method.getParent() instanceof TypeDeclaration owner && !owner.isInterface() && !owner.isLocalTypeDeclaration())
            return owner;
        return null;
    }

    private static Guard guard(CompilationUnit ast, IfStatement statement, ITypeBinding owner) {
        var parts = conjunction(statement.getExpression());
        if (parts.size() < 3) return null;
        IVariableBinding receiver = nonNullRead(parts.getFirst());
        if (!sameOwnerFinal(receiver, owner, false) || !type(receiver.getType(), BIG_INTEGER)
                || !(ast.findDeclaringNode(receiver) instanceof VariableDeclarationFragment field) || field.getInitializer() != null)
            return null;
        if (!(unwrap(parts.get(2)) instanceof MethodInvocation equals) || equals.arguments().size() != 1) return null;
        IVariableBinding constant = staticField((Expression) equals.arguments().getFirst());
        if (!sameOwnerFinal(constant, owner, true) || !type(constant.getType(), BIG_INTEGER)
                || !prefixMatches(parts.subList(0, 3), receiver, constant)
                || !(ast.findDeclaringNode(constant) instanceof VariableDeclarationFragment declaration)
                || !(declaration.getInitializer() instanceof ClassInstanceCreation creation)
                || creation.getAnonymousClassDeclaration() != null || creation.getExpression() != null
                || !type(creation.resolveTypeBinding(), BIG_INTEGER) || !resolved(creation.resolveConstructorBinding())) return null;
        return new Guard(statement, List.copyOf(parts.subList(0, 3)), receiver, constant);
    }

    private static List<Expression> conjunction(Expression expression) {
        var stack = new ArrayDeque<Expression>();
        var result = new ArrayList<Expression>();
        stack.push(unwrap(expression));
        while (!stack.isEmpty()) {
            var part = stack.pop();
            if (part instanceof InfixExpression and && and.getOperator() == InfixExpression.Operator.CONDITIONAL_AND) {
                for (Object operand : and.extendedOperands().reversed()) stack.push((Expression) operand);
                stack.push(and.getRightOperand());
                stack.push(and.getLeftOperand());
            } else result.add(part);
        }
        return result;
    }

    private static boolean exactClassCheck(Expression expression, IVariableBinding receiver) {
        if (!(unwrap(expression) instanceof InfixExpression comparison) || comparison.getOperator() != InfixExpression.Operator.EQUALS
                || comparison.hasExtendedOperands()) return false;
        Expression left = unwrap(comparison.getLeftOperand()), right = unwrap(comparison.getRightOperand());
        if (left instanceof TypeLiteral) { var swap = left; left = right; right = swap; }
        return right instanceof TypeLiteral literal && type(literal.getType().resolveBinding(), BIG_INTEGER)
                && left instanceof MethodInvocation call && call.getExpression() != null
                && same(receiver, instanceField(call.getExpression())) && call.arguments().isEmpty()
                && libraryMethod(call.resolveMethodBinding(), "java.lang.Object", "getClass"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean exactEquals(MethodInvocation call, IVariableBinding receiver) {
        var method = call.resolveMethodBinding();
        return call.getExpression() != null && same(receiver, instanceField(call.getExpression()))
                && call.arguments().size() == 1 && libraryMethod(method, BIG_INTEGER, "equals") //$NON-NLS-1$
                && method.getParameterTypes().length == 1 && type(method.getParameterTypes()[0], "java.lang.Object"); //$NON-NLS-1$
    }

    private static boolean libraryMethod(IMethodBinding method, String owner, String name) {
        return resolved(method) && method.getName().equals(name) && type(method.getDeclaringClass(), owner)
                && !method.getDeclaringClass().isFromSource();
    }

    private static IVariableBinding instanceField(Expression expression) {
        var node = unwrap(expression);
        if (node instanceof FieldAccess field && field.getExpression() instanceof ThisExpression self && self.getQualifier() == null)
            return field.resolveFieldBinding();
        return node instanceof SimpleName simple && simple.resolveBinding() instanceof IVariableBinding field ? field : null;
    }

    private static IVariableBinding staticField(Expression expression) {
        var node = unwrap(expression);
        if (node instanceof SimpleName simple && simple.resolveBinding() instanceof IVariableBinding field) return field;
        if (node instanceof QualifiedName name && name.getQualifier().resolveBinding() instanceof ITypeBinding
                && name.resolveBinding() instanceof IVariableBinding field) return field;
        return null;
    }

    private static boolean sameOwnerFinal(IVariableBinding field, ITypeBinding owner, boolean isStatic) {
        return resolved(field) && field.isField() && Modifier.isFinal(field.getModifiers())
                && Modifier.isStatic(field.getModifiers()) == isStatic && same(field.getDeclaringClass(), owner);
    }

    private static boolean collectFieldNames(ITypeBinding type, Set<String> fields, Set<String> visited,
            JavaLoopInvariantAnalysis.Work work) {
        work.tick();
        if (!resolved(type) || type(type, SERIALIZABLE)) return false;
        if (!visited.add(type.getKey())) return true;
        for (var field : type.getDeclaredFields()) {
            work.tick();
            fields.add(field.getName());
        }
        for (var ancestor : type.getInterfaces()) if (!collectFieldNames(ancestor, fields, visited, work)) return false;
        return type.getSuperclass() == null || collectFieldNames(type.getSuperclass(), fields, visited, work);
    }

    private static List<Site> sites(CompilationUnit ast, TypeDeclaration owner, IVariableBinding receiver,
            JavaLoopInvariantAnalysis.Work work) {
        var sites = new ArrayList<Site>();
        for (var constructor : owner.getMethods()) {
            work.tick();
            if (!constructor.isConstructor()) continue;
            if (!resolved(constructor.resolveBinding()) || constructor.getBody() == null) return List.of();
            Statement assigned = null, superCall = null;
            boolean delegates = false;
            for (Object item : constructor.getBody().statements()) {
                work.tick();
                var statement = (Statement) item;
                if (statement instanceof ConstructorInvocation delegation) {
                    var target = delegation.resolveConstructorBinding();
                    if (!resolved(target) || ast.findDeclaringNode(target.getMethodDeclaration()) == null
                            || !same(target.getDeclaringClass(), owner.resolveBinding())) return List.of();
                    delegates = true;
                }
                if (statement instanceof SuperConstructorInvocation) superCall = statement;
                if (statement instanceof ExpressionStatement effect && effect.getExpression() instanceof Assignment write
                        && write.getOperator() == Assignment.Operator.ASSIGN
                        && same(receiver, instanceField(write.getLeftHandSide()))) {
                    if (assigned != null) return List.of();
                    assigned = statement;
                }
            }
            // Definite assignment in valid Java ensures this is the field's only write on each admitted root path.
            if (delegates) continue;
            if (assigned == null) return List.of();
            sites.add(new Site(constructor, superCall != null && end(superCall) > end(assigned) ? superCall : assigned));
        }
        return sites;
    }

    private static MathematicalAnalysis.VerifiedRegion emit(CompilationUnit ast, String source, MathCleanUpOptions options,
            IProgressMonitor monitor, Map<String, String> compiler, MathematicalAnalysis.SourceEnvironment environment,
            TypeDeclaration owner, Guard guard, List<Site> sites, String name, JavaLoopInvariantAnalysis.Work work) {
        // Reserve complete source preparation and generated-source validation before allocating edits.
        work.charge(10L * source.length() + 512L * sites.size());
        String delimiter = source.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String receiver = "this." + guard.receiver().getName(); //$NON-NLS-1$
        String constant = owner.getName().getIdentifier() + "." + guard.constant().getName(); //$NON-NLS-1$
        String cache = "this." + name; //$NON-NLS-1$
        String original = source.substring(guard.start(), guard.end());
        var edit = new MultiTextEdit();
        var declaration = (FieldDeclaration) ((VariableDeclarationFragment) ast.findDeclaringNode(guard.receiver())).getParent();
        int fieldStart = declaration.getStartPosition();
        edit.addChild(new InsertEdit(fieldStart, "private final boolean " + name + ";" + delimiter //$NON-NLS-1$ //$NON-NLS-2$
                + indent(source, fieldStart, "    "))); //$NON-NLS-1$
        var anchors = new ArrayList<ASTNode>(List.of(owner.getName(), declaration, guard.statement().getExpression(),
                ast.findDeclaringNode(guard.constant())));
        for (var site : sites) {
            // Extended ranges retain any line-end comment with the original assignment.
            int at = ast.getExtendedStartPosition(site.after()) + ast.getExtendedLength(site.after());
            String lineIndent = indent(source, site.after().getStartPosition(),
                    indent(source, site.constructor().getStartPosition(), "    ") + "    "); //$NON-NLS-1$ //$NON-NLS-2$
            String initialization = cache + " = " + receiver + " != null && " + receiver //$NON-NLS-1$ //$NON-NLS-2$
                    + ".getClass() == java.math.BigInteger.class" + delimiter //$NON-NLS-1$
                    + lineIndent + "    && " + constant + " != null && " //$NON-NLS-1$ //$NON-NLS-2$
                    + receiver + ".equals(" + constant + ");"; //$NON-NLS-1$ //$NON-NLS-2$
            edit.addChild(new InsertEdit(at, delimiter + lineIndent + initialization));
            anchors.add(site.constructor().getName());
            anchors.add(site.after());
        }
        // Recheck the source field after an early escape and keep the original getstatic initialization boundary.
        edit.addChild(new ReplaceEdit(guard.start(), guard.end() - guard.start(),
                "(" + cache + " ? " + receiver + " != null && " + constant + " != null : (" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + original + "))")); //$NON-NLS-1$
        var plan = JavaSourceRewritePlan.coordinated(ast, source, owner, anchors, edit);
        String replaced = plan.replacement(source);
        String generated = source.substring(0, plan.start()) + replaced + source.substring(plan.start() + plan.length());
        var proof = new ConstructorGuardEvidence(owner.resolveBinding().getKey(), guard.receiver().getKey(),
                guard.constant().getKey(), name, guard.start(),
                sites.stream().map(site -> site.constructor().resolveBinding().getKey()).toList());
        var receipt = new MathematicalAnalysis.VerifiedRegion(plan.start(), plan.length(), null, null,
                source.substring(plan.start(), plan.start() + plan.length()), replaced,
                null, plan, null, null, null, null, null, null, null, proof);
        var after = MathematicalAnalysis.checkGeneratedSource(ast, source, generated, compiler, options.targetJava(),
                monitor, environment, GeneratedSourceDiagnostics.edits(edit));
        verify(after, generated, receipt, List.of(), work);
        return receipt;
    }

    static void verify(CompilationUnit ast, String generated, MathematicalAnalysis.VerifiedRegion receipt,
            List<MathematicalAnalysis.Replacement> otherEdits, JavaLoopInvariantAnalysis.Work work) {
        var proof = receipt.constructorGuard();
        var plan = receipt.sourcePlan();
        int shift = 0;
        for (var edit : otherEdits) {
            work.tick();
            if (edit.offset() + edit.length() <= plan.start()) shift += edit.replacement().length() - edit.length();
        }
        int start = plan.start() + shift;
        if (!generated.startsWith(receipt.replacement(), start))
            throw invalid();
        if (!(ast.findDeclaringNode(proof.owner()) instanceof TypeDeclaration owner)) throw invalid();
        var cache = findCache(owner, proof, work);
        if (!(ast.findDeclaringNode(proof.receiver()) instanceof VariableDeclarationFragment receiver)
                || !(ast.findDeclaringNode(proof.constant()) instanceof VariableDeclarationFragment constant))
            throw invalid();
        for (String key : proof.constructors()) {
            work.tick();
            if (!(ast.findDeclaringNode(key) instanceof MethodDeclaration constructor)
                    || !initializerMatches(constructor, cache, receiver.resolveBinding(), constant.resolveBinding(), work))
                throw invalid();
        }
        int fallbackStart = proof.guardOffset() + shift;
        for (var change : plan.changes()) if (change.offset() < proof.guardOffset())
            fallbackStart += change.replacement().length() - change.length();
        ASTNode mapped = NodeFinder.perform(ast, fallbackStart, 1);
        while (mapped != null && !(mapped instanceof IfStatement)) mapped = mapped.getParent();
        if (!(mapped instanceof IfStatement statement)) throw invalid();
        var receiverBinding = receiver.resolveBinding();
        var constantBinding = constant.resolveBinding();
        Expression expression = conjunction(statement.getExpression()).getFirst();
        if (!(unwrap(expression) instanceof ConditionalExpression cached)
                || !same(cache, instanceField(cached.getExpression()))
                || !prefixMatches(conjunction(cached.getElseExpression()), receiverBinding, constantBinding)) throw invalid();
        var positive = conjunction(cached.getThenExpression());
        if (positive.size() != 2 || !same(receiverBinding, nonNullRead(positive.getFirst()))
                || !same(constantBinding, nonNullStaticRead(positive.getLast()))) throw invalid();
    }

    private static IVariableBinding findCache(TypeDeclaration owner, ConstructorGuardEvidence proof,
            JavaLoopInvariantAnalysis.Work work) {
        for (var field : owner.getFields()) {
            work.tick();
            for (Object fragment : field.fragments()) {
                work.tick();
                var declaration = (VariableDeclarationFragment) fragment;
                var binding = declaration.resolveBinding();
                if (resolved(binding) && binding.getName().equals(proof.cache()) && declaration.getInitializer() == null
                        && sameOwnerFinal(binding, owner.resolveBinding(), false)
                        && Modifier.isPrivate(binding.getModifiers()) && type(binding.getType(), "boolean")) return binding; //$NON-NLS-1$
            }
        }
        throw invalid();
    }

    private static boolean initializerMatches(MethodDeclaration constructor, IVariableBinding cache,
            IVariableBinding receiver, IVariableBinding constant, JavaLoopInvariantAnalysis.Work work) {
        for (Object item : constructor.getBody().statements()) {
            work.tick();
            if (item instanceof ExpressionStatement statement && statement.getExpression() instanceof Assignment assignment
                    && same(cache, instanceField(assignment.getLeftHandSide()))) {
                var terms = conjunction(assignment.getRightHandSide());
                return terms.size() == 4 && same(constant, nonNullStaticRead(terms.get(2)))
                        && prefixMatches(List.of(terms.get(0), terms.get(1), terms.get(3)), receiver, constant);
            }
        }
        return false;
    }

    private static boolean prefixMatches(List<Expression> parts, IVariableBinding receiver, IVariableBinding constant) {
        return parts.size() == 3 && same(receiver, nonNullRead(parts.getFirst()))
                && exactClassCheck(parts.get(1), receiver)
                && unwrap(parts.get(2)) instanceof MethodInvocation call && exactEquals(call, receiver)
                && same(constant, staticField((Expression) call.arguments().getFirst()));
    }

    private static IVariableBinding nonNullRead(Expression expression) {
        return instanceField(nonNullOperand(expression));
    }

    private static IVariableBinding nonNullStaticRead(Expression expression) {
        return staticField(nonNullOperand(expression));
    }

    private static Expression nonNullOperand(Expression expression) {
        if (!(unwrap(expression) instanceof InfixExpression test)
                || test.getOperator() != InfixExpression.Operator.NOT_EQUALS || test.hasExtendedOperands()) return null;
        Expression left = unwrap(test.getLeftOperand()), right = unwrap(test.getRightOperand());
        if (left instanceof NullLiteral) return right;
        return right instanceof NullLiteral ? left : null;
    }

    private static Expression unwrap(Expression expression) {
        while (expression instanceof ParenthesizedExpression parentheses) expression = parentheses.getExpression();
        return expression;
    }

    private static boolean resolved(IBinding binding) {
        return binding != null && !binding.isRecovered() && binding.getKey() != null;
    }

    private static boolean same(IBinding left, IBinding right) {
        return resolved(left) && resolved(right) && left.isEqualTo(right);
    }

    private static boolean type(ITypeBinding binding, String qualifiedName) {
        return resolved(binding) && binding.getQualifiedName().equals(qualifiedName);
    }

    private static int end(ASTNode node) {
        return node.getStartPosition() + node.getLength();
    }

    private static boolean overlaps(int a, int n, int b, int m) {
        return a < (long) b + m && b < (long) a + n;
    }

    private static String indent(String source, int offset, String fallback) {
        int line = source.lastIndexOf('\n', offset - 1) + 1;
        String prefix = source.substring(line, offset);
        return prefix.isBlank() ? prefix : fallback;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("CONSTRUCTOR_GUARD_BINDINGS_CHANGED"); //$NON-NLS-1$
    }
}
