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
	 * Registers a visitor for AnnotationTypeDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAnnotationTypeDeclaration(BiPredicate<AnnotationTypeDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callAnnotationTypeDeclarationVisitor((node, holder) -> predicate.test((AnnotationTypeDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for AnnotationTypeMemberDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAnnotationTypeMemberDeclaration(BiPredicate<AnnotationTypeMemberDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callAnnotationTypeMemberDeclarationVisitor((node, holder) -> predicate.test((AnnotationTypeMemberDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for AnonymousClassDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAnonymousClassDeclaration(BiPredicate<AnonymousClassDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callAnonymousClassDeclarationVisitor((node, holder) -> predicate.test((AnonymousClassDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayAccess nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayAccess(BiPredicate<ArrayAccess, ReferenceHolder<V, T>> predicate) {
		processor.callArrayAccessVisitor((node, holder) -> predicate.test((ArrayAccess) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayCreation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayCreation(BiPredicate<ArrayCreation, ReferenceHolder<V, T>> predicate) {
		processor.callArrayCreationVisitor((node, holder) -> predicate.test((ArrayCreation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayInitializer nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayInitializer(BiPredicate<ArrayInitializer, ReferenceHolder<V, T>> predicate) {
		processor.callArrayInitializerVisitor((node, holder) -> predicate.test((ArrayInitializer) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ArrayType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onArrayType(BiPredicate<ArrayType, ReferenceHolder<V, T>> predicate) {
		processor.callArrayTypeVisitor((node, holder) -> predicate.test((ArrayType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for AssertStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onAssertStatement(BiPredicate<AssertStatement, ReferenceHolder<V, T>> predicate) {
		processor.callAssertStatementVisitor((node, holder) -> predicate.test((AssertStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Block nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBlock(BiPredicate<Block, ReferenceHolder<V, T>> predicate) {
		processor.callBlockVisitor((node, holder) -> predicate.test((Block) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for BlockComment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBlockComment(BiPredicate<BlockComment, ReferenceHolder<V, T>> predicate) {
		processor.callBlockCommentVisitor((node, holder) -> predicate.test((BlockComment) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for BooleanLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onBooleanLiteral(BiPredicate<BooleanLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callBooleanLiteralVisitor((node, holder) -> predicate.test((BooleanLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CastExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCastExpression(BiPredicate<CastExpression, ReferenceHolder<V, T>> predicate) {
		processor.callCastExpressionVisitor((node, holder) -> predicate.test((CastExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CatchClause nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCatchClause(BiPredicate<CatchClause, ReferenceHolder<V, T>> predicate) {
		processor.callCatchClauseVisitor((node, holder) -> predicate.test((CatchClause) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CharacterLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCharacterLiteral(BiPredicate<CharacterLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callCharacterLiteralVisitor((node, holder) -> predicate.test((CharacterLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CompilationUnit nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCompilationUnit(BiPredicate<CompilationUnit, ReferenceHolder<V, T>> predicate) {
		processor.callCompilationUnitVisitor((node, holder) -> predicate.test((CompilationUnit) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ConditionalExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onConditionalExpression(BiPredicate<ConditionalExpression, ReferenceHolder<V, T>> predicate) {
		processor.callConditionalExpressionVisitor((node, holder) -> predicate.test((ConditionalExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ConstructorInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onConstructorInvocation(BiPredicate<ConstructorInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callConstructorInvocationVisitor((node, holder) -> predicate.test((ConstructorInvocation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for CreationReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onCreationReference(BiPredicate<CreationReference, ReferenceHolder<V, T>> predicate) {
		processor.callCreationReferenceVisitor((node, holder) -> predicate.test((CreationReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Dimension nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onDimension(BiPredicate<Dimension, ReferenceHolder<V, T>> predicate) {
		processor.callDimensionVisitor((node, holder) -> predicate.test((Dimension) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for DoStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onDoStatement(BiPredicate<DoStatement, ReferenceHolder<V, T>> predicate) {
		processor.callDoStatementVisitor((node, holder) -> predicate.test((DoStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for EmptyStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEmptyStatement(BiPredicate<EmptyStatement, ReferenceHolder<V, T>> predicate) {
		processor.callEmptyStatementVisitor((node, holder) -> predicate.test((EmptyStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for EnumConstantDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEnumConstantDeclaration(BiPredicate<EnumConstantDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callEnumConstantDeclarationVisitor((node, holder) -> predicate.test((EnumConstantDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for EnumDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onEnumDeclaration(BiPredicate<EnumDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callEnumDeclarationVisitor((node, holder) -> predicate.test((EnumDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ExportsDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onExportsDirective(BiPredicate<ExportsDirective, ReferenceHolder<V, T>> predicate) {
		processor.callExportsDirectiveVisitor((node, holder) -> predicate.test((ExportsDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ExpressionMethodReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onExpressionMethodReference(BiPredicate<ExpressionMethodReference, ReferenceHolder<V, T>> predicate) {
		processor.callExpressionMethodReferenceVisitor((node, holder) -> predicate.test((ExpressionMethodReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ExpressionStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onExpressionStatement(BiPredicate<ExpressionStatement, ReferenceHolder<V, T>> predicate) {
		processor.callExpressionStatementVisitor((node, holder) -> predicate.test((ExpressionStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for FieldAccess nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onFieldAccess(BiPredicate<FieldAccess, ReferenceHolder<V, T>> predicate) {
		processor.callFieldAccessVisitor((node, holder) -> predicate.test((FieldAccess) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for FieldDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onFieldDeclaration(BiPredicate<FieldDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callFieldDeclarationVisitor((node, holder) -> predicate.test((FieldDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ForStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onForStatement(BiPredicate<ForStatement, ReferenceHolder<V, T>> predicate) {
		processor.callForStatementVisitor((node, holder) -> predicate.test((ForStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for IfStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onIfStatement(BiPredicate<IfStatement, ReferenceHolder<V, T>> predicate) {
		processor.callIfStatementVisitor((node, holder) -> predicate.test((IfStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ImportDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onImportDeclaration(BiPredicate<ImportDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callImportDeclarationVisitor((node, holder) -> predicate.test((ImportDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for InfixExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onInfixExpression(BiPredicate<InfixExpression, ReferenceHolder<V, T>> predicate) {
		processor.callInfixExpressionVisitor((node, holder) -> predicate.test((InfixExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Initializer nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onInitializer(BiPredicate<Initializer, ReferenceHolder<V, T>> predicate) {
		processor.callInitializerVisitor((node, holder) -> predicate.test((Initializer) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for InstanceofExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onInstanceofExpression(BiPredicate<InstanceofExpression, ReferenceHolder<V, T>> predicate) {
		processor.callInstanceofExpressionVisitor((node, holder) -> predicate.test((InstanceofExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for IntersectionType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onIntersectionType(BiPredicate<IntersectionType, ReferenceHolder<V, T>> predicate) {
		processor.callIntersectionTypeVisitor((node, holder) -> predicate.test((IntersectionType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Javadoc nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onJavadoc(BiPredicate<Javadoc, ReferenceHolder<V, T>> predicate) {
		processor.callJavadocVisitor((node, holder) -> predicate.test((Javadoc) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for LabeledStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLabeledStatement(BiPredicate<LabeledStatement, ReferenceHolder<V, T>> predicate) {
		processor.callLabeledStatementVisitor((node, holder) -> predicate.test((LabeledStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for LambdaExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLambdaExpression(BiPredicate<LambdaExpression, ReferenceHolder<V, T>> predicate) {
		processor.callLambdaExpressionVisitor((node, holder) -> predicate.test((LambdaExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for LineComment nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onLineComment(BiPredicate<LineComment, ReferenceHolder<V, T>> predicate) {
		processor.callLineCommentVisitor((node, holder) -> predicate.test((LineComment) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MarkerAnnotation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMarkerAnnotation(BiPredicate<MarkerAnnotation, ReferenceHolder<V, T>> predicate) {
		processor.callMarkerAnnotationVisitor((node, holder) -> predicate.test((MarkerAnnotation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MemberRef nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMemberRef(BiPredicate<MemberRef, ReferenceHolder<V, T>> predicate) {
		processor.callMemberRefVisitor((node, holder) -> predicate.test((MemberRef) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MemberValuePair nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMemberValuePair(BiPredicate<MemberValuePair, ReferenceHolder<V, T>> predicate) {
		processor.callMemberValuePairVisitor((node, holder) -> predicate.test((MemberValuePair) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MethodRef nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodRef(BiPredicate<MethodRef, ReferenceHolder<V, T>> predicate) {
		processor.callMethodRefVisitor((node, holder) -> predicate.test((MethodRef) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for MethodRefParameter nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onMethodRefParameter(BiPredicate<MethodRefParameter, ReferenceHolder<V, T>> predicate) {
		processor.callMethodRefParameterVisitor((node, holder) -> predicate.test((MethodRefParameter) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for Modifier nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModifier(BiPredicate<Modifier, ReferenceHolder<V, T>> predicate) {
		processor.callModifierVisitor((node, holder) -> predicate.test((Modifier) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ModuleDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModuleDeclaration(BiPredicate<ModuleDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callModuleDeclarationVisitor((node, holder) -> predicate.test((ModuleDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ModuleModifier nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModuleModifier(BiPredicate<ModuleModifier, ReferenceHolder<V, T>> predicate) {
		processor.callModuleModifierVisitor((node, holder) -> predicate.test((ModuleModifier) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ModuleQualifiedName nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onModuleQualifiedName(BiPredicate<ModuleQualifiedName, ReferenceHolder<V, T>> predicate) {
		processor.callModuleQualifiedNameVisitor((node, holder) -> predicate.test((ModuleQualifiedName) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NameQualifiedType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNameQualifiedType(BiPredicate<NameQualifiedType, ReferenceHolder<V, T>> predicate) {
		processor.callNameQualifiedTypeVisitor((node, holder) -> predicate.test((NameQualifiedType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NormalAnnotation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNormalAnnotation(BiPredicate<NormalAnnotation, ReferenceHolder<V, T>> predicate) {
		processor.callNormalAnnotationVisitor((node, holder) -> predicate.test((NormalAnnotation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NullLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNullLiteral(BiPredicate<NullLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callNullLiteralVisitor((node, holder) -> predicate.test((NullLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for NumberLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onNumberLiteral(BiPredicate<NumberLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callNumberLiteralVisitor((node, holder) -> predicate.test((NumberLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for OpensDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onOpensDirective(BiPredicate<OpensDirective, ReferenceHolder<V, T>> predicate) {
		processor.callOpensDirectiveVisitor((node, holder) -> predicate.test((OpensDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PackageDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPackageDeclaration(BiPredicate<PackageDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callPackageDeclarationVisitor((node, holder) -> predicate.test((PackageDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ParameterizedType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onParameterizedType(BiPredicate<ParameterizedType, ReferenceHolder<V, T>> predicate) {
		processor.callParameterizedTypeVisitor((node, holder) -> predicate.test((ParameterizedType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ParenthesizedExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onParenthesizedExpression(BiPredicate<ParenthesizedExpression, ReferenceHolder<V, T>> predicate) {
		processor.callParenthesizedExpressionVisitor((node, holder) -> predicate.test((ParenthesizedExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PatternInstanceofExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPatternInstanceofExpression(BiPredicate<PatternInstanceofExpression, ReferenceHolder<V, T>> predicate) {
		processor.callPatternInstanceofExpressionVisitor((node, holder) -> predicate.test((PatternInstanceofExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PostfixExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPostfixExpression(BiPredicate<PostfixExpression, ReferenceHolder<V, T>> predicate) {
		processor.callPostfixExpressionVisitor((node, holder) -> predicate.test((PostfixExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PrefixExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPrefixExpression(BiPredicate<PrefixExpression, ReferenceHolder<V, T>> predicate) {
		processor.callPrefixExpressionVisitor((node, holder) -> predicate.test((PrefixExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for PrimitiveType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onPrimitiveType(BiPredicate<PrimitiveType, ReferenceHolder<V, T>> predicate) {
		processor.callPrimitiveTypeVisitor((node, holder) -> predicate.test((PrimitiveType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ProvidesDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onProvidesDirective(BiPredicate<ProvidesDirective, ReferenceHolder<V, T>> predicate) {
		processor.callProvidesDirectiveVisitor((node, holder) -> predicate.test((ProvidesDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for QualifiedName nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onQualifiedName(BiPredicate<QualifiedName, ReferenceHolder<V, T>> predicate) {
		processor.callQualifiedNameVisitor((node, holder) -> predicate.test((QualifiedName) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for QualifiedType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onQualifiedType(BiPredicate<QualifiedType, ReferenceHolder<V, T>> predicate) {
		processor.callQualifiedTypeVisitor((node, holder) -> predicate.test((QualifiedType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for RecordDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onRecordDeclaration(BiPredicate<RecordDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callRecordDeclarationVisitor((node, holder) -> predicate.test((RecordDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for RequiresDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onRequiresDirective(BiPredicate<RequiresDirective, ReferenceHolder<V, T>> predicate) {
		processor.callRequiresDirectiveVisitor((node, holder) -> predicate.test((RequiresDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SimpleType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSimpleType(BiPredicate<SimpleType, ReferenceHolder<V, T>> predicate) {
		processor.callSimpleTypeVisitor((node, holder) -> predicate.test((SimpleType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SingleMemberAnnotation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSingleMemberAnnotation(BiPredicate<SingleMemberAnnotation, ReferenceHolder<V, T>> predicate) {
		processor.callSingleMemberAnnotationVisitor((node, holder) -> predicate.test((SingleMemberAnnotation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SingleVariableDeclaration nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSingleVariableDeclaration(BiPredicate<SingleVariableDeclaration, ReferenceHolder<V, T>> predicate) {
		processor.callSingleVariableDeclarationVisitor((node, holder) -> predicate.test((SingleVariableDeclaration) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for StringLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onStringLiteral(BiPredicate<StringLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callStringLiteralVisitor((node, holder) -> predicate.test((StringLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperConstructorInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperConstructorInvocation(BiPredicate<SuperConstructorInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callSuperConstructorInvocationVisitor((node, holder) -> predicate.test((SuperConstructorInvocation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperFieldAccess nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperFieldAccess(BiPredicate<SuperFieldAccess, ReferenceHolder<V, T>> predicate) {
		processor.callSuperFieldAccessVisitor((node, holder) -> predicate.test((SuperFieldAccess) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperMethodInvocation nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperMethodInvocation(BiPredicate<SuperMethodInvocation, ReferenceHolder<V, T>> predicate) {
		processor.callSuperMethodInvocationVisitor((node, holder) -> predicate.test((SuperMethodInvocation) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SuperMethodReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSuperMethodReference(BiPredicate<SuperMethodReference, ReferenceHolder<V, T>> predicate) {
		processor.callSuperMethodReferenceVisitor((node, holder) -> predicate.test((SuperMethodReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SwitchCase nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSwitchCase(BiPredicate<SwitchCase, ReferenceHolder<V, T>> predicate) {
		processor.callSwitchCaseVisitor((node, holder) -> predicate.test((SwitchCase) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SwitchExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSwitchExpression(BiPredicate<SwitchExpression, ReferenceHolder<V, T>> predicate) {
		processor.callSwitchExpressionVisitor((node, holder) -> predicate.test((SwitchExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SwitchStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSwitchStatement(BiPredicate<SwitchStatement, ReferenceHolder<V, T>> predicate) {
		processor.callSwitchStatementVisitor((node, holder) -> predicate.test((SwitchStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for SynchronizedStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onSynchronizedStatement(BiPredicate<SynchronizedStatement, ReferenceHolder<V, T>> predicate) {
		processor.callSynchronizedStatementVisitor((node, holder) -> predicate.test((SynchronizedStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TagElement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTagElement(BiPredicate<TagElement, ReferenceHolder<V, T>> predicate) {
		processor.callTagElementVisitor((node, holder) -> predicate.test((TagElement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TextBlock nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTextBlock(BiPredicate<TextBlock, ReferenceHolder<V, T>> predicate) {
		processor.callTextBlockVisitor((node, holder) -> predicate.test((TextBlock) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TextElement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTextElement(BiPredicate<TextElement, ReferenceHolder<V, T>> predicate) {
		processor.callTextElementVisitor((node, holder) -> predicate.test((TextElement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for ThisExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onThisExpression(BiPredicate<ThisExpression, ReferenceHolder<V, T>> predicate) {
		processor.callThisExpressionVisitor((node, holder) -> predicate.test((ThisExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TryStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTryStatement(BiPredicate<TryStatement, ReferenceHolder<V, T>> predicate) {
		processor.callTryStatementVisitor((node, holder) -> predicate.test((TryStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeDeclarationStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeDeclarationStatement(BiPredicate<TypeDeclarationStatement, ReferenceHolder<V, T>> predicate) {
		processor.callTypeDeclarationStatementVisitor((node, holder) -> predicate.test((TypeDeclarationStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeLiteral nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeLiteral(BiPredicate<TypeLiteral, ReferenceHolder<V, T>> predicate) {
		processor.callTypeLiteralVisitor((node, holder) -> predicate.test((TypeLiteral) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeMethodReference nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeMethodReference(BiPredicate<TypeMethodReference, ReferenceHolder<V, T>> predicate) {
		processor.callTypeMethodReferenceVisitor((node, holder) -> predicate.test((TypeMethodReference) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for TypeParameter nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onTypeParameter(BiPredicate<TypeParameter, ReferenceHolder<V, T>> predicate) {
		processor.callTypeParameterVisitor((node, holder) -> predicate.test((TypeParameter) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for UnionType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onUnionType(BiPredicate<UnionType, ReferenceHolder<V, T>> predicate) {
		processor.callUnionTypeVisitor((node, holder) -> predicate.test((UnionType) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for UsesDirective nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onUsesDirective(BiPredicate<UsesDirective, ReferenceHolder<V, T>> predicate) {
		processor.callUsesDirectiveVisitor((node, holder) -> predicate.test((UsesDirective) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for VariableDeclarationExpression nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onVariableDeclarationExpression(BiPredicate<VariableDeclarationExpression, ReferenceHolder<V, T>> predicate) {
		processor.callVariableDeclarationExpressionVisitor((node, holder) -> predicate.test((VariableDeclarationExpression) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for VariableDeclarationStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onVariableDeclarationStatement(BiPredicate<VariableDeclarationStatement, ReferenceHolder<V, T>> predicate) {
		processor.callVariableDeclarationStatementVisitor((node, holder) -> predicate.test((VariableDeclarationStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for VisitorEnum nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onVisitorEnum(BiPredicate<VisitorEnum, ReferenceHolder<V, T>> predicate) {
		processor.callVisitorEnumVisitor((node, holder) -> predicate.test((VisitorEnum) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for WhileStatement nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onWhileStatement(BiPredicate<WhileStatement, ReferenceHolder<V, T>> predicate) {
		processor.callWhileStatementVisitor((node, holder) -> predicate.test((WhileStatement) node, holder));
		return this;
	}
	
	/**
	 * Registers a visitor for WildcardType nodes.
	 *
	 * @param predicate the predicate to test and process matching nodes
	 * @return this builder for method chaining
	 */
	public AstProcessorBuilder<V, T> onWildcardType(BiPredicate<WildcardType, ReferenceHolder<V, T>> predicate) {
		processor.callWildcardTypeVisitor((node, holder) -> predicate.test((WildcardType) node, holder));
		return this;
	}

/**
 * Registers a visitor for YieldStatement nodes.
 *
 * @param predicate the predicate to test and process matching nodes
 * @return this builder for method chaining
 */
public AstProcessorBuilder<V, T> onYieldStatement(BiPredicate<YieldStatement, ReferenceHolder<V, T>> predicate) {
processor.callYieldStatementVisitor((node, holder) -> predicate.test((YieldStatement) node, holder));
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
