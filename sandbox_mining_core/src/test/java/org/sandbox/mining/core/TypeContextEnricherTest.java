/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.enrichment.TypeContextEnricher;

/**
 * Tests for {@link TypeContextEnricher}.
 */
class TypeContextEnricherTest {

	@Test
	void testEnrichFromDiffWithEclipseTypes() {
		TypeContextEnricher enricher = new TypeContextEnricher();
		String diff = "- IResource resource = getResource();\n+ IFile file = getFile();";
		String result = enricher.enrichFromDiff(diff);
		assertTrue(result.contains("IResource"), "Should detect IResource");
		assertTrue(result.contains("Type Context"), "Should have Type Context header");
	}

	@Test
	void testEnrichFromDiffWithNoEclipseTypes() {
		TypeContextEnricher enricher = new TypeContextEnricher();
		String diff = "- int x = 1;\n+ int y = 2;";
		String result = enricher.enrichFromDiff(diff);
		assertEquals("", result, "No Eclipse types found should return empty");
	}

	@Test
	void testEnrichFromNullDiff() {
		TypeContextEnricher enricher = new TypeContextEnricher();
		assertEquals("", enricher.enrichFromDiff(null));
	}

	@Test
	void testEnrichFromBlankDiff() {
		TypeContextEnricher enricher = new TypeContextEnricher();
		assertEquals("", enricher.enrichFromDiff("   "));
	}

	@Test
	void testEnrichDetectsMultipleTypes() {
		TypeContextEnricher enricher = new TypeContextEnricher();
		String diff = "Display display = Shell.getDisplay();\nComposite parent = new Composite(Shell, SWT.NONE);";
		String result = enricher.enrichFromDiff(diff);
		assertTrue(result.contains("Display"), "Should detect Display");
		assertTrue(result.contains("Composite"), "Should detect Composite");
		assertTrue(result.contains("Shell"), "Should detect Shell");
	}

	@Test
	void testKnownTypeCount() {
		TypeContextEnricher enricher = new TypeContextEnricher();
		assertTrue(enricher.getKnownTypeCount() > 0, "Should have known types");
	}
}
