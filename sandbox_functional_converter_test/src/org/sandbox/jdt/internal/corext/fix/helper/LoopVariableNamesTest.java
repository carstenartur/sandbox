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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

class LoopVariableNamesTest {

	@Test
	void reservesNamesInsideNestedTypesAndLambdas() {
		CompilationUnit root= parse("""
				class Sample {
					int element;
					class Nested { int element1; }
					void run() { java.util.function.Consumer<String> action = element2 -> use(element2); }
				}
				""");
		Set<String> names= LoopVariableNames.usedNames(root);
		assertTrue(names.containsAll(Set.of("element", "element1", "element2", "Nested", "action"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		assertEquals("element3", LoopVariableNames.fresh("element", names)); //$NON-NLS-1$ //$NON-NLS-2$
		assertSame(names, LoopVariableNames.usedNames((org.eclipse.jdt.core.dom.ASTNode) root.types().get(0)));
	}

	@Test
	void previewScopeRestoresSharedReservationsAndProducesStableNames() {
		CompilationUnit root= parse("class Sample { int element; }"); //$NON-NLS-1$
		Set<String> original= LoopVariableNames.usedNames(root);
		original.add("temporary"); //$NON-NLS-1$
		for (int attempt= 0; attempt < 2; attempt++) {
			LoopVariableNames.Scope scope= new LoopVariableNames.Scope(root, Set.of("element1")); //$NON-NLS-1$
			try (scope) {
				Set<String> preview= LoopVariableNames.usedNames(root);
				assertEquals("element2", LoopVariableNames.fresh("element", preview)); //$NON-NLS-1$ //$NON-NLS-2$
			}
			assertSame(original, LoopVariableNames.usedNames(root));
			assertTrue(original.contains("temporary")); //$NON-NLS-1$
			assertFalse(original.contains("element2")); //$NON-NLS-1$
		}
	}

	@Test
	void reservationsDoNotLeakBetweenCompilationUnits() {
		CompilationUnit first= parse("class First { int element; }"); //$NON-NLS-1$
		CompilationUnit second= parse("class Second {}"); //$NON-NLS-1$
		assertEquals("element1", LoopVariableNames.fresh("element", LoopVariableNames.usedNames(first))); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("element", LoopVariableNames.fresh("element", LoopVariableNames.usedNames(second))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
