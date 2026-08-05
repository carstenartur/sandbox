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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Regression tests for traversal below non-matching scoped-pipeline candidates.
 */
class ScopedPipelineNestedTraversalTest {

	@Test
	void nonMatchingInvocationDoesNotHideNestedMatch() {
		CompilationUnit unit= parse("""
				class Sample {
					void run() {
						consume(target());
					}

					void consume(Object value) {
					}

					Object target() {
						return null;
					}
				}
				""");
		AtomicInteger targetMatches= new AtomicInteger();

		AstProcessorBuilder.with(new ReferenceHolder<String, Object>())
				.scoped()
				.findMethodInvocation((invocation, holder) -> {
					boolean target= "target".equals(invocation.getName().getIdentifier()); //$NON-NLS-1$
					if (target) {
						targetMatches.incrementAndGet();
					}
					return target;
				})
				.build(unit);

		assertEquals(1, targetMatches.get(),
				"The non-matching outer invocation must not prune its nested target invocation"); //$NON-NLS-1$
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setEnvironment(new String[0], new String[0], null, true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
