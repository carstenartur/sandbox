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

import org.eclipse.jdt.core.dom.*;

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
		processor.callMethodInvocationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MethodInvocation) node, holder));
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
		processor.callMethodDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MethodDeclaration) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for TypeDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeDeclaration(BiPredicate<TypeDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callTypeDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TypeDeclaration) node, holder));
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
		processor.callVariableDeclarationFragmentVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((VariableDeclarationFragment) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for Assignment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAssignment(BiPredicate<Assignment, ReferenceHolder<V, T>> predicate) {
		processor.callAssignmentVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((Assignment) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for BreakStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBreakStatement(BiPredicate<BreakStatement, ReferenceHolder<V, T>> predicate) {
		processor.callBreakStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((BreakStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ContinueStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onContinueStatement(BiPredicate<ContinueStatement, ReferenceHolder<V, T>> predicate) {
		processor.callContinueStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ContinueStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ReturnStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onReturnStatement(BiPredicate<ReturnStatement, ReferenceHolder<V, T>> predicate) {
		processor.callReturnStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ReturnStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for ThrowStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onThrowStatement(BiPredicate<ThrowStatement, ReferenceHolder<V, T>> predicate) {
		processor.callThrowStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ThrowStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for EnhancedForStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEnhancedForStatement(BiPredicate<EnhancedForStatement, ReferenceHolder<V, T>> predicate) {
		processor.callEnhancedForStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((EnhancedForStatement) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for SimpleName nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSimpleName(BiPredicate<SimpleName, ReferenceHolder<V, T>> predicate) {
		processor.callSimpleNameVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SimpleName) node, holder));
		return this;
	}
	/**
	 * Registers a visitor for AnnotationTypeDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAnnotationTypeDeclaration(BiPredicate<AnnotationTypeDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callAnnotationTypeDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((AnnotationTypeDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for AnnotationTypeMemberDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAnnotationTypeMemberDeclaration(BiPredicate<AnnotationTypeMemberDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callAnnotationTypeMemberDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((AnnotationTypeMemberDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for AnonymousClassDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAnonymousClassDeclaration(BiPredicate<AnonymousClassDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callAnonymousClassDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((AnonymousClassDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayAccess nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayAccess(BiPredicate<ArrayAccess, ReferenceHolder<V, T>> predicate) {
		processor.callArrayAccessVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ArrayAccess) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayCreation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayCreation(BiPredicate<ArrayCreation, ReferenceHolder<V, T>> predicate) {
		processor.callArrayCreationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ArrayCreation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayInitializer nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayInitializer(BiPredicate<ArrayInitializer, ReferenceHolder<V, T>> predicate) {
		processor.callArrayInitializerVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ArrayInitializer) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayType(BiPredicate<ArrayType, ReferenceHolder<V, T>> predicate) {
		processor.callArrayTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ArrayType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for AssertStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAssertStatement(BiPredicate<AssertStatement, ReferenceHolder<V, T>> predicate) {
		processor.callAssertStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((AssertStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Block nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBlock(BiPredicate<Block, ReferenceHolder<V, T>> predicate) {
		processor.callBlockVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((Block) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for BlockComment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBlockComment(BiPredicate<BlockComment, ReferenceHolder<V, T>> predicate) {
		processor.callBlockCommentVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((BlockComment) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for BooleanLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBooleanLiteral(BiPredicate<BooleanLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callBooleanLiteralVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((BooleanLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CastExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCastExpression(BiPredicate<CastExpression, ReferenceHolder<V, T>> predicate) {
		processor.callCastExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((CastExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CatchClause nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCatchClause(BiPredicate<CatchClause, ReferenceHolder<V, T>> predicate) {
		processor.callCatchClauseVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((CatchClause) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CharacterLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCharacterLiteral(BiPredicate<CharacterLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callCharacterLiteralVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((CharacterLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CompilationUnit nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCompilationUnit(BiPredicate<CompilationUnit, ReferenceHolder<V, T>> predicate) {
		processor.callCompilationUnitVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((CompilationUnit) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ConditionalExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onConditionalExpression(BiPredicate<ConditionalExpression, ReferenceHolder<V, T>> predicate) {
		processor.callConditionalExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ConditionalExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ConstructorInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onConstructorInvocation(BiPredicate<ConstructorInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callConstructorInvocationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ConstructorInvocation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CreationReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCreationReference(BiPredicate<CreationReference, ReferenceHolder<V, T>> predicate) {
		processor.callCreationReferenceVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((CreationReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Dimension nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onDimension(BiPredicate<Dimension, ReferenceHolder<V, T>> predicate) {
		processor.callDimensionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((Dimension) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for DoStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onDoStatement(BiPredicate<DoStatement, ReferenceHolder<V, T>> predicate) {
		processor.callDoStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((DoStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for EmptyStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEmptyStatement(BiPredicate<EmptyStatement, ReferenceHolder<V, T>> predicate) {
		processor.callEmptyStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((EmptyStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for EnumConstantDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEnumConstantDeclaration(BiPredicate<EnumConstantDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callEnumConstantDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((EnumConstantDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for EnumDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEnumDeclaration(BiPredicate<EnumDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callEnumDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((EnumDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ExportsDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onExportsDirective(BiPredicate<ExportsDirective, ReferenceHolder<V, T>> predicate) {
		processor.callExportsDirectiveVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ExportsDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ExpressionMethodReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onExpressionMethodReference(BiPredicate<ExpressionMethodReference, ReferenceHolder<V, T>> predicate) {
		processor.callExpressionMethodReferenceVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ExpressionMethodReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ExpressionStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onExpressionStatement(BiPredicate<ExpressionStatement, ReferenceHolder<V, T>> predicate) {
		processor.callExpressionStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ExpressionStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for FieldAccess nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onFieldAccess(BiPredicate<FieldAccess, ReferenceHolder<V, T>> predicate) {
		processor.callFieldAccessVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((FieldAccess) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for FieldDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onFieldDeclaration(BiPredicate<FieldDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callFieldDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((FieldDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ForStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onForStatement(BiPredicate<ForStatement, ReferenceHolder<V, T>> predicate) {
		processor.callForStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ForStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for IfStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onIfStatement(BiPredicate<IfStatement, ReferenceHolder<V, T>> predicate) {
		processor.callIfStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((IfStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ImportDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onImportDeclaration(BiPredicate<ImportDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callImportDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ImportDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for InfixExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onInfixExpression(BiPredicate<InfixExpression, ReferenceHolder<V, T>> predicate) {
		processor.callInfixExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((InfixExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Initializer nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onInitializer(BiPredicate<Initializer, ReferenceHolder<V, T>> predicate) {
		processor.callInitializerVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((Initializer) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for InstanceofExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onInstanceofExpression(BiPredicate<InstanceofExpression, ReferenceHolder<V, T>> predicate) {
		processor.callInstanceofExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((InstanceofExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for IntersectionType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onIntersectionType(BiPredicate<IntersectionType, ReferenceHolder<V, T>> predicate) {
		processor.callIntersectionTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((IntersectionType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Javadoc nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onJavadoc(BiPredicate<Javadoc, ReferenceHolder<V, T>> predicate) {
		processor.callJavadocVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((Javadoc) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for LabeledStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLabeledStatement(BiPredicate<LabeledStatement, ReferenceHolder<V, T>> predicate) {
		processor.callLabeledStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((LabeledStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for LambdaExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLambdaExpression(BiPredicate<LambdaExpression, ReferenceHolder<V, T>> predicate) {
		processor.callLambdaExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((LambdaExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for LineComment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLineComment(BiPredicate<LineComment, ReferenceHolder<V, T>> predicate) {
		processor.callLineCommentVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((LineComment) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MarkerAnnotation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMarkerAnnotation(BiPredicate<MarkerAnnotation, ReferenceHolder<V, T>> predicate) {
		processor.callMarkerAnnotationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MarkerAnnotation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MemberRef nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMemberRef(BiPredicate<MemberRef, ReferenceHolder<V, T>> predicate) {
		processor.callMemberRefVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MemberRef) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MemberValuePair nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMemberValuePair(BiPredicate<MemberValuePair, ReferenceHolder<V, T>> predicate) {
		processor.callMemberValuePairVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MemberValuePair) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MethodRef nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodRef(BiPredicate<MethodRef, ReferenceHolder<V, T>> predicate) {
		processor.callMethodRefVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MethodRef) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MethodRefParameter nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodRefParameter(BiPredicate<MethodRefParameter, ReferenceHolder<V, T>> predicate) {
		processor.callMethodRefParameterVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((MethodRefParameter) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Modifier nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModifier(BiPredicate<Modifier, ReferenceHolder<V, T>> predicate) {
		processor.callModifierVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((Modifier) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ModuleDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModuleDeclaration(BiPredicate<ModuleDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callModuleDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ModuleDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ModuleModifier nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModuleModifier(BiPredicate<ModuleModifier, ReferenceHolder<V, T>> predicate) {
		processor.callModuleModifierVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ModuleModifier) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ModuleQualifiedName nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModuleQualifiedName(BiPredicate<ModuleQualifiedName, ReferenceHolder<V, T>> predicate) {
		processor.callModuleQualifiedNameVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ModuleQualifiedName) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NameQualifiedType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNameQualifiedType(BiPredicate<NameQualifiedType, ReferenceHolder<V, T>> predicate) {
		processor.callNameQualifiedTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((NameQualifiedType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NormalAnnotation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNormalAnnotation(BiPredicate<NormalAnnotation, ReferenceHolder<V, T>> predicate) {
		processor.callNormalAnnotationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((NormalAnnotation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NullLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNullLiteral(BiPredicate<NullLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callNullLiteralVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((NullLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NumberLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNumberLiteral(BiPredicate<NumberLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callNumberLiteralVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((NumberLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for OpensDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onOpensDirective(BiPredicate<OpensDirective, ReferenceHolder<V, T>> predicate) {
		processor.callOpensDirectiveVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((OpensDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PackageDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPackageDeclaration(BiPredicate<PackageDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callPackageDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((PackageDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ParameterizedType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onParameterizedType(BiPredicate<ParameterizedType, ReferenceHolder<V, T>> predicate) {
		processor.callParameterizedTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ParameterizedType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ParenthesizedExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onParenthesizedExpression(BiPredicate<ParenthesizedExpression, ReferenceHolder<V, T>> predicate) {
		processor.callParenthesizedExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ParenthesizedExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PatternInstanceofExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPatternInstanceofExpression(BiPredicate<PatternInstanceofExpression, ReferenceHolder<V, T>> predicate) {
		processor.callPatternInstanceofExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((PatternInstanceofExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PostfixExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPostfixExpression(BiPredicate<PostfixExpression, ReferenceHolder<V, T>> predicate) {
		processor.callPostfixExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((PostfixExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PrefixExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPrefixExpression(BiPredicate<PrefixExpression, ReferenceHolder<V, T>> predicate) {
		processor.callPrefixExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((PrefixExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PrimitiveType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPrimitiveType(BiPredicate<PrimitiveType, ReferenceHolder<V, T>> predicate) {
		processor.callPrimitiveTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((PrimitiveType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ProvidesDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onProvidesDirective(BiPredicate<ProvidesDirective, ReferenceHolder<V, T>> predicate) {
		processor.callProvidesDirectiveVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ProvidesDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for QualifiedName nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onQualifiedName(BiPredicate<QualifiedName, ReferenceHolder<V, T>> predicate) {
		processor.callQualifiedNameVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((QualifiedName) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for QualifiedType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onQualifiedType(BiPredicate<QualifiedType, ReferenceHolder<V, T>> predicate) {
		processor.callQualifiedTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((QualifiedType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for RecordDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onRecordDeclaration(BiPredicate<RecordDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callRecordDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((RecordDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for RequiresDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onRequiresDirective(BiPredicate<RequiresDirective, ReferenceHolder<V, T>> predicate) {
		processor.callRequiresDirectiveVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((RequiresDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SimpleType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSimpleType(BiPredicate<SimpleType, ReferenceHolder<V, T>> predicate) {
		processor.callSimpleTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SimpleType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SingleMemberAnnotation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSingleMemberAnnotation(BiPredicate<SingleMemberAnnotation, ReferenceHolder<V, T>> predicate) {
		processor.callSingleMemberAnnotationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SingleMemberAnnotation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SingleVariableDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSingleVariableDeclaration(BiPredicate<SingleVariableDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callSingleVariableDeclarationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SingleVariableDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for StringLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onStringLiteral(BiPredicate<StringLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callStringLiteralVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((StringLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperConstructorInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperConstructorInvocation(BiPredicate<SuperConstructorInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callSuperConstructorInvocationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SuperConstructorInvocation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperFieldAccess nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperFieldAccess(BiPredicate<SuperFieldAccess, ReferenceHolder<V, T>> predicate) {
		processor.callSuperFieldAccessVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SuperFieldAccess) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperMethodInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperMethodInvocation(BiPredicate<SuperMethodInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callSuperMethodInvocationVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SuperMethodInvocation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperMethodReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperMethodReference(BiPredicate<SuperMethodReference, ReferenceHolder<V, T>> predicate) {
		processor.callSuperMethodReferenceVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SuperMethodReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SwitchCase nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSwitchCase(BiPredicate<SwitchCase, ReferenceHolder<V, T>> predicate) {
		processor.callSwitchCaseVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SwitchCase) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SwitchExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSwitchExpression(BiPredicate<SwitchExpression, ReferenceHolder<V, T>> predicate) {
		processor.callSwitchExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SwitchExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SwitchStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSwitchStatement(BiPredicate<SwitchStatement, ReferenceHolder<V, T>> predicate) {
		processor.callSwitchStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SwitchStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SynchronizedStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSynchronizedStatement(BiPredicate<SynchronizedStatement, ReferenceHolder<V, T>> predicate) {
		processor.callSynchronizedStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((SynchronizedStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TagElement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTagElement(BiPredicate<TagElement, ReferenceHolder<V, T>> predicate) {
		processor.callTagElementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TagElement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TextBlock nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTextBlock(BiPredicate<TextBlock, ReferenceHolder<V, T>> predicate) {
		processor.callTextBlockVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TextBlock) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TextElement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTextElement(BiPredicate<TextElement, ReferenceHolder<V, T>> predicate) {
		processor.callTextElementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TextElement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ThisExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onThisExpression(BiPredicate<ThisExpression, ReferenceHolder<V, T>> predicate) {
		processor.callThisExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((ThisExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TryStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTryStatement(BiPredicate<TryStatement, ReferenceHolder<V, T>> predicate) {
		processor.callTryStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TryStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeDeclarationStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeDeclarationStatement(BiPredicate<TypeDeclarationStatement, ReferenceHolder<V, T>> predicate) {
		processor.callTypeDeclarationStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TypeDeclarationStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeLiteral(BiPredicate<TypeLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callTypeLiteralVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TypeLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeMethodReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeMethodReference(BiPredicate<TypeMethodReference, ReferenceHolder<V, T>> predicate) {
		processor.callTypeMethodReferenceVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TypeMethodReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeParameter nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeParameter(BiPredicate<TypeParameter, ReferenceHolder<V, T>> predicate) {
		processor.callTypeParameterVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((TypeParameter) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for UnionType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onUnionType(BiPredicate<UnionType, ReferenceHolder<V, T>> predicate) {
		processor.callUnionTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((UnionType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for UsesDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onUsesDirective(BiPredicate<UsesDirective, ReferenceHolder<V, T>> predicate) {
		processor.callUsesDirectiveVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((UsesDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for VariableDeclarationExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onVariableDeclarationExpression(BiPredicate<VariableDeclarationExpression, ReferenceHolder<V, T>> predicate) {
		processor.callVariableDeclarationExpressionVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((VariableDeclarationExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for VariableDeclarationStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onVariableDeclarationStatement(BiPredicate<VariableDeclarationStatement, ReferenceHolder<V, T>> predicate) {
		processor.callVariableDeclarationStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((VariableDeclarationStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for WhileStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onWhileStatement(BiPredicate<WhileStatement, ReferenceHolder<V, T>> predicate) {
		processor.callWhileStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((WhileStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for WildcardType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onWildcardType(BiPredicate<WildcardType, ReferenceHolder<V, T>> predicate) {
		processor.callWildcardTypeVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((WildcardType) node, holder));
		return this;
	}

	/**
	 * Registers a visitor for YieldStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onYieldStatement(BiPredicate<YieldStatement, ReferenceHolder<V, T>> predicate) {
		processor.callYieldStatementVisitor((BiPredicate<ASTNode, ReferenceHolder<V, T>>) (node, holder) -> predicate.test((YieldStatement) node, holder));
		return this;
	}

	// ========== Convenience Methods for Common Patterns ==========

	/**
	 * Registers a visitor for IfStatement nodes that have no else branch.
	 * This is a convenience method for a common pattern.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onIfStatementWithoutElse(BiPredicate<IfStatement, ReferenceHolder<V, T>> predicate) {
		return onIfStatement((node, holder) -> {
			if (node.getElseStatement() == null) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for IfStatement nodes that have an else branch.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onIfStatementWithElse(BiPredicate<IfStatement, ReferenceHolder<V, T>> predicate) {
		return onIfStatement((node, holder) -> {
			if (node.getElseStatement() != null) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for Assignment nodes with a compound operator (+=, -=, *=, etc.).
	 * Excludes simple assignment (=).
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCompoundAssignment(BiPredicate<Assignment, ReferenceHolder<V, T>> predicate) {
		return onAssignment((node, holder) -> {
			if (node.getOperator() != Assignment.Operator.ASSIGN) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for Assignment nodes with a specific operator.
	 *
	 * @param operator the operator to match
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAssignmentWithOperator(Assignment.Operator operator, BiPredicate<Assignment, ReferenceHolder<V, T>> predicate) {
		return onAssignment((node, holder) -> {
			if (node.getOperator() == operator) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for PostfixExpression nodes with increment (i++) or decrement (i--).
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPostfixIncrementOrDecrement(BiPredicate<PostfixExpression, ReferenceHolder<V, T>> predicate) {
		return onPostfixExpression((node, holder) -> {
			if (node.getOperator() == PostfixExpression.Operator.INCREMENT
					|| node.getOperator() == PostfixExpression.Operator.DECREMENT) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for PrefixExpression nodes with increment (++i) or decrement (--i).
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPrefixIncrementOrDecrement(BiPredicate<PrefixExpression, ReferenceHolder<V, T>> predicate) {
		return onPrefixExpression((node, holder) -> {
			if (node.getOperator() == PrefixExpression.Operator.INCREMENT
					|| node.getOperator() == PrefixExpression.Operator.DECREMENT) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for ContinueStatement nodes without a label.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onUnlabeledContinue(BiPredicate<ContinueStatement, ReferenceHolder<V, T>> predicate) {
		return onContinueStatement((node, holder) -> {
			if (node.getLabel() == null) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for ContinueStatement nodes with a label.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLabeledContinue(BiPredicate<ContinueStatement, ReferenceHolder<V, T>> predicate) {
		return onContinueStatement((node, holder) -> {
			if (node.getLabel() != null) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for ReturnStatement nodes that return a BooleanLiteral.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onReturnBoolean(BiPredicate<ReturnStatement, ReferenceHolder<V, T>> predicate) {
		return onReturnStatement((node, holder) -> {
			if (node.getExpression() instanceof BooleanLiteral) {
				return predicate.test(node, holder);
			}
			return true;
		});
	}

	/**
	 * Registers a visitor for MethodInvocation nodes on a specific class (e.g., Math.max).
	 *
	 * @param className the simple name of the class (e.g., "Math")
	 * @param methodName the name of the method (e.g., "max")
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onStaticMethodInvocation(String className, String methodName, BiPredicate<MethodInvocation, ReferenceHolder<V, T>> predicate) {
		return onMethodInvocation((node, holder) -> {
			if (methodName.equals(node.getName().getIdentifier())) {
				Expression expr = node.getExpression();
				if (expr instanceof SimpleName) {
					if (className.equals(((SimpleName) expr).getIdentifier())) {
						return predicate.test(node, holder);
					}
				}
			}
			return true;
		});
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
