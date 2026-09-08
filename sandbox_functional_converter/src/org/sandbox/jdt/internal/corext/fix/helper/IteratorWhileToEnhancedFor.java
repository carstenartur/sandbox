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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/** Converts verified iterator while/for protocols to enhanced for through the ULR. */
public class IteratorWhileToEnhancedFor extends AbstractFunctionalCall<ASTNode> {
	private final IteratorPatternDetector detector = new IteratorPatternDetector();

	@Override
	public void find(UseFunctionalCallFixCore fix, CompilationUnit unit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> processed) {
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(WhileStatement loop) {
				if (!(loop.getParent() instanceof Block block)) return true;
				Statement previous = IteratorPatternDetector.findPreviousStatement(block, loop);
				return schedule(loop, previous, detector.detectWhilePattern(loop, previous));
			}

			@Override
			public boolean visit(ForStatement loop) {
				return schedule(loop, null, detector.detectForLoopPattern(loop));
			}

			private boolean schedule(Statement loop, Statement previous, IteratorPattern pattern) {
				if (ExpressionHelper.overlapsProcessedNode(loop, processed)) return false;
				if (IteratorLoopBindings.element(loop, previous, pattern) == null) return true;
				ReferenceHolder<ASTNode, Object> data = ReferenceHolder.create();
				data.put(loop, pattern);
				operations.add(fix.rewrite(loop, data));
				processed.add(loop);
				if (previous != null) processed.add(previous);
				return false;
			}
		});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore fix, ASTNode visited, CompilationUnitRewrite rewrite,
			TextEditGroup group, ReferenceHolder<ASTNode, Object> data) throws CoreException {
		if (!(visited instanceof Statement loop) || !(data.get(visited) instanceof IteratorPattern pattern)) return;
		Statement previous = loop instanceof WhileStatement ? IteratorPatternDetector.findPreviousStatement((Block) loop.getParent(), loop) : null;
		VariableDeclarationFragment element = IteratorLoopBindings.element(loop, previous, pattern);
		if (element == null) return;
		ITypeBinding type = element.resolveBinding().getType();
		List<String> body = ExpressionHelper.bodyStatementsToStrings(pattern.loopBody());
		var model = new LoopModelBuilder()
				.source(SourceDescriptor.SourceType.ITERABLE, pattern.collectionExpression().toString(),
						IteratorLoopBindings.sourceElementType(pattern.collectionExpression().resolveTypeBinding()))
				.element(element.getName().getIdentifier(), type.getQualifiedName(), type.isPrimitive())
				.terminal(new ForEachTerminal(body.subList(1, body.size()), false)).build();
		new ASTEnhancedForRenderer(rewrite.getAST(), rewrite.getASTRewrite()).renderIteratorLoop(model, loop,
				previous, pattern.collectionExpression(), (VariableDeclarationStatement) element.getParent(), rewrite.getImportRewrite(), group);
	}

	@Override
	public String getPreview(boolean after) {
		return after ? """
				for (String item : items) {
					System.out.println(item);
				}
				""" : """
				Iterator<String> it = items.iterator();
				while (it.hasNext()) {
					String item = it.next();
					System.out.println(item);
				}
				""";
	}
}
