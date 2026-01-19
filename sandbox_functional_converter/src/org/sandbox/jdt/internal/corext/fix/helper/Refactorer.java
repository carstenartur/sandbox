/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Orchestrates the refactoring of enhanced for-loops into functional stream pipelines.
 * 
 * <p>
 * This class serves as the main entry point for converting imperative for-loops
 * into declarative stream operations. It coordinates the analysis, validation,
 * and transformation phases using {@link PreconditionsChecker} and
 * {@link StreamPipelineBuilder}.
 * </p>
 * 
 * <p><b>Refactoring Process:</b></p>
 * <ol>
 * <li><b>Precondition Check</b>: Validates loop is safe to refactor (no breaks,
 *     returns, exceptions, etc.)</li>
 * <li><b>Analysis</b>: Parses loop body into stream operations</li>
 * <li><b>Pipeline Construction</b>: Builds method invocation chain</li>
 * <li><b>AST Replacement</b>: Replaces for-loop with stream pipeline in AST</li>
 * </ol>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * PreconditionsChecker preconditions = new PreconditionsChecker(forLoop, cu);
 * Refactorer refactorer = new Refactorer(forLoop, rewrite, preconditions, group);
 * 
 * if (refactorer.isRefactorable()) {
 *     refactorer.refactor(); // Performs the transformation
 * }
 * }</pre>
 * 
 * <p><b>AST Modification:</b></p>
 * <p>
 * This class uses Eclipse JDT's {@link ASTRewrite} mechanism to modify the AST.
 * All changes are tracked in a {@link TextEditGroup} for editor integration.
 * Comments on the original for-loop are preserved when possible.
 * </p>
 * 
 * <p><b>Thread Safety:</b> This class is not thread-safe. Create a new instance
 * for each refactoring operation.</p>
 * 
 * @see StreamPipelineBuilder
 * @see PreconditionsChecker
 * @see ASTRewrite
 */
public class Refactorer {
	
	private static final String JAVA_UTIL_ARRAYS = "java.util.Arrays"; //$NON-NLS-1$
	private static final String JAVA_UTIL_STREAM_COLLECTORS = StreamConstants.COLLECTORS_CLASS; //$NON-NLS-1$
	
	private final EnhancedForStatement forLoop;
	private final ASTRewrite rewrite;
	private final PreconditionsChecker preconditions;
	private final TextEditGroup group;
	private final CompilationUnitRewrite cuRewrite;

	/**
	 * Creates a new Refactorer.
	 * 
	 * @param forLoop       the enhanced for-loop to refactor
	 * @param rewrite       the AST rewrite to use
	 * @param preconditions the preconditions checker
	 * @param group         the text edit group for tracking changes
	 * @deprecated Use {@link #Refactorer(EnhancedForStatement, ASTRewrite, PreconditionsChecker, TextEditGroup, CompilationUnitRewrite)} instead
	 */
	@Deprecated
	public Refactorer(EnhancedForStatement forLoop, ASTRewrite rewrite, PreconditionsChecker preconditions,
			TextEditGroup group) {
		this(forLoop, rewrite, preconditions, group, null);
	}
	
	/**
	 * Creates a new Refactorer with CompilationUnitRewrite for import management.
	 * 
	 * @param forLoop       the enhanced for-loop to refactor
	 * @param rewrite       the AST rewrite to use
	 * @param preconditions the preconditions checker
	 * @param group         the text edit group for tracking changes
	 * @param cuRewrite     the compilation unit rewrite for import management (may be null)
	 */
	public Refactorer(EnhancedForStatement forLoop, ASTRewrite rewrite, PreconditionsChecker preconditions,
			TextEditGroup group, CompilationUnitRewrite cuRewrite) {
		this.forLoop = forLoop;
		this.rewrite = rewrite;
		this.preconditions = preconditions;
		this.group = group;
		this.cuRewrite = cuRewrite;
	}

