/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.cleanup.actions.StructuredRewriteAnnotationGuard.AnnotationMatch;

class StructuredRewriteActionAnnotationTest {

	@Test
	void qualifiedRemovalUsesResolvedAnnotationIdentity() {
		assertEquals(AnnotationMatch.EXACT,
				StructuredRewriteAnnotationGuard.classifyAnnotationName(
						"java.lang.Override", "Override", "java.lang.Override")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(AnnotationMatch.COLLISION,
				StructuredRewriteAnnotationGuard.classifyAnnotationName(
						"java.lang.Override", "Override", "com.example.Override")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void unresolvedUnqualifiedAnnotationFailsClosed() {
		assertEquals(AnnotationMatch.COLLISION,
				StructuredRewriteAnnotationGuard.classifyAnnotationName(
						"org.junit.jupiter.api.Test", "Test", null)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void exactQualifiedSourceNameWorksWithoutBinding() {
		assertEquals(AnnotationMatch.EXACT,
				StructuredRewriteAnnotationGuard.classifyAnnotationName(
						"java.lang.Override", "java.lang.Override", null)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void differentQualifiedAnnotationWithSameSimpleNameIsRejected() {
		assertEquals(AnnotationMatch.COLLISION,
				StructuredRewriteAnnotationGuard.classifyAnnotationName(
						"org.junit.jupiter.api.Test", "com.example.Test", null)); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
