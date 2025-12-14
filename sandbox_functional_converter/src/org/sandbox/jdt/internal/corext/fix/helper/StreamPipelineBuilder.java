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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Builder class for constructing stream pipelines from enhanced for-loops.
 * 
 * <p>This class analyzes the body of an enhanced for-loop and determines if it can be
 * converted into a stream pipeline. It handles various patterns including:
 * <ul>
 * <li>Simple forEach operations</li>
 * <li>MAP operations (variable declarations with initializers)</li>
 * <li>FILTER operations (IF statements)</li>
 * <li>REDUCE operations (accumulator patterns)</li>
 * <li>ANYMATCH/NONEMATCH operations (early returns)</li>
 * </ul>
 * 
 * <p>Based on the NetBeans mapreduce hints implementation:
 * https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce
 * 
 * @see ProspectiveOperation
 * @see PreconditionsChecker
 */
public class StreamPipelineBuilder {
    private final EnhancedForStatement forLoop;
    private final PreconditionsChecker preconditions;
    private final AST ast;
    
    private List<ProspectiveOperation> operations;
    private String loopVariableName;
    private boolean analyzed = false;
    private boolean convertible = false;

    /**
     * Creates a new StreamPipelineBuilder for the given for-loop.
     * 
     * @param forLoop the enhanced for-loop to analyze
     * @param preconditions the preconditions checker for the loop
     */
    public StreamPipelineBuilder(EnhancedForStatement forLoop, PreconditionsChecker preconditions) {
        this.forLoop = forLoop;
        this.preconditions = preconditions;
        this.ast = forLoop.getAST();
        this.loopVariableName = forLoop.getParameter().getName().getIdentifier();
        this.operations = new ArrayList<>();
    }

    /**
     * Analyzes the loop body to determine if it can be converted to a stream pipeline.
     * 
     * <p>This method should be called before attempting to build the pipeline.
     * It inspects the loop body and extracts a sequence of {@link ProspectiveOperation}s
     * that represent the transformation.
     * 
     * @return true if the loop can be converted to a stream pipeline, false otherwise
     */
    public boolean analyze() {
        if (analyzed) {
            return convertible;
        }
        
        analyzed = true;
        
        // Check basic preconditions
        if (!preconditions.isSafeToRefactor() || !preconditions.iteratesOverIterable()) {
            convertible = false;
            return false;
        }
        
        // Parse the loop body into operations
        Statement loopBody = forLoop.getBody();
        operations = parseLoopBody(loopBody, loopVariableName);
        
        // Check if we have any operations
        convertible = !operations.isEmpty();
        return convertible;
    }

    /**
     * Builds the stream pipeline from the analyzed operations.
     * 
     * <p>This method should be called after {@link #analyze()} returns true.
     * It constructs a {@link MethodInvocation} representing the complete stream pipeline.
     * 
     * @return a MethodInvocation representing the stream pipeline, or null if the loop cannot be converted
     */
    public MethodInvocation buildPipeline() {
        if (!analyzed || !convertible) {
            return null;
        }
        
        // Check if we need .stream() or can use direct .forEach()
        boolean needsStream = requiresStreamPrefix();
        
        MethodInvocation pipeline;
        if (needsStream) {
            // Start with .stream()
            pipeline = ast.newMethodInvocation();
            pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            pipeline.setName(ast.newSimpleName("stream"));
            
            // Chain each operation
            for (int i = 0; i < operations.size(); i++) {
                ProspectiveOperation op = operations.get(i);
                MethodInvocation next = ast.newMethodInvocation();
                next.setExpression(pipeline);
                next.setName(ast.newSimpleName(op.getSuitableMethod()));

                // Get the current parameter name for this operation
                String paramName = getVariableNameFromPreviousOp(operations, i, loopVariableName);
                
                // Use the current paramName for this operation
                List<Expression> args = op.getArguments(ast, paramName);
                for (Expression arg : args) {
                    next.arguments().add(arg);
                }
                pipeline = next;
            }
        } else {
            // Simple forEach without stream()
            ProspectiveOperation op = operations.get(0);
            pipeline = ast.newMethodInvocation();
            pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            pipeline.setName(ast.newSimpleName("forEach"));
            List<Expression> args = op.getArguments(ast, loopVariableName);
            for (Expression arg : args) {
                pipeline.arguments().add(arg);
            }
        }
        
        return pipeline;
    }

