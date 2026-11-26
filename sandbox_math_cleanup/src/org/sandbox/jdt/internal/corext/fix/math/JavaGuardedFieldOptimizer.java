/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.sandbox.jdt.internal.corext.fix.math.JavaGuardedFieldSource.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.*;
import de.regelsuche.math.algorithms.modular.PrimeEvidenceCatalog;
import de.regelsuche.math.algorithms.modular.QuadraticRootContract;
import de.regelsuche.math.algorithms.modular.QuadraticRootSearch;
import de.regelsuche.math.algorithms.modular.SharedPowerPlan;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.power.QuadraticRootSdk;

/** The source adapter supplies executable scalar input and consumes only independently proven exact pairs. */
final class JavaGuardedFieldOptimizer {
    private JavaGuardedFieldOptimizer() {}

    static void optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selection, int length, Map<String,String> compiler, SourceEnvironment environment,
            List<Replacement> edits, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics, JavaValueSourceSummary.Work work) {
        if (options.goal() != OptimizationGoal.LOWER_ESTIMATED_RUNTIME || options.safety() != SafetyProfile.PRESERVE_JAVA
                || !options.kinds().contains(NumericKind.BIG_INTEGER)) return;
        boolean diagnosticsChecked = false;
        JavaSourceContext.valueWork(ast, work);
        try {
            for (Object top : ast.types()) {
                if (!(top instanceof TypeDeclaration type)) continue;
                for (var method : type.getMethods()) {
                    if (method.parameters().size() != 3 || method.resolveBinding() == null || method.getBody() == null
                            || !Arrays.stream(method.resolveBinding().getParameterTypes()).allMatch(JavaGuardedFieldSource::big)
                            || !overlaps(method, selection, length)) continue;
                    List<?> statements = method.getBody().statements();
                    if (statements.size() < 5 || !(statements.get(1) instanceof IfStatement)
                            || !(statements.get(2) instanceof VariableDeclarationStatement)
                            || !(statements.getLast() instanceof ReturnStatement last)
                            || !(last.getExpression() instanceof MethodInvocation factory) || factory.arguments().size() != 2) continue;
                    if (edits.stream().anyMatch(edit -> overlaps(method, edit.offset(), edit.length()))) continue;
                    if (!diagnosticsChecked) {
                        if (Arrays.stream(ast.getProblems()).anyMatch(problem -> problem.isError())) return;
                        diagnosticsChecked = true;
                    }
                    try (var local = JavaSourceContext.captureLocalSources(ast)) {
                        for (boolean swapped : List.of(false, true)) {
                            var site = new JavaGuardedFieldSource(ast, source, method, false, swapped, work);
                            if (selection >= 0 && !(selection < site.end && site.start < (long)selection + Math.max(1, length))) break;
                            if (propose(site, local, options, compiler, environment, monitor, edits, evidence, diagnostics)) break;
                        }
                    } catch (JavaValueSourceSummary.DeadlineExceeded expired) { throw expired; }
                    catch (IllegalArgumentException | ArithmeticException rejected) {
                        diagnostics.add(new Diagnostic("QUADRATIC_ROOT_UNAVAILABLE", rejected.getMessage(),
                                method.getStartPosition(), method.getLength(), Stage.SOURCE));
                    }
                }
            }
        } finally { JavaSourceContext.valueWork(ast, null); }
    }

    private static boolean propose(JavaGuardedFieldSource site, JavaSourceContext.LocalSources local, MathCleanUpOptions options,
            Map<String,String> compiler, SourceEnvironment environment, IProgressMonitor monitor,
            List<Replacement> edits, List<VerifiedRegion> evidence, List<Diagnostic> diagnostics) {
        var work = site.work;
        var prime = PrimeEvidenceCatalog.forModulus(site.modulus.value());
        if (prime.isEmpty()) throw new IllegalArgumentException("Prime evidence unavailable");
        var localHashes = localHashes(site.source, local);
        var deps = JavaSourceContext.receipts(site.unit);
        String methodHash = hash(site.source, site.method);
        String identity = MathematicalAnalysis.digest(key(site.method.resolveBinding()) + methodHash + site.definitions + localHashes + deps);
        var contract = new QuadraticRootContract(identity, site.modulus.value(), prime.get(), site.original,
                QuadraticRootContract.Observation.EXACT_PAIR);
        var found = QuadraticRootSdk.search(contract, new SharedPowerPlan.Weights(1, 1, 10),
                work.budget(work.remaining(), options), work);
        work.charge(found.work());
        if (found.certificate().isEmpty()) {
            if (!site.swappedInputs) return false;
            throw new IllegalArgumentException(found.reason());
        }
        var certificate = found.certificate().get();
        verifyCertificate(contract, certificate, options, work);
        work.charge(site.source.length());
        String prefix = "mathRoot";
        while (site.source.contains(prefix)) { work.charge(site.source.length()); prefix += "X"; }
        int line = site.source.lastIndexOf('\n', site.start - 1) + 1;
        String indent = site.source.substring(line, site.start);
        if (!indent.isBlank()) indent = "";
        var lowering = new JavaFieldProgramLowering(site, prefix, indent);
        String addition = lowering.emit(certificate.program());
        var proof = new FieldRootEvidence(certificate, key(site.method.resolveBinding()), key(site.sink.resolveMethodBinding()),
                prefix, indent, site.swappedInputs, methodHash, List.copyOf(site.definitions), localHashes, deps, found.work(), found.candidates());
        String original = site.source.substring(site.start, site.end);
        var anchors = new ArrayList<JavaSourceRewritePlan.Node>();
        for (Object item : site.method.getBody().statements()) {
            var statement = (Statement)item;
            if (statement.getStartPosition() >= site.start) anchors.add(JavaSourceRewritePlan.captureNode(statement, site.source));
        }
        var plan = new JavaSourceRewritePlan("Source.java", MathematicalAnalysis.digest(site.source), site.source.length(),
                site.start, original.length(), anchors, anchors, Map.of("field-program", anchors.stream()
                        .map(JavaSourceRewritePlan.Node::id).toList()), List.of(new JavaSourceRewritePlan.Change(
                        site.start, 0, MathematicalAnalysis.digest(""), addition)));
        plan.resolve(site.unit, site.source);
        String replacement = addition + original;
        var region = new VerifiedRegion(site.start, original.length(), null, null, original, replacement, null, plan,
                null, null, null, null, null, null, null, null, proof);
        String generated = site.source.substring(0, site.start) + addition + site.source.substring(site.start);
        work.charge(generated.length());
        var after = MathematicalAnalysis.checkGeneratedSource(site.unit, site.source, generated, compiler, options.targetJava(),
                monitor, environment, site.start, 0, addition.length());
        verify(after, generated, region, List.of(), options, environment, monitor, work);
        String message = "Verified exact ordered field coordinates. Canonical inputs satisfying the norm use the discovered schedule. "
                + "Other inputs execute the original suffix. Diagnostic source and bytecode positions are not preserved.";
        edits.add(new Replacement(site.start, original.length(), replacement, message));
        evidence.add(region);
        diagnostics.add(new Diagnostic("VERIFIED_QUADRATIC_ROOT", message, site.start, original.length(), Stage.PROOF));
        return true;
    }

    static void verify(CompilationUnit ast, String source, VerifiedRegion region, List<Replacement> edits,
            MathCleanUpOptions options, SourceEnvironment environment, IProgressMonitor monitor, JavaValueSourceSummary.Work work) {
        var proof = region.fieldRoot();
        int start = region.offset();
        for (var edit : edits) if (edit.offset() < region.offset()) start += edit.replacement().length() - edit.length();
        require(source.startsWith(region.replacement(), start));
        var node = ast.findDeclaringNode(proof.methodKey());
        var method = requireType(node, MethodDeclaration.class);
        try (var context = JavaSourceContext.open(ast, source, environment, monitor, options, work.remaining());
                var local = JavaSourceContext.captureLocalSources(ast)) {
            JavaSourceContext.valueWork(ast, work);
            var site = new JavaGuardedFieldSource(ast, source, method, true, proof.swappedInputs(), work);
            var fast = requireType(method.getBody().statements().get(2), IfStatement.class);
            require(fast.getStartPosition() == start && site.start - start == region.replacement().length() - region.original().length());
            String restored = source.substring(method.getStartPosition(), start)
                    + source.substring(site.start, method.getStartPosition() + method.getLength());
            require(MathematicalAnalysis.digest(restored).equals(proof.methodHash()) && proof.sinkKey().equals(key(site.sink.resolveMethodBinding()))
                    && List.copyOf(site.definitions).equals(proof.bindings()) && localHashes(source, local).equals(proof.localDefinitions()) && proof.dependencies().containsAll(context.checkpoint().receipts()));
            var stored = proof.certificate();
            var expected = new QuadraticRootContract(stored.contract().sourceIdentity(), site.modulus.value(), stored.contract().primeEvidence(),
                    site.original, QuadraticRootContract.Observation.EXACT_PAIR);
            var decoded = new JavaFieldProgramLowering(site, proof.prefix(), proof.indent()).decode(fast);
            require(stored.contract().equals(expected) && decoded.equals(stored.program()));
            var actual = new QuadraticRootSearch.Certificate(expected, stored.power(), decoded);
            verifyCertificate(expected, actual, options, work);
            context.checkpoint().requireCurrent();
        }
    }

    private static void verifyCertificate(QuadraticRootContract expected, QuadraticRootSearch.Certificate certificate,
            MathCleanUpOptions options, JavaValueSourceSummary.Work work) {
        var replay = QuadraticRootSdk.verify(expected, certificate, work.budget(work.remaining(), options), work);
        work.charge(replay.work());
        if (!replay.valid()) throw new IllegalArgumentException(replay.reason());
    }

    private static List<String> localHashes(String source, JavaSourceContext.LocalSources local) {
        var hashes = new ArrayList<String>();
        for (var declaration : local.declarations()) hashes.add(hash(source, declaration));
        hashes.sort(String::compareTo);
        return List.copyOf(hashes);
    }
    private static String hash(String source, ASTNode node) {
        return MathematicalAnalysis.digest(source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength()));
    }
    private static boolean overlaps(ASTNode node, int offset, int length) {
        return offset < 0 || node.getStartPosition() < (long)offset + Math.max(1, length)
                && offset < (long)node.getStartPosition() + node.getLength();
    }
}
