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
 * Converts traditional iterator-based loops (while and for) to functional stream operations.
 * 
 * <p><b>Example Transformations:</b></p>
 * <pre>{@code
 * // Pattern 1: While loop with iterator
 * Iterator<String> it = list.iterator();
 * while (it.hasNext()) {
 *     String item = it.next();
 *     System.out.println(item);
 * }
 * 
 * // Pattern 2: For loop with iterator
 * for (Iterator<String> it = list.iterator(); it.hasNext(); ) {
 *     String item = it.next();
 *     System.out.println(item);
 * }
 * 
 * // Both convert to:
 * list.forEach(item -> System.out.println(item));
 * }</pre>
 * 
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 * <li>While-iterator loops and for-iterator loops</li>
 * <li>Simple iteration with forEach</li>
 * <li>Mapping and filtering operations</li>
 * <li>Reduction operations</li>
 * <li>Early return patterns (anyMatch, noneMatch, allMatch)</li>
 * </ul>
 * 
 * <p><b>Safety Checks (via IteratorLoopAnalyzer):</b></p>
 * <ul>
 * <li>No calls to iterator.remove()</li>
 * <li>Single next() call at loop start</li>
 * <li>No break or labeled continue statements</li>
 * </ul>
 * 
 * @see IteratorPatternDetector
 * @see IteratorLoopAnalyzer
 * @see IteratorLoopBodyParser
 * @see LoopToFunctional
 * @see StreamPipelineBuilder
 */
public class IteratorLoopToFunctional extends AbstractFunctionalCall<ASTNode> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, FunctionalHolder> dataHolder = new ReferenceHolder<>();
		
		// Find while-iterator loops
		HelperVisitor.callWhileStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processWhileLoop(fixcore, operations, nodesprocessed, visited),
				(visited, aholder) -> {});
		
		// Find for-iterator loops
		HelperVisitor.callForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processForLoop(fixcore, operations, nodesprocessed, visited),
				(visited, aholder) -> {});
	}

	private boolean processWhileLoop(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, WhileStatement visited) {
		
		// Check if this while loop matches the iterator pattern
		IteratorPatternDetector.IteratorPattern pattern = IteratorPatternDetector.detectWhileIteratorPattern(visited);
		if (pattern == null) {
			// Not an iterator loop - continue visiting children
			return true;
		}
		
		// Analyze for safety
		IteratorLoopAnalyzer analyzer = new IteratorLoopAnalyzer(pattern);
		if (!analyzer.isSafeToConvert()) {
			// Not safe to convert
			return true;
		}
		
		// Parse the loop body
		IteratorLoopBodyParser parser = new IteratorLoopBodyParser(pattern);
		if (!parser.parse()) {
			// Could not parse
			return true;
		}
		
		// Found a convertible iterator loop
		operations.add(fixcore.rewrite(visited));
		nodesprocessed.add(visited);
		
		// Also mark the iterator declaration as processed
		if (pattern.hasExternalDeclaration()) {
			nodesprocessed.add(pattern.getExternalIteratorDecl());
		}
		
		// Return false to prevent visiting children
		return false;
	}

	private boolean processForLoop(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ForStatement visited) {
		
		// Check if this for loop matches the iterator pattern
		IteratorPatternDetector.IteratorPattern pattern = IteratorPatternDetector.detectForLoopIteratorPattern(visited);
		if (pattern == null) {
			// Not an iterator loop - continue visiting children
			return true;
		}
		
		// Analyze for safety
		IteratorLoopAnalyzer analyzer = new IteratorLoopAnalyzer(pattern);
		if (!analyzer.isSafeToConvert()) {
			// Not safe to convert
			return true;
		}
		
		// Parse the loop body
		IteratorLoopBodyParser parser = new IteratorLoopBodyParser(pattern);
		if (!parser.parse()) {
			// Could not parse
			return true;
		}
		
		// Found a convertible iterator loop
		operations.add(fixcore.rewrite(visited));
		nodesprocessed.add(visited);
		
		// Return false to prevent visiting children
		return false;
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore upp, final ASTNode visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		
		try {
			IteratorPatternDetector.IteratorPattern pattern = null;
			
			if (visited instanceof WhileStatement) {
				pattern = IteratorPatternDetector.detectWhileIteratorPattern((WhileStatement) visited);
			} else if (visited instanceof ForStatement) {
				pattern = IteratorPatternDetector.detectForLoopIteratorPattern((ForStatement) visited);
			}
			
			if (pattern == null) {
				return;
			}
			
			// Re-analyze for safety
			IteratorLoopAnalyzer analyzer = new IteratorLoopAnalyzer(pattern);
			if (!analyzer.isSafeToConvert()) {
				return;
			}
			
			// Re-parse the loop body
			IteratorLoopBodyParser parser = new IteratorLoopBodyParser(pattern);
			if (!parser.parse()) {
				return;
			}
			
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = visited.getAST();
			
			// Create a synthetic EnhancedForStatement for the StreamPipelineBuilder
			EnhancedForStatement syntheticLoop = createSyntheticEnhancedFor(ast, pattern, parser);
			
			// Use existing PreconditionsChecker and StreamPipelineBuilder
			PreconditionsChecker pc = new PreconditionsChecker(syntheticLoop, (CompilationUnit) visited.getRoot());
			
			if (!pc.isSafeToRefactor()) {
				return;
			}
			
			StreamPipelineBuilder builder = new StreamPipelineBuilder(syntheticLoop, pc);
			if (!builder.analyze()) {
				return;
			}
			
			MethodInvocation pipeline = builder.buildPipeline();
			if (pipeline == null) {
				return;
			}
			
			Statement replacement = builder.wrapPipeline(pipeline);
			if (replacement != null) {
				// For while loops, also remove the external iterator declaration
				if (pattern.hasExternalDeclaration()) {
					rewrite.remove(pattern.getExternalIteratorDecl(), group);
				}
				
				// Replace the loop with the stream operation
				rewrite.replace(visited, replacement, group);
			}
		} catch (Exception e) {
			// Silently skip if conversion fails - better to leave code unchanged than to break
			return;
		}
	}
	
	/**
	 * Creates a synthetic EnhancedForStatement that represents the iterator loop.
	 * This allows reuse of all existing StreamPipelineBuilder logic.
	 * 
	 * @param ast the AST to create nodes in
	 * @param pattern the detected iterator pattern
	 * @param parser the parsed loop body
	 * @return a synthetic enhanced-for statement
	 */
	private EnhancedForStatement createSyntheticEnhancedFor(AST ast,
			IteratorPatternDetector.IteratorPattern pattern, IteratorLoopBodyParser parser) {
		EnhancedForStatement enhancedFor = ast.newEnhancedForStatement();
		
		// Set the parameter: T item
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setName(ast.newSimpleName(parser.getElementVarName()));
		
		// Try to set the type if we know it
		if (parser.getElementType() != null) {
			param.setType(ast.newSimpleType(ast.newName(parser.getElementType().getName())));
		} else {
			// Fallback to Object if type unknown
			param.setType(ast.newSimpleType(ast.newSimpleName("Object"))); //$NON-NLS-1$
		}
		
		enhancedFor.setParameter(param);
		
		// Set the expression: collection
		enhancedFor.setExpression((Expression) ASTNode.copySubtree(ast, pattern.getCollectionExpression()));
		
		// Set the body: loop body without the next() call
		enhancedFor.setBody((Statement) ASTNode.copySubtree(ast, parser.getBodyWithoutNext()));
		
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
