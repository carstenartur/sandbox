/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** Advisory review metadata. It never admits a candidate, changes proof or predicts measured time. */
public final class MathCandidateReview {
    private static final int MAX_SOURCE_CHARS = 1_000_000;
    private static final long MAX_DIFF_CELLS = 1_000_000;
    private static final int LARGE_DIFF_LINES = 40, MANY_NEW_LOCALS = 8;
    private MathCandidateReview() { }

    public record Assessment(String disposition, long estimatedScoreSaving, int changedLines,
            int addedBranches, int addedLocals, List<String> cautions) {
        public Assessment { cautions = List.copyOf(cautions); }
    }

    public static Assessment assess(String original, String replacement, long sourceScore, long candidateScore,
            boolean estimatedImprovement, boolean contractReviewRequired, int guardedReceivers) {
        return assess(original, replacement, sourceScore, candidateScore, estimatedImprovement,
                contractReviewRequired, guardedReceivers, 17);
    }

    public static Assessment assess(String original, String replacement, long sourceScore, long candidateScore,
            boolean estimatedImprovement, boolean contractReviewRequired, int guardedReceivers, int targetJava) {
        Objects.requireNonNull(original); Objects.requireNonNull(replacement);
        if (targetJava < 8 || targetJava > 25 || sourceScore < 0 || candidateScore < 0 || guardedReceivers < 0
                || original.length() > MAX_SOURCE_CHARS || replacement.length() > MAX_SOURCE_CHARS)
            throw new IllegalArgumentException("REVIEW_INPUT_OUTSIDE_BOUND"); //$NON-NLS-1$
        int[] before = structure(original, targetJava), after = structure(replacement, targetJava);
        int branches = Math.max(0, after[0] - before[0]), locals = Math.max(0, after[1] - before[1]);
        int changedLines = changedLines(original, replacement);
        var cautions = new ArrayList<String>();
        cautions.add("ESTIMATE_NOT_BENCHMARK"); //$NON-NLS-1$
        cautions.add("TIMING_BEHAVIOR_NOT_VERIFIED"); //$NON-NLS-1$
        if (contractReviewRequired) cautions.add("CONTRACT_CHANGE_ALLOWED"); //$NON-NLS-1$
        if (guardedReceivers > 0) cautions.add("RECEIVER_GUARDS_RETAINED"); //$NON-NLS-1$
        if (branches > 0) cautions.add("ADDED_CONTROL_FLOW"); //$NON-NLS-1$
        boolean large = changedLines < 0 || changedLines > LARGE_DIFF_LINES || locals > MANY_NEW_LOCALS;
        if (large) cautions.add("LARGE_REWRITE"); //$NON-NLS-1$
        if (changedLines < 0) cautions.add("DIFF_LIMIT_REACHED"); //$NON-NLS-1$
        long saving = sourceScore - candidateScore; // Nonnegative operands cannot overflow this subtraction.
        String disposition = !estimatedImprovement || saving <= 0 ? "NO_ESTIMATED_GAIN" //$NON-NLS-1$
                : contractReviewRequired ? "CONTRACT_REVIEW" //$NON-NLS-1$
                : large ? "LARGE_REWRITE_REVIEW" //$NON-NLS-1$
                : branches > 0 || guardedReceivers > 0 ? "GUARDED_REVIEW" : "COMPACT_REVIEW"; //$NON-NLS-1$ //$NON-NLS-2$
        return new Assessment(disposition, saving, changedLines, branches, locals, cautions);
    }

    private static int[] structure(String source, int targetJava) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_STATEMENTS); parser.setSource(source.toCharArray());
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions(targetJava == 8 ? "1.8" : Integer.toString(targetJava), options); //$NON-NLS-1$
        parser.setCompilerOptions(options);
        ASTNode root = parser.createAST(null);
        if (root.getRoot() instanceof CompilationUnit unit) {
            for (var problem : unit.getProblems()) if (problem.isError())
                throw new IllegalArgumentException("REVIEW_SOURCE_NOT_PARSED"); //$NON-NLS-1$
        }
        int[] result = {0, 0};
        root.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {
                if ((node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0)
                    throw new IllegalArgumentException("REVIEW_SOURCE_NOT_PARSED"); //$NON-NLS-1$
            }
            @Override public boolean visit(IfStatement node) { result[0]++; return true; }
            @Override public boolean visit(ConditionalExpression node) { result[0]++; return true; }
            @Override public boolean visit(SwitchStatement node) { result[0]++; return true; }
            @Override public boolean visit(VariableDeclarationFragment node) { result[1]++; return true; }
        });
        return result;
    }

    /** Exact inserted/deleted line count, ignoring indentation. -1 means the explicit work limit was reached. */
    private static int changedLines(String original, String replacement) {
        String[] before = original.lines().map(String::strip).toArray(String[]::new);
        String[] after = replacement.lines().map(String::strip).toArray(String[]::new);
        int prefix = 0, endBefore = before.length, endAfter = after.length;
        while (prefix < endBefore && prefix < endAfter && before[prefix].equals(after[prefix])) prefix++;
        while (endBefore > prefix && endAfter > prefix && before[endBefore - 1].equals(after[endAfter - 1])) { endBefore--; endAfter--; }
        int n = endBefore - prefix, m = endAfter - prefix;
        if ((long) n * m > MAX_DIFF_CELLS) return -1;
        int[] row = new int[m + 1];
        for (int i = prefix; i < endBefore; i++) {
            int diagonal = 0;
            for (int j = 1; j <= m; j++) {
                int previous = row[j];
                row[j] = before[i].equals(after[prefix + j - 1]) ? diagonal + 1 : Math.max(row[j], row[j - 1]);
                diagonal = previous;
            }
        }
        return n + m - 2 * row[m];
    }
}
