/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Assembles stream pipeline method invocations from a list of operations.
 * 
 * <p>This class is responsible for the final assembly phase of the stream
 * conversion, taking a list of {@link ProspectiveOperation}s and constructing
 * the actual AST nodes for the stream pipeline.</p>
 * 
 * <p><b>Responsibilities:</b></p>
 * <ul>
 * <li>Building the method invocation chain (stream().filter().map()...)</li>
 * <li>Determining if .stream() prefix is needed</li>
 * <li>Wrapping the pipeline in appropriate statement types</li>
 * <li>Handling special cases (REDUCE assignments, match IF statements)</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * PipelineAssembler assembler = new PipelineAssembler(forLoop, operations, loopVarName);
 * assembler.setUsedVariableNames(scopeVars);
 * assembler.setReduceDetector(reduceDetector);
 * 
 * MethodInvocation pipeline = assembler.buildPipeline();
 * Statement replacement = assembler.wrapPipeline(pipeline);
 * }</pre>
 * 
 * @see StreamPipelineBuilder
 * @see ProspectiveOperation
 */
public class PipelineAssembler {

	private final EnhancedForStatement forLoop;
	private final AST ast;
	private final List<ProspectiveOperation> operations;
	private final String loopVariableName;
	
	private Collection<String> usedVariableNames;
	private ReducePatternDetector reduceDetector;

	/**
	 * Creates a new PipelineAssembler.
	 * 
	 * @param forLoop          the enhanced for-loop being converted
	 * @param operations       the list of operations to assemble
	 * @param loopVariableName the loop variable name
	 */
	public PipelineAssembler(EnhancedForStatement forLoop, 
			List<ProspectiveOperation> operations,
			String loopVariableName) {
		this.forLoop = forLoop;
		this.ast = forLoop.getAST();
		this.operations = operations;
		this.loopVariableName = loopVariableName;
	}

	/**
	 * Sets the collection of variable names in use (for unique name generation).
	 * 
	 * @param usedNames the used variable names
	 */
	public void setUsedVariableNames(Collection<String> usedNames) {
		this.usedVariableNames = usedNames;
	}

	/**
	 * Sets the reduce pattern detector (for accumulator variable access).
	 * 
	 * @param detector the reduce pattern detector
	 */
	public void setReduceDetector(ReducePatternDetector detector) {
		this.reduceDetector = detector;
	}

	/**
	 * Builds the stream pipeline method invocation chain.
	 * 
	 * @return the assembled pipeline, or null if operations are empty
	 */
	public MethodInvocation buildPipeline() {
		if (operations.isEmpty()) {
			return null;
		}

		boolean needsStream = requiresStreamPrefix();

		if (needsStream) {
			return buildStreamPipeline();
		} else {
			return buildDirectForEach();
		}
	}

	/**
	 * Builds a pipeline starting with .stream().
	 */
	private MethodInvocation buildStreamPipeline() {
		// Start with .stream()
		MethodInvocation pipeline = ast.newMethodInvocation();
		pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
		pipeline.setName(ast.newSimpleName(StreamConstants.STREAM_METHOD));

		// Chain each operation
		for (int i = 0; i < operations.size(); i++) {
			ProspectiveOperation op = operations.get(i);
			
			if (usedVariableNames != null) {
				op.setUsedVariableNames(usedVariableNames);
			}

			MethodInvocation next = ast.newMethodInvocation();
			next.setExpression(pipeline);
			next.setName(ast.newSimpleName(op.getSuitableMethod()));

			String paramName = getVariableNameFromPreviousOp(i);
			List<Expression> args = op.getArguments(ast, paramName);
			
			for (Expression arg : args) {
				next.arguments().add(arg);
			}
			pipeline = next;
		}

		return pipeline;
	}

	/**
	 * Builds a direct forEach without .stream().
	 */
	private MethodInvocation buildDirectForEach() {
		ProspectiveOperation op = operations.get(0);
		
		if (usedVariableNames != null) {
			op.setUsedVariableNames(usedVariableNames);
		}

		MethodInvocation pipeline = ast.newMethodInvocation();
		pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
		pipeline.setName(ast.newSimpleName(StreamConstants.FOR_EACH_METHOD));
		
		List<Expression> args = op.getArguments(ast, loopVariableName);
		for (Expression arg : args) {
			pipeline.arguments().add(arg);
		}

		return pipeline;
	}

