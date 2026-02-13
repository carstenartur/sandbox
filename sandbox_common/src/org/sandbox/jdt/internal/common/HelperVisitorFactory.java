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
package org.sandbox.jdt.internal.common;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import org.eclipse.jdt.core.dom.*;

/**
 * Factory class containing static factory methods and convenience methods for creating
 * and invoking {@link HelperVisitor} instances.
 * 
 * <p>This class was extracted from {@link HelperVisitor} to reduce its size and improve
 * separation of concerns. It provides:</p>
 * <ul>
 * <li><b>Factory methods</b> ({@code forXxx()}) - Create fluent builder instances for common visitor patterns</li>
 * <li><b>Convenience methods</b> ({@code callXxxVisitor()}) - One-shot visitor invocations for specific AST node types</li>
 * </ul>
 * 
 * @author chammer
 * @since 1.17
 */
public class HelperVisitorFactory {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private HelperVisitorFactory() {
		// Static utility class
	}

	/**
	 * Creates a fluent builder for visiting annotations of all types.
	 * 
	 * <p>This method matches <b>all annotation types</b> regardless of whether they have parameters:</p>
	 * <ul>
	 * <li>{@code MarkerAnnotation} - annotations without parameters (e.g., {@code @Override})</li>
	 * <li>{@code SingleMemberAnnotation} - annotations with a single value (e.g., {@code @SuppressWarnings("unchecked")})</li>
	 * <li>{@code NormalAnnotation} - annotations with named parameters (e.g., {@code @RequestMapping(path="/api", method=GET)})</li>
	 * </ul>
	 * 
	 * <p><b>Note:</b> This differs from the underlying {@code callMarkerAnnotationVisitor()} method, 
	 * which only matches {@code MarkerAnnotation} nodes. This fluent API provides a more intuitive 
	 * interface by automatically handling all annotation types.</p>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * // Matches @Deprecated (MarkerAnnotation), @SuppressWarnings("unchecked") (SingleMemberAnnotation), etc.
	 * HelperVisitor.forAnnotation("java.lang.Deprecated")
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach((node, holder) -&gt; {
	 *         processNode(node, holder);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @param annotationFQN the fully qualified name of the annotation to find
	 * @return a fluent builder for annotation visitors that matches all annotation types
	 * @since 1.15
	 */
	public static AnnotationVisitorBuilder forAnnotation(String annotationFQN) {
		return new AnnotationVisitorBuilder(annotationFQN);
	}

	/**
	 * Creates a fluent builder for visiting multiple method invocations.
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * HelperVisitor.forMethodCalls("org.junit.Assert", ALL_ASSERTION_METHODS)
	 *     .andStaticImports()
	 *     .andImportsOf("org.junit.Assert")
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach((node, holder) -&gt; {
	 *         processNode(node, holder);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @param typeFQN the fully qualified name of the type containing the methods
	 * @param methodNames the set of method names to find
	 * @return a fluent builder for method invocation visitors
	 * @since 1.15
	 */
	public static MethodCallVisitorBuilder forMethodCalls(String typeFQN, Set<String> methodNames) {
		return new MethodCallVisitorBuilder(typeFQN, methodNames);
	}

	/**
	 * Creates a fluent builder for visiting a single method invocation.
	 * 
	 * <p>This method returns a type-safe builder where the processor receives 
	 * {@code MethodInvocation} directly, without requiring casts.</p>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * HelperVisitor.forMethodCall("org.junit.Assert", "assertTrue")
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach((methodInv, holder) -&gt; {
	 *         // methodInv is MethodInvocation - no cast needed!
	 *         processNode(methodInv, holder);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @param typeFQN the fully qualified name of the type containing the method
	 * @param methodName the method name to find
	 * @return a fluent builder for method invocation visitors
	 * @since 1.15
	 */
	public static SimpleMethodCallVisitorBuilder forMethodCall(String typeFQN, String methodName) {
		return new SimpleMethodCallVisitorBuilder(typeFQN, methodName);
	}
	
	/**
	 * Creates a fluent builder for visiting a single method invocation using a Class object.
	 * 
	 * <p>This method returns a type-safe builder where the processor receives 
	 * {@code MethodInvocation} directly, without requiring casts.</p>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * HelperVisitor.forMethodCall(String.class, "getBytes")
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach((methodInv, holder) -&gt; {
	 *         // methodInv is MethodInvocation - no cast needed!
	 *         processNode(methodInv, holder);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @param typeClass the class containing the method
	 * @param methodName the method name to find
	 * @return a fluent builder for method invocation visitors
	 * @since 1.16
	 */
	public static SimpleMethodCallVisitorBuilder forMethodCall(Class<?> typeClass, String methodName) {
		return new SimpleMethodCallVisitorBuilder(typeClass, methodName);
	}
	
