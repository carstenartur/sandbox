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

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.*;

/**
 * Parses iterator loop body to extract element variable and actual body.
 * 
 * <p>The typical pattern is:</p>
 * <pre>
 * while (it.hasNext()) {
 *     T item = it.next();  // Element extraction (first statement)
 *     // ... actual body ... // Remaining statements
 * }
 * </pre>
 */
public class IteratorLoopBodyParser {
    
    /**
     * Immutable result of parsing an iterator loop body.
     *
     * @param elementVariableName the element variable name (e.g. {@code "item"})
     * @param elementType the element type string (e.g. {@code "String"})
     * @param actualBodyStatements the body statements after the {@code it.next()} declaration
     */
    public record ParsedBody(String elementVariableName, String elementType,
                             List<Statement> actualBodyStatements) {
    }
    
    /**
     * Parses the loop body to extract element variable and actual body statements.
     * 
     * @param loopBody the loop body to parse
     * @param iteratorVarName the name of the iterator variable
     * @return parsed body or null if pattern doesn't match
     */
    public ParsedBody parse(Statement loopBody, String iteratorVarName) {
        if (!(loopBody instanceof Block block)) {
            return null;
        }
        
        List<?> statements = block.statements();
        
        if (statements.isEmpty()) {
            return null;
        }
        
        // First statement should be: T item = it.next();
        Statement firstStmt = (Statement) statements.get(0);
        
        if (!(firstStmt instanceof VariableDeclarationStatement varDecl)) {
            return null;
        }
        if (varDecl.fragments().size() != 1) {
            return null;
        }
        
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) varDecl.fragments().get(0);
        String elementVarName = fragment.getName().getIdentifier();
        
        // Check initializer: it.next()
        Expression initializer = fragment.getInitializer();
        if (!isNextCall(initializer, iteratorVarName)) {
            return null;
        }
        
        // Extract element type
        String elementType = varDecl.getType().toString();
        
        // Remaining statements are the actual body
        List<Statement> actualBody = new ArrayList<>();
        for (int i = 1; i < statements.size(); i++) {
            actualBody.add((Statement) statements.get(i));
        }
        
        return new ParsedBody(elementVarName, elementType, actualBody);
    }
    
    /**
     * Checks if an expression is it.next() call.
     */
    private boolean isNextCall(Expression expr, String iteratorVarName) {
        if (!(expr instanceof MethodInvocation methodInv)) {
            return false;
        }
        
        if (!"next".equals(methodInv.getName().getIdentifier())) {
            return false;
        }
        
        Expression iteratorExpr = methodInv.getExpression();
        if (!(iteratorExpr instanceof SimpleName iteratorName)) {
            return false;
        }
        
        return iteratorName.getIdentifier().equals(iteratorVarName);
    }
    
    /**
     * Creates a synthetic block containing the actual body statements.
     * This is useful for AST transformations.
     */
    public Block createSyntheticBlock(AST ast, ParsedBody parsedBody) {
        Block syntheticBlock = ast.newBlock();
        
        for (Statement stmt : parsedBody.actualBodyStatements()) {
            syntheticBlock.statements().add(ASTNode.copySubtree(ast, stmt));
        }
        
        return syntheticBlock;
    }
}
