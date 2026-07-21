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

import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Adds a conservative safety boundary around array collect conversions.
 *
 * <p>The direct existing-target fallback copies the original loop body for
 * collections. Arrays use the renderer's stream path, where an empty fallback
 * body would otherwise create a no-op lambda. Until array existing-target
 * rendering is implemented explicitly, such loops are deliberately left
 * unchanged. Array collect conversions with a fresh preceding accumulator remain
 * supported.</p>
 */
public class ArraySafeEnhancedForHandler extends EnhancedForHandler {

	@Override
	public void rewrite(UseFunctionalCallFixCore fixCore, EnhancedForStatement visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> data) throws CoreException {
		Object stored= data.get(visited);
		if (stored instanceof JdtLoopExtractor.ExtractedLoop extracted
				&& extracted.model != null
				&& extracted.model.getSource() != null
				&& extracted.model.getSource().type() == SourceDescriptor.SourceType.ARRAY
				&& extracted.model.getTerminal() instanceof CollectTerminal collectTerminal
				&& !hasFreshAccumulator(visited, collectTerminal.targetVariable())) {
			return;
		}
		super.rewrite(fixCore, visited, cuRewrite, group, data);
	}

	private boolean hasFreshAccumulator(EnhancedForStatement loop, String targetVariable) {
		if (targetVariable == null || !(loop.getParent() instanceof Block block)) {
			return false;
		}
		@SuppressWarnings("unchecked") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		int loopIndex= statements.indexOf(loop);
		if (loopIndex <= 0) {
			return false;
		}
		Statement previous= statements.get(loopIndex - 1);
		if (!(previous instanceof VariableDeclarationStatement declaration)) {
			return false;
		}
		return targetVariable.equals(CollectPatternDetector.isEmptyCollectionDeclaration(declaration));
	}
}
