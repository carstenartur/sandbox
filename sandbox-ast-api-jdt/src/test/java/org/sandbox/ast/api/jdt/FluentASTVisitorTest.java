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
package org.sandbox.ast.api.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;

/**
 * Tests for {@link FluentASTVisitor}.
 *
 * <p>Verifies that the visitor properly pre-converts JDT nodes to fluent wrappers
 * and delegates to the protected methods.</p>
 */
@DisplayName("FluentASTVisitor")
class FluentASTVisitorTest {

	private AST ast;

	@BeforeEach
	void setUp() {
		ast = AST.newAST(AST.JLS21, false);
	}

	@Test
	@DisplayName("visitMethodInvocation receives pre-converted wrapper")
	void visitMethodInvocationReceivesWrapper() {
		MethodInvocation mi = ast.newMethodInvocation();
		mi.setName(ast.newSimpleName("testMethod"));

		List<String> visitedMethods = new ArrayList<>();
		FluentASTVisitor visitor = new FluentASTVisitor() {
			@Override
			protected boolean visitMethodInvocation(MethodInvocationExpr expr, MethodInvocation node) {
				visitedMethods.add(expr.methodName().orElse(""));
				return true;
			}
		};

		mi.accept(visitor);

		assertThat(visitedMethods).containsExactly("testMethod");
	}

	@Test
	@DisplayName("visitSimpleName receives pre-converted wrapper")
	void visitSimpleNameReceivesWrapper() {
		SimpleName sn = ast.newSimpleName("myVariable");

		List<String> visitedNames = new ArrayList<>();
		FluentASTVisitor visitor = new FluentASTVisitor() {
			@Override
			protected boolean visitSimpleName(SimpleNameExpr expr, SimpleName node) {
				visitedNames.add(expr.identifier());
				return true;
			}
		};

		sn.accept(visitor);

		assertThat(visitedNames).containsExactly("myVariable");
	}

	@Test
	@DisplayName("visit methods are final and cannot be overridden")
	void visitMethodsAreFinal() throws NoSuchMethodException {
		// Verify that visit(MethodInvocation) is final
		var visitMethodInvocation = FluentASTVisitor.class.getMethod("visit", MethodInvocation.class);
		assertThat(java.lang.reflect.Modifier.isFinal(visitMethodInvocation.getModifiers())).isTrue();

		// Verify that visit(SimpleName) is final
		var visitSimpleName = FluentASTVisitor.class.getMethod("visit", SimpleName.class);
		assertThat(java.lang.reflect.Modifier.isFinal(visitSimpleName.getModifiers())).isTrue();
	}

	@Test
	@DisplayName("raw JDT node is accessible in visitor callback")
	void rawNodeAccessible() {
		SimpleName sn = ast.newSimpleName("test");

		List<SimpleName> rawNodes = new ArrayList<>();
		FluentASTVisitor visitor = new FluentASTVisitor() {
			@Override
			protected boolean visitSimpleName(SimpleNameExpr expr, SimpleName node) {
				rawNodes.add(node);
				return true;
			}
		};

		sn.accept(visitor);

		assertThat(rawNodes).containsExactly(sn);
	}
}
