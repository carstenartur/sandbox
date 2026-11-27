/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import de.regelsuche.math.algorithms.modular.QuadraticRootSearch;

/** The root certificate and its Java source bindings. A normalized call supplies a separate observation contract. */
public record FieldRootEvidence(QuadraticRootSearch.Certificate certificate, String methodKey, String sinkKey,
        String prefix, String indent, boolean swappedInputs, String methodHash, List<String> bindings, List<String> localDefinitions,
        List<JavaSourceContext.Receipt> dependencies, long searchWork, int candidates, FieldCallEvidence callSite) {
    /** Local suffix evidence predating call-specific specialization. */
    public FieldRootEvidence(QuadraticRootSearch.Certificate certificate, String methodKey, String sinkKey, String prefix,
            String indent, boolean swappedInputs, String methodHash, List<String> bindings, List<String> localDefinitions,
            List<JavaSourceContext.Receipt> dependencies, long searchWork, int candidates) {
        this(certificate, methodKey, sinkKey, prefix, indent, swappedInputs, methodHash, bindings, localDefinitions,
                dependencies, searchWork, candidates, null);
    }

    public FieldRootEvidence {
        bindings = List.copyOf(bindings);
        localDefinitions = List.copyOf(localDefinitions);
        dependencies = List.copyOf(dependencies);
    }
}
