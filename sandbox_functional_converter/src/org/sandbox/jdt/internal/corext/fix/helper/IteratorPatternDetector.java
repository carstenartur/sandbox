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
 * Detects traditional iterator patterns in Java code.
 * 
 * <p>Supports two patterns:</p>
 * <ol>
 * <li><b>While-Iterator Pattern:</b>
 * <pre>{@code
 * Iterator<T> it = collection.iterator();
 * while (it.hasNext()) {
 *     T item = it.next();
 *     // loop body
 * }
 * }</pre>
 * </li>
 * <li><b>For-Loop-Iterator Pattern:</b>
 * <pre>{@code
 * for (Iterator<T> it = collection.iterator(); it.hasNext(); ) {
 *     T item = it.next();
 *     // loop body
 * }
 * }</pre>
 * </li>
 * </ol>
 * 
 * @see IteratorLoopAnalyzer
 * @see IteratorLoopBodyParser
 */
public class IteratorPatternDetector {
	
	/**
	 * Represents a detected iterator pattern with all its components.
	 */
	public static class IteratorPattern {
		public enum PatternType {
			WHILE_LOOP,
			FOR_LOOP
		}
		
		private final PatternType type;
		private final Statement loop;
		private final String iteratorVarName;
		private final Expression collectionExpression;
		private final Statement loopBody;
		private final VariableDeclarationStatement externalIteratorDecl; // Only for WHILE_LOOP
		
		public IteratorPattern(PatternType type, Statement loop, String iteratorVarName,
				Expression collectionExpression, Statement loopBody,
				VariableDeclarationStatement externalIteratorDecl) {
			this.type = type;
			this.loop = loop;
			this.iteratorVarName = iteratorVarName;
			this.collectionExpression = collectionExpression;
			this.loopBody = loopBody;
			this.externalIteratorDecl = externalIteratorDecl;
		}
		
		public PatternType getType() {
			return type;
		}
		
		public Statement getLoop() {
			return loop;
		}
		
		public String getIteratorVarName() {
			return iteratorVarName;
		}
		
		public Expression getCollectionExpression() {
			return collectionExpression;
		}
		
		public Statement getLoopBody() {
			return loopBody;
		}
		
		public VariableDeclarationStatement getExternalIteratorDecl() {
			return externalIteratorDecl;
		}
		
		public boolean hasExternalDeclaration() {
			return externalIteratorDecl != null;
		}
	}
	
	/**
	 * Detects if a WhileStatement matches the iterator pattern.
	 * 
	 * @param whileLoop the while statement to check
	 * @return detected pattern or null if no match
	 */
	public static IteratorPattern detectWhileIteratorPattern(WhileStatement whileLoop) {
		if (whileLoop == null || whileLoop.getExpression() == null) {
			return null;
		}
		
		// Check if condition is iterator.hasNext()
		String iteratorVarName = extractIteratorVarFromHasNext(whileLoop.getExpression());
		if (iteratorVarName == null) {
			return null;
		}
		
		// Find iterator declaration before the loop
		VariableDeclarationStatement iteratorDecl = findIteratorDeclarationBefore(whileLoop, iteratorVarName);
		if (iteratorDecl == null) {
			return null;
		}
		
		// Extract collection expression from declaration
		Expression collectionExpr = extractCollectionFromDeclaration(iteratorDecl, iteratorVarName);
		if (collectionExpr == null) {
			return null;
		}
		
		return new IteratorPattern(IteratorPattern.PatternType.WHILE_LOOP, whileLoop,
				iteratorVarName, collectionExpr, whileLoop.getBody(), iteratorDecl);
	}
	
