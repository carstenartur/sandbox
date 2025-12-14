/*******************************************************************************
c * Copyright (c) 2021 Alexandru Gyori and others.
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


import java.util.Set;

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

import java.util.*;

/**
 * Analyzes a loop statement to check various preconditions for safe refactoring
 * to stream operations. Uses AstProcessorBuilder for cleaner AST traversal.
 */
public class PreconditionsChecker {
    private final Statement loop;
//    private final CompilationUnit compilationUnit;
    private final Set<VariableDeclarationFragment> innerVariables = new HashSet<>();
    private boolean containsBreak = false;
    private boolean containsContinue = false;
    private boolean containsReturn = false;
    private boolean throwsException = false;
    private boolean containsNEFs = false;
    private boolean iteratesOverIterable = false;
    private boolean hasReducer = false;
    private Statement reducerStatement = null;

    public PreconditionsChecker(Statement loop, CompilationUnit compilationUnit) {
        this.loop = loop;
//        this.compilationUnit = compilationUnit;
        analyzeLoop();
    }

    /** (1) Prüft, ob die Schleife sicher in eine Stream-Operation umgewandelt werden kann. */
    public boolean isSafeToRefactor() {
        return !throwsException && !containsBreak && !containsContinue && !containsReturn && !containsNEFs;
    }

    /** (2) Überprüft, ob die Schleife eine Exception wirft. */
    public boolean throwsException() {
        return throwsException;
    }

    /** (3) Prüft, ob die Schleife Variablen enthält, die nicht effektiv final sind. */
    public boolean containsNEFs() {
        return containsNEFs;
    }

    /** (4) Prüft, ob die Schleife `break` enthält. */
    public boolean containsBreak() {
        return containsBreak;
    }

    /** (5) Prüft, ob die Schleife `continue` enthält. */
    public boolean containsContinue() {
        return containsContinue;
    }

    /** (6) Prüft, ob die Schleife `return` enthält. */
    public boolean containsReturn() {
        return containsReturn;
    }

    /** (7) Gibt die innerhalb der Schleife definierten Variablen zurück. */
    public Set<VariableDeclarationFragment> getInnerVariables() {
        return innerVariables;
    }

    /** (8) Überprüft, ob die Schleife über eine Iterable-Struktur iteriert. */
    public boolean iteratesOverIterable() {
        return iteratesOverIterable;
    }
    
    /**
     * Checks if the loop contains a reducer pattern.
     * Required by TODO section 2.
     * 
     * Scans loop body for accumulator patterns:
     * - i++, i--, ++i, --i
     * - sum += x, product *= x, count -= 1
     * - Other compound assignments (|=, &=, etc.)
     */
    public boolean isReducer() {
        return hasReducer;
    }
    
    /**
     * Returns the statement containing the reducer pattern.
     * Required by TODO section 2.
     */
    public Statement getReducer() {
        return reducerStatement;
    }

    /** 
     * Methode zur Analyse der Schleife und Identifikation relevanter Elemente.
     * Uses AstProcessorBuilder for cleaner and more maintainable AST traversal.
     */
    private void analyzeLoop() {
        AstProcessorBuilder.with(new ReferenceHolder<String, Object>())
            .onVariableDeclarationFragment((node, h) -> {
                innerVariables.add(node);
                return true;
            })
            .onBreakStatement((node, h) -> {
                containsBreak = true;
                return true;
            })
            .onContinueStatement((node, h) -> {
                containsContinue = true;
                return true;
            })
            .onReturnStatement((node, h) -> {
                containsReturn = true;
                return true;
            })
            .onThrowStatement((node, h) -> {
                throwsException = true;
                return true;
            })
            .onEnhancedForStatement((node, h) -> {
                iteratesOverIterable = true;
                return true;
            })
            .onPostfixExpression((node, h) -> {
                // Detect i++, i--
                if (node.getOperator() == PostfixExpression.Operator.INCREMENT ||
                    node.getOperator() == PostfixExpression.Operator.DECREMENT) {
                    hasReducer = true;
                    if (reducerStatement == null) {
                        reducerStatement = findEnclosingStatement(node);
                    }
                }
                return true;
            })
            .onPrefixExpression((node, h) -> {
                // Detect ++i, --i
                if (node.getOperator() == PrefixExpression.Operator.INCREMENT ||
                    node.getOperator() == PrefixExpression.Operator.DECREMENT) {
                    hasReducer = true;
                    if (reducerStatement == null) {
                        reducerStatement = findEnclosingStatement(node);
                    }
                }
                return true;
            })
            .onAssignment((node, h) -> {
                // Detect compound assignments: +=, -=, *=, /=, |=, &=, etc.
                if (node.getOperator() != Assignment.Operator.ASSIGN) {
                    hasReducer = true;
                    if (reducerStatement == null) {
                        reducerStatement = findEnclosingStatement(node);
                    }
                }
                return true;
            })
            .build(loop);
        
        analyzeEffectivelyFinalVariables();
    }
    
    /** Prüft, ob Variablen innerhalb der Schleife effektiv final sind. */
    private void analyzeEffectivelyFinalVariables() {
        for (VariableDeclarationFragment var : innerVariables) {
            if (!isEffectivelyFinal(var)) {
                containsNEFs = true;
                break;
            }
        }
    }
    
    /** Hilfsmethode zur Prüfung, ob eine Variable effektiv final ist. */
    private boolean isEffectivelyFinal(VariableDeclarationFragment var) {
        final boolean[] modified = {false};
        ASTNode methodBody = getEnclosingMethodBody(var);
        if (methodBody != null) {
            String varName = var.getName().getIdentifier();
            
            AstProcessorBuilder.with(new ReferenceHolder<String, Object>())
                .onAssignment((node, h) -> {
                    if (node.getLeftHandSide() instanceof SimpleName name) {
                        if (name.getIdentifier().equals(varName)) {
                            modified[0] = true;
                        }
                    }
                    return true;
                })
                .build(methodBody);
        }
        return !modified[0];
    }
    
    /** Hilfsmethode: Findet den umgebenden Methodenrumpf einer Variablendeklaration. */
    private ASTNode getEnclosingMethodBody(ASTNode node) {
        MethodDeclaration method = ASTNodes.getFirstAncestorOrNull(node, MethodDeclaration.class);
        return (method != null) ? method.getBody() : null;
    }
    
    /** Hilfsmethode: Findet das umgebende Statement für einen AST-Knoten. */
    private Statement findEnclosingStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof Statement)) {
            current = current.getParent();
        }
        return (Statement) current;
    }
}