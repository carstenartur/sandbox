package org.sandbox.jdt.internal.common;

/*-
 * #%L
 * Sandbox common
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * A fluent builder class that provides a simplified entry point for configuring and
 * executing AST processing operations. This class wraps the existing {@link ASTProcessor}
 * and provides typed convenience methods for commonly used AST node types.
 *
 * <p>Example usage:
 * <pre>{@code
 * ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
 * AstProcessorBuilder.with(holder)
 *     .onMethodInvocation("myMethod", (node, h) -> {
 *         // node is MethodInvocation, no cast needed
 *         return true;
 *     })
 *     .onAssignment((node, h) -> {
 *         // node is Assignment, no cast needed
 *         return true;
 *     })
 *     .build(compilationUnit);
 * }</pre>
 *
 * @author chammer
 * @param <V> the map key type used by the ReferenceHolder
 * @param <T> the map value type used by the ReferenceHolder
 * @since 1.16
 */
public final class AstProcessorBuilder<V, T> {

	private final ReferenceHolder<V, T> dataHolder;
	private final Set<ASTNode> nodesProcessed;
	private final ASTProcessor<ReferenceHolder<V, T>, V, T> processor;

	/**
	 * Creates a new builder with the specified data holder.
	 *
	 * @param dataHolder the reference holder to use for storing and retrieving data during processing
	 */
	private AstProcessorBuilder(ReferenceHolder<V, T> dataHolder) {
		this(dataHolder, new HashSet<>());
	}

	/**
	 * Creates a new builder with the specified data holder and set of processed nodes.
	 *
	 * @param dataHolder the reference holder to use for storing and retrieving data during processing
	 * @param nodesProcessed the set to track already processed nodes
	 */
	private AstProcessorBuilder(ReferenceHolder<V, T> dataHolder, Set<ASTNode> nodesProcessed) {
		this.dataHolder = dataHolder;
		this.nodesProcessed = nodesProcessed;
		this.processor = new ASTProcessor<>(dataHolder, nodesProcessed);
	}

	/**
	 * Creates a new AstProcessorBuilder with the specified data holder.
	 *
	 * @param <V> the map key type used by the ReferenceHolder
	 * @param <T> the map value type used by the ReferenceHolder
	 * @param dataHolder the reference holder to use for storing and retrieving data during processing
	 * @return a new AstProcessorBuilder instance
	 */
	public static <V, T> AstProcessorBuilder<V, T> with(ReferenceHolder<V, T> dataHolder) {
		return new AstProcessorBuilder<>(dataHolder);
	}

	/**
	 * Creates a new AstProcessorBuilder with the specified data holder and set of processed nodes.
	 *
	 * @param <V> the map key type used by the ReferenceHolder
	 * @param <T> the map value type used by the ReferenceHolder
	 * @param dataHolder the reference holder to use for storing and retrieving data during processing
	 * @param nodesProcessed the set to track already processed nodes
	 * @return a new AstProcessorBuilder instance
	 */
	public static <V, T> AstProcessorBuilder<V, T> with(ReferenceHolder<V, T> dataHolder, Set<ASTNode> nodesProcessed) {
		return new AstProcessorBuilder<>(dataHolder, nodesProcessed);
	}

	/**
	 * Registers a visitor for MethodInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodInvocation(BiPredicate<MethodInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callMethodInvocationVisitor((node, holder) -> predicate.test((MethodInvocation) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for MethodInvocation nodes with a specific method name.
	 *
	 * @param methodName the name of the method to match
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodInvocation(String methodName, BiPredicate<MethodInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callMethodInvocationVisitor(methodName, (node, holder) -> predicate.test((MethodInvocation) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for MethodInvocation nodes with a specific class type and method name.
	 *
	 * @param classType the class type to match
	 * @param methodName the name of the method to match
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodInvocation(Class<?> classType, String methodName, BiPredicate<MethodInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callMethodInvocationVisitor(classType, methodName, predicate);
		return this;
	}

	/**
	 * Registers a visitor for MethodDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodDeclaration(BiPredicate<MethodDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callMethodDeclarationVisitor((node, holder) -> predicate.test((MethodDeclaration) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for TypeDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeDeclaration(BiPredicate<TypeDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callTypeDeclarationVisitor((node, holder) -> predicate.test((TypeDeclaration) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ClassInstanceCreation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onClassInstanceCreation(BiPredicate<ClassInstanceCreation, ReferenceHolder<V, T>> predicate) {
		processor.callClassInstanceCreationVisitor(predicate);
		return this;
	}

	/**
	 * Registers a visitor for ClassInstanceCreation nodes with a specific class type.
	 *
	 * @param classType the class type to match
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onClassInstanceCreation(Class<?> classType, BiPredicate<ClassInstanceCreation, ReferenceHolder<V, T>> predicate) {
		processor.callClassInstanceCreationVisitor(classType, predicate);
		return this;
	}

	/**
	 * Registers a visitor for VariableDeclarationFragment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onVariableDeclarationFragment(BiPredicate<VariableDeclarationFragment, ReferenceHolder<V, T>> predicate) {
		processor.callVariableDeclarationFragmentVisitor((node, holder) -> predicate.test((VariableDeclarationFragment) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for Assignment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAssignment(BiPredicate<Assignment, ReferenceHolder<V, T>> predicate) {
		processor.callAssignmentVisitor((node, holder) -> predicate.test((Assignment) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for BreakStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBreakStatement(BiPredicate<BreakStatement, ReferenceHolder<V, T>> predicate) {
		processor.callBreakStatementVisitor((node, holder) -> predicate.test((BreakStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ContinueStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onContinueStatement(BiPredicate<ContinueStatement, ReferenceHolder<V, T>> predicate) {
		processor.callContinueStatementVisitor((node, holder) -> predicate.test((ContinueStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ReturnStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onReturnStatement(BiPredicate<ReturnStatement, ReferenceHolder<V, T>> predicate) {
		processor.callReturnStatementVisitor((node, holder) -> predicate.test((ReturnStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ThrowStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onThrowStatement(BiPredicate<ThrowStatement, ReferenceHolder<V, T>> predicate) {
		processor.callThrowStatementVisitor((node, holder) -> predicate.test((ThrowStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for EnhancedForStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEnhancedForStatement(BiPredicate<EnhancedForStatement, ReferenceHolder<V, T>> predicate) {
		processor.callEnhancedForStatementVisitor((node, holder) -> predicate.test((EnhancedForStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for SimpleName nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSimpleName(BiPredicate<SimpleName, ReferenceHolder<V, T>> predicate) {
		processor.callSimpleNameVisitor((node, holder) -> predicate.test((SimpleName) node, holder));
		return this;
	}

	/**
	 * Provides access to the underlying ASTProcessor for advanced configuration.
	 * This allows using any of the existing ASTProcessor methods directly.
	 *
	 * @return the underlying ASTProcessor instance
	 */
	public ASTProcessor<ReferenceHolder<V, T>, V, T> processor() {
		return processor;
	}

	/**
	 * Returns the data holder associated with this builder.
	 *
	 * @return the reference holder
	 */
	public ReferenceHolder<V, T> getDataHolder() {
		return dataHolder;
	}

	/**
	 * Returns the set of already processed nodes.
	 *
	 * @return the set of processed nodes
	 */
	public Set<ASTNode> getNodesProcessed() {
		return nodesProcessed;
	}

	/**
	 * Builds and executes the AST processor on the given node.
	 * This triggers the configured visitors to process the AST.
	 *
	 * @param node the AST node to process
	 */
	public void build(ASTNode node) {
		processor.build(node);
	}
}
