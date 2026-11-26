/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import de.regelsuche.math.algorithms.modular.QuadraticRootSearch;

/** The independently verified exact-pair certificate and the Java source/primitive bindings it refines. */
public record FieldRootEvidence(QuadraticRootSearch.Certificate certificate, String methodKey, String sinkKey,
        String prefix, String indent, boolean swappedInputs, String methodHash, List<String> bindings, List<String> localDefinitions,
        List<JavaSourceContext.Receipt> dependencies, long searchWork, int candidates) {
    public FieldRootEvidence {
        bindings = List.copyOf(bindings);
        localDefinitions = List.copyOf(localDefinitions);
        dependencies = List.copyOf(dependencies);
    }
}
