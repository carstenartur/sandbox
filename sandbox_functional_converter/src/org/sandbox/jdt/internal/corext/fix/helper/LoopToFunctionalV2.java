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

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * V2 implementation based on Unified Loop Representation (ULR).
 * 
 * <p>This implementation uses the ULR model from sandbox-functional-converter-core
 * to provide a more modular and testable approach to loop-to-functional transformations.</p>
 * 
 * <p><b>Current Status:</b> Phase 1 - Feature Parity with V1</p>
 * <p>This implementation currently delegates all operations to the V1 implementation
 * ({@link LoopToFunctional}) to ensure feature parity. Future phases will gradually
 * replace V1 logic with ULR-based transformations.</p>
 * 
 * <p><b>Architecture:</b></p>
 * <ul>
 * <li>Phase 1: Delegate to V1 for feature parity (current)</li>
 * <li>Phase 2: Implement AST-to-ULR extraction</li>
 * <li>Phase 3: Implement ULR-to-Stream transformation</li>
 * <li>Phase 4: Replace V1 delegation with ULR pipeline</li>
 * </ul>
 * 
 * @see LoopToFunctional
 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @since 1.0.0
 */
public class LoopToFunctionalV2 extends AbstractFunctionalCall<EnhancedForStatement> {
	
	/**
	 * V1 implementation used for delegation during Phase 1.
	 * 
	 * <p>This ensures feature parity while the ULR-based implementation
	 * is being developed.</p>
	 */
	private final LoopToFunctional v1Delegate = new LoopToFunctional();
	
	/**
	 * Finds convertible loops in the compilation unit.
	 * 
	 * <p><b>Current Implementation:</b> Delegates to V1</p>
	 * 
	 * @param fixcore the fix core context
	 * @param compilationUnit the compilation unit to search
	 * @param operations set to collect rewrite operations
	 * @param nodesprocessed set of already processed nodes
	 */
	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		// Phase 1: Delegate to V1 for feature parity
		v1Delegate.find(fixcore, compilationUnit, operations, nodesprocessed);
	}
	
	/**
	 * Rewrites a loop statement to use functional/stream operations.
	 * 
	 * <p><b>Current Implementation:</b> Delegates to V1</p>
	 * 
	 * @param upp the fix core context
	 * @param visited the enhanced for statement to transform
	 * @param cuRewrite the compilation unit rewrite context
	 * @param group the text edit group for tracking changes
	 * @throws CoreException if rewriting fails
	 */
	@Override
	public void rewrite(UseFunctionalCallFixCore upp, EnhancedForStatement visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		// Phase 1: Delegate to V1 for feature parity
		v1Delegate.rewrite(upp, visited, cuRewrite, group);
	}
	
	/**
	 * Provides a preview of the transformation.
	 * 
	 * <p><b>Current Implementation:</b> Delegates to V1</p>
	 * 
	 * @param afterRefactoring true for after preview, false for before
	 * @return preview string
	 */
	@Override
	public String getPreview(boolean afterRefactoring) {
		// Phase 1: Delegate to V1 for feature parity
		return v1Delegate.getPreview(afterRefactoring);
	}
}
