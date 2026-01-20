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
package org.sandbox.jdt.ui.tests.quickfix;

import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

/**
 * Utility methods for XML testing using XMLUnit.
 * Provides semantic XML comparison that ignores whitespace differences.
 */
public class XMLTestUtils {
	
	/**
	 * Check if two XML documents are semantically equivalent (ignoring whitespace).
	 * 
	 * @param expected the expected XML content
	 * @param actual the actual XML content
	 * @return true if semantically equivalent
	 */
	public static boolean isXmlSemanticallyEqual(String expected, String actual) {
		Diff diff = DiffBuilder.compare(expected)
				.withTest(actual)
				.ignoreWhitespace()
				.ignoreComments()
				.normalizeWhitespace()
				.build();
		return !diff.hasDifferences();
	}
	
	/**
	 * Check if two XML documents are semantically equivalent, preserving comments.
	 * 
	 * @param expected the expected XML content
	 * @param actual the actual XML content
	 * @return true if semantically equivalent (including comments)
	 */
	public static boolean isXmlSemanticallyEqualWithComments(String expected, String actual) {
		Diff diff = DiffBuilder.compare(expected)
				.withTest(actual)
				.ignoreWhitespace()
				.normalizeWhitespace()
				.build();
		return !diff.hasDifferences();
	}
	
	/**
	 * Get detailed differences between two XML documents.
	 * Ignores whitespace and comments for semantic comparison.
	 * 
	 * @param expected the expected XML content
	 * @param actual the actual XML content
	 * @return Diff object containing all differences
	 */
	public static Diff getXmlDifferences(String expected, String actual) {
		return DiffBuilder.compare(expected)
				.withTest(actual)
				.ignoreWhitespace()
				.ignoreComments()
				.normalizeWhitespace()
				.build();
	}
	
	/**
	 * Assert that two XML documents are semantically equivalent.
	 * Throws AssertionError with detailed diff if not equivalent.
	 * 
	 * @param expected the expected XML content
	 * @param actual the actual XML content
	 */
	public static void assertXmlSemanticallyEqual(String expected, String actual) {
		Diff diff = getXmlDifferences(expected, actual);
		if (diff.hasDifferences()) {
			throw new AssertionError("XML documents are not semantically equal:\n" + diff.toString());
		}
	}
}
