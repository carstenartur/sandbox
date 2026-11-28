/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

import de.regelsuche.math.algorithms.iteration.OrderedInputPrefix.Schedule;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.iteration.TableXorReductionSdk;

/** Peel a bounded prefix without moving any Java array read, write or exception past another. */
public final class JavaOrderedInputPrefix {
    private JavaOrderedInputPrefix() { }

    public record Evidence(JavaByteTableAccumulation source, Schedule schedule) {
        public Evidence {
            require(TableXorReductionSdk.verifyOrderedPrefix(
                    TableXorReductionSdk.describe(source.loopOrder(), source.storage()), schedule));
        }

        /** Counts loop tests/updates only. The JVM may already optimize some of them. */
        String controlCost() {
            int width = schedule.prefixIndices().size();
            return "At least " + width + " inputs: " + width + " fewer index increments and " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + (width - 2) + " fewer comparisons per reduction. Shorter inputs incur two extra comparisons."; //$NON-NLS-1$
        }
    }
    private record Loop(ForStatement node, VariableDeclarationFragment index, SimpleName bound, Block body) { }

    static void optimize(CompilationUnit unit, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selection, int selectionLength, Map<String, String> compiler, MathematicalAnalysis.SourceEnvironment env,
            List<JavaByteTableAccumulation> regions, List<MathematicalAnalysis.Replacement> edits,
            List<MathematicalAnalysis.VerifiedRegion> verified, List<MathematicalAnalysis.Diagnostic> diagnostics,
            JavaValueSourceSummary.Work work) {
        if (options.goal() != OptimizationGoal.LOWER_ESTIMATED_RUNTIME || options.safety() != SafetyProfile.PRESERVE_JAVA) return;
        for (var region : regions) {
            try {
                var descriptor = TableXorReductionSdk.describe(region.loopOrder(), region.storage());
                var plan = TableXorReductionSdk.orderedPrefixPlan(descriptor, 4).orElseThrow(JavaOrderedInputPrefix::unavailable);
                var loop = loop(unit, region);
                int start = loop.node().getStartPosition(), length = loop.node().getLength();
                if (selection >= 0 && (selection > start || (long) selection + selectionLength < (long) start + length)) continue;
                if (edits.stream().anyMatch(e -> start < (long) e.offset() + e.length() && e.offset() < (long) start + length)) continue;
                if (length > 1200) continue;
                work.charge(unit.getCommentList().size() + 1L);
                if (containsComment(unit, start, length)) continue;
                if (!options.orderedInputPrefix()) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("ORDERED_INPUT_PREFIX_AVAILABLE", //$NON-NLS-1$
                            "An ordered four-input prefix can be reviewed by enabling the ordered input prefix option.", //$NON-NLS-1$
                            start, length, MathematicalAnalysis.Stage.SOURCE));
                    continue;
                }
                work.charge(2L * source.length() + 8L * length);
                String replacement = render(loop, source, plan);
                var edit = new MathematicalAnalysis.Replacement(start, length, replacement,
                        "Peel four ordered input iterations before the original table reduction"); //$NON-NLS-1$
                var sourcePlan = JavaSourceRewritePlan.replaceStatements(unit, source, List.of(loop.node()), List.of(replacement));
                String generated = source.substring(0, start) + replacement + source.substring(start + length);
                var after = MathematicalAnalysis.checkGeneratedSource(unit, source, generated, compiler, options.targetJava(),
                        monitor, env, GeneratedSourceDiagnostics.edits(sourcePlan));
                var proof = new Evidence(region, plan);
                replay(unit, after, proof, start, replacement.length(), work);
                edits.add(edit);
                verified.add(new MathematicalAnalysis.VerifiedRegion(start, length, null, null, source.substring(start, start + length),
                        replacement, null, sourcePlan, null, null, null, null, null, null, null, null, null, proof));
            } catch (JavaValueSourceSummary.DeadlineExceeded expired) { throw expired; }
            catch (IllegalArgumentException unavailable) { /* Outside the checked or affordable source fragment. */ }
        }
    }

    private static boolean containsComment(CompilationUnit unit, int start, int length) {
        for (Object entry : unit.getCommentList()) {
            int position = ((Comment) entry).getStartPosition();
            if (position >= start && position < start + length) return true;
        }
        return false;
    }

    private static Loop loop(CompilationUnit unit, JavaByteTableAccumulation region) {
        var inner = region.loops().getLast();
        if (!(NodeFinder.perform(unit, inner.sourceStart(), inner.sourceLength()) instanceof ForStatement node)
                || !(node.getBody() instanceof Block body) || node.initializers().size() != 1
                || !(node.initializers().getFirst() instanceof VariableDeclarationExpression declaration)
                || declaration.fragments().size() != 1 || !(declaration.fragments().getFirst() instanceof VariableDeclarationFragment index)
                || !(index.getInitializer() instanceof NumberLiteral zero) || !Integer.valueOf(0).equals(zero.resolveConstantExpressionValue())
                || !(node.getExpression() instanceof InfixExpression condition) || condition.hasExtendedOperands()
                || condition.getOperator() != InfixExpression.Operator.LESS || !(condition.getRightOperand() instanceof SimpleName bound)
                || !(bound.resolveBinding() instanceof IVariableBinding limit) || limit.isRecovered() || limit.isField()
                || !"int".equals(limit.getType().getQualifiedName()) || !binding(index.getName()).getKey().equals(inner.counterBinding())) throw unavailable(); //$NON-NLS-1$
        return new Loop(node, index, bound, body);
    }

    private static String render(Loop loop, String source, Schedule plan) {
        String bound = loop.bound().getIdentifier(), condition = bound + " >= " + plan.prefixThreshold(); //$NON-NLS-1$
        int lineStart = source.lastIndexOf('\n', loop.node().getStartPosition()) + 1;
        String linePrefix = source.substring(lineStart, loop.node().getStartPosition());
        String indent = linePrefix.isBlank() ? linePrefix : ""; //$NON-NLS-1$
        String step = indent.indexOf('\t') >= 0 ? "\t" : "    "; //$NON-NLS-1$ //$NON-NLS-2$
        String lineBreak = source.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String nested = indent + step, prefix = nested + step;
        var indexUses = new ArrayList<SimpleName>();
        loop.body().accept(new ASTVisitor() {
            @Override public boolean visit(SimpleName name) {
                if (loop.index().resolveBinding().isEqualTo(name.resolveBinding())) indexUses.add(name);
                return true;
            }
        });
        require(!indexUses.isEmpty());
        int bodyStart = loop.body().getStartPosition();
        String body = source.substring(bodyStart, bodyStart + loop.body().getLength());
        var result = new StringBuilder("{").append(lineBreak).append(nested).append("if (").append(condition).append(") {"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int index : plan.prefixIndices()) {
            var copy = new StringBuilder(body);
            for (var use : indexUses.reversed()) {
                int position = use.getStartPosition() - bodyStart;
                copy.replace(position, position + use.getLength(), Integer.toString(index));
            }
            result.append(lineBreak).append(prefix).append(copy.toString().replace(lineBreak, lineBreak + step + step));
        }
        result.append(lineBreak).append(nested).append('}').append(lineBreak).append(nested);
        int start = loop.node().getStartPosition(), zero = loop.index().getInitializer().getStartPosition();
        String tail = source.substring(start, zero) + bound + " >= " + plan.tailThreshold() + " ? " + plan.fastTailStart() //$NON-NLS-1$ //$NON-NLS-2$
                + " : " + plan.fallbackTailStart() + source.substring(zero + loop.index().getInitializer().getLength(), //$NON-NLS-1$
                        start + loop.node().getLength());
        return result.append(tail.replace(lineBreak, lineBreak + step)).append(lineBreak).append(indent).append('}').toString();
    }

    /** Reconstruct indices and both partitions from the compiled AST, independently of the renderer. */
    static void replay(CompilationUnit before, CompilationUnit after, Evidence proof, int start, int length,
            JavaValueSourceSummary.Work work) {
        var old = loop(before, proof.source());
        if (!(NodeFinder.perform(after, start, length) instanceof Block replacement) || replacement.statements().size() != 2
                || !(replacement.statements().getFirst() instanceof IfStatement guard) || guard.getElseStatement() != null
                || !(guard.getThenStatement() instanceof Block prefix) || !(replacement.statements().getLast() instanceof ForStatement tail)
                || tail.initializers().size() != 1 || !(tail.initializers().getFirst() instanceof VariableDeclarationExpression declaration)
                || declaration.fragments().size() != 1 || !(declaration.fragments().getFirst() instanceof VariableDeclarationFragment index)
                || !(index.getInitializer() instanceof ConditionalExpression initial)) throw unavailable();
        require(binding(index.getName()).getType().isEqualTo(binding(old.index().getName()).getType()));
        require(tail.updaters().size() == old.node().updaters().size() && tail.updaters().size() == 1);
        var tailMatcher = new BodyMatcher(old.index().resolveBinding(), index.resolveBinding(), work);
        require(old.node().getExpression().subtreeMatch(tailMatcher, tail.getExpression())
                && ((ASTNode) old.node().updaters().getFirst()).subtreeMatch(tailMatcher, tail.updaters().getFirst())
                && old.body().subtreeMatch(tailMatcher, tail.getBody()));
        var indices = new ArrayList<Integer>();
        for (var candidateBody : prefix.statements()) {
            var matcher = new BodyMatcher(old.index().resolveBinding(), null, work);
            require(old.body().subtreeMatch(matcher, candidateBody) && matcher.literal != null);
            indices.add(matcher.literal);
        }
        var decoded = new Schedule(threshold(guard.getExpression(), old.bound()), indices,
                threshold(initial.getExpression(), old.bound()), integer(initial.getThenExpression()), integer(initial.getElseExpression()));
        var model = TableXorReductionSdk.describe(proof.source().loopOrder(), proof.source().storage());
        require(decoded.equals(proof.schedule()) && TableXorReductionSdk.verifyOrderedPrefix(model, decoded));
    }

    private static int threshold(Expression expression, SimpleName bound) {
        if (!(expression instanceof InfixExpression test) || test.hasExtendedOperands()
                || test.getOperator() != InfixExpression.Operator.GREATER_EQUALS || !(test.getLeftOperand() instanceof SimpleName actual)
                || !binding(actual).isEqualTo(binding(bound))) throw unavailable();
        return integer(test.getRightOperand());
    }

    private static int integer(Expression expression) {
        if (!(expression instanceof NumberLiteral literal) || !(literal.resolveConstantExpressionValue() instanceof Integer value)) throw unavailable();
        return value;
    }

    /** All tree nodes retain their Java operators, types and ordering. Only the counter is substituted. */
    private static final class BodyMatcher extends ASTMatcher {
        private final IVariableBinding original, residual;
        private final JavaValueSourceSummary.Work work;
        private final Map<String, IVariableBinding> locals = new HashMap<>();
        private Integer literal;

        BodyMatcher(IVariableBinding original, IVariableBinding residual, JavaValueSourceSummary.Work work) {
            this.original = Objects.requireNonNull(original);
            this.residual = residual;
            this.work = work;
        }
        @Override public boolean match(SimpleName expected, Object candidate) {
            work.charge(1);
            var variable = binding(expected);
            if (original.isEqualTo(variable)) {
                if (residual != null) return candidate instanceof SimpleName name && residual.isEqualTo(binding(name));
                if (!(candidate instanceof NumberLiteral constant)) return false;
                int value = integer(constant);
                if (literal != null && literal != value) return false;
                literal = value;
                return true;
            }
            if (!(candidate instanceof SimpleName name) || !expected.getIdentifier().equals(name.getIdentifier())) return false;
            var actual = binding(name);
            if (expected.isDeclaration()) {
                if (!name.isDeclaration() || !variable.getType().isEqualTo(actual.getType())) return false;
                locals.put(variable.getKey(), actual);
            }
            return locals.getOrDefault(variable.getKey(), variable).isEqualTo(actual);
        }
    }

    private static IVariableBinding binding(SimpleName name) {
        if (!(name.resolveBinding() instanceof IVariableBinding variable) || variable.isRecovered()) throw unavailable();
        return variable;
    }

    private static void require(boolean condition) { if (!condition) throw unavailable(); }
    private static IllegalArgumentException unavailable() { return new IllegalArgumentException("BYTE_TABLE_PREFIX_REPLAY_DIFFERS"); } //$NON-NLS-1$
}
