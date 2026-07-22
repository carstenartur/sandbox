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
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.CollectTerminal;
import org.sandbox.functional.core.terminal.ReduceTerminal;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Adds conservative semantic boundaries around enhanced-for conversions.
 *
 * <p>Array collect into an existing target remains unchanged until the renderer
 * can copy the original body into the array stream lambda. The handler also
 * rejects any conversion whose generated lambda would capture a local variable
 * that is not effectively final. A collect/reduce accumulator is exempt only
 * when the transformation removes that variable from the lambda body.</p>
 */
public class SafeEnhancedForHandler extends EnhancedForHandler {

	@Override
	public void rewrite(UseFunctionalCallFixCore fixCore, EnhancedForStatement visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group,
			ReferenceHolder<ASTNode, Object> data) throws CoreException {
		Object stored= data.get(visited);
		if (stored instanceof JdtLoopExtractor.ExtractedLoop extracted && extracted.model != null) {
			if (isUnsupportedArrayExistingTarget(visited, extracted.model)
					|| LambdaCaptureSafety.hasUnsafeCapture(visited.getBody(),
							liftedAccumulatorNames(visited, extracted.model),
							visited.getParameter().resolveBinding())) {
				return;
			}
		}
		super.rewrite(fixCore, visited, cuRewrite, group, data);
	}

	private boolean isUnsupportedArrayExistingTarget(EnhancedForStatement loop, LoopModel model) {
		return model.getSource() != null
				&& model.getSource().type() == SourceDescriptor.SourceType.ARRAY
				&& model.getTerminal() instanceof CollectTerminal collectTerminal
				&& !hasFreshAccumulator(loop, collectTerminal.targetVariable());
	}

	private Set<String> liftedAccumulatorNames(EnhancedForStatement loop, LoopModel model) {
		String targetVariable= null;
		if (model.getTerminal() instanceof ReduceTerminal reduceTerminal) {
			targetVariable= reduceTerminal.targetVariable();
		} else if (model.getTerminal() instanceof CollectTerminal collectTerminal
				&& hasFreshAccumulator(loop, collectTerminal.targetVariable())) {
			targetVariable= collectTerminal.targetVariable();
		}
		if (targetVariable == null || targetVariable.indexOf('.') >= 0) {
			return Set.of();
		}
		return Set.of(targetVariable);
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
