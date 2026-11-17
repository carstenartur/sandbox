/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;

/** A resolved source guard and the nondelegating constructors that populate its cache. */
public record ConstructorGuardEvidence(String owner, String receiver, String constant, String cache,
        int guardOffset, List<String> constructors) {
    public ConstructorGuardEvidence {
        constructors = List.copyOf(constructors);
    }

    public String description() {
        return "Cache the exact BigInteger guard after final-field assignment. Keep the original guard when the cache is unavailable."; //$NON-NLS-1$
    }
}
