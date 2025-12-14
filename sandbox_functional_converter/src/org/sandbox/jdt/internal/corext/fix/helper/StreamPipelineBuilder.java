/*******************************************************************************
 * Copyright (c) 2021 Alexandru Gyori and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Alexandru Gyori original code
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType;

/**
 * Helper class to build stream pipelines from for-each loops.
 * Based on NetBeans mapreduce hints implementation.
 */
public class StreamPipelineBuilder {
    private final EnhancedForStatement forLoop;
    private final AST ast;
    private final PreconditionsChecker preconditions;
    private List<ProspectiveOperation> operations;
    private final String loopVarName;

    public StreamPipelineBuilder(EnhancedForStatement forLoop, PreconditionsChecker preconditions) {
        this.forLoop = forLoop;
        this.ast = forLoop.getAST();
        this.preconditions = preconditions;
        this.loopVarName = forLoop.getParameter().getName().getIdentifier();
        this.operations = new ArrayList<>();
    }

    /**
     * Analyzes the loop and builds a list of stream operations.
     * @return true if the loop can be converted to streams
     */
    public boolean analyze() {
        if (!preconditions.isSafeToRefactor() || !preconditions.iteratesOverIterable()) {
            return false;
        }

        operations = getListRepresentation(forLoop.getBody(), true);
        if (operations == null || operations.isEmpty()) {
            return false;
        }

        // Mark the last operation as eager (terminal)
        operations.get(operations.size() - 1).setEager(true);

        // Merge compatible operations
        operations = ProspectiveOperation.mergeRecursivelyIntoComposableOperations(operations);

        return !operations.isEmpty();
    }

    /**
     * Builds the complete stream pipeline as a method invocation.
     */
    public MethodInvocation buildPipeline() {
        if (operations == null || operations.isEmpty()) {
            return null;
        }

        Expression expr = (Expression) ASTNode.copySubtree(ast, forLoop.getExpression());
        
        // Check if we can use simple forEach (no intermediate operations)
        if (operations.size() == 1 && operations.get(0).getOperationType() == OperationType.FOREACH) {
            MethodInvocation forEach = ast.newMethodInvocation();
            forEach.setExpression(expr);
            forEach.setName(ast.newSimpleName("forEach"));
            forEach.arguments().add(operations.get(0).createLambda(ast, loopVarName));
            return forEach;
        }

        // Build stream() call first
        MethodInvocation current = ast.newMethodInvocation();
        current.setExpression(expr);
        current.setName(ast.newSimpleName("stream"));

        // Chain all operations
        for (ProspectiveOperation op : operations) {
            MethodInvocation next = ast.newMethodInvocation();
            next.setExpression(current);
            next.setName(ast.newSimpleName(op.getStreamMethod()));
            
            // Add arguments (lambdas, initial values, etc.)
            List<Expression> args = op.getStreamArguments(ast, loopVarName);
            for (Expression arg : args) {
                next.arguments().add(arg);
            }
            
            current = next;
        }

        return current;
    }

    /**
     * Wraps the stream pipeline with any necessary side effects or control flow.
     */
    public Statement wrapPipeline(MethodInvocation pipeline) {
        if (pipeline == null) {
            return null;
        }

        ProspectiveOperation lastOp = operations.get(operations.size() - 1);
        
        // Handle anyMatch/noneMatch - need to wrap in if statement
        if (lastOp.getOperationType() == OperationType.ANYMATCH) {
            // if (stream.anyMatch(...)) { return true; }
            IfStatement ifStmt = ast.newIfStatement();
            ifStmt.setExpression(pipeline);
            ReturnStatement returnTrue = ast.newReturnStatement();
            BooleanLiteral trueLit = ast.newBooleanLiteral(true);
            returnTrue.setExpression(trueLit);
            ifStmt.setThenStatement(returnTrue);
            return ifStmt;
        } else if (lastOp.getOperationType() == OperationType.NONEMATCH) {
            // if (!stream.noneMatch(...)) { return false; }
            PrefixExpression notExpr = ast.newPrefixExpression();
            notExpr.setOperator(PrefixExpression.Operator.NOT);
            notExpr.setOperand(pipeline);
            
            IfStatement ifStmt = ast.newIfStatement();
            ifStmt.setExpression(notExpr);
            ReturnStatement returnFalse = ast.newReturnStatement();
            BooleanLiteral falseLit = ast.newBooleanLiteral(false);
            returnFalse.setExpression(falseLit);
            ifStmt.setThenStatement(returnFalse);
            return ifStmt;
        } else if (lastOp.getOperationType() == OperationType.REDUCE) {
            // variable = stream.reduce(...)
            Assignment assignment = ast.newAssignment();
            assignment.setLeftHandSide((Expression) ASTNode.copySubtree(ast, lastOp.getReducingVariable()));
            assignment.setOperator(Assignment.Operator.ASSIGN);
            assignment.setRightHandSide(pipeline);
            return ast.newExpressionStatement(assignment);
        } else {
            // Simple expression statement
            return ast.newExpressionStatement(pipeline);
        }
    }

