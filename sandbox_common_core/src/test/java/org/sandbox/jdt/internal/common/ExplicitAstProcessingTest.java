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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

class ExplicitAstProcessingTest {

	@Test
	void scopedPipelineNavigatesFromInvocationToFollowingArrayAccess() {
		CompilationUnit unit= parse("""
			import java.util.Arrays;
			class Sample {
				void append(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""");
		AtomicInteger matches= new AtomicInteger();

		AstProcessing.scoped(ReferenceHolder.<String, Object>create())
				.find(MethodInvocation.class,
						invocation -> "copyOf".equals(invocation.getName().getIdentifier()), //$NON-NLS-1$
						(invocation, holder) -> holder.put("growth", invocation), //$NON-NLS-1$
						ExplicitAstProcessingTest::followingStatement)
				.then(ArrayAccess.class,
						access -> true,
						(access, holder) -> matches.incrementAndGet())
				.build(unit);

		assertEquals(1, matches.get());
	}

	@Test
	void scopedPipelineAllowsRepeatedNodeTypes() {
		CompilationUnit unit= parse("""
			class Sample {
				void update(int first, int second) {
					int a;
					int b;
					a = first;
					b = second;
				}
			}
			""");
		AtomicInteger matches= new AtomicInteger();

		AstProcessing.scoped(ReferenceHolder.<String, Object>create())
				.find(Assignment.class,
						assignment -> hasLeftHandName(assignment, "a"), //$NON-NLS-1$
						(assignment, holder) -> { },
						ExplicitAstProcessingTest::followingStatement)
				.then(Assignment.class,
						assignment -> hasLeftHandName(assignment, "b"), //$NON-NLS-1$
						(assignment, holder) -> matches.incrementAndGet())
				.build(unit);

		assertEquals(1, matches.get());
	}

	@Test
	void failedScopedMatcherDoesNotAdvance() {
		CompilationUnit unit= parse("""
			class Sample {
				void run() {
					int a;
					int b;
					a = 1;
					b = 2;
				}
			}
			""");
		AtomicInteger laterStageCalls= new AtomicInteger();

		AstProcessing.scoped(ReferenceHolder.<String, Object>create())
				.find(Assignment.class,
						assignment -> false,
						(assignment, holder) -> { },
						ExplicitAstProcessingTest::followingStatement)
				.then(Assignment.class,
						assignment -> true,
						(assignment, holder) -> laterStageCalls.incrementAndGet())
				.build(unit);

		assertEquals(0, laterStageCalls.get());
	}

	@Test
	void nullNavigationEndsOnlyThatScopedBranch() {
		CompilationUnit unit= parse("""
			class Sample {
				void run() {
					int a;
					a = 1;
				}
			}
			""");
		AtomicInteger laterStageCalls= new AtomicInteger();

		AstProcessing.scoped(ReferenceHolder.<String, Object>create())
				.find(Assignment.class,
						assignment -> true,
						(assignment, holder) -> { },
						assignment -> null)
				.then(Assignment.class,
						assignment -> true,
						(assignment, holder) -> laterStageCalls.incrementAndGet())
				.build(unit);

		assertEquals(0, laterStageCalls.get());
	}

	@Test
	void everyFirstStageMatchGetsItsOwnScopedContinuation() {
		CompilationUnit unit= parse("""
			import java.util.Arrays;
			class Sample {
				void appendFirst(String value) {
					String[] first = new String[0];
					first = Arrays.copyOf(first, first.length + 1);
					first[first.length - 1] = value;
				}
				void appendSecond(String value) {
					String[] second = new String[0];
					second = Arrays.copyOf(second, second.length + 1);
					second[second.length - 1] = value;
				}
			}
			""");
		AtomicInteger continuations= new AtomicInteger();

		AstProcessing.scoped(ReferenceHolder.<String, Object>create())
				.find(MethodInvocation.class,
						invocation -> "copyOf".equals(invocation.getName().getIdentifier()), //$NON-NLS-1$
						(invocation, holder) -> { },
						ExplicitAstProcessingTest::followingStatement)
				.then(ArrayAccess.class,
						access -> true,
						(access, holder) -> continuations.incrementAndGet())
				.build(unit);

		assertEquals(2, continuations.get());
	}

	@Test
	void independentVisitorsEachInspectTheCompleteRoot() {
		CompilationUnit unit= parse("""
			class Sample {
				int first;
				int second;
			}
			""");
		AtomicInteger types= new AtomicInteger();
		AtomicInteger fields= new AtomicInteger();

		AstProcessing.independent(ReferenceHolder.<String, Object>create())
				.on(TypeDeclaration.class, (type, holder) -> {
					types.incrementAndGet();
					return true;
				})
				.on(FieldDeclaration.class, (field, holder) -> {
					fields.incrementAndGet();
					return true;
				})
				.build(unit);

		assertEquals(1, types.get());
		assertEquals(2, fields.get());
	}

	@Test
	void independentVisitorsAllowRepeatedNodeTypes() {
		CompilationUnit unit= parse("""
			class Sample {
				void run() {
					System.out.println("one");
					System.out.println("two");
				}
			}
			""");
		AtomicInteger firstVisitor= new AtomicInteger();
		AtomicInteger secondVisitor= new AtomicInteger();

		AstProcessing.independent(ReferenceHolder.<String, Object>create())
				.on(MethodInvocation.class, (invocation, holder) -> {
					firstVisitor.incrementAndGet();
					return true;
				})
				.on(MethodInvocation.class, (invocation, holder) -> {
					secondVisitor.incrementAndGet();
					return true;
				})
				.build(unit);

		assertEquals(2, firstVisitor.get());
		assertEquals(2, secondVisitor.get());
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
