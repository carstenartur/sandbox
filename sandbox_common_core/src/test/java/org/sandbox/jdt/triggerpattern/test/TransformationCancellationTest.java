/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.CancellationException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlanContext;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

public class TransformationCancellationTest {
	@Test
	public void aLargeSingleFileCanBeCanceledDuringMatching() throws Exception {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(("class Sample { void f() {" + "int value=1;".repeat(5000) + "} }").toCharArray());
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		var hint= new HintFileParser().parse("<!id: cancellation>\n1\n=> 2\n;;\n");
		int[] observations= { 0 };
		try (var scope= SemanticRewritePlanContext.install(SemanticRewritePlan.empty(), Map.of(), () -> {
			if (++observations[0] > 25) {
				throw new CancellationException();
			}
		})) {
			assertThrows(CancellationException.class, () -> new BatchTransformationProcessor(hint).process(root));
		}
		// The canceled execution does not poison the next scope on this thread.
		SemanticRewritePlanContext.checkCanceled();
	}
}
