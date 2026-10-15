/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.HashMap;
import java.util.HashSet;
import org.eclipse.jdt.core.dom.CompilationUnit;
import de.regelsuche.sdk.optimization.SemanticAssumption;
import de.regelsuche.sdk.optimization.SemanticAssumption.Kind;

/** Refines emitted guards after source-region projection; all numerical assumptions remain explicit. */
final class JavaSourceGuardRefinement {
    private JavaSourceGuardRefinement() { }

    static JavaComputationRegion refine(JavaComputationRegion region, CompilationUnit unit) {
        if (region.receiverGuards().isEmpty()) return region;
        var requirements = new HashMap<String, Boolean>();
        region.receiverGuards().forEach(name -> requirements.put(name, false));
        for (var assumption : region.assumptions()) {
            String name = region.inputNames().get(assumption.subject());
            if (assumption.kind() == Kind.POSITIVE && requirements.containsKey(name)) requirements.put(name, true);
        }
        var proven = new HashSet<>(JavaDominatingGuardFacts.provenReceivers(unit, region.start(), region.length(), requirements));
        var sourceFacts = JavaSourceContext.proveReceivers(unit, region, requirements);
        proven.addAll(sourceFacts.keySet());
        if (proven.isEmpty()) return region;
        var guards = new HashSet<>(region.receiverGuards());
        guards.removeAll(proven);
        var assumptions = new HashSet<SemanticAssumption>();
        for (var assumption : region.assumptions()) {
            String receiver = region.inputNames().get(assumption.subject());
            boolean sourceProven = receiver != null && proven.contains(receiver);
            boolean guardFact = assumption.kind() == Kind.BIG_INTEGER_VALUE_SEMANTICS || assumption.kind() == Kind.POSITIVE
                    || (assumption.kind() == Kind.BIG_INTEGER_BIT_LENGTH_BOUND && "4096".equals(assumption.parameter())); //$NON-NLS-1$
            String provenance = receiver != null && sourceFacts.containsKey(receiver) ? sourceFacts.get(receiver).provenance()
                : "binding-checked dominating source guard for effectively final local at " + region.start(); //$NON-NLS-1$
            assumptions.add(sourceProven && guardFact ? new SemanticAssumption(assumption.kind(), assumption.subject(),
                    assumption.parameter(), provenance) : assumption);
        }
        return new JavaComputationRegion(region.plan(), region.trace(), region.outputs(), region.inputNames(),
                assumptions, region.reservedNames(), guards, region.start(), region.length(), region.internalBindings(), region.arrayReads());
    }
}
