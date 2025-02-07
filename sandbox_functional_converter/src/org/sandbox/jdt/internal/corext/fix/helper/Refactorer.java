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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
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

    /** (1) Prüft, ob die Schleife in eine Stream-Operation umgewandelt werden kann. */
    public boolean isRefactorable() {
        return preconditions.isSafeToRefactor() && preconditions.iteratesOverIterable();
    }

    /** (2) Führt die Refaktorisierung der Schleife in eine Stream-Operation durch. */
    public void refactor() {
        if (!isRefactorable()) {
            return;
        }

        // Erzeugt den Stream-Call aus der Collection
        MethodInvocation streamCall = createStreamCall();
        LambdaExpression mapLambda = createMapLambdaExpression();
        LambdaExpression forEachLambda = createForEachLambdaExpression();

        MethodInvocation mapCall = ast.newMethodInvocation();
        mapCall.setExpression(streamCall);
        mapCall.setName(ast.newSimpleName("map"));
        mapCall.arguments().add(mapLambda);

        MethodInvocation forEachOrderedCall = ast.newMethodInvocation();
        forEachOrderedCall.setExpression(mapCall);
        forEachOrderedCall.setName(ast.newSimpleName("forEachOrdered"));
        forEachOrderedCall.arguments().add(forEachLambda);

        rewrite.replace(forLoop, forEachOrderedCall, null);
    }

    /** (3) Erstellt den Stream-Aufruf für die Collection der Schleife. */
    private MethodInvocation createStreamCall() {
        MethodInvocation streamCall = ast.newMethodInvocation();
        streamCall.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
        streamCall.setName(ast.newSimpleName("stream"));
        return streamCall;
    }

    /** (4) Erstellt eine Lambda-Expression für die `map`-Operation. */
    private LambdaExpression createMapLambdaExpression() {
        LambdaExpression lambda = ast.newLambdaExpression();
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName((SimpleName) ASTNode.copySubtree(ast, forLoop.getParameter().getName()));
        lambda.parameters().add(param);

        MethodInvocation toStringCall = ast.newMethodInvocation();
        toStringCall.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getParameter().getName()));
        toStringCall.setName(ast.newSimpleName("toString"));
        lambda.setBody(toStringCall);

        return lambda;
    }

    /** (5) Erstellt eine Lambda-Expression für die `forEachOrdered`-Operation. */
    private LambdaExpression createForEachLambdaExpression() {
        LambdaExpression lambda = ast.newLambdaExpression();
        SingleVariableDeclaration param = ast.newSingleVariableDeclaration();
        param.setName(ast.newSimpleName("s"));
        lambda.parameters().add(param);
        lambda.setBody(ASTNode.copySubtree(ast, forLoop.getBody()));
        return lambda;
    }
}
