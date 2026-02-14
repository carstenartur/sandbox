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

import org.eclipse.jdt.core.dom.*;

/**
 * Detects iterator patterns in while and for loops.
 * 
 * <p>This detector recognizes the following patterns:</p>
 * <ul>
 *   <li>while-iterator: {@code Iterator<T> it = collection.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</li>
 *   <li>for-loop-iterator: {@code for (Iterator<T> it = collection.iterator(); it.hasNext(); ) { T item = it.next(); ... }}</li>
 * </ul>
 */
public class IteratorPatternDetector {
    
    /**
     * Immutable result of iterator pattern detection.
     *
     * @param collectionExpression the expression representing the iterated collection
     * @param iteratorVariableName the name of the iterator variable
     * @param elementType the element type (e.g. {@code "String"})
     * @param loopBody the loop body statement
     * @param isForLoop {@code true} for for-loop-iterator, {@code false} for while-iterator
     */
    public record IteratorPattern(Expression collectionExpression, String iteratorVariableName,
                                  String elementType, Statement loopBody, boolean isForLoop) {
    }
    
    /**
     * Attempts to detect while-iterator pattern.
     * 
     * Pattern:
     * <pre>
     * Iterator<T> it = collection.iterator();
     * while (it.hasNext()) {
     *     T item = it.next();
     *     // body
     * }
     * </pre>
     */
    public IteratorPattern detectWhilePattern(WhileStatement whileStmt, Statement previousStatement) {
        // Check condition: it.hasNext()
        Expression condition = whileStmt.getExpression();
        String iteratorVar = extractIteratorFromHasNext(condition);
        if (iteratorVar == null) {
            return null;
        }
        
        // Check previous statement: Iterator<T> it = collection.iterator();
        if (!(previousStatement instanceof VariableDeclarationStatement varDecl)) {
            return null;
        }
        if (varDecl.fragments().size() != 1) {
            return null;
        }
        
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDecl.fragments().get(0);
        if (!fragment.getName().getIdentifier().equals(iteratorVar)) {
            return null;
        }
        
        // Check initializer: collection.iterator()
        Expression initializer = fragment.getInitializer();
        Expression collectionExpr = extractCollectionFromIteratorCall(initializer);
        if (collectionExpr == null) {
            return null;
        }
        
        // Extract element type from Iterator<T>
        String elementType = extractElementType(varDecl.getType());
        
        return new IteratorPattern(collectionExpr, iteratorVar, elementType, 
                                    whileStmt.getBody(), false);
    }
    
    /**
     * Attempts to detect for-loop-iterator pattern.
     * 
     * Pattern:
     * <pre>
     * for (Iterator<T> it = collection.iterator(); it.hasNext(); ) {
     *     T item = it.next();
     *     // body
     * }
     * </pre>
     */
    public IteratorPattern detectForLoopPattern(ForStatement forStmt) {
        // Check initializers: Iterator<T> it = collection.iterator();
        if (forStmt.initializers().size() != 1) {
            return null;
        }
        
        Object init = forStmt.initializers().get(0);
        if (!(init instanceof VariableDeclarationExpression varDeclExpr)) {
            return null;
        }
        if (varDeclExpr.fragments().size() != 1) {
            return null;
        }
        
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDeclExpr.fragments().get(0);
        String iteratorVar = fragment.getName().getIdentifier();
        
        // Check initializer: collection.iterator()
        Expression initializer = fragment.getInitializer();
        Expression collectionExpr = extractCollectionFromIteratorCall(initializer);
        if (collectionExpr == null) {
            return null;
        }
        
        // Check condition: it.hasNext()
        Expression condition = forStmt.getExpression();
        String conditionIteratorVar = extractIteratorFromHasNext(condition);
        if (conditionIteratorVar == null || !conditionIteratorVar.equals(iteratorVar)) {
            return null;
        }
        
        // Updaters should be empty (no updaters in this pattern)
        if (!forStmt.updaters().isEmpty()) {
            return null;
        }
        
        // Extract element type from Iterator<T>
        String elementType = extractElementType(varDeclExpr.getType());
        
        return new IteratorPattern(collectionExpr, iteratorVar, elementType, 
                                    forStmt.getBody(), true);
    }
    
    /**
     * Extracts iterator variable from it.hasNext() expression.
     * 
     * @return iterator variable name or null if pattern doesn't match
     */
    private String extractIteratorFromHasNext(Expression expr) {
        if (!(expr instanceof MethodInvocation methodInv)) {
            return null;
        }
        
        if (!"hasNext".equals(methodInv.getName().getIdentifier())) {
            return null;
        }
        
        Expression iteratorExpr = methodInv.getExpression();
        if (!(iteratorExpr instanceof SimpleName iteratorName)) {
            return null;
        }
        
        return iteratorName.getIdentifier();
    }
    
    /**
     * Extracts collection expression from collection.iterator() call.
     * 
     * @return collection expression or null if pattern doesn't match
     */
    private Expression extractCollectionFromIteratorCall(Expression expr) {
        if (!(expr instanceof MethodInvocation methodInv)) {
            return null;
        }
        
        if (!"iterator".equals(methodInv.getName().getIdentifier())) {
            return null;
        }
        
        // Arguments should be empty
        if (!methodInv.arguments().isEmpty()) {
            return null;
        }
        
        return methodInv.getExpression();
    }
    
    /**
     * Extracts element type from Iterator<T> type.
     * 
     * @return element type as string or "Object" if not determinable
     */
    private String extractElementType(Type type) {
        if (!(type instanceof ParameterizedType paramType)) {
            return "Object";
        }
        
        if (paramType.typeArguments().isEmpty()) {
            return "Object";
        }
        
        Type typeArg = (Type) paramType.typeArguments().get(0);
        return typeArg.toString();
    }
    
    /**
     * Finds the previous statement before a given statement in a block.
     * 
     * @param block the block containing the statements
     * @param statement the statement to find the predecessor of
     * @return the previous statement or null if not found or first statement
     */
    public static Statement findPreviousStatement(Block block, Statement statement) {
        if (block == null) {
            return null;
        }
        
        Statement previous = null;
        for (Object obj : block.statements()) {
            Statement stmt = (Statement) obj;
            if (stmt == statement) {
                return previous;
            }
            previous = stmt;
        }
        
        return null;
    }
    
    /**
     * Checks if a statement is part of a block (required for while-iterator pattern detection).
     */
    public static boolean isStatementInBlock(Statement statement) {
        ASTNode parent = statement.getParent();
        return parent instanceof Block;
    }
}
