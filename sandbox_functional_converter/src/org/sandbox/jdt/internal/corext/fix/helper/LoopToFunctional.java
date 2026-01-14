/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Converts enhanced for-loops to functional stream operations.
 * 
 * <p>
 * This class implements the Eclipse JDT cleanup framework to find and transform
 * imperative for-loops into declarative stream pipelines. It integrates with
 * the Eclipse IDE's quick fix and cleanup mechanisms.
 * </p>
 * 
 * <p><b>Example Transformation:</b></p>
 * <pre>{@code
 * // Before:
 * for (Integer l : ls) {
 *     System.out.println(l);
 * }
 * 
 * // After:
 * ls.forEach(l -> {
 *     System.out.println(l);
 * });
 * }</pre>
 * 
 * <p><b>Integration with Eclipse:</b></p>
 * <p>
 * This class extends {@link AbstractFunctionalCall} and is registered as a
 * cleanup contributor in the Eclipse JDT UI framework. It participates in:
 * <ul>
 * <li>Source cleanup actions (Ctrl+Shift+F in Eclipse)</li>
 * <li>Quick fix suggestions (Ctrl+1)</li>
 * <li>Batch cleanup operations</li>
 * </ul>
 * </p>
 * 
 * <p><b>Processing Flow:</b></p>
 * <ol>
 * <li>{@link #find(UseFunctionalCallFixCore, CompilationUnit, Set, Set)}: 
 *     Visits all EnhancedForStatements and identifies convertible loops</li>
 * <li>{@link #rewrite(UseFunctionalCallFixCore, EnhancedForStatement, CompilationUnitRewrite, TextEditGroup)}:
 *     Performs the actual AST transformation for each identified loop</li>
 * <li>{@link #getPreview(boolean)}: Provides before/after preview in Eclipse UI</li>
 * </ol>
 * 
 * <p><b>Safety Checks:</b></p>
 * <p>
 * The conversion only occurs if:
 * <ul>
 * <li>{@link PreconditionsChecker} validates the loop is safe to refactor</li>
 * <li>{@link StreamPipelineBuilder} successfully analyzes the loop body</li>
 * <li>All variables are effectively final</li>
 * <li>No break, labeled continue, or exception throwing occurs</li>
 * </ul>
 * </p>
 * 
 * @see AbstractFunctionalCall
 * @see StreamPipelineBuilder
 * @see PreconditionsChecker
 * @see Refactorer
 */
public class LoopToFunctional extends AbstractFunctionalCall<EnhancedForStatement> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, FunctionalHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, nodesprocessed, visited, aholder),(visited, aholder) -> {});
	}

	private boolean processFoundNode(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, EnhancedForStatement visited,
			ReferenceHolder<Integer, FunctionalHolder> dataHolder) {
		PreconditionsChecker pc = new PreconditionsChecker(visited, (CompilationUnit) visited.getRoot());
		if (!pc.isSafeToRefactor()) {
			// Loop cannot be safely refactored to functional style
			// Return true to continue visiting children - inner loops may still be convertible
			return false;
		}
		// Check if the loop can be analyzed for stream conversion
		StreamPipelineBuilder builder = new StreamPipelineBuilder(visited, pc);
		if (!builder.analyze()) {
			// Cannot convert this loop to functional style
			// Return true to continue visiting children - inner loops may still be convertible
			return false;
		}
		operations.add(fixcore.rewrite(visited));
		nodesprocessed.add(visited);
		// Return false to prevent visiting children since this loop was converted
		// (children are now part of the lambda expression)
		return false;
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore upp, final EnhancedForStatement visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		PreconditionsChecker pc = new PreconditionsChecker(visited, (CompilationUnit) visited.getRoot());
		Refactorer refactorer = new Refactorer(visited, rewrite, pc, group, cuRewrite);
		// Preconditions already checked in find(), but refactorer.refactor() handles edge cases
		refactorer.refactor();
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "ls.forEach(l -> {\n	System.out.println(l);\n});\n"; //$NON-NLS-1$
		}
		return "for (Integer l : ls)\n	System.out.println(l);\n\n"; //$NON-NLS-1$
	}
}
