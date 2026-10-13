/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.FunctionExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan;

/** Composes original typed plans by Java binding, never by recognizing a formula.
 * Only total primitive-local calculations may cross pinned call statements. */
final class JavaComputationSlices {
    private static final int MAX_PARTS = 4, MAX_STATEMENTS = 64, MAX_CHARACTERS = 16000;
    record Slice(List<JavaComputationRegion> parts, List<Statement> pinned) {
        Slice { parts = List.copyOf(parts); pinned = List.copyOf(pinned); }
    }
    record Discovery(List<JavaComputationRegion> regions, Map<JavaComputationRegion, Slice> slices,
            List<MathematicalAnalysis.Diagnostic> diagnostics, long work) {
        Discovery { regions = List.copyOf(regions); slices = Map.copyOf(slices); diagnostics = List.copyOf(diagnostics); }
    }
    private JavaComputationSlices() { }

    static Discovery discover(CompilationUnit unit, List<JavaComputationRegion> original, MathCleanUpOptions options,
            CancellationToken cancellation) {
        if (options.safety() != SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn() || original.size() < 2)
            return new Discovery(original, Map.of(), List.of(), 0);
        var budget = new Work(Math.min(65536, options.workBudget() / 16), cancellation);
        var result = new ArrayList<JavaComputationRegion>();
        var slices = new LinkedHashMap<JavaComputationRegion, Slice>();
        var diagnostics = new ArrayList<MathematicalAnalysis.Diagnostic>();
        try {
            for (int index = 0; index < original.size(); index++) {
                var parts = new ArrayList<JavaComputationRegion>(); parts.add(original.get(index));
                JavaComputationRegion selected = parts.getFirst();
                int consumed = index;
                for (int next = index + 1; parts.size() < MAX_PARTS && next < original.size(); next++) {
                    var attempt = new ArrayList<>(parts); attempt.add(original.get(next));
                    List<Statement> pinned = eligible(unit, attempt, budget);
                    if (pinned == null) break;
                    parts = attempt;
                    JavaComputationRegion joined = compose(unit, parts, budget);
                    // Independent values may meet only in a third/fourth source region.
                    if (joined == null) continue;
                    slices.remove(selected); selected = joined; consumed = next;
                    slices.put(selected, new Slice(parts, pinned));
                }
                index = consumed;
                result.add(selected);
            }
        } catch (Limit exceeded) {
            // Discovery never consumes the whole file allowance or hides the original regions.
            return new Discovery(original, Map.of(), List.of(new MathematicalAnalysis.Diagnostic(
                    "SOURCE_SLICE_LIMIT", "Bounded source connection stopped; original regions retained", 0, 0)), budget.used); //$NON-NLS-1$ //$NON-NLS-2$
        }
        slices.forEach((region, slice) -> diagnostics.add(new MathematicalAnalysis.Diagnostic(
                "SOURCE_SLICE_CONNECTED", "Connected " + slice.parts().size() + " original regions; " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + slice.pinned().size() + " call statements remain pinned", region.start(), region.length()))); //$NON-NLS-1$
        return new Discovery(result, slices, diagnostics, budget.used);
    }

