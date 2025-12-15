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
    private boolean isAnyMatchPattern = false;
    private boolean isNoneMatchPattern = false;
    private IfStatement earlyReturnIf = null;

    public PreconditionsChecker(Statement loop, CompilationUnit compilationUnit) {
        this.loop = loop;
//        this.compilationUnit = compilationUnit;
        analyzeLoop();
    }

    /** (1) Prüft, ob die Schleife sicher in eine Stream-Operation umgewandelt werden kann. */
    public boolean isSafeToRefactor() {
        // Allow early returns if they match anyMatch/noneMatch patterns
        boolean allowedReturn = containsReturn && (isAnyMatchPattern || isNoneMatchPattern);
        return !throwsException && !containsBreak && !containsContinue && (!containsReturn || allowedReturn) && !containsNEFs;
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
     * 
     * <p>Scans loop body for accumulator patterns such as:
     * <ul>
     * <li>i++, i--, ++i, --i</li>
     * <li>sum += x, product *= x, count -= 1</li>
     * <li>Other compound assignments (|=, &=, etc.)</li>
     * </ul>
     * 
     * @return true if a reducer pattern is detected, false otherwise
     * 
     * @see #getReducer()
     */
    public boolean isReducer() {
        return hasReducer;
    }
    
    /**
     * Returns the statement containing the reducer pattern.
     * 
     * <p>If multiple reducers exist in the loop, this returns only the first one encountered.</p>
     * 
     * @return the statement containing the reducer, or null if no reducer was detected
     * 
     * @see #isReducer()
     */
    public Statement getReducer() {
        return reducerStatement;
    }
    
    /**
     * Checks if the loop matches the anyMatch pattern.
     * 
     * <p>AnyMatch pattern: loop contains {@code if (condition) return true;}</p>
     * 
     * @return true if anyMatch pattern is detected
     */
    public boolean isAnyMatchPattern() {
        return isAnyMatchPattern;
    }
    
    /**
     * Checks if the loop matches the noneMatch pattern.
     * 
     * <p>NoneMatch pattern: loop contains {@code if (condition) return false;}</p>
     * 
     * @return true if noneMatch pattern is detected
     */
    public boolean isNoneMatchPattern() {
        return isNoneMatchPattern;
    }
    
    /**
     * Returns the IF statement containing the early return for anyMatch/noneMatch patterns.
     * 
     * @return the IF statement with early return, or null if no pattern detected
     */
    public IfStatement getEarlyReturnIf() {
        return earlyReturnIf;
    }

    /** 
     * Methode zur Analyse der Schleife und Identifikation relevanter Elemente.
     * Uses AstProcessorBuilder for cleaner and more maintainable AST traversal.
     */
    private void analyzeLoop() {
        AstProcessorBuilder<String, Object> builder = AstProcessorBuilder.with(new ReferenceHolder<String, Object>());
        
        builder.onVariableDeclarationFragment((node, h) -> {
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
            .onAssignment((node, h) -> {
                // Detect compound assignments: +=, -=, *=, /=, |=, &=, etc.
                if (node.getOperator() != Assignment.Operator.ASSIGN) {
                    hasReducer = true;
                    if (reducerStatement == null) {
                        reducerStatement = ASTNodes.getFirstAncestorOrNull(node, Statement.class);
                    }
                }
                return true;
            });
        
        // Use processor() to access PostfixExpression and PrefixExpression visitors
        builder.processor()
            .callPostfixExpressionVisitor((node, h) -> {
                PostfixExpression postfix = (PostfixExpression) node;
                // Detect i++, i--
                if (postfix.getOperator() == PostfixExpression.Operator.INCREMENT ||
                    postfix.getOperator() == PostfixExpression.Operator.DECREMENT) {
                    hasReducer = true;
                    if (reducerStatement == null) {
                        reducerStatement = ASTNodes.getFirstAncestorOrNull(node, Statement.class);
                    }
                }
                return true;
            })
            .callPrefixExpressionVisitor((node, h) -> {
                PrefixExpression prefix = (PrefixExpression) node;
                // Detect ++i, --i
                if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT ||
                    prefix.getOperator() == PrefixExpression.Operator.DECREMENT) {
                    hasReducer = true;
                    if (reducerStatement == null) {
                        reducerStatement = ASTNodes.getFirstAncestorOrNull(node, Statement.class);
                    }
                }
                return true;
            });
        
        builder.build(loop);
        
        // Detect anyMatch/noneMatch patterns
        detectEarlyReturnPatterns();
        
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
    
    /**
     * Detects anyMatch and noneMatch patterns in the loop.
     * 
     * <p>Patterns:
     * <ul>
     * <li>AnyMatch: {@code if (condition) return true;}</li>
     * <li>NoneMatch: {@code if (condition) return false;}</li>
     * </ul>
     * 
     * <p>These patterns must be the only statement with a return in the loop body.
     */
    private void detectEarlyReturnPatterns() {
        if (!containsReturn || !(loop instanceof EnhancedForStatement)) {
            return;
        }
        
        EnhancedForStatement forLoop = (EnhancedForStatement) loop;
        Statement body = forLoop.getBody();
        
        // Find all IF statements with return statements in the loop
        final List<IfStatement> ifStatementsWithReturn = new ArrayList<>();
        
        // Use ASTVisitor to find IF statements
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(IfStatement node) {
                if (hasReturnInThenBranch(node)) {
                    ifStatementsWithReturn.add(node);
                }
                return true;
            }
        });
        
        // For anyMatch/noneMatch, we expect exactly one IF with return
        if (ifStatementsWithReturn.size() != 1) {
            return;
        }
        
        IfStatement ifStmt = ifStatementsWithReturn.get(0);
        
        // Check if the IF returns a boolean literal
        BooleanLiteral returnValue = getReturnValueFromIf(ifStmt);
        if (returnValue == null) {
            return;
        }
        
        // Determine pattern based on return value
        if (returnValue.booleanValue()) {
            // if (condition) return true; → anyMatch
            isAnyMatchPattern = true;
            earlyReturnIf = ifStmt;
        } else {
            // if (condition) return false; → noneMatch  
            isNoneMatchPattern = true;
            earlyReturnIf = ifStmt;
        }
    }
    
    /**
     * Checks if the IF statement has a return in its then branch.
     */
    private boolean hasReturnInThenBranch(IfStatement ifStmt) {
        Statement thenStmt = ifStmt.getThenStatement();
        
        // Direct return statement
        if (thenStmt instanceof ReturnStatement) {
            return true;
        }
        
        // Block with single return statement
        if (thenStmt instanceof Block) {
            Block block = (Block) thenStmt;
            if (block.statements().size() == 1 && block.statements().get(0) instanceof ReturnStatement) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extracts the boolean literal value from a return statement in an IF.
     * 
     * @return the BooleanLiteral if the IF returns a boolean literal, null otherwise
     */
    private BooleanLiteral getReturnValueFromIf(IfStatement ifStmt) {
        Statement thenStmt = ifStmt.getThenStatement();
        ReturnStatement returnStmt = null;
        
        if (thenStmt instanceof ReturnStatement) {
            returnStmt = (ReturnStatement) thenStmt;
        } else if (thenStmt instanceof Block) {
            Block block = (Block) thenStmt;
            if (block.statements().size() == 1 && block.statements().get(0) instanceof ReturnStatement) {
                returnStmt = (ReturnStatement) block.statements().get(0);
            }
        }
        
        if (returnStmt != null && returnStmt.getExpression() instanceof BooleanLiteral) {
            return (BooleanLiteral) returnStmt.getExpression();
        }
        
        return null;
    }
}