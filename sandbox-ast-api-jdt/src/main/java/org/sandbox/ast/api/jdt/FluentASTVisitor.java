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
package org.sandbox.ast.api.jdt;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;

/**
 * An ASTVisitor that pre-converts JDT nodes to fluent API wrappers.
 * 
 * <p>Subclasses override the {@code visitXxx(FluentType, JDTType)} methods instead of
 * the raw {@code visit(JDTType)} methods. The raw JDT node is still available for
 * position checks, parent navigation, and rewriting.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>
 * FluentASTVisitor visitor = new FluentASTVisitor() {
 *     {@literal @}Override
 *     protected boolean visitMethodInvocation(MethodInvocationExpr expr, MethodInvocation node) {
 *         // No need to call JDTConverter.convert() - expr is already converted
 *         if (expr.isMethodCall("add", 1)) {
 *             // Use fluent API
 *         }
 *         return true;
 *     }
 * };
 * astNode.accept(visitor);
 * </pre>
 * 
 * <h2>Design:</h2>
 * <ul>
 *   <li>Only node types with existing {@link JDTConverter} conversions are wrapped</li>
 *   <li>The {@code visit()} methods are {@code final} to force use of fluent overrides</li>
 *   <li>Other {@code visit()} methods (FieldAccess, CastExpression, etc.) remain overridable</li>
 *   <li>Raw JDT nodes remain accessible for AST rewriting and parent traversal</li>
 * </ul>
 */
public class FluentASTVisitor extends ASTVisitor {

	/**
	 * Creates a new FluentASTVisitor.
	 */
	public FluentASTVisitor() {
		super();
	}

	/**
	 * Creates a new FluentASTVisitor with doc tag visiting control.
	 * 
	 * @param visitDocTags whether to visit Javadoc tags
	 */
	public FluentASTVisitor(boolean visitDocTags) {
		super(visitDocTags);
	}

	/**
	 * Pre-converts the MethodInvocation to a fluent wrapper and delegates to
	 * {@link #visitMethodInvocation(MethodInvocationExpr, MethodInvocation)}.
	 * 
	 * <p>This method is {@code final}. Subclasses must override
	 * {@link #visitMethodInvocation(MethodInvocationExpr, MethodInvocation)} instead.</p>
	 */
	@Override
	public final boolean visit(MethodInvocation node) {
		return visitMethodInvocation(JDTConverter.convert(node), node);
	}

	/**
	 * Pre-converts the SimpleName to a fluent wrapper and delegates to
	 * {@link #visitSimpleName(SimpleNameExpr, SimpleName)}.
	 * 
	 * <p>This method is {@code final}. Subclasses must override
	 * {@link #visitSimpleName(SimpleNameExpr, SimpleName)} instead.</p>
	 */
	@Override
	public final boolean visit(SimpleName node) {
		return visitSimpleName(JDTConverter.convert(node), node);
	}

	/**
	 * Called when visiting a MethodInvocation. Override this instead of {@code visit(MethodInvocation)}.
	 * 
	 * @param expr the pre-converted fluent wrapper
	 * @param node the raw JDT node (for position checks, parent navigation, rewriting)
	 * @return {@code true} to visit children, {@code false} to skip
	 */
	protected boolean visitMethodInvocation(MethodInvocationExpr expr, MethodInvocation node) {
		return true;
	}

	/**
	 * Called when visiting a SimpleName. Override this instead of {@code visit(SimpleName)}.
	 * 
	 * @param expr the pre-converted fluent wrapper
	 * @param node the raw JDT node (for position checks, parent navigation, rewriting)
	 * @return {@code true} to visit children, {@code false} to skip
	 */
	protected boolean visitSimpleName(SimpleNameExpr expr, SimpleName node) {
		return true;
	}
}
