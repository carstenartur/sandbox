/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.ast.*;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.search.program.ComputationBackend.Type;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.PreparedJointComputation;
import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

/** Rewrites only total RHS values; calls, reads, commits and stores stay at their original positions. */
final class JavaTaskSourceRewriter {
    private JavaTaskSourceRewriter() { }
    record Applied(MathematicalAnalysis.Replacement replacement, MathematicalAnalysis.VerifiedRegion evidence) { }
    record Attempt(Optional<Applied> applied, long remaining) { }
    static final class BudgetOverrun extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        final long additional;
        BudgetOverrun(long additional) { super("TASK_COMPLETED_WORK_OVERRUN"); this.additional = additional; } //$NON-NLS-1$
    }
    private static void completed(long work, long reserved) { if (work > reserved) throw new BudgetOverrun(work - reserved); }
    private record Known(String name, Type type, List<Expr> values) { }
    /** One reserved quarter covers source discovery, realization and explicit preparations. */
    private static final class SourceWork {
        long remaining;
        final CancellationToken token;
        SourceWork(long allowance, CancellationToken token) { remaining = allowance; this.token = token; }
        void charge(long amount) {
            if (amount < 0 || amount > remaining || token.isCancelled()) throw new IllegalArgumentException("TASK_SOURCE_BUDGET_EXCEEDED"); //$NON-NLS-1$
            remaining -= amount;
        }
        List<Expr> outputs(JointComputationPlan plan) { return plan.resolveOutputs(this::charge).expressions(); }
        PreparedJointComputation prepare(JointComputationPlan plan) {
            var resolution = plan.resolveOutputs(this::charge);
            // All realized plans have no definitions. Resolution + 10 visits per expanded
            // node + 2 per output bounds preparation's intern/type/liveness passes.
            charge(resolution.work() + 10L * resolution.expandedNodes() + 2L * plan.outputs().size());
            return ComputationOptimizer.prepare(plan);
        }
    }

    static Optional<Applied> optimize(CompilationUnit ast, String source, JavaComputationTask task,
            MathCleanUpOptions options, long allowance, IProgressMonitor monitor,
            Map<String, String> compiler, MathematicalAnalysis.SourceEnvironment environment) {
        return optimizeWithRemaining(ast, source, task, options, allowance, monitor, compiler, environment).applied();
    }

    static Attempt optimizeWithRemaining(CompilationUnit ast, String source, JavaComputationTask task,
            MathCleanUpOptions options, long allowance, IProgressMonitor monitor,
            Map<String, String> compiler, MathematicalAnalysis.SourceEnvironment environment) {
        if (!task.matchesSource(source) || allowance < 6000) return new Attempt(Optional.empty(), allowance);
        CancellationToken token = () -> Thread.currentThread().isInterrupted() || monitor.isCanceled();
        // Reserve the full allocation: re-extraction and Java parsing have no exact SDK work receipt.
        var sourceWork = new SourceWork(allowance / 4, token);
        var request = task.numericRequest(options.goal(), options.optimizationBudget(allowance / 4));
        var optimizer = new ComputationOptimizer();
        var searched = optimizer.optimize(request, token);
        if (searched instanceof OptimizationResult.BudgetExceeded exceeded) completed(exceeded.work(), allowance / 4);
        if (searched instanceof OptimizationResult.Unsupported unsupported && unsupported.work() >= 0) completed(unsupported.work(), allowance / 4);
        if (!(searched instanceof OptimizationResult.Candidate candidate))
            return new Attempt(Optional.empty(), allowance - MathematicalAnalysis.consumedWork(searched, allowance / 4));
        var edit = edits(ast, source, task, candidate.plan(), options, token, sourceWork);
        if (!edit.hasChildren()) return new Attempt(Optional.empty(), 0);
        try {
            var document = new Document(source); edit.apply(document); String generated = document.get();
            var generatedAst = MathematicalAnalysis.checkGeneratedSource(ast, generated, compiler, options.targetJava(), monitor, environment);
            JavaComputationTask emitted;
            try (var context = JavaSourceContext.open(generatedAst, generated, environment, monitor, options, sourceWork.remaining)) {
                emitted = new JavaComputationExtractor().extractTasks(generatedAst, generated, options).tasks().stream()
                        .filter(t -> t.start() == task.start()).findFirst().orElseThrow(() -> new IllegalArgumentException("TASK_EMISSION_NOT_EXTRACTED")); //$NON-NLS-1$
                sourceWork.charge(context.takeWork()); context.checkpoint().requireCurrent();
            }
            var emittedPlan = bindEmitted(task, emitted, source, generated, sourceWork);
            var verified = optimizer.verify(request, emittedPlan, token);
            if (verified instanceof VerificationResult.BudgetExceeded exceeded) completed(exceeded.work(), allowance / 4);
            if (!(verified instanceof VerificationResult.Verified proof)) return new Attempt(Optional.empty(), 0);
            var prepared = sourceWork.prepare(emittedPlan);
            String before = source.substring(task.start(), task.start() + task.length());
            int newLength = task.length() + generated.length() - source.length();
            String after = generated.substring(task.start(), task.start() + newLength);
            long beforeWork = RuntimeCalculationPolicy.arithmeticOperations(before), afterWork = RuntimeCalculationPolicy.arithmeticOperations(after);
            if (afterWork >= beforeWork) return new Attempt(Optional.empty(), 0);
            var cost = new OptimizationResult.CostAssessment(beforeWork * 10, afterWork * 10, 0, 0,
                    candidate.cost().sourceCost(), prepared.cost(), true);
            // This is a fresh proof of emitted source, not an invented derivation of the producer's plan.
            sourceWork.charge(prepared.cost().inspectionWork()); // Candidate's independent constructor preparation.
            var realized = new OptimizationResult.Candidate(emittedPlan, prepared, proof.evidence(), proof.obligations(), cost,
                    OptimizationResult.SearchCompletion.IMPROVEMENT_FOUND, 0);
            var described = ComputationExplanations.describe(request, realized, allowance / 4, token);
            completed(described.work(), allowance / 4);
            if (described.explanation().isEmpty()) return new Attempt(Optional.empty(), 0);
            var names = new LinkedHashMap<String, String>();
            task.schedule().stream().filter(e -> e.kind() != JavaComputationTask.Kind.SOURCE_READ)
                    .forEach(e -> names.put(e.valueId(), e.kind() + ": " + e.bindingKey())); //$NON-NLS-1$
            var explanation = new ComputationExplanation(task.inputNames(), names, Set.of(), described.explanation().orElseThrow());
            String description = "Verified source task; original read/call/store order and local commits retained; arithmetic operations " //$NON-NLS-1$
                    + beforeWork + " -> " + afterWork + "; measured speedup: not assessed"; //$NON-NLS-1$ //$NON-NLS-2$
            return new Attempt(Optional.of(new Applied(new MathematicalAnalysis.Replacement(task.start(), task.length(), after, description),
                    new MathematicalAnalysis.VerifiedRegion(task.start(), task.length(), proof.evidence(), cost, before, after, explanation))), 0);
        } catch (org.eclipse.jface.text.BadLocationException impossible) { throw new IllegalArgumentException(impossible); }
    }

    private static MultiTextEdit edits(CompilationUnit ast, String source, JavaComputationTask task,
            JointComputationPlan candidate, MathCleanUpOptions options, CancellationToken token, SourceWork work) {
        work.charge(task.inputBindings().size());
        var available = new LinkedHashMap<String, Known>();
        task.inputBindings().forEach((id, binding) -> {
            if (!binding.startsWith("array@")) available.put(binding, new Known(task.inputNames().get(id), task.plan().inputs().get(id), List.of(new VariableExpr(id)))); //$NON-NLS-1$
        });
        var declarations = new HashMap<String, VariableDeclarationFragment>();
        ast.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) { work.charge(1); }
            @Override public boolean visit(VariableDeclarationFragment node) {
            if (node.resolveBinding() != null) declarations.put(node.resolveBinding().getKey(), node); return true;
        }});
        var originals = work.outputs(task.plan()); var optimized = work.outputs(candidate);
        var edit = new MultiTextEdit(); int output = 0;
        for (var event : task.schedule()) {
            if (token.isCancelled()) return new MultiTextEdit();
            work.charge(1);
            if (event.kind() == JavaComputationTask.Kind.SOURCE_READ) continue;
            Statement statement = statement(ast, event); Expression target = value(statement);
            if (target != null) work.charge(2L * target.getLength() + ast.getCommentList().size() + available.size());
            var type = task.plan().outputs().get(output).type();
            if (target != null && !protectedValue(ast, target) && !RuntimeCalculationPolicy.preserve(statement, declarations)) {
                var inputs = new LinkedHashMap<String, Type>(); var names = new LinkedHashMap<String, String>();
                var substitutions = new HashMap<Expr, Expr>();
                for (var known : available.values()) {
                    String id = "local" + inputs.size(); //$NON-NLS-1$
                    inputs.put(id, known.type()); names.put(id, known.name());
                    known.values().forEach(v -> substitutions.putIfAbsent(v, new VariableExpr(id)));
                }
                try {
                    Expr expression = substitute(optimized.get(output), substitutions, new int[]{0}, work);
                    var plan = new JointComputationPlan(inputs, Map.of(), List.of(new JointComputationPlan.Output("value", type, expression))); //$NON-NLS-1$
                    var prepared = work.prepare(plan); work.charge(prepared.cost().inspectionWork());
                    var emitted = new JavaComputationEmitter().emit(prepared, names, Set.copyOf(names.values()), options.targetJava());
                    String replacement = emitted.outputValues().get("value"); //$NON-NLS-1$
                    if (target instanceof Assignment assignment) replacement = assignment.getLeftHandSide() + " = " + replacement; //$NON-NLS-1$
                    String original = source.substring(target.getStartPosition(), target.getStartPosition() + target.getLength());
                    if (emitted.statements().isEmpty() && RuntimeCalculationPolicy.arithmeticOperations("return " + replacement + ";") //$NON-NLS-1$ //$NON-NLS-2$
                            < RuntimeCalculationPolicy.arithmeticOperations("return " + original + ";")) //$NON-NLS-1$ //$NON-NLS-2$
                        edit.addChild(new ReplaceEdit(target.getStartPosition(), target.getLength(), replacement));
                } catch (IllegalArgumentException | UnsupportedOperationException unavailableValue) { /* Old input or shared temporary unavailable here. */ }
            }
            if (event.kind() == JavaComputationTask.Kind.LOCAL_COMMIT) {
                String name = statement instanceof VariableDeclarationStatement declaration
                        ? ((VariableDeclarationFragment)declaration.fragments().getFirst()).getName().getIdentifier()
                        : ((Assignment)((ExpressionStatement)statement).getExpression()).getLeftHandSide().toString();
                available.put(event.bindingKey(), new Known(name, type, List.of(originals.get(output), optimized.get(output))));
            }
            output++;
        }
        return edit;
    }

    private static Expr substitute(Expr expression, Map<Expr, Expr> replacements, int[] visits, SourceWork work) {
        work.charge(1);
        if (++visits[0] > 10_000) throw new IllegalArgumentException("TASK_REALIZATION_BOUND"); //$NON-NLS-1$
        Expr known = replacements.get(expression); if (known != null) return known;
        if (expression instanceof VariableExpr) throw new IllegalArgumentException("TASK_VALUE_NO_LONGER_AVAILABLE"); //$NON-NLS-1$
        if (expression instanceof FunctionExpr function) return new FunctionExpr(function.name(), function.arguments().stream().map(a -> substitute(a, replacements, visits, work)).toList());
        if (expression instanceof BinaryExpr binary) return new BinaryExpr(substitute(binary.left(), replacements, visits, work), binary.operator(), substitute(binary.right(), replacements, visits, work));
        return expression;
    }
    private static Statement statement(CompilationUnit ast, JavaComputationTask.Event event) {
        ASTNode node = NodeFinder.perform(ast, event.start(), event.length());
        while (node != null && !(node instanceof Statement)) node = node.getParent();
        if (node == null) throw new IllegalArgumentException("TASK_EVENT_NOT_FOUND"); //$NON-NLS-1$
        return (Statement) node;
    }
    private static Expression value(Statement statement) {
        if (statement instanceof ReturnStatement returned) return returned.getExpression();
        if (statement instanceof VariableDeclarationStatement declaration)
            return ((VariableDeclarationFragment)declaration.fragments().getFirst()).getInitializer();
        if (statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment assignment)
            return assignment.getOperator() == Assignment.Operator.ASSIGN ? assignment.getRightHandSide() : assignment;
        return null;
    }
    private static boolean protectedValue(CompilationUnit ast, Expression expression) {
        boolean[] effect = {false};
        expression.accept(new ASTVisitor() { @Override public boolean preVisit2(ASTNode node) {
            if (node instanceof MethodInvocation || node instanceof ClassInstanceCreation || node instanceof ArrayAccess
                    || node instanceof FieldAccess || node instanceof QualifiedName
                    || node instanceof SimpleName name && name.resolveBinding() instanceof IVariableBinding binding && binding.isField()) effect[0] = true;
            return !effect[0];
        }});
        for (Object comment : ast.getCommentList()) {
            ASTNode node = (ASTNode)comment;
            if (node.getStartPosition() >= expression.getStartPosition() && node.getStartPosition() < expression.getStartPosition() + expression.getLength()) effect[0] = true;
        }
        return effect[0];
    }

    /** Independent re-extraction checks effect identity/order and rebinds all numeric inputs to the original request. */
    private static JointComputationPlan bindEmitted(JavaComputationTask original, JavaComputationTask emitted, String before, String after, SourceWork work) {
        work.charge(original.schedule().size() + (long)original.inputBindings().size() * emitted.inputBindings().size());
        if (original.schedule().size() != emitted.schedule().size() || original.plan().outputs().size() != emitted.plan().outputs().size())
            throw new IllegalArgumentException("TASK_EVENT_COUNT_DIFFERS"); //$NON-NLS-1$
        var replacements = new HashMap<Expr, Expr>();
        emitted.inputBindings().forEach((id, binding) -> original.inputBindings().forEach((oldId, oldBinding) -> {
            if (binding.equals(oldBinding) && !binding.startsWith("array@")) replacements.put(new VariableExpr(id), new VariableExpr(oldId)); //$NON-NLS-1$
        }));
        for (int i = 0; i < original.schedule().size(); i++) {
            var a = original.schedule().get(i); var b = emitted.schedule().get(i);
            if (a.kind() != b.kind()) throw new IllegalArgumentException("TASK_EVENT_ORDER_DIFFERS"); //$NON-NLS-1$
            if (a.kind() == JavaComputationTask.Kind.SOURCE_READ || a.kind() == JavaComputationTask.Kind.ARRAY_STORE) {
                if (!before.substring(a.start(), a.start() + a.length()).equals(after.substring(b.start(), b.start() + b.length())))
                    throw new IllegalArgumentException("TASK_MEMORY_EVENT_DIFFERS"); //$NON-NLS-1$
                if (a.kind() == JavaComputationTask.Kind.SOURCE_READ) replacements.put(new VariableExpr(b.valueId()), new VariableExpr(a.valueId()));
            } else if (a.kind() == JavaComputationTask.Kind.LOCAL_COMMIT && !a.bindingKey().equals(b.bindingKey()))
                throw new IllegalArgumentException("TASK_LOCAL_BINDING_DIFFERS"); //$NON-NLS-1$
        }
        return original.plan().withOutputs(work.outputs(emitted.plan()).stream().map(e -> substitute(e, replacements, new int[]{0}, work)).toList());
    }
}