    private static List<Statement> eligible(CompilationUnit unit, List<JavaComputationRegion> parts, Work work) {
        int start = parts.getFirst().start(), end = end(parts.getLast());
        if (end <= start || end - start > MAX_CHARACTERS) return null;
        ASTNode first = NodeFinder.perform(unit, start, 0);
        while (first != null && !(first instanceof Statement)) first = first.getParent();
        if (first == null || !(first.getParent() instanceof Block block)) return null;
        var selected = new HashSet<Integer>();
        for (var part : parts) {
            if (!part.arrayReads().isEmpty() || !part.assumptions().isEmpty() || !part.receiverGuards().isEmpty()) return null;
            for (var output : bindings(part)) {
                if (!output.declaration() && !output.returnValue()) return null;
                ASTNode statement = NodeFinder.perform(unit, output.statementStart(), output.statementLength());
                if (!(statement instanceof Statement) || statement.getParent() != block
                        || statement.getStartPosition() != output.statementStart() || statement.getLength() != output.statementLength()) return null;
                selected.add(output.statementStart());
            }
        }
        if (selected.size() > MAX_STATEMENTS) return null;
        var produced = new HashSet<String>();
        parts.forEach(part -> bindings(part).stream().filter(o -> !o.returnValue()).forEach(o -> produced.add(o.bindingKey())));
        var pinned = new ArrayList<Statement>();
        int count = 0;
        for (Object value : block.statements()) {
            Statement statement = (Statement) value;
            if (statement.getStartPosition() < start || statement.getStartPosition() >= end) continue;
            if (++count > MAX_STATEMENTS) return null;
            if (selected.contains(statement.getStartPosition())) {
                if (!pureStatement(statement, work)) return null;
            } else {
                if (!(statement instanceof ExpressionStatement expression)
                        || !(expression.getExpression() instanceof MethodInvocation call)
                        || call.resolveMethodBinding() == null || call.resolveMethodBinding().isRecovered()) return null;
                boolean[] safe = {true};
                statement.accept(new ASTVisitor() {
                    @Override public boolean preVisit2(ASTNode node) {
                        work.tick();
                        if (node instanceof Assignment || node instanceof PostfixExpression || node instanceof LambdaExpression
                                || node instanceof AnonymousClassDeclaration || node instanceof MethodReference
                                || node instanceof PrefixExpression p && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                                    || p.getOperator() == PrefixExpression.Operator.DECREMENT)) safe[0] = false;
                        if (node instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding
                                && (binding.isRecovered() || produced.contains(binding.getKey()))) safe[0] = false;
                        return safe[0];
                    }
                });
                if (!safe[0]) return null;
                pinned.add(statement);
            }
        }
        return pinned.isEmpty() ? null : pinned;
    }

    private static boolean pureStatement(Statement statement, Work work) {
        Expression value;
        if (statement instanceof VariableDeclarationStatement declaration && declaration.fragments().size() == 1) {
            var fragment = (VariableDeclarationFragment) declaration.fragments().getFirst();
            var binding = fragment.resolveBinding();
            if (binding == null || binding.isRecovered() || !stable(binding) || !integral(binding.getType())) return false;
            value = fragment.getInitializer();
        } else if (statement instanceof ReturnStatement returned) value = returned.getExpression();
        else return false;
        if (value == null || !integral(value.resolveTypeBinding())) return false;
        boolean[] safe = {true};
        value.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                work.tick();
                if (node instanceof SimpleName name) {
                    if (!(name.resolveBinding() instanceof IVariableBinding binding) || binding.isRecovered()
                            || binding.isField() || !stable(binding) || !integral(binding.getType())) safe[0] = false;
                } else if (node instanceof InfixExpression expression) {
                    if (!Set.of(InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS, InfixExpression.Operator.TIMES,
                            InfixExpression.Operator.AND, InfixExpression.Operator.OR, InfixExpression.Operator.XOR,
                            InfixExpression.Operator.LEFT_SHIFT, InfixExpression.Operator.RIGHT_SHIFT_SIGNED,
                            InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED).contains(expression.getOperator())) safe[0] = false;
                } else if (node instanceof PrefixExpression expression) {
                    if (!Set.of(PrefixExpression.Operator.PLUS, PrefixExpression.Operator.MINUS,
                            PrefixExpression.Operator.COMPLEMENT).contains(expression.getOperator())) safe[0] = false;
                } else if (!(node instanceof NumberLiteral || node instanceof CharacterLiteral || node instanceof ParenthesizedExpression
                        || node instanceof CastExpression || node instanceof PrimitiveType)) safe[0] = false;
                return safe[0];
            }
        });
        return safe[0];
    }

    private static boolean stable(IVariableBinding binding) {
        return Modifier.isFinal(binding.getModifiers()) || binding.isEffectivelyFinal();
    }

    private static boolean integral(ITypeBinding type) {
        return type != null && !type.isRecovered() && type.isPrimitive()
                && Set.of("byte", "short", "char", "int", "long").contains(type.getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    private static JavaComputationRegion compose(CompilationUnit unit, List<JavaComputationRegion> parts, Work work) {
        var inputs = new LinkedHashMap<String, Type>();
        var names = new LinkedHashMap<String, String>();
        var byBinding = new HashMap<String, Expr>();
        var allOutputs = new ArrayList<JavaComputationRegion.OutputBinding>();
        var expressions = new ArrayList<JointComputationPlan.Output>();
        var internal = new ArrayList<JavaComputationRegion.OutputBinding>();
        var occurrences = new ArrayList<SourceEvaluationTrace.Occurrence>();
        var reserved = new HashSet<String>();
        int connections = 0;
        var declaredInParts = new HashSet<String>();
        for (var part : parts) {
            var sourceBindings = new HashMap<String, IVariableBinding>();
            for (var output : bindings(part)) {
                ASTNode statement = NodeFinder.perform(unit, output.statementStart(), output.statementLength());
                statement.accept(new ASTVisitor() {
                    @Override public boolean visit(SimpleName name) {
                        work.tick();
                        if (!name.isDeclaration() && name.resolveBinding() instanceof IVariableBinding binding) {
                            IVariableBinding old = sourceBindings.putIfAbsent(name.getIdentifier(), binding);
                            if (old != null && !old.isEqualTo(binding)) throw new Limit();
                        }
                        return true;
                    }
                });
            }
            var substitutions = new HashMap<String, Expr>();
            for (var input : part.plan().inputs().entrySet()) {
                String name = part.inputNames().get(input.getKey());
                IVariableBinding binding = sourceBindings.get(name);
                if (binding == null || binding.isRecovered()) return null;
                Expr value = byBinding.get(binding.getKey());
                if (value == null) {
                    String id = "sliceInput" + inputs.size(); //$NON-NLS-1$
                    value = new VariableExpr(id); byBinding.put(binding.getKey(), value);
                    inputs.put(id, input.getValue()); names.put(id, name);
                } else if (declaredInParts.contains(binding.getKey())) connections++;
                substitutions.put(input.getKey(), value);
            }
            for (var occurrence : part.trace().occurrences()) {
                occurrences.add(new SourceEvaluationTrace.Occurrence(occurrence.sourceId(), substitute(occurrence.expression(), substitutions, work, 0),
                        occurrence.declaredKind(), occurrence.evaluatedKind()));
            }
            for (int index = 0; index < part.outputs().size(); index++) {
                var output = part.outputs().get(index); String id = "sliceOutput" + allOutputs.size(); //$NON-NLS-1$
                var expression = substitute(part.plan().outputs().get(index).expression(), substitutions, work, 0);
                allOutputs.add(rename(output, id));
                expressions.add(new JointComputationPlan.Output(id, output.declaredKind().type(), expression));
                if (!output.returnValue()) { byBinding.put(output.bindingKey(), expression); declaredInParts.add(output.bindingKey()); }
            }
            internal.addAll(part.internalBindings()); reserved.addAll(part.reservedNames());
        }
        if (connections == 0) return null;
        int start = parts.getFirst().start(), end = end(parts.getLast());
        var visible = new HashSet<String>();
        // Include uses in closures and later statements; never delete an externally observed declaration.
        ASTNode scope = NodeFinder.perform(unit, start, 0);
        while (scope != null && !(scope instanceof MethodDeclaration)) scope = scope.getParent();
        if (scope == null) return null;
        scope.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.tick(); }
            @Override public boolean visit(SimpleName name) {
                if ((name.getStartPosition() < start || name.getStartPosition() >= end)
                        && name.resolveBinding() instanceof IVariableBinding binding) visible.add(binding.getKey());
                return true;
            }
        });
        for (var output : allOutputs) for (Object entry : unit.getCommentList()) {
            ASTNode comment = (ASTNode) entry;
            if (comment.getStartPosition() >= output.statementStart()
                    && comment.getStartPosition() < output.statementStart() + output.statementLength()) visible.add(output.bindingKey());
        }
        var live = new ArrayList<JavaComputationRegion.OutputBinding>();
        var outputs = new ArrayList<JointComputationPlan.Output>();
        for (int i = 0; i < allOutputs.size(); i++) {
            var output = allOutputs.get(i);
            if (output.returnValue() || visible.contains(output.bindingKey())) { live.add(output); outputs.add(expressions.get(i)); }
            else internal.add(output);
        }
        if (live.isEmpty()) return null;
        // Substitution can expand shared local values exponentially. Bound the
        // resulting tree (including trace multiplicity) before record hashing
        // or SDK preparation performs any traversal of the composed request.
        for (var output : outputs) requireBounded(output.expression(), work, 0);
        for (var occurrence : occurrences) requireBounded(occurrence.expression(), work, 0);
        return new JavaComputationRegion(new JointComputationPlan(inputs, Map.of(), outputs), new SourceEvaluationTrace(occurrences),
                live, names, Set.of(), reserved, Set.of(), start, end - start, internal, List.of());
    }

    private static void requireBounded(Expr expression, Work work, int depth) {
        work.tick(); if (depth > 64) throw new Limit();
        if (expression instanceof FunctionExpr function)
            for (Expr child : function.arguments()) requireBounded(child, work, depth + 1);
    }

    private static Expr substitute(Expr expression, Map<String, Expr> substitutions, Work work, int depth) {
        work.tick(); if (depth > 64) throw new Limit();
        if (expression instanceof VariableExpr variable) {
            Expr value = substitutions.get(variable.name()); if (value == null) throw new Limit(); return value;
        }
        if (expression instanceof FunctionExpr function) return new FunctionExpr(function.name(), function.arguments().stream()
                .map(child -> substitute(child, substitutions, work, depth + 1)).toList());
        return expression;
    }
    private static JavaComputationRegion.OutputBinding rename(JavaComputationRegion.OutputBinding output, String id) {
        return new JavaComputationRegion.OutputBinding(id, output.javaName(), output.declaredKind(), output.initializerStart(),
                output.initializerLength(), output.statementStart(), output.statementLength(), output.declaration(), output.bindingKey(), output.returnValue());
    }
    private static List<JavaComputationRegion.OutputBinding> bindings(JavaComputationRegion region) {
        var all = new ArrayList<>(region.outputs()); all.addAll(region.internalBindings());
        all.sort(Comparator.comparingInt(JavaComputationRegion.OutputBinding::statementStart)); return all;
    }
    private static int end(JavaComputationRegion region) { return Math.addExact(region.start(), region.length()); }
    private static final class Work {
        private final long maximum; private final CancellationToken token; private long used;
        Work(long maximum, CancellationToken token) { this.maximum = maximum; this.token = token; }
        void tick() { if (used >= maximum || token.isCancelled()) throw new Limit(); used++; }
    }
    private static final class Limit extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Limit() { super(null, null, false, false); }
    }
}
