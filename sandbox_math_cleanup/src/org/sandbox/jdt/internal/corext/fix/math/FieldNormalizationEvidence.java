/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.math.algorithms.modular.QuadraticRootNormalization.Contract;

/** Source commitments for the caller's exact observation of the ordered, normalized coordinates. */
public record FieldNormalizationEvidence(Contract contract, int ordinal, String factoryKey, String factoryHash,
        String sinkKey, String sinkHash, String pairKey, String realKey, String imaginaryKey, String targetKey, String modulusKey) {}