	/**
	 * Wraps the pipeline in an appropriate statement type.
	 * 
	 * @param pipeline the pipeline to wrap
	 * @return the wrapped statement
	 */
	public Statement wrapPipeline(MethodInvocation pipeline) {
		if (pipeline == null) {
			return null;
		}

		// Check for match operations
		if (hasOperationType(ProspectiveOperation.OperationType.ANYMATCH)) {
			return wrapAnyMatch(pipeline);
		}
		if (hasOperationType(ProspectiveOperation.OperationType.NONEMATCH)) {
			return wrapNegatedMatch(pipeline, false);
		}
		if (hasOperationType(ProspectiveOperation.OperationType.ALLMATCH)) {
			return wrapNegatedMatch(pipeline, false);
		}

		// Check for REDUCE operation
		if (hasOperationType(ProspectiveOperation.OperationType.REDUCE)) {
			return wrapReduce(pipeline);
		}

		// Default: wrap in ExpressionStatement
		return ast.newExpressionStatement(pipeline);
	}

	/**
	 * Wraps in: if (stream.anyMatch(...)) { return true; }
	 */
	private IfStatement wrapAnyMatch(MethodInvocation pipeline) {
		IfStatement ifStmt = ast.newIfStatement();
		ifStmt.setExpression(pipeline);

		Block thenBlock = ast.newBlock();
		ReturnStatement returnStmt = ast.newReturnStatement();
		returnStmt.setExpression(ast.newBooleanLiteral(true));
		thenBlock.statements().add(returnStmt);
		ifStmt.setThenStatement(thenBlock);

		return ifStmt;
	}

	/**
	 * Wraps in: if (!stream.noneMatch/allMatch(...)) { return returnValue; }
	 */
	private IfStatement wrapNegatedMatch(MethodInvocation pipeline, boolean returnValue) {
		IfStatement ifStmt = ast.newIfStatement();

		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand(pipeline);
		ifStmt.setExpression(negation);

		Block thenBlock = ast.newBlock();
		ReturnStatement returnStmt = ast.newReturnStatement();
		returnStmt.setExpression(ast.newBooleanLiteral(returnValue));
		thenBlock.statements().add(returnStmt);
		ifStmt.setThenStatement(thenBlock);

		return ifStmt;
	}

	/**
	 * Wraps in: accumulatorVariable = stream.reduce(...);
	 */
	private Statement wrapReduce(MethodInvocation pipeline) {
		String accumulatorVariable = reduceDetector != null ? reduceDetector.getAccumulatorVariable() : null;
		
		if (accumulatorVariable != null) {
			Assignment assignment = ast.newAssignment();
			assignment.setLeftHandSide(ast.newSimpleName(accumulatorVariable));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			assignment.setRightHandSide(pipeline);
			return ast.newExpressionStatement(assignment);
		}
		
		return ast.newExpressionStatement(pipeline);
	}

	/**
	 * Determines if .stream() prefix is required.
	 */
	private boolean requiresStreamPrefix() {
		if (operations.isEmpty()) {
			return true;
		}
		return operations.size() > 1
				|| operations.get(0).getOperationType() != ProspectiveOperation.OperationType.FOREACH;
	}

	/**
	 * Gets the variable name to use for the current operation.
	 */
	private String getVariableNameFromPreviousOp(int currentIndex) {
		for (int i = currentIndex - 1; i >= 0; i--) {
			ProspectiveOperation op = operations.get(i);
			if (op != null && op.getProducedVariableName() != null) {
				return op.getProducedVariableName();
			}
		}
		return loopVariableName;
	}

	/**
	 * Checks if any operation has the given type.
	 */
	private boolean hasOperationType(ProspectiveOperation.OperationType type) {
		return operations.stream()
				.anyMatch(op -> op.getOperationType() == type);
	}
}
