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
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class Refactorer {
    private final EnhancedForStatement forLoop;
    private final ASTRewrite rewrite;
    private final PreconditionsChecker preconditions;
    private final AST ast;

    public Refactorer(EnhancedForStatement forLoop, ASTRewrite rewrite, PreconditionsChecker preconditions) {
        this.forLoop = forLoop;
        this.rewrite = rewrite;
        this.preconditions = preconditions;
        this.ast = forLoop.getAST();
    }

    /** (1) Prüft, ob ein gegebenes Statement ein Block mit genau einer Anweisung ist. */
    private boolean isOneStatementBlock(Statement statement) {
        return (statement instanceof Block) && ((Block) statement).statements().size() == 1;
    }

    /** (2) Prüft, ob ein `IfStatement` eine `return`-Anweisung enthält. */
    private boolean isReturningIf(IfStatement ifStatement) {
        Statement thenStatement = ifStatement.getThenStatement();
        if (thenStatement instanceof ReturnStatement) {
            return true;
        }
        if (isOneStatementBlock(thenStatement)) {
            return ((Block) thenStatement).statements().get(0) instanceof ReturnStatement;
        }
        return false;
    }

    /** (3) Prüft, ob die Schleife in eine Stream-Operation umgewandelt werden kann. */
    public boolean isRefactorable() {
        return preconditions.isSafeToRefactor() && preconditions.iteratesOverIterable();
    }

    /** (4) Zerlegt eine Schleife in eine Liste von Stream-Operationen. */
    private List<Statement> getListRepresentation(Statement statement, boolean last) {
        List<Statement> operations = new ArrayList<>();
        if (statement instanceof Block) {
            operations.addAll(((Block) statement).statements());
        } else {
            operations.add(statement);
        }
        return operations;
    }

    /** (5) Prüft, ob ein `IfStatement` eine `continue`-Anweisung enthält. */
    private boolean isIfWithContinue(IfStatement ifStatement) {
        Statement thenStatement = ifStatement.getThenStatement();
        if (thenStatement instanceof ContinueStatement) {
            return true;
        }
        if (isOneStatementBlock(thenStatement)) {
            return ((Block) thenStatement).statements().get(0) instanceof ContinueStatement;
        }
        return false;
    }

    /** (6) Konvertiert `IfStatement` mit `continue` zu Stream-Operationen. */
    private void refactorContinuingIf(IfStatement ifStatement, List<Statement> newStatements) {
        if (isIfWithContinue(ifStatement)) {
            newStatements.add(ifStatement.getThenStatement());
        }
    }

    /** (6) Erstellt eine Lambda-Expression für die `reduce()`-Operation. */
    private LambdaExpression createReduceLambdaExpression() {
        LambdaExpression lambda = ast.newLambdaExpression();
        
        SingleVariableDeclaration acc = ast.newSingleVariableDeclaration();
        acc.setName(ast.newSimpleName("acc"));
        lambda.parameters().add(acc);
        
        SingleVariableDeclaration item = ast.newSingleVariableDeclaration();
        item.setName(ast.newSimpleName("item"));
        lambda.parameters().add(item);
        
        InfixExpression sumExpression = ast.newInfixExpression();
        sumExpression.setLeftOperand(ast.newSimpleName("acc"));
        sumExpression.setRightOperand(ast.newSimpleName("item"));
        sumExpression.setOperator(InfixExpression.Operator.PLUS);
        
        lambda.setBody(sumExpression);
        return lambda;
    }

    /** (7) Führt die Refaktorisierung der Schleife in eine Stream-Operation durch. */
    public void refactor() {
        // Option 1: Use StreamPipelineBuilder (recommended for new code)
        if (useStreamPipelineBuilder()) {
            refactorWithBuilder();
            return;
        }
        
        // Option 2: Legacy inline implementation
        if (!isRefactorable()) {
            return;
        }

        Statement loopBody = forLoop.getBody();
        String loopVarName = forLoop.getParameter().getName().getIdentifier();
        
        // Parse loop body into operations
        List<ProspectiveOperation> operations = parseLoopBody(loopBody, loopVarName);
        
        if (operations.isEmpty()) {
            // Fallback to simple forEach
            LambdaExpression forEachLambda = createForEachLambdaExpression();
            MethodInvocation forEachCall = ast.newMethodInvocation();
            forEachCall.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            forEachCall.setName(ast.newSimpleName("forEach"));
            forEachCall.arguments().add(forEachLambda);
            
            ExpressionStatement exprStmt = ast.newExpressionStatement(forEachCall);
            rewrite.replace(forLoop, exprStmt, null);
            return;
        }
        
        // Check if we need .stream() or can use direct .forEach()
        boolean needsStream = operations.size() > 1 || 
                              operations.get(0).getOperationType() != ProspectiveOperation.OperationType.FOREACH;
        
        // Build the stream pipeline
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
                String paramName = getVariableNameFromPreviousOp(operations, i, loopVarName);
                
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
            List<Expression> args = op.getArguments(ast, loopVarName);
            for (Expression arg : args) {
                pipeline.arguments().add(arg);
            }
        }
        
        ExpressionStatement exprStmt = ast.newExpressionStatement(pipeline);
        rewrite.replace(forLoop, exprStmt, null);
    }
    
    /**
     * Analyzes the body of an enhanced for-loop and extracts a list of {@link ProspectiveOperation}
     * objects representing the operations that can be mapped to stream operations.
     * <p>
     * This method inspects the statements within the loop body to identify possible
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
        List<ProspectiveOperation> operations = new ArrayList<>();
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
                            operations.add(mapOp);
                            
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
                        
                        // Add FILTER operation for the condition
                        ProspectiveOperation filterOp = new ProspectiveOperation(
                            ifStmt.getExpression(),
                            ProspectiveOperation.OperationType.FILTER);
                        operations.add(filterOp);
                        
                        // Process the body of the IF statement recursively
                        List<ProspectiveOperation> nestedOps = parseLoopBody(thenStmt, currentVarName);
                        operations.addAll(nestedOps);
                        
                        // Update current var name if the nested operations produced a new variable
                        if (!nestedOps.isEmpty()) {
                            ProspectiveOperation lastNested = nestedOps.get(nestedOps.size() - 1);
                            if (lastNested.getProducedVariableName() != null) {
                                currentVarName = lastNested.getProducedVariableName();
                            }
                        }
                    }
                } else if (isLast) {
                    // Last statement → FOREACH
                    ProspectiveOperation forEachOp = new ProspectiveOperation(
                        stmt,
                        ProspectiveOperation.OperationType.FOREACH,
                        currentVarName);
                    operations.add(forEachOp);
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
                operations.add(filterOp);
                
                // Process the then statement
                List<ProspectiveOperation> nestedOps = parseLoopBody(ifStmt.getThenStatement(), currentVarName);
                operations.addAll(nestedOps);
            }
        } else {
            // Single statement → FOREACH
            ProspectiveOperation forEachOp = new ProspectiveOperation(
                body,
                ProspectiveOperation.OperationType.FOREACH,
                currentVarName);
            operations.add(forEachOp);
        }
        
        return operations;
    }
    
    /**
     * Determines the variable name to use for the current operation in a chain of stream operations.
     * <p>
     * This method inspects the list of {@link ProspectiveOperation}s up to {@code currentIndex - 1}
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
    /** (7) Erstellt eine Lambda-Expression für die `map()`-Operation. */
    private LambdaExpression createMapLambdaExpression() {
        LambdaExpression lambda = ast.newLambdaExpression();
        
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName((SimpleName) ASTNode.copySubtree(ast, forLoop.getParameter().getName()));
        lambda.parameters().add(param);
        
        ReturnStatement returnStmt = ast.newReturnStatement();
        returnStmt.setExpression((Expression) ASTNode.copySubtree(ast, param.getName()));
        
        Block block = ast.newBlock();
        block.statements().add(returnStmt);
        
        lambda.setBody(block);
        return lambda;
    }

    private LambdaExpression createForEachLambdaExpression() {
        LambdaExpression lambda = ast.newLambdaExpression();
        
        // Use VariableDeclarationFragment for the parameter (simpler form without type)
        org.eclipse.jdt.core.dom.VariableDeclarationFragment paramFragment = ast.newVariableDeclarationFragment();
        paramFragment.setName((SimpleName) ASTNode.copySubtree(ast, forLoop.getParameter().getName()));
        lambda.parameters().add(paramFragment);
        
        Statement body = forLoop.getBody();
        if (body instanceof ExpressionStatement) {
            // Single expression - use expression as lambda body
            lambda.setBody(ASTNode.copySubtree(ast, ((ExpressionStatement) body).getExpression()));
        } else if (body instanceof Block) {
            // Block body - copy the whole block
            lambda.setBody(ASTNode.copySubtree(ast, body));
        } else {
            // Other statement type - wrap in block
            Block block = ast.newBlock();
            block.statements().add(ASTNode.copySubtree(ast, body));
            lambda.setBody(block);
        }
        return lambda;
    }
    
    /**
     * Determines whether to use the StreamPipelineBuilder for refactoring.
     * Returns true to enable the builder-based approach.
     */
    /**
     * Determines whether to use the StreamPipelineBuilder for refactoring.
     * Returns true by default. To enable the legacy implementation for testing or fallback,
     * set the system property "org.sandbox.jdt.useLegacyLoopRefactor" to "true".
     */
    private boolean useStreamPipelineBuilder() {
        // If the system property is set to true, use the legacy implementation.
        return !Boolean.getBoolean("org.sandbox.jdt.useLegacyLoopRefactor");
    }
    
    /**
     * Refactors the loop using the StreamPipelineBuilder approach.
     * This is the recommended method for converting loops to streams.
     */
    private void refactorWithBuilder() {
        StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);
        
        if (!builder.analyze()) {
            return; // Cannot convert
        }
        
        MethodInvocation pipeline = builder.buildPipeline();
        if (pipeline == null) {
            return; // Failed to build pipeline
        }
        
        Statement replacement = builder.wrapPipeline(pipeline);
        if (replacement != null) {
            rewrite.replace(forLoop, replacement, null);
        }
    }
  
}
