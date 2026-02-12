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
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/**
 * Transformer for converting iterator-based while-loops to enhanced for-loops.
 * 
 * <p>Transformation: {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }} → {@code for (T item : collection) { ... }}</p>
 * 
 * <p><b>Safety rules (Issue #670):</b></p>
 * <ul>
 *   <li>Rejects conversion when iterator.remove() is used (cannot be expressed in enhanced for)</li>
 *   <li>Rejects conversion when multiple iterator.next() calls are detected</li>
 *   <li>Rejects conversion when break or labeled continue is present</li>
 * </ul>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/549">Issue #549</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 */
public class IteratorWhileToEnhancedFor extends AbstractFunctionalCall<ASTNode> {

	private final IteratorPatternDetector patternDetector = new IteratorPatternDetector();
	private final IteratorLoopAnalyzer loopAnalyzer = new IteratorLoopAnalyzer();
	
	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(WhileStatement node) {
				if (nodesprocessed.contains(node)) {
					return false;
				}
				
				// Find previous statement for while-iterator pattern
				if (!IteratorPatternDetector.isStatementInBlock(node)) {
					return true;
				}
				
				Block parentBlock = (Block) node.getParent();
				Statement previousStmt = IteratorPatternDetector.findPreviousStatement(parentBlock, node);
				
				IteratorPattern pattern = patternDetector.detectWhilePattern(node, previousStmt);
				if (pattern == null) {
					return true;
				}
				
				// Issue #670: Safety check - reject conversion if iterator has unsafe usage
				// (remove(), multiple next(), break, labeled continue)
				IteratorLoopAnalyzer.SafetyAnalysis analysis = loopAnalyzer.analyze(
						node.getBody(), pattern.iteratorVariableName);
				if (!analysis.isSafe) {
					return true;
				}
				
				// Mark both the iterator declaration and the while loop as processed
				nodesprocessed.add(previousStmt);
				nodesprocessed.add(node);
				
				ReferenceHolder<ASTNode, Object> holder = ReferenceHolder.create();
				holder.put(node, pattern);
				holder.put(previousStmt, pattern); // Store pattern for both nodes
				
				operations.add(fixcore.rewrite(node, holder));
				
				return false;
			}
		});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore useExplicitEncodingFixCore, ASTNode visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> data)
			throws CoreException {
		if (!(visited instanceof WhileStatement)) {
			return;
		}
		
		Object patternObj = data.get(visited);
		if (!(patternObj instanceof IteratorPattern)) {
			return;
		}
		
		IteratorPattern pattern = (IteratorPattern) patternObj;
		AST ast = cuRewrite.getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Find the iterator declaration statement that should be removed
		WhileStatement whileStmt = (WhileStatement) visited;
		Block parentBlock = (Block) whileStmt.getParent();
		Statement iteratorDecl = IteratorPatternDetector.findPreviousStatement(parentBlock, whileStmt);
		
		// Parse the element type
		Type elementType;
		if (pattern.elementType != null && !"Object".equals(pattern.elementType)) { //$NON-NLS-1$
			elementType = (Type) rewrite.createStringPlaceholder(pattern.elementType, ASTNode.SIMPLE_TYPE);
		} else {
			elementType = ast.newSimpleType(ast.newName("Object")); //$NON-NLS-1$
		}
		
		// Create the enhanced for-loop parameter
		SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
		param.setType(elementType);
		param.setName(ast.newSimpleName("item")); //$NON-NLS-1$
		
		// Create enhanced for-loop
		EnhancedForStatement forStmt = ast.newEnhancedForStatement();
		forStmt.setParameter(param);
		forStmt.setExpression((Expression) ASTNode.copySubtree(ast, pattern.collectionExpression));
		
		// Copy the while body, but skip the first item = it.next() declaration if present
		Statement whileBody = whileStmt.getBody();
		Block forBody = ast.newBlock();
		
		if (whileBody instanceof Block) {
			Block block = (Block) whileBody;
			boolean skipFirst = false;
			
			// Check if first statement is item = it.next()
			if (!block.statements().isEmpty()) {
				Object firstStmt = block.statements().get(0);
				if (firstStmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement) {
					// This is likely the item = it.next() statement, skip it
					skipFirst = true;
				}
			}
			
			int startIdx = skipFirst ? 1 : 0;
			for (int i = startIdx; i < block.statements().size(); i++) {
				Statement stmt = (Statement) block.statements().get(i);
				forBody.statements().add(rewrite.createCopyTarget(stmt));
			}
		} else {
			forBody.statements().add(rewrite.createCopyTarget(whileBody));
		}
		
		forStmt.setBody(forBody);
		
		// Replace while with for-loop and remove iterator declaration
		org.eclipse.jdt.core.dom.rewrite.ListRewrite listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
		if (iteratorDecl != null) {
			listRewrite.remove(iteratorDecl, group);
		}
		listRewrite.replace(whileStmt, forStmt, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					for (String item : items) {
						System.out.println(item);
					}
					""";
		}
		return """
				Iterator<String> it = items.iterator();
				while (it.hasNext()) {
					String item = it.next();
					System.out.println(item);
				}
				""";
	}
}
