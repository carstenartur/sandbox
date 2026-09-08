/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Registers stream-to-loop rewrites using the common ULR and imperative renderers.
 * Parsing and semantic metadata belong to JdtStreamExtractor; this class owns
 * only integration with the cleanup lifecycle.
 */
public final class StreamForEachConverter {
	private StreamForEachConverter() {
	}

	public static void find(UseFunctionalCallFixCore fix, CompilationUnit unit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> processed) {
		LoopConversionService.scanRoot(unit).accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (ExpressionHelper.overlapsProcessedNode(node, processed)) {
					return false;
				}
				JdtStreamExtractor.ExtractedStream extracted = JdtStreamExtractor.extract(node);
				if (extracted == null) {
					return true;
				}
				ReferenceHolder<ASTNode, Object> data = new ReferenceHolder<>();
				data.put(node, extracted);
				operations.add(fix.rewrite(node, data));
				processed.add(node);
				// One owner per replaced statement, including nested calls in its lambdas.
				return false;
			}
		});
	}

	public static void rewrite(ASTNode node, CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> data, boolean iteratorTarget) {
		if (data.get(node) instanceof JdtStreamExtractor.ExtractedStream extracted) {
			if (iteratorTarget) {
				new ASTIteratorWhileRenderer(cuRewrite.getAST(), cuRewrite.getASTRewrite())
						.renderPipeline(extracted.model(), extracted.context(), cuRewrite.getImportRewrite(), group);
			} else {
				new ASTEnhancedForRenderer(cuRewrite.getAST(), cuRewrite.getASTRewrite())
						.renderPipeline(extracted.model(), extracted.context(), cuRewrite.getImportRewrite(), group);
			}
		}
	}
}
