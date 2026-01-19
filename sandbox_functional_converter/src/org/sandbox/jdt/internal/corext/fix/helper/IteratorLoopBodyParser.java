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
 * Parses the body of an iterator loop to extract the element variable and loop body.
 * 
 * <p>Expected pattern in loop body:</p>
 * <pre>{@code
 * T item = iterator.next();
 * // rest of loop body
 * }</pre>
 * 
 * @see IteratorPatternDetector
 * @see IteratorLoopAnalyzer
 */
public class IteratorLoopBodyParser {
	
	private final IteratorPatternDetector.IteratorPattern pattern;
	private final String iteratorVarName;
	
	private String elementVarName;
	private ITypeBinding elementType;
	private Statement bodyWithoutNext;
	
	/**
	 * Creates a new parser for the given iterator pattern.
	 * 
	 * @param pattern the detected iterator pattern
	 */
	public IteratorLoopBodyParser(IteratorPatternDetector.IteratorPattern pattern) {
		this.pattern = pattern;
		this.iteratorVarName = pattern.getIteratorVarName();
	}
	
	/**
	 * Parses the loop body to extract element variable and actual body.
	 * 
	 * @return true if parsing was successful
	 */
	public boolean parse() {
		Statement body = pattern.getLoopBody();
		if (body == null) {
			return false;
		}
		
		// Handle both Block and single statement
		if (!(body instanceof Block)) {
			return false; // Single statement loops not supported
		}
		
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
		
		// Try to get element type
		ITypeBinding typeBinding = varDecl.getType().resolveBinding();
		if (typeBinding != null) {
			elementType = typeBinding;
		}
		
		// Rest of the statements form the actual loop body
		if (statements.size() > 1) {
			Block newBody = body.getAST().newBlock();
			@SuppressWarnings("unchecked")
			java.util.List<Statement> newStatements = newBody.statements();
			for (int i = 1; i < statements.size(); i++) {
				newStatements.add((Statement) ASTNode.copySubtree(newBody.getAST(), statements.get(i)));
			}
			bodyWithoutNext = newBody;
		} else {
			// Empty body after next() call
			bodyWithoutNext = body.getAST().newBlock();
		}
		
		return true;
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
	 * Returns the element variable name extracted from the loop.
	 * 
	 * @return element variable name
	 */
	public String getElementVarName() {
		return elementVarName;
	}
	
	/**
	 * Returns the type binding of the element variable.
	 * 
	 * @return element type binding or null
	 */
	public ITypeBinding getElementType() {
		return elementType;
	}
	
	/**
	 * Returns the loop body without the next() call.
	 * 
	 * @return loop body statement
	 */
	public Statement getBodyWithoutNext() {
		return bodyWithoutNext;
	}
}
