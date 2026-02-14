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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector.IteratorPattern;

/**
 * Transformer for converting iterator-based while-loops to enhanced for-loops.
 * 
 * <p>Transformation: {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }} → {@code for (T item : collection) { ... }}</p>
 * 
 * <p>Uses the ULR pipeline: {@code LoopModelBuilder → LoopModel → ASTEnhancedForRenderer}.</p>
 * 
 * <p><b>Safety rules (Issue #670):</b></p>
 * <ul>
 *   <li>Rejects conversion when iterator.remove() is used (cannot be expressed in enhanced for)</li>
 *   <li>Rejects conversion when multiple iterator.next() calls are detected</li>
 *   <li>Rejects conversion when break or labeled continue is present</li>
 * </ul>
 * 
 * @see LoopModel
 * @see ASTEnhancedForRenderer
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
						node.getBody(), pattern.iteratorVariableName());
				if (!analysis.isSafe()) {
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
		if (!(visited instanceof WhileStatement whileStmt)) {
			return;
		}
		
		Object patternObj = data.get(visited);
		if (!(patternObj instanceof IteratorPattern pattern)) {
			return;
		}
		
		AST ast = cuRewrite.getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Find the iterator declaration statement that should be removed
		Block parentBlock = (Block) whileStmt.getParent();
		Statement iteratorDecl = IteratorPatternDetector.findPreviousStatement(parentBlock, whileStmt);
		
		// Build LoopModel from the iterator-while pattern using ULR pipeline
		LoopModel model = buildLoopModel(pattern);
		
		// Extract body statements (skip the first item = it.next() declaration)
		List<Statement> bodyStatements = extractBodyStatements(whileStmt);
		
		// Render enhanced for-loop using ULR-based renderer
		ASTEnhancedForRenderer renderer = new ASTEnhancedForRenderer(ast, rewrite);
		renderer.render(model, whileStmt, iteratorDecl, bodyStatements, group);
	}

	/**
	 * Builds a LoopModel from an iterator-while pattern using the ULR pipeline.
	 */
	private LoopModel buildLoopModel(IteratorPattern pattern) {
		String collectionExpr = pattern.collectionExpression().toString();
		String elementType = pattern.elementType() != null ? pattern.elementType() : "Object"; //$NON-NLS-1$
		String elementName = "item"; //$NON-NLS-1$
		
		return new LoopModelBuilder()
			.source(SourceDescriptor.SourceType.COLLECTION, collectionExpr, elementType)
			.element(elementName, elementType, false)
			.terminal(new ForEachTerminal(List.of(), false))
			.build();
	}

	/**
	 * Extracts the actual body statements from the while loop, skipping the
	 * first {@code T item = it.next()} variable declaration.
	 */
	private List<Statement> extractBodyStatements(WhileStatement whileStmt) {
		List<Statement> result = new ArrayList<>();
		Statement whileBody = whileStmt.getBody();
		
		if (whileBody instanceof Block block) {
			boolean skipFirst = false;
			
			// Check if first statement is item = it.next()
			if (!block.statements().isEmpty()) {
				Object firstStmt = block.statements().get(0);
				if (firstStmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement) {
					skipFirst = true;
				}
			}
			
			int startIdx = skipFirst ? 1 : 0;
			for (int i = startIdx; i < block.statements().size(); i++) {
				result.add((Statement) block.statements().get(i));
			}
		} else {
			result.add(whileBody);
		}
		return result;
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
