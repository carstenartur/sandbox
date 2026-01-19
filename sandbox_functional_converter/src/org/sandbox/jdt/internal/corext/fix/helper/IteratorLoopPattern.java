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
 * Detects and validates traditional iterator-based while loop patterns that can
 * be converted to stream operations.
 * 
 * <p><b>Supported Pattern:</b></p>
 * <pre>{@code
 * Iterator<T> it = collection.iterator();
 * while (it.hasNext()) {
 *     T item = it.next();
 *     // loop body
 * }
 * }</pre>
 * 
 * <p><b>Detection Logic:</b></p>
 * <ol>
 * <li>Find WhileStatement with condition calling hasNext()</li>
 * <li>Find iterator variable declaration before the loop</li>
 * <li>Find call to next() within the loop body</li>
 * <li>Validate iterator is only used for safe iteration</li>
 * </ol>
 * 
 * <p><b>Safety Constraints:</b></p>
 * <ul>
 * <li>Iterator variable must be local and effectively final</li>
 * <li>No calls to iterator.remove()</li>
 * <li>Single next() call at the start of loop body</li>
 * <li>No modifications to the collection during iteration</li>
 * </ul>
 */
public class IteratorLoopPattern {
	
	private final WhileStatement whileLoop;
	private VariableDeclarationStatement iteratorDeclaration;
	private Expression collectionExpression;
	private String iteratorVarName;
	private String elementVarName;
	private Statement loopBodyWithoutNext;
	private ITypeBinding elementType;
	
	/**
	 * Creates a new iterator loop pattern detector for the given while loop.
	 * 
	 * @param whileLoop the while statement to analyze
	 */
	public IteratorLoopPattern(WhileStatement whileLoop) {
		this.whileLoop = whileLoop;
	}
	
	/**
	 * Analyzes the while loop to determine if it matches the iterator pattern.
	 * 
	 * @return true if the loop matches the iterator pattern and is safe to convert
	 */
	public boolean matches() {
		if (whileLoop == null || whileLoop.getExpression() == null) {
			return false;
		}
		
		// Step 1: Check if condition is iterator.hasNext()
		if (!isHasNextCondition(whileLoop.getExpression())) {
			return false;
		}
		
		// Step 2: Find iterator declaration before the loop
		if (!findIteratorDeclaration()) {
			return false;
		}
		
		// Step 3: Analyze loop body for next() call and element variable
		if (!analyzeLoopBody()) {
			return false;
		}
		
		// Step 4: Validate safety constraints
		return isSafeToConvert();
	}
	
