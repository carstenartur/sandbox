/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.sandbox.jdt.internal.corext.fix.math.JavaMontgomeryDotPattern.Site;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.*;

import de.regelsuche.math.algorithms.modular.MontgomeryReductionPlanner.Product;
import de.regelsuche.math.algorithms.modular.MontgomerySchedule.*;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.modular.MontgomeryScheduleSdk;

/** Delays pure signed reductions only when the Java return is the exact canonical observation. */
final class JavaMontgomeryDotOptimizer {
    private JavaMontgomeryDotOptimizer() { }

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String, String> compiler, SourceEnvironment environment,
            List<Replacement> replacements, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics,
            JavaValueSourceSummary.Work work) {
        if (!options.mathematicalOptIn() || options.goal() != OptimizationGoal.LOWER_ESTIMATED_RUNTIME
                || options.safety() != SafetyProfile.PRESERVE_JAVA || !options.kinds().contains(NumericKind.INT)
                || !options.kinds().contains(NumericKind.LONG) || !options.kinds().contains(NumericKind.SHORT)) return;
        try {
            if (!source.contains("floorMod") && !source.contains("pointwiseAccountMontgomery")) return; //$NON-NLS-1$ //$NON-NLS-2$
            work.charge(1L + source.length() / 16);
            if (Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError())) return;
            for (Object type : ast.types()) if (type instanceof TypeDeclaration owner) {
                for (var method : owner.getMethods()) {
                    work.charge(1);
                    var body = method.getBody();
                    if (body == null || selectionOffset >= 0
                            && !overlap(body, selectionOffset, Math.max(1, selectionLength))) continue;
                    if (bcCandidate(owner, method, source)) {
                        diagnostics.add(new Diagnostic("MONTGOMERY_CALLER_PROOF_REQUIRED", //$NON-NLS-1$
                                "The matrix kernel writes caller-owned coefficients. Its callers must prove input ranges, non-aliasing, exception order, and the downstream canonical observation.", //$NON-NLS-1$
                                body.getStartPosition(), body.getLength(), Stage.SOURCE));
                        continue;
                    }
                    if (!JavaMontgomeryDotPattern.candidate(method) || replacements.stream()
                            .anyMatch(edit -> overlap(body, edit.offset(), Math.max(1, edit.length())))) continue;
                    work.charge(body.getLength());
                    Site site;
                    try { site = JavaMontgomeryDotPattern.read(ast, method, false); }
                    catch (IllegalArgumentException unmatched) { continue; }
                    admit(ast, source, site, options, monitor, compiler, environment, replacements, evidence, diagnostics, work);
                }
            }
        } catch (JavaValueSourceSummary.DeadlineExceeded cancelled) {
            throw cancelled;
        } catch (IllegalArgumentException unavailable) {
            diagnostics.add(new Diagnostic("MONTGOMERY_UNAVAILABLE", unavailable.getMessage(), 0, source.length(), Stage.PROOF)); //$NON-NLS-1$
        }
    }

    private static boolean bcCandidate(TypeDeclaration owner, MethodDeclaration method, String source) {
        var binding = method.resolveBinding();
        if (binding == null || binding.isRecovered() || !binding.getName().equals("pointwiseAccountMontgomery") //$NON-NLS-1$
                || owner.resolveBinding() == null) return false;
        if (Arrays.stream(binding.getParameterTypes()).anyMatch(ITypeBinding::isRecovered)) return false;
        // These exact compilation units at bcgit/bc-java bb81bfa75 identify diagnostics only.
        // Neither digest gives a closed-world caller or observation proof.
        String pin = switch (owner.resolveBinding().getQualifiedName()) {
            case "org.bouncycastle.crypto.signers.mldsa.Poly" -> "5d52a1955dd4dddcb1a2638e0067e47ce62955a9432b6f6c30a06b326c59d292"; //$NON-NLS-1$ //$NON-NLS-2$
            case "org.bouncycastle.crypto.kems.mlkem.PolyVec" -> "c7647159bc82d97263bced1530b2f5cc801338f2047d834e4370b07c99d8fee5"; //$NON-NLS-1$ //$NON-NLS-2$
            default -> null;
        };
        return pin != null && pin.equals(MathematicalAnalysis.digest(source));
    }

    private static boolean overlap(ASTNode body, int start, int length) {
        return body.getStartPosition() < (long)start + length && start < body.getStartPosition() + body.getLength();
    }

    private static void admit(CompilationUnit ast, String source, Site site, MathCleanUpOptions options,
            IProgressMonitor monitor, Map<String, String> compiler, SourceEnvironment environment,
            List<Replacement> replacements, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics,
            JavaValueSourceSummary.Work work) {
        var inputs = inputs(site.count());
        var products = new ArrayList<Product>();
        for (int i = 0; i < site.count(); i++) products.add(new Product(2 * i, 2 * i + 1));
        var planned = MontgomeryScheduleSdk.dotProduct(site.parameters(), inputs, products, Word.INT,
                Observation.CANONICAL_RESIDUE, work.budget(work.remaining(), options), work);
        work.charge(planned.work());
        if (planned.certificate().isEmpty()) {
            diagnostics.add(new Diagnostic("MONTGOMERY_UNAVAILABLE", planned.reason(), site.method().getStartPosition(), //$NON-NLS-1$
                    site.method().getLength(), Stage.PROOF));
            return;
        }

        var edits = new MultiTextEdit();
        replace(edits, site.sumType(), "long"); //$NON-NLS-1$
        // Keep comments inside the argument. A comment between the name, parentheses and argument
        // belongs to the original invocation; decline if removing the invocation would lose it.
        for (Object item : ast.getCommentList()) {
            var comment = (ASTNode)item;
            if (overlap(site.reducer(), comment.getStartPosition(), comment.getLength())
                    && !overlap(site.product(), comment.getStartPosition(), comment.getLength())) return;
        }
        replace(edits, site.reducer(), slice(source, site.product()));
        replace(edits, site.observed(), site.reducer().getName().getIdentifier() + "(" + slice(source, site.observed()) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
        var body = site.method().getBody();
        var plan = JavaSourceRewritePlan.coordinated(ast, source, body,
                List.of(site.reducer().getName(), site.observed(), site.product()), edits);
        var generated = new StringBuilder(source);
        for (var change : plan.changes().reversed())
            generated.replace(change.offset(), change.offset() + change.length(), change.replacement());
        work.charge((long)source.length() + generated.length());
        var after = MathematicalAnalysis.checkGeneratedSource(ast, source, generated.toString(), compiler,
                options.targetJava(), monitor, environment, GeneratedSourceDiagnostics.edits(plan));
        var receipt = new MontgomeryDotEvidence(site.method().resolveBinding().getKey(), site.reducerKey(), planned.certificate().get());
        replay(after, receipt, options, work);
        int start = body.getStartPosition(), length = body.getLength();
        String replacement = generated.substring(start, start + length + generated.length() - source.length());
        String detail = "Delayed " + site.count() + " signed Montgomery reductions to one before Math.floorMod."; //$NON-NLS-1$ //$NON-NLS-2$
        evidence.add(new VerifiedRegion(start, length, null, null, slice(source, body), replacement,
                null, plan, null, null, null, null, null, null, null, null, receipt));
        replacements.add(new Replacement(start, length, replacement, detail));
        diagnostics.add(new Diagnostic("VERIFIED_MONTGOMERY_DOT", detail, start, length, Stage.PROOF)); //$NON-NLS-1$
    }

    private static void replace(MultiTextEdit edits, ASTNode node, String text) {
        edits.addChild(new ReplaceEdit(node.getStartPosition(), node.getLength(), text));
    }

    private static String slice(String source, ASTNode node) {
        return source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
    }

    static void replay(CompilationUnit generated, MontgomeryDotEvidence evidence, MathCleanUpOptions options,
            JavaValueSourceSummary.Work work) {
        work.charge(1L + generated.getLength() / 16);
        if (!(generated.findDeclaringNode(evidence.methodKey()) instanceof MethodDeclaration method))
            throw new IllegalArgumentException("MONTGOMERY_EMITTED_METHOD"); //$NON-NLS-1$
        Site site = JavaMontgomeryDotPattern.read(generated, method, true);
        if (!site.reducerKey().equals(evidence.reducerKey())) throw new IllegalArgumentException("MONTGOMERY_EMITTED_REDUCER"); //$NON-NLS-1$
        // Independently lower the two recognized loop grammars. Each ordered read is a fresh
        // input, even for aliased parameters. The leading addition to literal zero is exact.
        var request = new Request(site.parameters(), inputs(site.count()), schedule(site.count(), false),
                schedule(site.count(), true), Observation.CANONICAL_RESIDUE);
        var verification = MontgomeryScheduleSdk.verify(request, evidence.certificate(), work.budget(work.remaining(), options), work);
        work.charge(verification.work());
        if (!verification.valid()) throw new IllegalArgumentException(verification.reason());
    }

    private static List<Input> inputs(int count) {
        var inputs = new ArrayList<Input>(2 * count);
        var range = new Interval(Short.MIN_VALUE, Short.MAX_VALUE);
        for (int i = 0; i < count; i++) {
            inputs.add(new Input("a" + i, Word.SHORT, range)); //$NON-NLS-1$
            inputs.add(new Input("b" + i, Word.SHORT, range)); //$NON-NLS-1$
        }
        return inputs;
    }

    private static Schedule schedule(int count, boolean delayed) {
        var steps = new ArrayList<Step>();
        int total = -1, base = count * 2;
        for (int i = 0; i < count; i++) {
            int term = base + steps.size();
            steps.add(Step.apply(Operation.MULTIPLY, Word.LONG, 2 * i, 2 * i + 1));
            if (!delayed) {
                steps.add(Step.apply(Operation.MONTGOMERY, Word.INT, term));
                term++;
            }
            if (i == 0) total = term;
            else {
                steps.add(Step.apply(Operation.ADD, delayed ? Word.LONG : Word.INT, total, term));
                total = base + steps.size() - 1;
            }
        }
        if (delayed) {
            steps.add(Step.apply(Operation.MONTGOMERY, Word.INT, total));
            total = base + steps.size() - 1;
        }
        return new Schedule(steps, List.of(total));
    }
}
