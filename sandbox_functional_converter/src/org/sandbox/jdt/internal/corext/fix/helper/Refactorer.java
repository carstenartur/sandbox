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

        boolean hasReturn = false;
        boolean isReduction = false;
        List<Statement> operations = getListRepresentation(forLoop.getBody(), true);
        for (Statement operation : operations) {
            if (operation instanceof IfStatement) {
                IfStatement ifStatement = (IfStatement) operation;
                if (isReturningIf(ifStatement)) {
                    hasReturn = true;
                }
            } else if (operation instanceof ExpressionStatement) {
                Expression expr = ((ExpressionStatement) operation).getExpression();
                if (expr instanceof Assignment) {
                    isReduction = true;
                }
            }
        }

        if (isReduction) {
            LambdaExpression reduceLambda = createReduceLambdaExpression();
            MethodInvocation reduceCall = ast.newMethodInvocation();
            reduceCall.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            reduceCall.setName(ast.newSimpleName("reduce"));
            reduceCall.arguments().add(ast.newSimpleName("0"));
            reduceCall.arguments().add(reduceLambda);
            
            rewrite.replace(forLoop, reduceCall, null);
        } else if (hasReturn) {
            LambdaExpression mapLambda = createMapLambdaExpression();
            MethodInvocation mapCall = ast.newMethodInvocation();
            mapCall.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            mapCall.setName(ast.newSimpleName("stream"));
            mapCall.arguments().add(mapLambda);
            
            rewrite.replace(forLoop, mapCall, null);
        } else {
            LambdaExpression forEachLambda = createForEachLambdaExpression();
            MethodInvocation forEachCall = ast.newMethodInvocation();
            forEachCall.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
            forEachCall.setName(ast.newSimpleName("forEach"));
            forEachCall.arguments().add(forEachLambda);
            
            rewrite.replace(forLoop, forEachCall, null);
        }
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
        
        // Korrekte Deklaration des Lambda-Parameters
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName((SimpleName) ASTNode.copySubtree(ast, forLoop.getParameter().getName()));
        lambda.parameters().add(param);
        
        Statement body = forLoop.getBody();
        if (body instanceof ExpressionStatement) {
            lambda.setBody(ASTNode.copySubtree(ast, ((ExpressionStatement) body).getExpression()));
        } else {
            Block block = ast.newBlock();
            block.statements().add(ASTNode.copySubtree(ast, body));
            lambda.setBody(block);
        }
        return lambda;
    }
  
}
