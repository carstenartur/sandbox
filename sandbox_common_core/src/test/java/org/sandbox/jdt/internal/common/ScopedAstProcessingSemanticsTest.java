/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.sandbox.jdt.internal.common.ScopedAstProcessorBuilder.TraversalDecision.SKIP_CHILDREN;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.junit.jupiter.api.Test;

class ScopedAstProcessingSemanticsTest {

	@Test
	void childTraversalDecisionDoesNotControlPipelineAdvancement() {
		CompilationUnit unit= parseAssignments();
		AtomicInteger laterStageCalls= new AtomicInteger();

		AstProcessing.scoped(ReferenceHolder.<String, Object>create())
				.findWithTraversal(Assignment.class,
						assignment -> hasLeftHandName(assignment, "a"), //$NON-NLS-1$
						(assignment, holder) -> SKIP_CHILDREN,
						ScopedAstProcessingSemanticsTest::followingStatement)
				.then(Assignment.class,
						assignment -> hasLeftHandName(assignment, "b"), //$NON-NLS-1$
						(assignment, holder) -> laterStageCalls.incrementAndGet())
				.build(unit);

		assertEquals(1, laterStageCalls.get());
	}

	@Test
	void laterMatcherCanReadFactsFromEarlierStage() {
		CompilationUnit unit= parseAssignments();
		ReferenceHolder<String, Object> state= ReferenceHolder.create();
		AtomicInteger matches= new AtomicInteger();

		AstProcessing.scoped(state)
				.find(Assignment.class,
						assignment -> hasLeftHandName(assignment, "a"), //$NON-NLS-1$
						(assignment, holder) -> holder.put("nextName", "b"), //$NON-NLS-1$ //$NON-NLS-2$
						ScopedAstProcessingSemanticsTest::followingStatement)
				.then(Assignment.class,
						(assignment, holder) -> hasLeftHandName(
								assignment, (String) holder.get("nextName")), //$NON-NLS-1$
						(assignment, holder) -> matches.incrementAndGet())
				.build(unit);

		assertEquals(1, matches.get());
	}

	private static CompilationUnit parseAssignments() {
		return parse("""
			class Sample {
				void update(int first, int second) {
					int a;
					int b;
					a = first;
					b = second;
				}
			}
			""");
	}

	private static boolean hasLeftHandName(Assignment assignment, String expectedName) {
		return assignment.getLeftHandSide() instanceof SimpleName name
				&& expectedName.equals(name.getIdentifier());
	}

	private static ASTNode followingStatement(ASTNode node) {
		Statement statement= containingStatement(node);
		if (statement == null || !(statement.getParent() instanceof Block block)) {
			return null;
		}
		List<?> statements= block.statements();
		int index= statements.indexOf(statement);
		return index >= 0 && index + 1 < statements.size()
				? (ASTNode) statements.get(index + 1)
				: null;
	}

	private static Statement containingStatement(ASTNode node) {
		ASTNode current= node;
		while (current != null && !(current instanceof Statement)) {
			current= current.getParent();
		}
		return (Statement) current;
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		return (CompilationUnit) parser.createAST(null);
	}
}