	/** Checks if the loop can be refactored to a stream operation. */
	public boolean isRefactorable() {
		if (!preconditions.isSafeToRefactor()) {
			return false;
		}
		// Also verify that the StreamPipelineBuilder can analyze and build the pipeline
		StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);
		return builder.analyze() && builder.buildPipeline() != null;
	}

	/**
	 * Performs the refactoring of the loop into a stream operation. Uses
	 * StreamPipelineBuilder for all conversions.
	 */
	public void refactor() {
		refactorWithBuilder();
	}

	/**
	 * Refactors the loop using the StreamPipelineBuilder approach.
	 */
	private void refactorWithBuilder() {
		StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);

		if (!builder.analyze()) {
			return; // Cannot convert
		}

		MethodInvocation pipeline = builder.buildPipeline();
		Statement replacement = builder.wrapPipeline(pipeline);
		if (replacement != null) {
			// Add Arrays import if needed (for array iteration)
			if (builder.needsArraysImport() && cuRewrite != null) {
				cuRewrite.getImportRewrite().addImport(JAVA_UTIL_ARRAYS);
			}
			// Add Collectors import if needed (for collect operations)
			if (builder.needsCollectorsImport() && cuRewrite != null) {
				cuRewrite.getImportRewrite().addImport(JAVA_UTIL_STREAM_COLLECTORS);
			}
			
			// Check if this is a COLLECT operation with a preceding empty collection declaration
			Statement mergedDeclaration = tryMergeWithPrecedingDeclaration(builder, pipeline, replacement);
			if (mergedDeclaration != null) {
				// Use the merged declaration instead
				replacement = mergedDeclaration;
			}
			
			ASTNodes.replaceButKeepComment(rewrite, forLoop, replacement, group);
		}
	}
	
	/**
	 * Attempts to merge a COLLECT operation with its preceding empty collection declaration.
	 * 
	 * <p>Pattern detected:</p>
	 * <pre>{@code
	 * List<Integer> result = new ArrayList<>();  // Empty collection declaration
	 * for (Integer l : ls) {
	 *     result.add(l);  // COLLECT operation
	 * }
	 * }</pre>
	 * 
	 * <p>Merged result:</p>
	 * <pre>{@code
	 * List<Integer> result = ls.stream().collect(Collectors.toList());
	 * }</pre>
	 * 
	 * @param builder the stream pipeline builder
	 * @param pipeline the stream pipeline method invocation
	 * @param replacement the current replacement statement (assignment)
	 * @return a merged VariableDeclarationStatement if merge is possible, null otherwise
	 */
	private Statement tryMergeWithPrecedingDeclaration(StreamPipelineBuilder builder, 
			MethodInvocation pipeline, Statement replacement) {
		// Only apply to COLLECT operations
		if (!builder.needsCollectorsImport()) {
			return null;
		}
		
		// The replacement must be an ExpressionStatement containing an assignment
		if (!(replacement instanceof ExpressionStatement)) {
			return null;
		}
		
		// Find the target variable from the CollectPatternDetector
		String targetVariable = builder.getCollectTargetVariable();
		if (targetVariable == null) {
			return null;
		}
		
		// Get the parent block
		ASTNode parent = forLoop.getParent();
		if (!(parent instanceof Block)) {
			return null;
		}
		
		Block block = (Block) parent;
		List<?> statements = block.statements();
		
		// Find the index of the for-loop
		int forLoopIndex = -1;
		for (int i = 0; i < statements.size(); i++) {
			if (statements.get(i) == forLoop) {
				forLoopIndex = i;
				break;
			}
		}
		
		// Check if there's a statement before the for-loop
		if (forLoopIndex <= 0) {
			return null;
		}
		
		Statement precedingStmt = (Statement) statements.get(forLoopIndex - 1);
		
		// Check if the preceding statement is an empty collection declaration for the same variable
		String declaredVar = CollectPatternDetector.isEmptyCollectionDeclaration(precedingStmt);
		if (declaredVar == null || !declaredVar.equals(targetVariable)) {
			return null;
		}
		
		// We have a match! Create a merged VariableDeclarationStatement
		VariableDeclarationStatement originalDecl = (VariableDeclarationStatement) precedingStmt;
		VariableDeclarationFragment originalFragment = 
				(VariableDeclarationFragment) originalDecl.fragments().get(0);
		
		AST ast = forLoop.getAST();
		
		// Create new VariableDeclarationFragment with the pipeline as initializer
		VariableDeclarationFragment newFragment = ast.newVariableDeclarationFragment();
		newFragment.setName(ast.newSimpleName(targetVariable));
		newFragment.setInitializer((MethodInvocation) ASTNode.copySubtree(ast, pipeline));
		
		// Create new VariableDeclarationStatement with the same type
		VariableDeclarationStatement newDecl = ast.newVariableDeclarationStatement(newFragment);
		Type originalType = originalDecl.getType();
		newDecl.setType((Type) ASTNode.copySubtree(ast, originalType));
		
		// Copy modifiers if any
		newDecl.modifiers().addAll(ASTNode.copySubtrees(ast, originalDecl.modifiers()));
		
		// Remove the preceding declaration
		rewrite.remove(precedingStmt, group);
		
		return newDecl;
	}
}