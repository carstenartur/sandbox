/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.math.algorithms.modular.MontgomerySchedule.Certificate;

/** Identifies the Java declarations whose bound source and emitted schedules were checked. */
public record MontgomeryDotEvidence(String methodKey, String reducerKey, Certificate certificate) { }
