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
            String paramName = loopVarName; // Start with the loop variable name
            for (int i = 0; i < operations.size(); i++) {
                ProspectiveOperation op = operations.get(i);
                MethodInvocation next = ast.newMethodInvocation();
                next.setExpression(pipeline);
                next.setName(ast.newSimpleName(op.getSuitableMethod()));

                // Use the current paramName for this operation
                List<Expression> args = op.getArguments(ast, paramName);
                for (Expression arg : args) {
                    next.arguments().add(arg);
                }
                pipeline = next;

                // Update paramName for the next operation, if any
                if (i + 1 < operations.size()) {
                    paramName = getVariableNameFromPreviousOp(operations, i + 1);
                }
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
     * stream operations such as {@code map} (for variable declarations with initializers)
     * and {@code forEach} (for the final or sole statement). For block bodies, it processes
     * each statement in order, treating variable declarations with initializers as {@code map}
     * operations and the last statement as a {@code forEach} operation. For single-statement
     * bodies, it treats the statement as a {@code forEach} operation.
     *
     * @param body the {@link Statement} representing the loop body; may be a {@link Block} or a single statement
     * @param loopVarName the name of the loop variable currently in scope; may be updated if a map operation is found
     * @return a list of {@link ProspectiveOperation} objects, in the order they should be applied,
     *         representing the sequence of stream operations inferred from the loop body
     * @see ProspectiveOperation
     */
    private List<ProspectiveOperation> parseLoopBody(Statement body, String loopVarName) {
        List<ProspectiveOperation> operations = new ArrayList<>();
        
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
                            ProspectiveOperation mapOp = new ProspectiveOperation(
                                frag.getInitializer(),
                                ProspectiveOperation.OperationType.MAP);
                            operations.add(mapOp);
                            
                            // Update loop var name for subsequent operations
                            loopVarName = frag.getName().getIdentifier();
                        }
                    }
                } else if (isLast) {
                    // Last statement → FOREACH
                    ProspectiveOperation forEachOp = new ProspectiveOperation(
                        stmt,
                        ProspectiveOperation.OperationType.FOREACH,
                        loopVarName);
                    operations.add(forEachOp);
                }
            }
        } else {
            // Single statement → FOREACH
            ProspectiveOperation forEachOp = new ProspectiveOperation(
                body,
                ProspectiveOperation.OperationType.FOREACH,
                loopVarName);
            operations.add(forEachOp);
        }
        
        return operations;
    }
    
    /**
     * Determines the variable name to use for the current operation in a chain of stream operations.
     * <p>
     * This method inspects the list of {@link ProspectiveOperation}s up to {@code currentIndex - 1}
     * to find if a previous MAP operation exists. If so, it returns a default variable name ("s")
     * to represent the result of the MAP operation. Otherwise, it returns "item" as the default variable name.
     * </p>
     *
     * @param operations   the list of prospective operations representing the loop body transformation
     * @param currentIndex the index of the current operation in the list; operations before this index are considered
     * @return "s" if a previous MAP operation is found (currently always "s" as a placeholder), otherwise "item"
     * @implNote
     *   <b>Limitation:</b> Currently, this method always returns "s" when a previous MAP operation is found,
     *   rather than extracting the actual variable name introduced by the MAP. This should be improved
     *   in the future to reflect the real variable name used in the stream chain.
     */
    private String getVariableNameFromPreviousOp(List<ProspectiveOperation> operations, int currentIndex) {
        if (currentIndex > 0) {
            // Look back to find a MAP operation that defines a variable
            for (int i = currentIndex - 1; i >= 0; i--) {
                ProspectiveOperation op = operations.get(i);
                if (op.getOperationType() == ProspectiveOperation.OperationType.MAP) {
                    // Try to extract variable name - for now return a default
                    return "s"; // This should be improved to extract actual variable name
                }
            }
        }
        return "item";
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
  
}