    /**
     * Analyzes a statement and converts it to a list of stream operations.
     * Based on NetBeans Refactorer.getListRepresentation().
     */
    private List<ProspectiveOperation> getListRepresentation(Statement stmt, boolean isLast) {
        List<ProspectiveOperation> ops = new ArrayList<>();

        if (stmt instanceof Block) {
            ops.addAll(getBlockRepresentation((Block) stmt, isLast));
        } else if (stmt instanceof IfStatement) {
            ops.addAll(getIfRepresentation((IfStatement) stmt, isLast));
        } else {
            ops.addAll(getSingleStatementRepresentation(stmt));
        }

        return ops;
    }

    private List<ProspectiveOperation> getBlockRepresentation(Block block, boolean isLast) {
        List<ProspectiveOperation> ops = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Statement> statements = block.statements();

        for (int i = 0; i < statements.size(); i++) {
            Statement stmt = statements.get(i);
            boolean last = isLast && (i == statements.size() - 1);

            if (stmt instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) stmt;
                
                // Check for "if (...) continue;" pattern - convert to filter
                if (isIfWithContinue(ifStmt)) {
                    // Negate the condition and wrap remaining statements
                    List<Statement> remaining = statements.subList(i + 1, statements.size());
                    ProspectiveOperation filterOp = createFilterOperation(ifStmt, true); // negated
                    ops.add(filterOp);
                    
                    // Process remaining statements
                    for (Statement remainingStmt : remaining) {
                        ops.addAll(getListRepresentation(remainingStmt, isLast));
                    }
                    break; // All statements processed
                } else if (last) {
                    ops.addAll(getListRepresentation(ifStmt, true));
                } else if (isReturningIf(ifStmt)) {
                    // Early return in middle of loop - can't handle cleanly for now
                    //  untransformable = true;
                    ops.addAll(createMapOperation(ifStmt));
                } else {
                    // If statement as side effect - wrap in map
                    ops.addAll(createMapOperation(ifStmt));
                }
            } else {
                ops.addAll(getListRepresentation(stmt, last));
            }
        }