    /**
     * Wraps the pipeline in an appropriate statement.
     * 
     * <p>For most pipelines, this wraps the method invocation in an ExpressionStatement.
     * For REDUCE operations, this may wrap the result in an assignment.
     * 
     * @param pipeline the pipeline method invocation
     * @return a Statement wrapping the pipeline
     */
    public Statement wrapPipeline(MethodInvocation pipeline) {
        if (pipeline == null) {
            return null;
        }
        
        // For now, wrap in an ExpressionStatement
        // Future: handle REDUCE operations with assignment
        ExpressionStatement exprStmt = ast.newExpressionStatement(pipeline);
        return exprStmt;
    }

    /**
     * Returns the list of operations extracted from the loop body.
     * 
     * @return the list of prospective operations
     */
    public List<ProspectiveOperation> getOperations() {
        return operations;
    }

    /**
     * Analyzes the body of an enhanced for-loop and extracts a list of {@link ProspectiveOperation}
     * objects representing the operations that can be mapped to stream operations.
     * 
     * <p>This method inspects the statements within the loop body to identify possible
     * stream operations such as {@code map} (for variable declarations with initializers),
     * {@code filter} (for IF statements), and {@code forEach} (for the final or sole statement).
     * For block bodies, it processes each statement in order, treating:
     * - IF statements with single block body as FILTER operations
     * - Variable declarations with initializers as MAP operations
     * - The last statement as a FOREACH operation
     *
     * @param body the {@link Statement} representing the loop body; may be a {@link Block} or a single statement
     * @param loopVarName the name of the loop variable currently in scope; may be updated if a map operation is found
     * @return a list of {@link ProspectiveOperation} objects, in the order they should be applied,
     *         representing the sequence of stream operations inferred from the loop body
     * @see ProspectiveOperation
     */
    private List<ProspectiveOperation> parseLoopBody(Statement body, String loopVarName) {
        List<ProspectiveOperation> ops = new ArrayList<>();
        String currentVarName = loopVarName; // Track the current variable name through the pipeline
        
        if (body instanceof Block) {
            Block block = (Block) body;
            List<Statement> statements = block.statements();
            
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                boolean isLast = (i == statements.size() - 1);
                
                if (stmt instanceof VariableDeclarationStatement) {
                    // Variable declaration → MAP operation
                    VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
                    List<VariableDeclarationFragment> fragments = varDecl.fragments();
                    if (!fragments.isEmpty()) {
                        VariableDeclarationFragment frag = fragments.get(0);
                        if (frag.getInitializer() != null) {
                            String newVarName = frag.getName().getIdentifier();
                            ProspectiveOperation mapOp = new ProspectiveOperation(
                                frag.getInitializer(),
                                ProspectiveOperation.OperationType.MAP,
                                newVarName);
                            ops.add(mapOp);
                            
                            // Update current var name for subsequent operations
                            currentVarName = newVarName;
                        }
                    }
                } else if (stmt instanceof IfStatement && !isLast) {
                    // IF statement (not the last statement) → potential FILTER or nested processing
                    IfStatement ifStmt = (IfStatement) stmt;
                    
                    // Check if this is a filtering IF (simple condition with block body)
                    if (ifStmt.getElseStatement() == null) {
                        Statement thenStmt = ifStmt.getThenStatement();
                        
                        // Check if this is an "if (condition) continue;" pattern
                        if (isIfWithContinue(ifStmt)) {
                            // Convert "if (condition) continue;" to ".filter(x -> !(condition))"
                            Expression negatedCondition = createNegatedExpression(ast, ifStmt.getExpression());
                            ProspectiveOperation filterOp = new ProspectiveOperation(
                                negatedCondition,
                                ProspectiveOperation.OperationType.FILTER);
                            ops.add(filterOp);
                            // Don't process the body since it's just a continue statement
                        } else {
                            // Regular filter with nested processing
                            // Add FILTER operation for the condition
                            ProspectiveOperation filterOp = new ProspectiveOperation(
                                ifStmt.getExpression(),
                                ProspectiveOperation.OperationType.FILTER);
                            ops.add(filterOp);
                            
                            // Process the body of the IF statement recursively
                            List<ProspectiveOperation> nestedOps = parseLoopBody(thenStmt, currentVarName);
                            ops.addAll(nestedOps);
                            
                            // Update current var name if the nested operations produced a new variable
                            if (!nestedOps.isEmpty()) {
                                ProspectiveOperation lastNested = nestedOps.get(nestedOps.size() - 1);
                                if (lastNested.getProducedVariableName() != null) {
                                    currentVarName = lastNested.getProducedVariableName();
                                }
                            }
                        }
                    }
                } else if (isLast) {
                    // Last statement → FOREACH
                    ProspectiveOperation forEachOp = new ProspectiveOperation(
                        stmt,
                        ProspectiveOperation.OperationType.FOREACH,
                        currentVarName);
                    ops.add(forEachOp);
                }
            }
        } else if (body instanceof IfStatement) {
            // Single IF statement → process as filter with nested body
            IfStatement ifStmt = (IfStatement) body;
            if (ifStmt.getElseStatement() == null) {
                // Add FILTER operation
                ProspectiveOperation filterOp = new ProspectiveOperation(
                    ifStmt.getExpression(),
                    ProspectiveOperation.OperationType.FILTER);
                ops.add(filterOp);
                
                // Process the then statement
                List<ProspectiveOperation> nestedOps = parseLoopBody(ifStmt.getThenStatement(), currentVarName);
                ops.addAll(nestedOps);
            }
        } else {
            // Single statement → FOREACH
            ProspectiveOperation forEachOp = new ProspectiveOperation(
                body,
                ProspectiveOperation.OperationType.FOREACH,
                currentVarName);
            ops.add(forEachOp);
        }
        
