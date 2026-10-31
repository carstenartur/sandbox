/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SemanticAssumption;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace;
import de.regelsuche.search.program.JointComputationPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A typed value graph plus the original Java effect schedule.
 * Each assignment has its own output; each source read has its own input.
 * Numeric verification says nothing about moving these source events. This
 * object has no direct conversion to an edit. Realization separately preserves
 * the event schedule and independently verifies the re-extracted emitted source.
 */
public record JavaComputationTask(JointComputationPlan plan, SourceEvaluationTrace trace,
        List<Event> schedule, Map<String, String> inputNames, Map<String, String> inputBindings,
        Set<NumericKind> selectedKinds, Set<SemanticAssumption> assumptions,
        String sourceSha256, int sourceLength, int start, int length) {
    public enum Kind { SOURCE_READ, LOCAL_COMMIT, ARRAY_STORE, RETURN }

    /** traceBefore is the number of original numeric occurrences evaluated before this event. */
    public record Event(Kind kind, String valueId, String bindingKey, int start, int length, int traceBefore) {
        public Event { Objects.requireNonNull(kind); Objects.requireNonNull(valueId); Objects.requireNonNull(bindingKey); }
    }

    public JavaComputationTask {
        schedule = List.copyOf(schedule); inputNames = Map.copyOf(inputNames); inputBindings = Map.copyOf(inputBindings);
        selectedKinds = Set.copyOf(selectedKinds); assumptions = Set.copyOf(assumptions);
        if (start < 0 || length <= 0 || (long) start + length > sourceLength)
            throw new IllegalArgumentException("INVALID_TASK_SOURCE_RANGE"); //$NON-NLS-1$
        int previousTrace = 0;
        var outputIds = plan.outputs().stream().map(JointComputationPlan.Output::name).collect(java.util.stream.Collectors.toSet());
        for (var event : schedule) {
            if (event.start() < start || event.length() <= 0 || (long) event.start() + event.length() > (long) start + length
                    || event.traceBefore() < previousTrace || event.traceBefore() > trace.occurrences().size()
                    || !(event.kind() == Kind.SOURCE_READ ? plan.inputs().containsKey(event.valueId()) : outputIds.contains(event.valueId())))
                throw new IllegalArgumentException("INVALID_TASK_SOURCE_EVENT"); //$NON-NLS-1$
            previousTrace = event.traceBefore();
        }
    }

    /** Caller supplies the remaining search allowance. Source effects never enter the numeric solver. */
    public OptimizationRequest numericRequest(OptimizationGoal goal, OptimizationBudget budget) {
        return new OptimizationRequest(plan, trace, selectedKinds, ComputationOptimizer.SEMANTICS_REVISION,
                assumptions, SafetyProfile.PRESERVE_JAVA, goal, budget, CheckedPolicy.NONE);
    }

    public boolean matchesSource(String source) { return source.length() == sourceLength && sourceSha256.equals(hash(source)); }

    static String hash(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); } //$NON-NLS-1$
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
