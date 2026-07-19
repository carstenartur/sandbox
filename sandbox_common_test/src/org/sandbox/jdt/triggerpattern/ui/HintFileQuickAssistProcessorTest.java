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
package org.sandbox.jdt.triggerpattern.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/** Exercises DSL proposal selection and application without an editor fixture. */
class HintFileQuickAssistProcessorTest {

	private final HintFileRegistry registry = HintFileRegistry.getInstance();

	@BeforeEach
	void setUp() throws Exception {
		registry.clear();
		registry.loadFromString("quick-assist-test", """
				<!id: quick-assist-test>

				"Remove addition of zero":
				$x + 0
				=> $x
				;;
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@AfterEach
	void tearDown() {
		registry.clear();
	}

	@Test
	void createsAndAppliesProposalAtMatchingCursorLocation() {
		String source = "class Test { int value() { return 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit unit = parse(source);
		int cursor = source.indexOf("1 + 0") + 2; //$NON-NLS-1$

		IJavaCompletionProposal[] proposals = new HintFileQuickAssistProcessor()
				.collectAssists(unit, cursor);

		assertNotNull(proposals);
		assertEquals(1, proposals.length);
		assertEquals("Remove addition of zero", proposals[0].getDisplayString()); //$NON-NLS-1$

		Document document = new Document(source);
		proposals[0].apply(document);
		assertEquals("class Test { int value() { return 1; } }", document.get()); //$NON-NLS-1$
	}

	@Test
	void doesNotOfferProposalWhenCursorIsOutsideMatch() {
		String source = "class Test { int value() { return 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit unit = parse(source);

		IJavaCompletionProposal[] proposals = new HintFileQuickAssistProcessor()
				.collectAssists(unit, source.indexOf("class")); //$NON-NLS-1$

		assertNull(proposals);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