        return ops;
    }
    
    /**
     * Determines the variable name to use for the current operation in a chain of stream operations.
     * 
     * <p>This method inspects the list of {@link ProspectiveOperation}s up to {@code currentIndex - 1}
     * to find if a previous MAP operation exists. If so, it returns the produced variable name
     * from that MAP operation. Otherwise, it returns the loop variable name.
     * </p>
     *
     * @param operations   the list of prospective operations representing the loop body transformation
     * @param currentIndex the index of the current operation in the list; operations before this index are considered
     * @param loopVarName  the original loop variable name
     * @return the variable name produced by the most recent MAP operation, or the loop variable name if none found
     */
    private String getVariableNameFromPreviousOp(List<ProspectiveOperation> operations, int currentIndex, String loopVarName) {
        // Look back to find the most recent operation that produces a variable
        for (int i = currentIndex - 1; i >= 0; i--) {
            ProspectiveOperation op = operations.get(i);
            if (op.getProducedVariableName() != null) {
                return op.getProducedVariableName();
            }
        }
        return loopVarName;
    }
    
    /**
     * Determines whether the stream pipeline requires an explicit .stream() prefix.
     * 
     * <p>Returns false if the pipeline consists of a single FOREACH operation,
     * which can be called directly on the collection. Returns true for all other cases,
     * including multiple operations or non-FOREACH terminal operations.
     * 
     * @return true if .stream() is required, false if direct collection method can be used
     */
    private boolean requiresStreamPrefix() {
        if (operations.isEmpty()) {
            return true;
        }
        return operations.size() > 1 || 
               operations.get(0).getOperationType() != ProspectiveOperation.OperationType.FOREACH;
    }
    
    /**
     * Checks if an IF statement contains a continue statement.
     * This pattern should be converted to a negated filter.
     * 
     * @param ifStatement the IF statement to check
     * @return true if the then branch contains a continue statement
     */
    private boolean isIfWithContinue(IfStatement ifStatement) {
        Statement thenStatement = ifStatement.getThenStatement();
        if (thenStatement instanceof ContinueStatement) {
            return true;
        }
        if (thenStatement instanceof Block) {
            Block block = (Block) thenStatement;
            if (block.statements().size() == 1 && block.statements().get(0) instanceof ContinueStatement) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Creates a negated expression for filter operations.
     * Used when converting "if (condition) continue;" to ".filter(x -> !(condition))".
     * 
     * @param ast the AST to create nodes in
     * @param condition the condition to negate
     * @return a negated expression
     */
    private Expression createNegatedExpression(AST ast, Expression condition) {
        PrefixExpression negation = ast.newPrefixExpression();
        negation.setOperator(PrefixExpression.Operator.NOT);
        negation.setOperand((Expression) ASTNode.copySubtree(ast, condition));
        return negation;
    }
}