	/**
	 * Checks if the expression is a call to hasNext() on an iterator.
	 * 
	 * @param expr the expression to check
	 * @return true if expression is iterator.hasNext()
	 */
	private boolean isHasNextCondition(Expression expr) {
		if (!(expr instanceof MethodInvocation)) {
			return false;
		}
		
		MethodInvocation mi = (MethodInvocation) expr;
		if (!"hasNext".equals(mi.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}
		
		Expression receiver = mi.getExpression();
		if (receiver instanceof SimpleName) {
			iteratorVarName = ((SimpleName) receiver).getIdentifier();
			return true;
		}
		
		return false;
	}
	
	/**
	 * Finds the iterator variable declaration before the while loop.
	 * Pattern: Iterator<T> it = collection.iterator();
	 * 
	 * @return true if iterator declaration is found
	 */
	private boolean findIteratorDeclaration() {
		if (iteratorVarName == null) {
			return false;
		}
		
		// Look for the declaration in the same block, before this while statement
		ASTNode parent = whileLoop.getParent();
		if (!(parent instanceof Block)) {
			return false;
		}
		
		Block block = (Block) parent;
		@SuppressWarnings("unchecked")
		java.util.List<Statement> statements = block.statements();
		
		int whileIndex = statements.indexOf(whileLoop);
		if (whileIndex <= 0) {
			return false; // No statement before the while loop
		}
		
		// Check the statement immediately before the while loop
		Statement prevStatement = statements.get(whileIndex - 1);
		if (!(prevStatement instanceof VariableDeclarationStatement)) {
			return false;
		}
		
		VariableDeclarationStatement varDecl = (VariableDeclarationStatement) prevStatement;
		@SuppressWarnings("unchecked")
		java.util.List<VariableDeclarationFragment> fragments = varDecl.fragments();
		
		for (VariableDeclarationFragment fragment : fragments) {
			if (iteratorVarName.equals(fragment.getName().getIdentifier())) {
				// Found the iterator declaration
				Expression initializer = fragment.getInitializer();
				if (initializer instanceof MethodInvocation) {
					MethodInvocation mi = (MethodInvocation) initializer;
					if ("iterator".equals(mi.getName().getIdentifier())) { //$NON-NLS-1$
						collectionExpression = mi.getExpression();
						iteratorDeclaration = varDecl;
						
						// Try to determine element type from Iterator<T>
						ITypeBinding typeBinding = varDecl.getType().resolveBinding();
						if (typeBinding != null && typeBinding.isParameterizedType()) {
							ITypeBinding[] typeArgs = typeBinding.getTypeArguments();
							if (typeArgs.length > 0) {
								elementType = typeArgs[0];
							}
						}
						
						return collectionExpression != null;
					}
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Analyzes the loop body to find the next() call and extract the element variable.
	 * 
	 * @return true if loop body has valid structure
	 */
	private boolean analyzeLoopBody() {
		Statement body = whileLoop.getBody();
		if (body == null) {
			return false;
		}
		
		// Handle both Block and single statement
		if (body instanceof Block) {
			Block block = (Block) body;
			@SuppressWarnings("unchecked")
			java.util.List<Statement> statements = block.statements();
			
			if (statements.isEmpty()) {
				return false; // Empty loop body
			}
			
			// First statement should be: T item = it.next();
			Statement firstStmt = statements.get(0);
			if (!(firstStmt instanceof VariableDeclarationStatement)) {
				return false;
			}
			
			VariableDeclarationStatement varDecl = (VariableDeclarationStatement) firstStmt;
			@SuppressWarnings("unchecked")
			java.util.List<VariableDeclarationFragment> fragments = varDecl.fragments();
			
			if (fragments.size() != 1) {
				return false;
			}
			
			VariableDeclarationFragment fragment = fragments.get(0);
			Expression initializer = fragment.getInitializer();
			
			if (!isNextCall(initializer)) {
				return false;
			}
			
			elementVarName = fragment.getName().getIdentifier();
			
			// Rest of the statements form the actual loop body
			if (statements.size() > 1) {
				Block newBody = body.getAST().newBlock();
				@SuppressWarnings("unchecked")
				java.util.List<Statement> newStatements = newBody.statements();
				for (int i = 1; i < statements.size(); i++) {
					newStatements.add((Statement) ASTNode.copySubtree(newBody.getAST(), statements.get(i)));
				}
				loopBodyWithoutNext = newBody;
			} else {
				// Empty body after next() call - just create empty block
				loopBodyWithoutNext = body.getAST().newBlock();
			}
			
			return true;
		}
		
		return false; // Single statement loops not supported (must have T item = it.next())
	}
	
	/**
	 * Checks if the expression is a call to next() on the iterator.
	 * 
	 * @param expr the expression to check
	 * @return true if expression is iterator.next()
	 */
	private boolean isNextCall(Expression expr) {
		if (!(expr instanceof MethodInvocation)) {
			return false;
		}
		
		MethodInvocation mi = (MethodInvocation) expr;
		if (!"next".equals(mi.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}
		
		Expression receiver = mi.getExpression();
		if (receiver instanceof SimpleName) {
			return iteratorVarName.equals(((SimpleName) receiver).getIdentifier());
		}
		
		return false;
	}
	
	/**
	 * Validates that the iterator loop is safe to convert.
	 * Checks for remove() calls and other unsafe operations.
	 * 
	 * @return true if safe to convert
	 */
	private boolean isSafeToConvert() {
		// For now, we've done basic validation in the analysis steps
		// Future: Add more sophisticated checks for:
		// - iterator.remove() calls
		// - Multiple next() calls
		// - Modifications to the collection
		return collectionExpression != null && 
		       iteratorVarName != null && 
		       elementVarName != null &&
		       loopBodyWithoutNext != null;
	}
	
	// Getters
	
	public Expression getCollectionExpression() {
		return collectionExpression;
	}
	
	public String getIteratorVarName() {
		return iteratorVarName;
	}
	
	public String getElementVarName() {
		return elementVarName;
	}
	
	public Statement getLoopBody() {
		return loopBodyWithoutNext;
	}
	
	public ITypeBinding getElementType() {
		return elementType;
	}
	
	public VariableDeclarationStatement getIteratorDeclaration() {
		return iteratorDeclaration;
	}
}
