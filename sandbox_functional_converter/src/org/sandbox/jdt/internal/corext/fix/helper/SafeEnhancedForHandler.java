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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
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
					|| capturesNonEffectivelyFinalLocal(visited, extracted.model)) {
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

	private boolean capturesNonEffectivelyFinalLocal(EnhancedForStatement loop, LoopModel model) {
		Set<String> localBindingKeys= new HashSet<>();
		boolean[] incompleteBindings= { !addBindingKey(localBindingKeys, loop.getParameter().resolveBinding()) };
		loop.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment node) {
				incompleteBindings[0]|= !addBindingKey(localBindingKeys, node.resolveBinding());
				return true;
			}

			@Override
			public boolean visit(SingleVariableDeclaration node) {
				incompleteBindings[0]|= !addBindingKey(localBindingKeys, node.resolveBinding());
				return true;
			}
		});
		if (incompleteBindings[0]) {
			return true;
		}

		Set<String> liftedAccumulatorKeys= liftedAccumulatorKeys(loop, model);
		boolean[] unsafe= { false };
		loop.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				IBinding binding= node.resolveBinding();
				if (!(binding instanceof IVariableBinding variableBinding)) {
					return true;
				}
				IVariableBinding declaration= variableBinding.getVariableDeclaration();
				String key= declaration.getKey();
				if (key == null) {
					unsafe[0]= true;
					return false;
				}
				if (!declaration.isField() && !localBindingKeys.contains(key)
						&& !liftedAccumulatorKeys.contains(key) && !declaration.isEffectivelyFinal()) {
					unsafe[0]= true;
					return false;
				}
				return true;
			}
		});
		return unsafe[0];
	}

	private Set<String> liftedAccumulatorKeys(EnhancedForStatement loop, LoopModel model) {
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

		Set<String> result= new HashSet<>();
		String expectedName= targetVariable;
		loop.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (expectedName.equals(node.getIdentifier())
						&& node.resolveBinding() instanceof IVariableBinding variableBinding) {
					String key= variableBinding.getVariableDeclaration().getKey();
					if (key != null) {
						result.add(key);
					}
				}
				return true;
			}
		});
		return result;
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

	private static boolean addBindingKey(Set<String> keys, IVariableBinding binding) {
		if (binding == null) {
			return false;
		}
		String key= binding.getVariableDeclaration().getKey();
		if (key == null) {
			return false;
		}
		keys.add(key);
		return true;
	}
}
