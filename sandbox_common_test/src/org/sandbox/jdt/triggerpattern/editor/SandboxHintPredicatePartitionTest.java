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
package org.sandbox.jdt.triggerpattern.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.rules.IToken;

class SandboxHintPredicatePartitionTest {

	@Test
	void treatsCompleteMultilinePredicateDeclarationAsMetadataPartition() {
		String source= """
				<!predicate exactTest($method):
				    isPublic($method)
				    && !isStatic($method)>
				foo($method) :: exactTest($method)
				""";
		SandboxHintPartitionScanner scanner= new SandboxHintPartitionScanner();
		scanner.setRange(new Document(source), 0, source.length());

		IToken predicate= scanner.nextToken();

		assertEquals(SandboxHintPartitionScanner.METADATA, predicate.getData());
		assertEquals(0, scanner.getTokenOffset());
		assertEquals(source.indexOf('>') + 1, scanner.getTokenLength());
	}
}
