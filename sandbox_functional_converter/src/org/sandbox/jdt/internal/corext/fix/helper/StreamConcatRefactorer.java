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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.ConsecutiveLoopGroupDetector.ConsecutiveLoopGroup;

/**
 * Refactors consecutive loops adding to same collection into Stream.concat().
 * 
 * <p>This class handles the transformation of multiple consecutive for-loops that
 * all add elements to the same collection variable. It converts them into a single
 * Stream.concat() expression.</p>
 * 
 * <p><b>Transformation Example (2 loops):</b></p>
 * <pre>{@code
 * // Before:
 * List<Entry> entries = new ArrayList<>();
 * for (Type1 item : list1) {
 *     entries.add(transform1(item));
 * }
 * for (Type2 item : list2) {
 *     entries.add(transform2(item));
 * }
 * 
 * // After:
 * List<Entry> entries = Stream.concat(
 *     list1.stream().map(item -> transform1(item)),
 *     list2.stream().map(item -> transform2(item))
 * ).collect(Collectors.toList());
 * }</pre>
 * 
 * <p><b>Transformation Example (3+ loops):</b></p>
 * <pre>{@code
 * // Before: 3 consecutive loops
 * for (Type1 item : list1) { entries.add(new Entry(item)); }
 * for (Type2 item : list2) { entries.add(new Entry(item)); }
 * for (Type3 item : list3) { entries.add(new Entry(item)); }
 * 
 * // After: Nested Stream.concat()
 * entries = Stream.concat(
 *     Stream.concat(
 *         list1.stream().map(item -> new Entry(item)),
 *         list2.stream().map(item -> new Entry(item))
 *     ),
 *     list3.stream().map(item -> new Entry(item))
 * ).collect(Collectors.toList());
 * }</pre>
 * 
 * @see ConsecutiveLoopGroupDetector
 * @see StreamPipelineBuilder
 * @see Refactorer
 */
public class StreamConcatRefactorer {

	private static final String JAVA_UTIL_STREAM_COLLECTORS = StreamConstants.COLLECTORS_CLASS;
	private static final String JAVA_UTIL_STREAM_STREAM = "java.util.stream.Stream";

	private final ConsecutiveLoopGroup group;
	private final ASTRewrite rewrite;
	private final TextEditGroup editGroup;
	private final CompilationUnitRewrite cuRewrite;

	/**
	 * Creates a new StreamConcatRefactorer.
	 * 
	 * @param group      the group of consecutive loops to refactor
	 * @param rewrite    the AST rewrite to use
	 * @param editGroup  the text edit group for tracking changes
	 * @param cuRewrite  the compilation unit rewrite for import management
	 */
	public StreamConcatRefactorer(ConsecutiveLoopGroup group, ASTRewrite rewrite,
			TextEditGroup editGroup, CompilationUnitRewrite cuRewrite) {
		this.group = group;
		this.rewrite = rewrite;
		this.editGroup = editGroup;
		this.cuRewrite = cuRewrite;
	}