        return ops;
    }

    private List<ProspectiveOperation> getIfRepresentation(IfStatement ifStmt, boolean isLast) {
        List<ProspectiveOperation> ops = new ArrayList<>();

        // Only process if-without-else for now
        if (ifStmt.getElseStatement() == null) {
            Statement thenStmt = ifStmt.getThenStatement();
            
            // Unwrap single-statement blocks
            if (isOneStatementBlock(thenStmt)) {
                thenStmt = (Statement) ((Block) thenStmt).statements().get(0);
            }

            // Check for early return patterns
            if (thenStmt instanceof ReturnStatement) {
                ReturnStatement returnStmt = (ReturnStatement) thenStmt;
                Expression returnExpr = returnStmt.getExpression();

                if (returnExpr instanceof BooleanLiteral) {
                    boolean returnValue = ((BooleanLiteral) returnExpr).booleanValue();
                    if (returnValue) {
                        // return true -> anyMatch
                        ops.add(createAnyMatchOperation(ifStmt));
                    } else {
                        // return false -> noneMatch  
                        ops.add(createNoneMatchOperation(ifStmt));
                    }
                    return ops;
                }
            }

            // Regular filtering if
            ops.add(createFilterOperation(ifStmt, false));
            ops.addAll(getListRepresentation(thenStmt, isLast));
        } else {
            // if-else as side effect - wrap in map
            ops.addAll(createMapOperation(ifStmt));
        }

        return ops;
    }

    private List<ProspectiveOperation> getSingleStatementRepresentation(Statement stmt) {
        List<ProspectiveOperation> ops = new ArrayList<>();

        // Check if it's a reducer (accumulator pattern)
        if (preconditions.isReducer() && preconditions.getReducer() != null && 
            preconditions.getReducer().equals(stmt)) {
            ops.add(createReduceOperation(stmt));
        } else if (stmt instanceof ExpressionStatement) {
            Expression expr = ((ExpressionStatement) stmt).getExpression();
            
            // Check for increment/decrement or compound assignment
            if (isReducerExpression(expr)) {
                ops.add(createReduceOperation(stmt));
            } else {
                ops.add(createMapOperation(stmt));
            }
        } else if (stmt instanceof VariableDeclarationStatement) {
            // Variable declaration - map operation
            ops.add(createMapOperation(stmt));
        } else {
            // Default: treat as map/forEach
            ops.add(createMapOrForEachOperation(stmt));
        }

        return ops;
    }

    // Helper methods

    private boolean isOneStatementBlock(Statement stmt) {
        return stmt instanceof Block && ((Block) stmt).statements().size() == 1;
    }

    private boolean isIfWithContinue(IfStatement ifStmt) {
        Statement thenStmt = ifStmt.getThenStatement();
        if (thenStmt instanceof ContinueStatement) {
            return true;
        }
        if (isOneStatementBlock(thenStmt)) {
            return ((Block) thenStmt).statements().get(0) instanceof ContinueStatement;
        }
        return false;
    }

    private boolean isReturningIf(IfStatement ifStmt) {
        Statement thenStmt = ifStmt.getThenStatement();
        if (thenStmt instanceof ReturnStatement) {
            return true;
        }
        if (isOneStatementBlock(thenStmt)) {
            return ((Block) thenStmt).statements().get(0) instanceof ReturnStatement;
        }
        return false;
    }

    private boolean isReducerExpression(Expression expr) {
        if (expr instanceof PostfixExpression) {
            PostfixExpression postfix = (PostfixExpression) expr;
            return postfix.getOperator() == PostfixExpression.Operator.INCREMENT ||
                   postfix.getOperator() == PostfixExpression.Operator.DECREMENT;
        }
        if (expr instanceof PrefixExpression) {
            PrefixExpression prefix = (PrefixExpression) expr;
            return prefix.getOperator() == PrefixExpression.Operator.INCREMENT ||
                   prefix.getOperator() == PrefixExpression.Operator.DECREMENT;
        }
        if (expr instanceof Assignment) {
            Assignment assignment = (Assignment) expr;
            return TreeUtilities.isCompoundAssignment(assignment.getOperator());
        }
        return false;
    }

    // Operation factory methods

    private ProspectiveOperation createFilterOperation(IfStatement ifStmt, boolean negate) {
        Expression condition = ifStmt.getExpression();
        return new ProspectiveOperation(condition, OperationType.FILTER, negate);
    }

    private ProspectiveOperation createAnyMatchOperation(IfStatement ifStmt) {
        Expression condition = ifStmt.getExpression();
        return new ProspectiveOperation(condition, OperationType.ANYMATCH, false);
    }

    private ProspectiveOperation createNoneMatchOperation(IfStatement ifStmt) {
        Expression condition = ifStmt.getExpression();
        return new ProspectiveOperation(condition, OperationType.NONEMATCH, false);
    }

    private ProspectiveOperation createReduceOperation(Statement stmt) {
        Expression expr = null;
        if (stmt instanceof ExpressionStatement) {
            expr = ((ExpressionStatement) stmt).getExpression();
        }
        return new ProspectiveOperation(expr, OperationType.REDUCE, false);
    }

    private List<ProspectiveOperation> createMapOperation(Statement stmt) {
        return Collections.singletonList(new ProspectiveOperation(stmt, OperationType.MAP, false));
    }

    private List<ProspectiveOperation> createMapOperation(IfStatement ifStmt) {
        return Collections.singletonList(new ProspectiveOperation(ifStmt, OperationType.MAP, false));
    }

    private ProspectiveOperation createMapOrForEachOperation(Statement stmt) {
        // Simple heuristic: if it looks like a side effect, use forEach, otherwise map
        return new ProspectiveOperation(stmt, OperationType.FOREACH, false);
    }
}