	/**
	 * Detects if a ForStatement matches the iterator pattern.
	 * 
	 * <p>Pattern: {@code for (Iterator<T> it = coll.iterator(); it.hasNext(); ) { ... }}</p>
	 * 
	 * @param forLoop the for statement to check
	 * @return detected pattern or null if no match
	 */
	public static IteratorPattern detectForLoopIteratorPattern(ForStatement forLoop) {
		if (forLoop == null) {
			return null;
		}
		
		// Check initializers - should have exactly one: Iterator<T> it = collection.iterator()
		@SuppressWarnings("unchecked")
		java.util.List<Expression> initializers = forLoop.initializers();
		if (initializers.size() != 1) {
			return null;
		}
		
		Expression init = initializers.get(0);
		if (!(init instanceof VariableDeclarationExpression)) {
			return null;
		}
		
		VariableDeclarationExpression varDeclExpr = (VariableDeclarationExpression) init;
		@SuppressWarnings("unchecked")
		java.util.List<VariableDeclarationFragment> fragments = varDeclExpr.fragments();
		if (fragments.size() != 1) {
			return null;
		}
		
		VariableDeclarationFragment fragment = fragments.get(0);
		String iteratorVarName = fragment.getName().getIdentifier();
		
		// Check initializer is collection.iterator()
		Expression initializer = fragment.getInitializer();
		if (!isIteratorCall(initializer)) {
			return null;
		}
		
		MethodInvocation iteratorCall = (MethodInvocation) initializer;
		Expression collectionExpr = iteratorCall.getExpression();
		if (collectionExpr == null) {
			return null;
		}
		
		// Check condition is it.hasNext()
		Expression condition = forLoop.getExpression();
		if (condition == null) {
			return null;
		}
		
		String conditionIteratorVar = extractIteratorVarFromHasNext(condition);
		if (!iteratorVarName.equals(conditionIteratorVar)) {
			return null;
		}
		
		// Check there are no updaters (empty third part of for loop)
		@SuppressWarnings("unchecked")
		java.util.List<Expression> updaters = forLoop.updaters();
		if (!updaters.isEmpty()) {
			return null;
		}
		
		return new IteratorPattern(IteratorPattern.PatternType.FOR_LOOP, forLoop,
				iteratorVarName, collectionExpr, forLoop.getBody(), null);
	}
	
	/**
	 * Extracts the iterator variable name from a hasNext() method call.
	 * 
	 * @param expr the expression to check
	 * @return iterator variable name or null
	 */
	private static String extractIteratorVarFromHasNext(Expression expr) {
		if (!(expr instanceof MethodInvocation)) {
			return null;
		}
		
		MethodInvocation mi = (MethodInvocation) expr;
		if (!"hasNext".equals(mi.getName().getIdentifier())) { //$NON-NLS-1$
			return null;
		}
		
		Expression receiver = mi.getExpression();
		if (receiver instanceof SimpleName) {
			return ((SimpleName) receiver).getIdentifier();
		}
		
		return null;
	}
	
	/**
	 * Finds the iterator variable declaration before a while statement.
	 * 
	 * @param whileLoop the while statement
	 * @param iteratorVarName the iterator variable name to find
	 * @return the declaration statement or null
	 */
	private static VariableDeclarationStatement findIteratorDeclarationBefore(
			WhileStatement whileLoop, String iteratorVarName) {
		ASTNode parent = whileLoop.getParent();
		if (!(parent instanceof Block)) {
			return null;
		}
		
		Block block = (Block) parent;
		@SuppressWarnings("unchecked")
		java.util.List<Statement> statements = block.statements();
		
		int whileIndex = statements.indexOf(whileLoop);
		if (whileIndex <= 0) {
			return null;
		}
		
		// Check the statement immediately before
		Statement prevStatement = statements.get(whileIndex - 1);
		if (!(prevStatement instanceof VariableDeclarationStatement)) {
			return null;
		}
		
		VariableDeclarationStatement varDecl = (VariableDeclarationStatement) prevStatement;
		@SuppressWarnings("unchecked")
		java.util.List<VariableDeclarationFragment> fragments = varDecl.fragments();
		
		for (VariableDeclarationFragment fragment : fragments) {
			if (iteratorVarName.equals(fragment.getName().getIdentifier())) {
				// Found the declaration
				Expression initializer = fragment.getInitializer();
				if (isIteratorCall(initializer)) {
					return varDecl;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Checks if an expression is a call to iterator().
	 * 
	 * @param expr the expression to check
	 * @return true if it's an iterator() call
	 */
	private static boolean isIteratorCall(Expression expr) {
		if (!(expr instanceof MethodInvocation)) {
			return false;
		}
		
		MethodInvocation mi = (MethodInvocation) expr;
		return "iterator".equals(mi.getName().getIdentifier()); //$NON-NLS-1$
	}
	
	/**
	 * Extracts the collection expression from an iterator declaration.
	 * 
	 * @param varDecl the variable declaration statement
	 * @param iteratorVarName the iterator variable name
	 * @return the collection expression or null
	 */
	private static Expression extractCollectionFromDeclaration(
			VariableDeclarationStatement varDecl, String iteratorVarName) {
		@SuppressWarnings("unchecked")
		java.util.List<VariableDeclarationFragment> fragments = varDecl.fragments();
		
		for (VariableDeclarationFragment fragment : fragments) {
			if (iteratorVarName.equals(fragment.getName().getIdentifier())) {
				Expression initializer = fragment.getInitializer();
				if (initializer instanceof MethodInvocation) {
					MethodInvocation mi = (MethodInvocation) initializer;
					if ("iterator".equals(mi.getName().getIdentifier())) { //$NON-NLS-1$
						return mi.getExpression();
					}
				}
			}
		}
		
		return null;
	}
}