	/**
	 * Checks if this group can be refactored to Stream.concat().
	 * 
	 * @return true if refactoring is possible
	 */
	public boolean canRefactor() {
		List<EnhancedForStatement> loops = group.getLoops();

		// Must have at least 2 loops
		if (loops.size() < 2) {
			return false;
		}

		// Check that each loop can be converted individually
		for (EnhancedForStatement loop : loops) {
			PreconditionsChecker pc = new PreconditionsChecker(loop, (CompilationUnit) loop.getRoot());
			if (!pc.isSafeToRefactor()) {
				return false;
			}

			StreamPipelineBuilder builder = new StreamPipelineBuilder(loop, pc);
			if (!builder.analyze() || !builder.isCollectOperation()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Performs the refactoring to Stream.concat().
	 * 
	 * <p>This method:</p>
	 * <ul>
	 * <li>Builds individual stream pipelines for each loop (without .collect())</li>
	 * <li>Combines them using nested Stream.concat() calls</li>
	 * <li>Adds .collect(Collectors.toList()) at the end</li>
	 * <li>Removes all loops except the first, replacing it with the combined expression</li>
	 * <li>Attempts to merge with preceding empty collection declaration</li>
	 * </ul>
	 */
	public void refactor() {
		// Check if refactoring is possible before proceeding
		if (!canRefactor()) {
			// Silently skip if refactoring is not possible
			// This can happen if loops don't match the expected pattern
			return;
		}
		
		List<EnhancedForStatement> loops = group.getLoops();
		if (loops.size() < 2) {
			return;
		}

		EnhancedForStatement firstLoop = loops.get(0);
		AST ast = firstLoop.getAST();

		// Build stream expressions for each loop (without terminal collect)
		MethodInvocation[] streamExpressions = new MethodInvocation[loops.size()];
		for (int i = 0; i < loops.size(); i++) {
			streamExpressions[i] = buildStreamExpression(loops.get(i));
			if (streamExpressions[i] == null) {
				return; // Cannot build pipeline for this loop
			}
		}

		// Build nested Stream.concat() calls
		MethodInvocation concatExpression = buildConcatExpression(ast, streamExpressions);

		// Add .collect(Collectors.toList()) terminal operation
		MethodInvocation collectCall = ast.newMethodInvocation();
		collectCall.setExpression(concatExpression);
		collectCall.setName(ast.newSimpleName("collect"));

		// Collectors.toList() argument
		MethodInvocation toListCall = ast.newMethodInvocation();
		toListCall.setExpression(ast.newName("Collectors"));
		toListCall.setName(ast.newSimpleName("toList"));
		collectCall.arguments().add(toListCall);

		// Add necessary imports
		if (cuRewrite != null) {
			cuRewrite.getImportRewrite().addImport(JAVA_UTIL_STREAM_STREAM);
			cuRewrite.getImportRewrite().addImport(JAVA_UTIL_STREAM_COLLECTORS);
		}

		// Try to merge with preceding empty collection declaration
		Statement replacement = tryMergeWithPrecedingDeclaration(firstLoop, collectCall);

		if (replacement == null) {
			// No merge possible - create assignment statement
			// targetVar = Stream.concat(...).collect(Collectors.toList());
			ExpressionStatement assignStmt = ast.newExpressionStatement(
					createAssignment(ast, group.getTargetVariable(), collectCall));
			replacement = assignStmt;
		}

		// Replace first loop with combined expression
		ASTNodes.replaceButKeepComment(rewrite, firstLoop, replacement, editGroup);

		// Remove all subsequent loops in the group
		for (int i = 1; i < loops.size(); i++) {
			rewrite.remove(loops.get(i), editGroup);
		}
	}

	/**
	 * Builds a stream expression for a single loop (without terminal collect).
	 * 
	 * <p>For example: {@code list.stream().map(item -> new Entry(item))}</p>
	 * 
	 * @param loop the loop to convert
	 * @return the stream pipeline expression, or null if cannot convert
	 */
	private MethodInvocation buildStreamExpression(EnhancedForStatement loop) {
		PreconditionsChecker pc = new PreconditionsChecker(loop, (CompilationUnit) loop.getRoot());
		StreamPipelineBuilder builder = new StreamPipelineBuilder(loop, pc);

		if (!builder.analyze()) {
			return null;
		}

		// Build the pipeline - it will include .collect() for COLLECT operations
		MethodInvocation fullPipeline = builder.buildPipeline();
		if (fullPipeline == null) {
			return null;
		}

		// For COLLECT operations, we need to remove the .collect() and return just the stream
		// The pipeline structure is: source.stream().map(...).collect(Collectors.toList())
		// We want: source.stream().map(...)
		if (builder.isCollectOperation()) {
			return removeTerminalCollect(fullPipeline);
		}

		// For non-collect operations, return the full pipeline
		// (this shouldn't happen for our use case, but handle it gracefully)
		return fullPipeline;
	}

	/**
	 * Removes the terminal .collect() operation from a pipeline.
	 * 
	 * <p>Finds the .collect() call at the end of the chain and returns the expression
	 * before it.</p>
	 * 
	 * @param pipeline the complete pipeline with collect
	 * @return the pipeline without collect, or null if structure is unexpected
	 */
	private MethodInvocation removeTerminalCollect(MethodInvocation pipeline) {
		if (pipeline == null) {
			return null;
		}

		// Check if this is the collect call
		if ("collect".equals(pipeline.getName().getIdentifier())) {
			// Return the expression this collect is called on
			// e.g., "source.stream().map(...).collect(...)" -> return "source.stream().map(...)"
			if (pipeline.getExpression() instanceof MethodInvocation) {
				return (MethodInvocation) pipeline.getExpression();
			}
			// Unexpected structure - return null to fail safely
			return null;
		}

		// Not a collect call - return as is
		return pipeline;
	}

	/**
	 * Builds nested Stream.concat() calls to combine multiple streams.
	 * 
	 * <p>For 2 streams: {@code Stream.concat(stream1, stream2)}</p>
	 * <p>For 3+ streams: {@code Stream.concat(Stream.concat(stream1, stream2), stream3)}</p>
	 * 
	 * @param ast               the AST to use
	 * @param streamExpressions the individual stream expressions
	 * @return the combined concat expression
	 */
	private MethodInvocation buildConcatExpression(AST ast, MethodInvocation[] streamExpressions) {
		if (streamExpressions.length < 2) {
			throw new IllegalArgumentException("Need at least 2 streams to concat");
		}

		// Start with the first two streams
		MethodInvocation result = createConcatCall(ast,
				(MethodInvocation) ASTNode.copySubtree(ast, streamExpressions[0]),
				(MethodInvocation) ASTNode.copySubtree(ast, streamExpressions[1]));

		// Add remaining streams using nested concat
		for (int i = 2; i < streamExpressions.length; i++) {
			result = createConcatCall(ast, result,
					(MethodInvocation) ASTNode.copySubtree(ast, streamExpressions[i]));
		}

		return result;
	}

	/**
	 * Creates a single Stream.concat(stream1, stream2) call.
	 * 
	 * @param ast     the AST to use
	 * @param stream1 the first stream
	 * @param stream2 the second stream
	 * @return the concat method invocation
	 */
	private MethodInvocation createConcatCall(AST ast, Expression stream1, Expression stream2) {
		MethodInvocation concatCall = ast.newMethodInvocation();
		concatCall.setExpression(ast.newName("Stream"));
		concatCall.setName(ast.newSimpleName("concat"));
		concatCall.arguments().add(stream1);
		concatCall.arguments().add(stream2);
		return concatCall;
	}

	/**
	 * Creates an assignment expression: {@code targetVar = expression}.
	 * 
	 * @param ast         the AST to use
	 * @param targetVar   the target variable name
	 * @param expression  the expression to assign
	 * @return the assignment expression
	 */
	private Expression createAssignment(AST ast, String targetVar, Expression expression) {
		org.eclipse.jdt.core.dom.Assignment assignment = ast.newAssignment();
		assignment.setLeftHandSide(ast.newSimpleName(targetVar));
		assignment.setOperator(org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN);
		assignment.setRightHandSide(expression);
		return assignment;
	}

	/**
	 * Attempts to merge the concat expression with a preceding empty collection declaration.
	 * 
	 * <p>Pattern:</p>
	 * <pre>{@code
	 * List<Entry> entries = new ArrayList<>();  // Preceding declaration (with or without size)
	 * for (Type1 item : list1) { entries.add(...); }
	 * for (Type2 item : list2) { entries.add(...); }
	 * 
	 * // Becomes:
	 * List<Entry> entries = Stream.concat(...).collect(Collectors.toList());
	 * }</pre>
	 * 
	 * @param firstLoop        the first loop in the group
	 * @param concatExpression the Stream.concat() expression
	 * @return the merged declaration, or null if merge not possible
	 */
	private Statement tryMergeWithPrecedingDeclaration(EnhancedForStatement firstLoop,
			MethodInvocation concatExpression) {
		// Get the parent block
		ASTNode parent = firstLoop.getParent();
		if (!(parent instanceof Block)) {
			return null;
		}

		Block block = (Block) parent;
		List<?> statements = block.statements();

		// Find the index of the first loop
		int forLoopIndex = -1;
		for (int i = 0; i < statements.size(); i++) {
			if (statements.get(i) == firstLoop) {
				forLoopIndex = i;
				break;
			}
		}

		// Check if there's a statement before the first loop
		if (forLoopIndex <= 0) {
			return null;
		}

		Statement precedingStmt = (Statement) statements.get(forLoopIndex - 1);

		// Check if the preceding statement is a collection declaration for the target variable
		// We accept both empty collections and collections with size hints
		String declaredVar = getCollectionDeclarationVariable(precedingStmt);
		if (declaredVar == null || !declaredVar.equals(group.getTargetVariable())) {
			return null;
		}

		// We have a match! Create a merged VariableDeclarationStatement
		VariableDeclarationStatement originalDecl = (VariableDeclarationStatement) precedingStmt;
		VariableDeclarationFragment originalFragment = (VariableDeclarationFragment) originalDecl.fragments().get(0);

		AST ast = firstLoop.getAST();

		// Create new VariableDeclarationFragment with the concat expression as initializer
		VariableDeclarationFragment newFragment = ast.newVariableDeclarationFragment();
		newFragment.setName(ast.newSimpleName(group.getTargetVariable()));
		newFragment.setInitializer((MethodInvocation) ASTNode.copySubtree(ast, concatExpression));

		// Create new VariableDeclarationStatement with the same type
		VariableDeclarationStatement newDecl = ast.newVariableDeclarationStatement(newFragment);
		Type originalType = originalDecl.getType();
		newDecl.setType((Type) ASTNode.copySubtree(ast, originalType));

		// Copy modifiers if any
		newDecl.modifiers().addAll(ASTNode.copySubtrees(ast, originalDecl.modifiers()));

		// Remove the preceding declaration
		rewrite.remove(precedingStmt, editGroup);

		return newDecl;
	}

	/**
	 * Gets the variable name from a collection declaration statement.
	 * Accepts both empty collections and collections with constructor arguments (e.g., size hints).
	 * 
	 * @param stmt the statement to check
	 * @return the variable name if it's a collection declaration, null otherwise
	 */
	private String getCollectionDeclarationVariable(Statement stmt) {
		// Try the standard empty collection check first
		String emptyVar = CollectPatternDetector.isEmptyCollectionDeclaration(stmt);
		if (emptyVar != null) {
			return emptyVar;
		}

		// Also accept collections with constructor arguments (e.g., new ArrayList<>(size))
		if (!(stmt instanceof VariableDeclarationStatement)) {
			return null;
		}

		VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
		if (varDecl.fragments().size() != 1) {
			return null;
		}

		VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDecl.fragments().get(0);
		Expression initializer = fragment.getInitializer();

		if (!(initializer instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation)) {
			return null;
		}

		org.eclipse.jdt.core.dom.ClassInstanceCreation creation = 
			(org.eclipse.jdt.core.dom.ClassInstanceCreation) initializer;
		
		org.eclipse.jdt.core.dom.ITypeBinding typeBinding = creation.resolveTypeBinding();
		if (typeBinding == null) {
			return null;
		}

		// Check if it's a supported collection type
		String qualifiedName = typeBinding.getErasure().getQualifiedName();
		if (CollectorType.fromCollectionType(qualifiedName) != null) {
			return fragment.getName().getIdentifier();
		}

		return null;
	}
}
