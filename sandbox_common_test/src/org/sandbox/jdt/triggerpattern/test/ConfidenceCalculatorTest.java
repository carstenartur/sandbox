/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.mining.analysis.AlignmentKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.AstDiff;
import org.sandbox.jdt.triggerpattern.mining.analysis.ConfidenceCalculator;
import org.sandbox.jdt.triggerpattern.mining.analysis.NodeAlignment;

/**
 * Tests for {@link ConfidenceCalculator}.
 */
public class ConfidenceCalculatorTest {

	private final ConfidenceCalculator calc = new ConfidenceCalculator();

	@Test
	public void testAllIdenticalYieldsHighConfidence() {
		AstDiff diff = new AstDiff(true, List.of(
				new NodeAlignment(null, null, AlignmentKind.IDENTICAL),
				new NodeAlignment(null, null, AlignmentKind.IDENTICAL)));

		double confidence = calc.calculate(diff);
		assertEquals(1.0, confidence, 0.001, "All identical should yield 1.0"); //$NON-NLS-1$
	}

	@Test
	public void testEmptyDiffYieldsZero() {
		AstDiff diff = new AstDiff(true, List.of());
		double confidence = calc.calculate(diff);
		assertEquals(0.0, confidence, 0.001, "Empty diff should yield 0.0"); //$NON-NLS-1$
	}

	@Test
	public void testIncompatibleWithInsertionsYieldsLow() {
		AstDiff diff = new AstDiff(false, List.of(
				new NodeAlignment(null, null, AlignmentKind.INSERTED),
				new NodeAlignment(null, null, AlignmentKind.INSERTED)));

		double confidence = calc.calculate(diff);
		assertTrue(confidence < 0.5, "Incompatible diff with only insertions should yield low confidence"); //$NON-NLS-1$
	}

	@Test
	public void testMixedAlignments() {
		AstDiff diff = new AstDiff(true, List.of(
				new NodeAlignment(null, null, AlignmentKind.IDENTICAL),
				new NodeAlignment(null, null, AlignmentKind.IDENTICAL),
				new NodeAlignment(null, null, AlignmentKind.MODIFIED)));

		double confidence = calc.calculate(diff);
		assertTrue(confidence > 0.5, "Mostly identical should yield medium-high confidence"); //$NON-NLS-1$
		assertTrue(confidence < 1.0, "Not all identical should yield less than 1.0"); //$NON-NLS-1$
	}
}
