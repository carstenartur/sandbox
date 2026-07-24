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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.CollectTerminal.CollectorType;

class ConcreteCollectionASTStreamRendererTest {

	private AST ast;
	private ConcreteCollectionASTStreamRenderer renderer;

	@BeforeEach
	void setUp() {
		ast= AST.newAST(AST.getJLSLatest(), false);
		renderer= new ConcreteCollectionASTStreamRenderer(ast, ASTRewrite.create(ast), null, null);
	}

	@Test
	void rendersConcreteListFactory() {
		Expression result= renderer.renderCollect(ast.newSimpleName("stream"), //$NON-NLS-1$
				new CollectTerminal(CollectorType.TO_LIST, "result", "java.util.ArrayList::new"), "item"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertEquals("stream.collect(Collectors.toCollection(java.util.ArrayList::new))", result.toString()); //$NON-NLS-1$
	}

	@Test
	void rendersConcreteSetFactory() {
		Expression result= renderer.renderCollect(ast.newSimpleName("stream"), //$NON-NLS-1$
				new CollectTerminal(CollectorType.TO_SET, "result", "java.util.LinkedHashSet::new"), "item"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertEquals("stream.collect(Collectors.toCollection(java.util.LinkedHashSet::new))", result.toString()); //$NON-NLS-1$
	}

	@Test
	void delegatesInterfaceCollector() {
		Expression result= renderer.renderCollect(ast.newSimpleName("stream"), //$NON-NLS-1$
				new CollectTerminal(CollectorType.TO_LIST, "result"), "item"); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals("stream.collect(Collectors.toList())", result.toString()); //$NON-NLS-1$
	}

	@Test
	void rejectsUnmodeledFactorySyntax() {
		CollectTerminal terminal= new CollectTerminal(CollectorType.TO_LIST, "result", //$NON-NLS-1$
				"java.util.ArrayList::create"); //$NON-NLS-1$

		assertThrows(IllegalArgumentException.class,
				() -> renderer.renderCollect(ast.newSimpleName("stream"), terminal, "item")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
