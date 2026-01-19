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
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Converts traditional iterator-based while loops to functional stream operations.
 * 
 * <p><b>Example Transformation:</b></p>
 * <pre>{@code
 * // Before:
 * Iterator<String> it = list.iterator();
 * while (it.hasNext()) {
 *     String item = it.next();
 *     System.out.println(item);
 * }
 * 
 * // After:
 * list.forEach(item -> System.out.println(item));
 * }</pre>
 * 
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 * <li>Simple iteration with forEach</li>
 * <li>Mapping and filtering operations</li>
 * <li>Reduction operations</li>
 * <li>Early return patterns (anyMatch, noneMatch, allMatch)</li>
 * </ul>
 * 
 * <p><b>Safety Checks:</b></p>
 * <ul>
 * <li>Iterator must be used only for iteration (no remove())</li>
 * <li>Single next() call at loop start</li>
 * <li>No modifications to collection during iteration</li>
 * <li>All variables effectively final</li>
 * </ul>
 * 
 * @see IteratorLoopPattern
 * @see LoopToFunctional
 * @see StreamPipelineBuilder
 */
public class IteratorLoopToFunctional extends AbstractFunctionalCall<WhileStatement> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, FunctionalHolder> dataHolder = new ReferenceHolder<>();
		HelperVisitor.callWhileStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, nodesprocessed, visited, aholder),
				(visited, aholder) -> {});
	}

	private boolean processFoundNode(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, WhileStatement visited,
			ReferenceHolder<Integer, FunctionalHolder> dataHolder) {
		
		// Check if this while loop matches the iterator pattern
		IteratorLoopPattern pattern = new IteratorLoopPattern(visited);
		if (!pattern.matches()) {
			// Not an iterator loop - continue visiting children
			return true;
		}
		
		// Found a convertible iterator loop
		operations.add(fixcore.rewrite(visited));
		nodesprocessed.add(visited);
		
		// Also mark the iterator declaration as processed to prevent it from being touched
		if (pattern.getIteratorDeclaration() != null) {
			nodesprocessed.add(pattern.getIteratorDeclaration());
		}
		
		// Return false to prevent visiting children since this loop was converted
		return false;
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore upp, final WhileStatement visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		
		// Detect the iterator pattern again
		IteratorLoopPattern pattern = new IteratorLoopPattern(visited);
		if (!pattern.matches()) {
			// Should not happen since we already validated in find()
			return;
		}
		
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = visited.getAST();
		
		// Create a synthetic EnhancedForStatement for the StreamPipelineBuilder
		// This allows us to reuse all existing stream transformation logic
		EnhancedForStatement syntheticLoop = createSyntheticEnhancedFor(ast, pattern);
		
		// Use existing PreconditionsChecker and StreamPipelineBuilder
		PreconditionsChecker pc = new PreconditionsChecker(syntheticLoop, (CompilationUnit) visited.getRoot());
		
		if (!pc.isSafeToRefactor()) {
			// Cannot convert - skip
			return;
		}
		
		StreamPipelineBuilder builder = new StreamPipelineBuilder(syntheticLoop, pc);
		if (!builder.analyze()) {
			// Cannot convert - skip
			return;
		}
		
		MethodInvocation pipeline = builder.buildPipeline();
		if (pipeline == null) {
			return;
		}
		
		Statement replacement = builder.wrapPipeline(pipeline);
		if (replacement != null) {
			// Replace both the iterator declaration and the while loop
			// First, remove the iterator declaration
			rewrite.remove(pattern.getIteratorDeclaration(), group);
			
			// Then replace the while loop with the stream operation
			rewrite.replace(visited, replacement, group);
		}
	}
	
	/**
	 * Creates a synthetic EnhancedForStatement that represents the iterator loop.
	 * This allows reuse of all existing StreamPipelineBuilder logic.
	 * 
	 * @param ast the AST to create nodes in
	 * @param pattern the detected iterator pattern
	 * @return a synthetic enhanced-for statement
	 */
	private EnhancedForStatement createSyntheticEnhancedFor(AST ast, IteratorLoopPattern pattern) {
		EnhancedForStatement enhancedFor = ast.newEnhancedForStatement();
		
		// Set the parameter: T item
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setName(ast.newSimpleName(pattern.getElementVarName()));
		
		// Try to set the type if we know it
		if (pattern.getElementType() != null) {
			param.setType(ast.newSimpleType(ast.newName(pattern.getElementType().getName())));
		} else {
			// Fallback to Object if type unknown
			param.setType(ast.newSimpleType(ast.newSimpleName("Object"))); //$NON-NLS-1$
		}
		
		enhancedFor.setParameter(param);
		
		// Set the expression: collection
		enhancedFor.setExpression((Expression) ASTNode.copySubtree(ast, pattern.getCollectionExpression()));
		
		// Set the body: loop body without the next() call
		enhancedFor.setBody((Statement) ASTNode.copySubtree(ast, pattern.getLoopBody()));
		
		return enhancedFor;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "list.forEach(item -> {\n\tSystem.out.println(item);\n});\n"; //$NON-NLS-1$
		}
		return "Iterator<String> it = list.iterator();\nwhile (it.hasNext()) {\n\tString item = it.next();\n\tSystem.out.println(item);\n}\n"; //$NON-NLS-1$
	}
}