	/**
	 * Creates a fluent builder for visiting class instance creation expressions (new expressions).
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * // Find all "new String(...)" constructions
	 * HelperVisitor.forClassInstanceCreation(String.class)
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach(holder, (creation, h) -&gt; {
	 *         processStringCreation(creation, h);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @param targetClass the class to match (e.g., String.class, FileReader.class)
	 * @return a fluent builder for class instance creation visitors
	 * @since 1.16
	 */
	public static ClassInstanceCreationVisitorBuilder forClassInstanceCreation(Class<?> targetClass) {
		return new ClassInstanceCreationVisitorBuilder(targetClass);
	}

	/**
	 * Creates a fluent builder for visiting field declarations.
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * HelperVisitor.forField()
	 *     .withAnnotation("org.junit.Rule")
	 *     .ofType("org.junit.rules.TemporaryFolder")
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach((node, holder) -&gt; {
	 *         processNode(node, holder);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @return a fluent builder for field declaration visitors
	 * @since 1.15
	 */
	public static FieldVisitorBuilder forField() {
		return new FieldVisitorBuilder();
	}

	/**
	 * Creates a fluent builder for visiting import declarations.
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>
	 * HelperVisitor.forImport("org.junit.Assert")
	 *     .in(compilationUnit)
	 *     .excluding(nodesprocessed)
	 *     .processEach((node, holder) -&gt; {
	 *         processNode(node, holder);
	 *         return true;
	 *     });
	 * </pre>
	 * 
	 * @param importFQN the fully qualified name of the import to find
	 * @return a fluent builder for import declaration visitors
	 * @since 1.15
	 */
	public static ImportVisitorBuilder forImport(String importFQN) {
		return new ImportVisitorBuilder(importFQN);
	}


	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param cu
	 * @param myset
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callVisitor(ASTNode cu, EnumSet<VisitorEnum> myset, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ASTNode, ReferenceHolder<V, T>> bs, BiConsumer<ASTNode, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V,T>, V, T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		myset.forEach(ve -> {
			hv.add(ve, bs, bc);
		});
		hv.build(cu);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param cu
	 * @param myset
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callVisitor(ASTNode cu, EnumSet<VisitorEnum> myset, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ASTNode, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		myset.forEach(ve -> {
			hv.add(ve, bs);
		});
		hv.build(cu);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param cu
	 * @param myset
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callVisitor(ASTNode cu, EnumSet<VisitorEnum> myset, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ASTNode, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		myset.forEach(ve -> {
			hv.addEnd(ve, bc);
		});
		hv.build(cu);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callAnnotationTypeDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AnnotationTypeDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnnotationTypeDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callAnnotationTypeMemberDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AnnotationTypeMemberDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnnotationTypeMemberDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callAnonymousClassDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AnonymousClassDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnonymousClassDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callArrayAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayAccess, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayAccess(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callArrayCreationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayCreation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayCreation(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callArrayInitializerVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayInitializer, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayInitializer(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callArrayTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callAssertStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AssertStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAssertStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callAssignmentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Assignment, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAssignment(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callBlockVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Block, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBlock(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callBlockCommentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<BlockComment, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBlockComment(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callBooleanLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<BooleanLiteral, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBooleanLiteral(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callBreakStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<BreakStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBreakStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callCastExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CastExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCastExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callCatchClauseVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CatchClause, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCatchClause(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callCharacterLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CharacterLiteral, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCharacterLiteral(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callClassInstanceCreationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ClassInstanceCreation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addClassInstanceCreation(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param class1
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callClassInstanceCreationVisitor(Class<?> class1, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ClassInstanceCreation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addClassInstanceCreation(class1, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callCompilationUnitVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CompilationUnit, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCompilationUnit(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callConditionalExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ConditionalExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addConditionalExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callConstructorInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ConstructorInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addConstructorInvocation(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callContinueStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ContinueStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addContinueStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callCreationReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CreationReference, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCreationReference(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callDimensionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Dimension, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addDimension(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callDoStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<DoStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addDoStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callEmptyStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EmptyStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEmptyStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callEnhancedForStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EnhancedForStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnhancedForStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callEnumConstantDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EnumConstantDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnumConstantDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callEnumDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EnumDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnumDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callExportsDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ExportsDirective, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExportsDirective(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callExpressionMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ExpressionMethodReference, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExpressionMethodReference(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callExpressionStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ExpressionStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExpressionStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callFieldAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<FieldAccess, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldAccess(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callFieldDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<FieldDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldDeclaration(bs);
		hv.build(node);
	}
	
	/**
	 * @param <V>
	 * @param <T>
	 * @param annotationname 
	 * @param withsuperclass
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callFieldDeclarationVisitor(String annotationname, String withsuperclass, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<FieldDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldDeclaration(annotationname, withsuperclass, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callForStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ForStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addForStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callIfStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<IfStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addIfStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callImportDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ImportDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addImportDeclaration(bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param importname
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callImportDeclarationVisitor(String importname, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ImportDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addImportDeclaration(importname, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callInfixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<InfixExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInfixExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callInitializerVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Initializer, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInitializer(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callInstanceofExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<InstanceofExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInstanceofExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callIntersectionTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<IntersectionType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addIntersectionType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callJavadocVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Javadoc, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addJavadoc(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callLabeledStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<LabeledStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLabeledStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callLambdaExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<LambdaExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLambdaExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callLineCommentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<LineComment, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLineComment(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMarkerAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MarkerAnnotation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMarkerAnnotation(bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param name
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMarkerAnnotationVisitor(String name, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MarkerAnnotation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMarkerAnnotation(name, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMemberRefVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MemberRef, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMemberRef(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMemberValuePairVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MemberValuePair, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMemberValuePair(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodRefVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodRef, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodRef(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodRefParameterVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodRefParameter, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodRefParameter(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param methodname
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodInvocationVisitor(String methodname, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(methodname, bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param methodof
	 * @param methodname
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodInvocationVisitor(Class<?> methodof, String methodname, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(methodof, methodname, bs);
		hv.build(node);
	}
	
	/**
	 * @param <V>
	 * @param <T>
	 * @param methodof
	 * @param methodname
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callMethodInvocationVisitor(String methodof, String methodname, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(methodof, methodname, bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param methodof
	 * @param methodname
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param params
	 */
	public static <V, T> void callMethodInvocationVisitor(String methodof, String methodname, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodInvocation, ReferenceHolder<V, T>> bs, String[] params) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(methodof, methodname, bs, params);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callModifierVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Modifier, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModifier(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callModuleDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ModuleDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModuleDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callModuleModifierVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ModuleModifier, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModuleModifier(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callNameQualifiedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NameQualifiedType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNameQualifiedType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callNormalAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NormalAnnotation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNormalAnnotation(bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param name
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callNormalAnnotationVisitor(String name, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NormalAnnotation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNormalAnnotation(name, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callNullLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NullLiteral, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNullLiteral(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callNumberLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NumberLiteral, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNumberLiteral(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callOpensDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<OpensDirective, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addOpensDirective(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callPackageDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PackageDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPackageDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callParameterizedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ParameterizedType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addParameterizedType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callParenthesizedExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ParenthesizedExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addParenthesizedExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callPatternInstanceofExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PatternInstanceofExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPatternInstanceofExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callPostfixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PostfixExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPostfixExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callPrefixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PrefixExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPrefixExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callProvidesDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ProvidesDirective, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addProvidesDirective(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callPrimitiveTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PrimitiveType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPrimitiveType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callQualifiedNameVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<QualifiedName, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addQualifiedName(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callQualifiedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<QualifiedType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addQualifiedType(bs);
		hv.build(node);
	}

	//	public static <V,T> void callModuleQualifiedNameVisitor(ASTNode node, ReferenceHolder<V,T> dataholder, BiPredicate<ModuleQualifiedName, ReferenceHolder<V,T>> bs) {  HelperVisitor<ReferenceHolder<V,T>> hv = new HelperVisitor<>(nodesprocessed, dataholder); hv.addModuleQualifiedName(bs); hv.build(node);}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */

	public static <V, T> void callRequiresDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<RequiresDirective, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addRequiresDirective(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callRecordDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<RecordDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addRecordDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callReturnStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ReturnStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addReturnStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSimpleNameVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SimpleName, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSimpleName(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSimpleTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SimpleType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSimpleType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSingleMemberAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SingleMemberAnnotation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleMemberAnnotation(bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param name
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSingleMemberAnnotationVisitor(String name, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SingleMemberAnnotation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleMemberAnnotation(name, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSingleVariableDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SingleVariableDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleVariableDeclaration(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callStringLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<StringLiteral, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addStringLiteral(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSuperConstructorInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperConstructorInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperConstructorInvocation(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSuperFieldAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperFieldAccess, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperFieldAccess(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSuperMethodInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperMethodInvocation, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperMethodInvocation(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSuperMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperMethodReference, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperMethodReference(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSwitchCaseVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SwitchCase, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchCase(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSwitchExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SwitchExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSwitchStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SwitchStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callSynchronizedStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SynchronizedStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSynchronizedStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTagElementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TagElement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTagElement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTextBlockVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TextBlock, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTextBlock(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTextElementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TextElement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTextElement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callThisExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ThisExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addThisExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callThrowStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ThrowStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addThrowStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTryStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TryStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTryStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTypeDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclaration(bs);
		hv.build(node);
	}

	/**
	 * @param <V>
	 * @param <T>
	 * @param derivedfrom
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTypeDeclarationVisitor(String derivedfrom, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeDeclaration, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclaration(derivedfrom, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTypeDeclarationStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeDeclarationStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclarationStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTypeLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeLiteral, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeLiteral(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTypeMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeMethodReference, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeMethodReference(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callTypeParameterVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeParameter, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeParameter(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callUnionTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<UnionType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addUnionType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callUsesDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<UsesDirective, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addUsesDirective(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callVariableDeclarationExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationExpression, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationExpression(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callVariableDeclarationStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param class1
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callVariableDeclarationStatementVisitor(Class<?> class1, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationStatement(class1, bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callVariableDeclarationFragmentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationFragment, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationFragment(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callWhileStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<WhileStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addWhileStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callWildcardTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<WildcardType, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addWildcardType(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 */
	public static <V, T> void callYieldStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<YieldStatement, ReferenceHolder<V, T>> bs) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addYieldStatement(bs);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callAnnotationTypeDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<AnnotationTypeDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnnotationTypeDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callAnnotationTypeMemberDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<AnnotationTypeMemberDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnnotationTypeMemberDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callAnonymousClassDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<AnonymousClassDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnonymousClassDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callArrayAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ArrayAccess, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayAccess(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callArrayCreationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ArrayCreation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayCreation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callArrayInitializerVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ArrayInitializer, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayInitializer(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callArrayTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ArrayType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callAssertStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<AssertStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAssertStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callAssignmentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<Assignment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAssignment(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callBlockVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<Block, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBlock(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callBlockCommentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<BlockComment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBlockComment(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callBooleanLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<BooleanLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBooleanLiteral(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callBreakStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<BreakStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBreakStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callCastExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<CastExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCastExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callCatchClauseVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<CatchClause, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCatchClause(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callCharacterLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<CharacterLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCharacterLiteral(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callClassInstanceCreationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ClassInstanceCreation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addClassInstanceCreation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callCompilationUnitVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<CompilationUnit, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCompilationUnit(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callConditionalExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ConditionalExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addConditionalExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callConstructorInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ConstructorInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addConstructorInvocation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callContinueStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ContinueStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addContinueStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callCreationReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<CreationReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCreationReference(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callDimensionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<Dimension, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addDimension(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callDoStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<DoStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addDoStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callEmptyStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<EmptyStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEmptyStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callEnhancedForStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<EnhancedForStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnhancedForStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callEnumConstantDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<EnumConstantDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnumConstantDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callEnumDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<EnumDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnumDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callExportsDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ExportsDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExportsDirective(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callExpressionMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ExpressionMethodReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExpressionMethodReference(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callExpressionStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ExpressionStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExpressionStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callFieldAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<FieldAccess, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldAccess(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callFieldDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<FieldDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callForStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ForStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addForStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callIfStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<IfStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addIfStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callImportDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ImportDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addImportDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callInfixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<InfixExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInfixExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callInitializerVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<Initializer, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInitializer(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callInstanceofExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<InstanceofExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInstanceofExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callIntersectionTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<IntersectionType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addIntersectionType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callJavadocVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<Javadoc, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addJavadoc(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callLabeledStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<LabeledStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLabeledStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callLambdaExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<LambdaExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLambdaExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callLineCommentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<LineComment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLineComment(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMarkerAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MarkerAnnotation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMarkerAnnotation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMemberRefVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MemberRef, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMemberRef(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMemberValuePairVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MemberValuePair, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMemberValuePair(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMethodRefVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MethodRef, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodRef(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMethodRefParameterVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MethodRefParameter, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodRefParameter(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMethodDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MethodDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callMethodInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<MethodInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callModifierVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<Modifier, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModifier(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callModuleDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ModuleDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModuleDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callModuleModifierVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ModuleModifier, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModuleModifier(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callNameQualifiedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<NameQualifiedType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNameQualifiedType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callNormalAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<NormalAnnotation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNormalAnnotation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callNullLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<NullLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNullLiteral(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callNumberLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<NumberLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNumberLiteral(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callOpensDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<OpensDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addOpensDirective(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callPackageDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<PackageDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPackageDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callParameterizedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ParameterizedType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addParameterizedType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callParenthesizedExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ParenthesizedExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addParenthesizedExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callPatternInstanceofExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<PatternInstanceofExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPatternInstanceofExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callPostfixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<PostfixExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPostfixExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callPrefixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<PrefixExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPrefixExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callProvidesDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ProvidesDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addProvidesDirective(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callPrimitiveTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<PrimitiveType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPrimitiveType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callQualifiedNameVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<QualifiedName, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addQualifiedName(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callQualifiedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<QualifiedType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addQualifiedType(bc);
		hv.build(node);
	}

	//	public static <V,T> void callModuleQualifiedNameVisitor(ASTNode node, ReferenceHolder<V,T> dataholder, BiConsumer<ModuleQualifiedName, ReferenceHolder<V,T>> bc) {  HelperVisitor<ReferenceHolder<V,T>> hv = new HelperVisitor<>(nodesprocessed, dataholder); hv.addModuleQualifiedName(bc); hv.build(node);}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */

	public static <V, T> void callRequiresDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<RequiresDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addRequiresDirective(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callRecordDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<RecordDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addRecordDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callReturnStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ReturnStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addReturnStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSimpleNameVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SimpleName, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSimpleName(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSimpleTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SimpleType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSimpleType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSingleMemberAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SingleMemberAnnotation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleMemberAnnotation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSingleVariableDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SingleVariableDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleVariableDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callStringLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<StringLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addStringLiteral(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSuperConstructorInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SuperConstructorInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperConstructorInvocation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSuperFieldAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SuperFieldAccess, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperFieldAccess(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSuperMethodInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SuperMethodInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperMethodInvocation(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSuperMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SuperMethodReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperMethodReference(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSwitchCaseVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SwitchCase, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchCase(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSwitchExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SwitchExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSwitchStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SwitchStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callSynchronizedStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<SynchronizedStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSynchronizedStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTagElementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TagElement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTagElement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTextBlockVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TextBlock, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTextBlock(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTextElementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TextElement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTextElement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callThisExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ThisExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addThisExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callThrowStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<ThrowStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addThrowStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTryStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TryStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTryStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTypeDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TypeDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclaration(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTypeDeclarationStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TypeDeclarationStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclarationStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTypeLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TypeLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeLiteral(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTypeMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TypeMethodReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeMethodReference(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callTypeParameterVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<TypeParameter, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeParameter(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callUnionTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<UnionType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addUnionType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callUsesDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<UsesDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addUsesDirective(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<VariableDeclarationExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationExpression(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<VariableDeclarationStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param class1
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationStatementVisitor(Class<?> class1, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<VariableDeclarationStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationStatement(class1, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationFragmentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<VariableDeclarationFragment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationFragment(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callWhileStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<WhileStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addWhileStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callWildcardTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<WildcardType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addWildcardType(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bc
	 */
	public static <V, T> void callYieldStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiConsumer<YieldStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addYieldStatement(bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callAnnotationTypeDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AnnotationTypeDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<AnnotationTypeDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnnotationTypeDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callAnnotationTypeMemberDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AnnotationTypeMemberDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<AnnotationTypeMemberDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnnotationTypeMemberDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callAnonymousClassDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AnonymousClassDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<AnonymousClassDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAnonymousClassDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callArrayAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayAccess, ReferenceHolder<V, T>> bs, BiConsumer<ArrayAccess, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayAccess(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callArrayCreationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayCreation, ReferenceHolder<V, T>> bs, BiConsumer<ArrayCreation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayCreation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callArrayInitializerVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayInitializer, ReferenceHolder<V, T>> bs,
			BiConsumer<ArrayInitializer, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayInitializer(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callArrayTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ArrayType, ReferenceHolder<V, T>> bs, BiConsumer<ArrayType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addArrayType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callAssertStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<AssertStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<AssertStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAssertStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callAssignmentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Assignment, ReferenceHolder<V, T>> bs, BiConsumer<Assignment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addAssignment(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callBlockVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Block, ReferenceHolder<V, T>> bs, BiConsumer<Block, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBlock(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callBlockCommentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<BlockComment, ReferenceHolder<V, T>> bs, BiConsumer<BlockComment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBlockComment(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callBooleanLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<BooleanLiteral, ReferenceHolder<V, T>> bs,
			BiConsumer<BooleanLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBooleanLiteral(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callBreakStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<BreakStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<BreakStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addBreakStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callCastExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CastExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<CastExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCastExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callCatchClauseVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CatchClause, ReferenceHolder<V, T>> bs, BiConsumer<CatchClause, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCatchClause(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callCharacterLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CharacterLiteral, ReferenceHolder<V, T>> bs,
			BiConsumer<CharacterLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCharacterLiteral(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callClassInstanceCreationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ClassInstanceCreation, ReferenceHolder<V, T>> bs,
			BiConsumer<ClassInstanceCreation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addClassInstanceCreation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callCompilationUnitVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CompilationUnit, ReferenceHolder<V, T>> bs,
			BiConsumer<CompilationUnit, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCompilationUnit(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callConditionalExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ConditionalExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<ConditionalExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addConditionalExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callConstructorInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ConstructorInvocation, ReferenceHolder<V, T>> bs,
			BiConsumer<ConstructorInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addConstructorInvocation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callContinueStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ContinueStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<ContinueStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addContinueStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callCreationReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<CreationReference, ReferenceHolder<V, T>> bs,
			BiConsumer<CreationReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addCreationReference(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callDimensionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Dimension, ReferenceHolder<V, T>> bs, BiConsumer<Dimension, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addDimension(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callDoStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<DoStatement, ReferenceHolder<V, T>> bs, BiConsumer<DoStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addDoStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callEmptyStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EmptyStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<EmptyStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEmptyStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callEnhancedForStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EnhancedForStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<EnhancedForStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnhancedForStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callEnumConstantDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EnumConstantDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<EnumConstantDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnumConstantDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callEnumDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<EnumDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<EnumDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addEnumDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callExportsDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ExportsDirective, ReferenceHolder<V, T>> bs,
			BiConsumer<ExportsDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExportsDirective(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callExpressionMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ExpressionMethodReference, ReferenceHolder<V, T>> bs,
			BiConsumer<ExpressionMethodReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExpressionMethodReference(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callExpressionStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ExpressionStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<ExpressionStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addExpressionStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callFieldAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<FieldAccess, ReferenceHolder<V, T>> bs, BiConsumer<FieldAccess, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldAccess(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callFieldDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<FieldDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<FieldDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addFieldDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callForStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ForStatement, ReferenceHolder<V, T>> bs, BiConsumer<ForStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addForStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callIfStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<IfStatement, ReferenceHolder<V, T>> bs, BiConsumer<IfStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addIfStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callImportDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ImportDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<ImportDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addImportDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callInfixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<InfixExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<InfixExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInfixExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callInitializerVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Initializer, ReferenceHolder<V, T>> bs, BiConsumer<Initializer, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInitializer(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callInstanceofExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<InstanceofExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<InstanceofExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addInstanceofExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callIntersectionTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<IntersectionType, ReferenceHolder<V, T>> bs,
			BiConsumer<IntersectionType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addIntersectionType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callJavadocVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Javadoc, ReferenceHolder<V, T>> bs, BiConsumer<Javadoc, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addJavadoc(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callLabeledStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<LabeledStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<LabeledStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLabeledStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callLambdaExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<LambdaExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<LambdaExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLambdaExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callLineCommentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<LineComment, ReferenceHolder<V, T>> bs, BiConsumer<LineComment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addLineComment(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMarkerAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MarkerAnnotation, ReferenceHolder<V, T>> bs,
			BiConsumer<MarkerAnnotation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMarkerAnnotation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMemberRefVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MemberRef, ReferenceHolder<V, T>> bs, BiConsumer<MemberRef, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMemberRef(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMemberValuePairVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MemberValuePair, ReferenceHolder<V, T>> bs,
			BiConsumer<MemberValuePair, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMemberValuePair(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMethodRefVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodRef, ReferenceHolder<V, T>> bs, BiConsumer<MethodRef, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodRef(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMethodRefParameterVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodRefParameter, ReferenceHolder<V, T>> bs,
			BiConsumer<MethodRefParameter, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodRefParameter(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMethodDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<MethodDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callMethodInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<MethodInvocation, ReferenceHolder<V, T>> bs,
			BiConsumer<MethodInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addMethodInvocation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callModifierVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<Modifier, ReferenceHolder<V, T>> bs, BiConsumer<Modifier, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModifier(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callModuleDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ModuleDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<ModuleDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModuleDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callModuleModifierVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ModuleModifier, ReferenceHolder<V, T>> bs,
			BiConsumer<ModuleModifier, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addModuleModifier(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callNameQualifiedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NameQualifiedType, ReferenceHolder<V, T>> bs,
			BiConsumer<NameQualifiedType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNameQualifiedType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callNormalAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NormalAnnotation, ReferenceHolder<V, T>> bs,
			BiConsumer<NormalAnnotation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNormalAnnotation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callNullLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NullLiteral, ReferenceHolder<V, T>> bs, BiConsumer<NullLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNullLiteral(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callNumberLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<NumberLiteral, ReferenceHolder<V, T>> bs, BiConsumer<NumberLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addNumberLiteral(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callOpensDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<OpensDirective, ReferenceHolder<V, T>> bs,
			BiConsumer<OpensDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addOpensDirective(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callPackageDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PackageDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<PackageDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPackageDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callParameterizedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ParameterizedType, ReferenceHolder<V, T>> bs,
			BiConsumer<ParameterizedType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addParameterizedType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callParenthesizedExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ParenthesizedExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<ParenthesizedExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addParenthesizedExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callPatternInstanceofExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PatternInstanceofExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<PatternInstanceofExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPatternInstanceofExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callPostfixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PostfixExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<PostfixExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPostfixExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callPrefixExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PrefixExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<PrefixExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPrefixExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callProvidesDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ProvidesDirective, ReferenceHolder<V, T>> bs,
			BiConsumer<ProvidesDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addProvidesDirective(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callPrimitiveTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<PrimitiveType, ReferenceHolder<V, T>> bs, BiConsumer<PrimitiveType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addPrimitiveType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callQualifiedNameVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<QualifiedName, ReferenceHolder<V, T>> bs, BiConsumer<QualifiedName, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addQualifiedName(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callQualifiedTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<QualifiedType, ReferenceHolder<V, T>> bs, BiConsumer<QualifiedType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addQualifiedType(bs, bc);
		hv.build(node);
	}

	//	public static <V,T> void callModuleQualifiedNameVisitor(ASTNode node, ReferenceHolder<V,T> dataholder, BiPredicate<ModuleQualifiedName, ReferenceHolder<V,T>> bs, BiConsumer<ModuleQualifiedName, ReferenceHolder<V,T>> bc) {  HelperVisitor<ReferenceHolder<V,T>> hv = new HelperVisitor<>(nodesprocessed, dataholder); hv.addModuleQualifiedName(bs,bc); hv.build(node);}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */

	public static <V, T> void callRequiresDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<RequiresDirective, ReferenceHolder<V, T>> bs,
			BiConsumer<RequiresDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addRequiresDirective(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callRecordDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<RecordDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<RecordDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addRecordDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callReturnStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ReturnStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<ReturnStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addReturnStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSimpleNameVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SimpleName, ReferenceHolder<V, T>> bs, BiConsumer<SimpleName, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSimpleName(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSimpleTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SimpleType, ReferenceHolder<V, T>> bs, BiConsumer<SimpleType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSimpleType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSingleMemberAnnotationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SingleMemberAnnotation, ReferenceHolder<V, T>> bs,
			BiConsumer<SingleMemberAnnotation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleMemberAnnotation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSingleVariableDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SingleVariableDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<SingleVariableDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSingleVariableDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callStringLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<StringLiteral, ReferenceHolder<V, T>> bs, BiConsumer<StringLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addStringLiteral(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSuperConstructorInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperConstructorInvocation, ReferenceHolder<V, T>> bs,
			BiConsumer<SuperConstructorInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperConstructorInvocation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSuperFieldAccessVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperFieldAccess, ReferenceHolder<V, T>> bs,
			BiConsumer<SuperFieldAccess, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperFieldAccess(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSuperMethodInvocationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperMethodInvocation, ReferenceHolder<V, T>> bs,
			BiConsumer<SuperMethodInvocation, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperMethodInvocation(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSuperMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SuperMethodReference, ReferenceHolder<V, T>> bs,
			BiConsumer<SuperMethodReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSuperMethodReference(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSwitchCaseVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SwitchCase, ReferenceHolder<V, T>> bs, BiConsumer<SwitchCase, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchCase(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSwitchExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SwitchExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<SwitchExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSwitchStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SwitchStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<SwitchStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSwitchStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callSynchronizedStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<SynchronizedStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<SynchronizedStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addSynchronizedStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTagElementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TagElement, ReferenceHolder<V, T>> bs, BiConsumer<TagElement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTagElement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTextBlockVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TextBlock, ReferenceHolder<V, T>> bs, BiConsumer<TextBlock, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTextBlock(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTextElementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TextElement, ReferenceHolder<V, T>> bs, BiConsumer<TextElement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTextElement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callThisExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ThisExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<ThisExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addThisExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callThrowStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<ThrowStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<ThrowStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addThrowStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTryStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TryStatement, ReferenceHolder<V, T>> bs, BiConsumer<TryStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTryStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTypeDeclarationVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeDeclaration, ReferenceHolder<V, T>> bs,
			BiConsumer<TypeDeclaration, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclaration(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTypeDeclarationStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeDeclarationStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<TypeDeclarationStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeDeclarationStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTypeLiteralVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeLiteral, ReferenceHolder<V, T>> bs, BiConsumer<TypeLiteral, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeLiteral(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTypeMethodReferenceVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeMethodReference, ReferenceHolder<V, T>> bs,
			BiConsumer<TypeMethodReference, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeMethodReference(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callTypeParameterVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<TypeParameter, ReferenceHolder<V, T>> bs, BiConsumer<TypeParameter, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addTypeParameter(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callUnionTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<UnionType, ReferenceHolder<V, T>> bs, BiConsumer<UnionType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addUnionType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callUsesDirectiveVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<UsesDirective, ReferenceHolder<V, T>> bs, BiConsumer<UsesDirective, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addUsesDirective(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationExpressionVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationExpression, ReferenceHolder<V, T>> bs,
			BiConsumer<VariableDeclarationExpression, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationExpression(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<VariableDeclarationStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param class1
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationStatementVisitor(Class<?> class1, ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<VariableDeclarationStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationStatement(class1, bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callVariableDeclarationFragmentVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<VariableDeclarationFragment, ReferenceHolder<V, T>> bs,
			BiConsumer<VariableDeclarationFragment, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addVariableDeclarationFragment(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callWhileStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<WhileStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<WhileStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addWhileStatement(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callWildcardTypeVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<WildcardType, ReferenceHolder<V, T>> bs, BiConsumer<WildcardType, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addWildcardType(bs, bc);
		hv.build(node);
	}

	/**
	 *
	 * @param <V>
	 * @param <T>
	 * @param node
	 * @param dataholder
	 * @param nodesprocessed
	 * @param bs
	 * @param bc
	 */
	public static <V, T> void callYieldStatementVisitor(ASTNode node, ReferenceHolder<V, T> dataholder, Set<ASTNode> nodesprocessed,
			BiPredicate<YieldStatement, ReferenceHolder<V, T>> bs,
			BiConsumer<YieldStatement, ReferenceHolder<V, T>> bc) {

		HelperVisitor<ReferenceHolder<V, T>,V,T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		hv.addYieldStatement(bs, bc);
		hv.build(node);
	}
}
