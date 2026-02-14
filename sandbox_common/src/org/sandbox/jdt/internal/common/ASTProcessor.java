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


import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * Processor for building and executing AST visitor chains.
 * This class provides a fluent API for configuring visitor callbacks for different AST node types
 * and then building a visitor chain that processes the AST tree.
 * 
 * <p>The processor maintains a linked map of visitor callbacks associated with specific AST node types
 * (represented by {@link VisitorEnum}). When {@link #build(ASTNode)} is called, the processor creates
 * a visitor chain that processes nodes in the order the callbacks were registered.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>
 * ASTProcessor&lt;MyDataHolder, String, Object&gt; processor = new ASTProcessor&lt;&gt;(dataHolder, processedNodes);
 * processor
 *     .callMethodInvocationVisitor((node, holder) -&gt; {
 *         // Process method invocation
 *         return true;
 *     })
 *     .callFieldDeclarationVisitor((node, holder) -&gt; {
 *         // Process field declaration
 *         return true;
 *     })
 *     .build(compilationUnit);
 * </pre>
 *
 * @param <E> the type of data holder that implements {@link HelperVisitorProvider}
 * @param <V> the type of keys in the data holder
 * @param <T> the type of values in the data holder
 * 
 * @author chammer
 * @since 1.15
 */
public class ASTProcessor<E extends HelperVisitorProvider<V, T, E>, V, T> {

	class NodeHolder {
		@SuppressWarnings("unchecked")
		public NodeHolder(BiPredicate<? extends ASTNode, E> callee, Function<ASTNode, ASTNode> navigate) {
			this.callee= (BiPredicate<ASTNode, E>) callee;
			this.navigate= navigate;
		}

		@SuppressWarnings("unchecked")
		public NodeHolder(BiPredicate<? extends ASTNode, E> callee, Function<ASTNode, ASTNode> navigate, VisitorConfigData object) {
			this.callee= (BiPredicate<ASTNode, E>) callee;
			this.navigate= navigate;
			this.object= object;
		}

		@SuppressWarnings("unchecked")
		public NodeHolder(BiConsumer<? extends ASTNode, E> callee_end) {
			this.callee_end= (BiConsumer<ASTNode, E>) callee_end;
			//			this.object= object;
		}

		public BiPredicate<ASTNode, E> callee;

		public BiConsumer<ASTNode, E> callee_end;

		public Function<ASTNode, ASTNode> navigate;

		public VisitorConfigData object;
	}

	private final LinkedHashMap<VisitorEnum, NodeHolder> nodetypelist;

	E dataholder;

	Set<ASTNode> nodesprocessed;

	LinkedList<VisitorEnum> nodetypekeylist;

	/**
	 *
	 * @param dataholder
	 * @param nodesprocessed
	 */
	public ASTProcessor(E dataholder, Set<ASTNode> nodesprocessed) {
		this.dataholder= dataholder;
		this.nodesprocessed= nodesprocessed;
		this.nodetypelist= new LinkedHashMap<>();
	}

	/**
	 * Generic visitor registration using a VisitorEnum type.
	 * This allows registering a visitor for any AST node type without
	 * needing a type-specific method.
	 *
	 * @param visitorType the VisitorEnum identifying the node type
	 * @param bs the predicate to test and process matching nodes
	 * @return a reference to this object
	 */
	public ASTProcessor<E, V, T> callVisitor(VisitorEnum visitorType, BiPredicate<ASTNode, E> bs) {
		nodetypelist.put(visitorType, new NodeHolder(bs, null));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object. a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAnnotationTypeDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callAnnotationTypeDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAnnotationTypeDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.AnnotationTypeDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAnnotationTypeMemberDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callAnnotationTypeMemberDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAnnotationTypeMemberDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.AnnotationTypeMemberDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAnonymousClassDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callAnonymousClassDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAnonymousClassDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.AnonymousClassDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayAccessVisitor(BiPredicate<ASTNode, E> bs) {
		return callArrayAccessVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayAccessVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ArrayAccess, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayCreationVisitor(BiPredicate<ASTNode, E> bs) {
		return callArrayCreationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayCreationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ArrayCreation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayInitializerVisitor(BiPredicate<ASTNode, E> bs) {
		return callArrayInitializerVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayInitializerVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ArrayInitializer, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callArrayTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callArrayTypeVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ArrayType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAssertStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callAssertStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAssertStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.AssertStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAssignmentVisitor(BiPredicate<ASTNode, E> bs) {
		return callAssignmentVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAssignmentVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.Assignment, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters Assignment nodes by operator (e.g., PLUS_ASSIGN for +=).
	 *
	 * @param operator the operator to filter for (e.g., Assignment.Operator.PLUS_ASSIGN)
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAssignmentVisitor(org.eclipse.jdt.core.dom.Assignment.Operator operator, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.operator(operator.toString())
				.build();
		nodetypelist.put(VisitorEnum.Assignment, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters Assignment nodes by operator and allows navigation.
	 *
	 * @param operator the operator to filter for (e.g., Assignment.Operator.PLUS_ASSIGN)
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callAssignmentVisitor(org.eclipse.jdt.core.dom.Assignment.Operator operator, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.operator(operator.toString())
				.build();
		nodetypelist.put(VisitorEnum.Assignment, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBlockVisitor(BiPredicate<ASTNode, E> bs) {
		return callBlockVisitor(bs, null);
	}

	/**
	 *
	 * @param bc
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBlockVisitor(BiConsumer<ASTNode, E> bc) {
		nodetypelist.put(VisitorEnum.Block, new NodeHolder(bc));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBlockVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.Block, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBlockCommentVisitor(BiPredicate<ASTNode, E> bs) {
		return callBlockCommentVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBlockCommentVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.BlockComment, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBooleanLiteralVisitor(BiPredicate<ASTNode, E> bs) {
		return callBooleanLiteralVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBooleanLiteralVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.BooleanLiteral, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBreakStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callBreakStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callBreakStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.BreakStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCastExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callCastExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCastExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.CastExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCatchClauseVisitor(BiPredicate<ASTNode, E> bs) {
		return callCatchClauseVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCatchClauseVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.CatchClause, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters CatchClause nodes by exception type.
	 *
	 * @param exceptionType the type of exception to filter for
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCatchClauseVisitor(Class<?> exceptionType, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.exceptionType(exceptionType)
				.build();
		nodetypelist.put(VisitorEnum.CatchClause, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters CatchClause nodes by exception type and allows navigation.
	 *
	 * @param exceptionType the type of exception to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCatchClauseVisitor(Class<?> exceptionType, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.exceptionType(exceptionType)
				.build();
		nodetypelist.put(VisitorEnum.CatchClause, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCharacterLiteralVisitor(BiPredicate<ASTNode, E> bs) {
		return callCharacterLiteralVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCharacterLiteralVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.CharacterLiteral, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callClassInstanceCreationVisitor(BiPredicate<ClassInstanceCreation, E> bs) {
		return callClassInstanceCreationVisitor(bs, null);
	}

	/**
	 *
	 * @param typeof
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callClassInstanceCreationVisitor(Class<?> typeof,BiPredicate<ClassInstanceCreation, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.ClassInstanceCreation, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters ClassInstanceCreation nodes by the fully qualified type name being instantiated.
	 * 
	 * <p>This overload accepts a String instead of a Class to avoid deprecation warnings
	 * when the class being filtered is deprecated (e.g., SubProgressMonitor).</p>
	 *
	 * @param qualifiedTypeName the fully qualified class name to filter for (e.g., "org.eclipse.core.runtime.SubProgressMonitor")
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 * @since 1.2.5
	 */
	public ASTProcessor<E, V, T> callClassInstanceCreationVisitor(String qualifiedTypeName, BiPredicate<ClassInstanceCreation, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeofByName(qualifiedTypeName)
				.build();
		nodetypelist.put(VisitorEnum.ClassInstanceCreation, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callClassInstanceCreationVisitor(BiPredicate<ClassInstanceCreation, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ClassInstanceCreation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters ClassInstanceCreation nodes by the type being instantiated and allows navigation.
	 *
	 * @param typeof the class type to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callClassInstanceCreationVisitor(Class<?> typeof, BiPredicate<ClassInstanceCreation, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.ClassInstanceCreation, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 * Filters ClassInstanceCreation nodes by the fully qualified type name being instantiated.
	 * 
	 * <p>This overload accepts a String instead of a Class to avoid deprecation warnings
	 * when the class being filtered is deprecated (e.g., SubProgressMonitor).</p>
	 *
	 * @param qualifiedTypeName the fully qualified class name to filter for (e.g., "org.eclipse.core.runtime.SubProgressMonitor")
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 * @since 1.2.5
	 */
	public ASTProcessor<E, V, T> callClassInstanceCreationVisitor(String qualifiedTypeName, BiPredicate<ClassInstanceCreation, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeofByName(qualifiedTypeName)
				.build();
		nodetypelist.put(VisitorEnum.ClassInstanceCreation, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCompilationUnitVisitor(BiPredicate<ASTNode, E> bs) {
		return callCompilationUnitVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCompilationUnitVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.CompilationUnit, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callConditionalExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callConditionalExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callConditionalExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ConditionalExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callConstructorInvocationVisitor(BiPredicate<ASTNode, E> bs) {
		return callConstructorInvocationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callConstructorInvocationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ConstructorInvocation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callContinueStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callContinueStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callContinueStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ContinueStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCreationReferenceVisitor(BiPredicate<ASTNode, E> bs) {
		return callCreationReferenceVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callCreationReferenceVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.CreationReference, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callDimensionVisitor(BiPredicate<ASTNode, E> bs) {
		return callDimensionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callDimensionVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.Dimension, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callDoStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callDoStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callDoStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.DoStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEmptyStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callEmptyStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEmptyStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.EmptyStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEnhancedForStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callEnhancedForStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEnhancedForStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.EnhancedForStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEnumConstantDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callEnumConstantDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEnumConstantDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.EnumConstantDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEnumDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callEnumDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callEnumDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.EnumDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callExportsDirectiveVisitor(BiPredicate<ASTNode, E> bs) {
		return callExportsDirectiveVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callExportsDirectiveVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ExportsDirective, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callExpressionMethodReferenceVisitor(BiPredicate<ASTNode, E> bs) {
		return callExpressionMethodReferenceVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callExpressionMethodReferenceVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ExpressionMethodReference, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callExpressionStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callExpressionStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callExpressionStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ExpressionStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callFieldAccessVisitor(BiPredicate<ASTNode, E> bs) {
		return callFieldAccessVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callFieldAccessVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.FieldAccess, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callFieldDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callFieldDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callFieldDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.FieldDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters FieldDeclaration nodes by field type.
	 *
	 * @param typeof the type of the field to filter for
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callFieldDeclarationVisitor(Class<?> typeof, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.FieldDeclaration, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters FieldDeclaration nodes by field type and allows navigation.
	 *
	 * @param typeof the type of the field to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callFieldDeclarationVisitor(Class<?> typeof, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.FieldDeclaration, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callForStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callForStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callForStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ForStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters ForStatement nodes by the type of loop variable (useful for Iterator patterns).
	 *
	 * @param typeof the type of the loop variable to filter for
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callForStatementVisitor(Class<?> typeof, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.ForStatement, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters ForStatement nodes by the type of loop variable and allows navigation.
	 *
	 * @param typeof the type of the loop variable to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callForStatementVisitor(Class<?> typeof, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.ForStatement, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callIfStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callIfStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callIfStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.IfStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callImportDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callImportDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callImportDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ImportDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInfixExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callInfixExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInfixExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.InfixExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters InfixExpression nodes by operator (e.g., PLUS for string concatenation).
	 *
	 * @param operator the operator to filter for (e.g., InfixExpression.Operator.PLUS)
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInfixExpressionVisitor(org.eclipse.jdt.core.dom.InfixExpression.Operator operator, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.operator(operator.toString())
				.build();
		nodetypelist.put(VisitorEnum.InfixExpression, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters InfixExpression nodes by operator and allows navigation.
	 *
	 * @param operator the operator to filter for (e.g., InfixExpression.Operator.PLUS)
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInfixExpressionVisitor(org.eclipse.jdt.core.dom.InfixExpression.Operator operator, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.operator(operator.toString())
				.build();
		nodetypelist.put(VisitorEnum.InfixExpression, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInitializerVisitor(BiPredicate<ASTNode, E> bs) {
		return callInitializerVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInitializerVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.Initializer, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInstanceofExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callInstanceofExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callInstanceofExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.InstanceofExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callIntersectionTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callIntersectionTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callIntersectionTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.IntersectionType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callJavadocVisitor(BiPredicate<ASTNode, E> bs) {
		return callJavadocVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callJavadocVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.Javadoc, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callLabeledStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callLabeledStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callLabeledStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.LabeledStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callLambdaExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callLambdaExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callLambdaExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.LambdaExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callLineCommentVisitor(BiPredicate<ASTNode, E> bs) {
		return callLineCommentVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callLineCommentVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.LineComment, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMarkerAnnotationVisitor(BiPredicate<ASTNode, E> bs) {
		return callMarkerAnnotationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMarkerAnnotationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MarkerAnnotation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMemberRefVisitor(BiPredicate<ASTNode, E> bs) {
		return callMemberRefVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMemberRefVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MemberRef, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMemberValuePairVisitor(BiPredicate<ASTNode, E> bs) {
		return callMemberValuePairVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMemberValuePairVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MemberValuePair, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodRefVisitor(BiPredicate<ASTNode, E> bs) {
		return callMethodRefVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodRefVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MethodRef, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodRefParameterVisitor(BiPredicate<ASTNode, E> bs) {
		return callMethodRefParameterVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodRefParameterVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MethodRefParameter, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callMethodDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MethodDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters MethodDeclaration nodes by method name.
	 *
	 * @param methodName the name of the method to filter for
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodDeclarationVisitor(String methodName, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodName)
				.build();
		nodetypelist.put(VisitorEnum.MethodDeclaration, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters MethodDeclaration nodes by method name and allows navigation.
	 *
	 * @param methodName the name of the method to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodDeclarationVisitor(String methodName, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodName)
				.build();
		nodetypelist.put(VisitorEnum.MethodDeclaration, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(BiPredicate<ASTNode, E> bs) {
		return callMethodInvocationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.MethodInvocation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * @param methodname
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(String methodname, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodname)
				.build();
		nodetypelist.put(VisitorEnum.MethodInvocation, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * @param typeof
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(Class<?> typeof, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.MethodInvocation, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * @param typeof
	 * @param methodname
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(Class<?> typeof,String methodname, BiPredicate<MethodInvocation, E> bs) {
		return callMethodInvocationVisitor(typeof,methodname,bs,null);
	}

	/**
	 * @param typeof
	 * @param methodname
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(Class<?> typeof,String methodname, BiPredicate<MethodInvocation, E> bs, Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodname)
				.typeof(typeof)
				.build();
		nodetypelist.put(VisitorEnum.MethodInvocation, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 * @param methodname
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callMethodInvocationVisitor(String methodname, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodname)
				.build();
		nodetypelist.put(VisitorEnum.MethodInvocation, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModifierVisitor(BiPredicate<ASTNode, E> bs) {
		return callModifierVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModifierVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.Modifier, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModuleDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callModuleDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModuleDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ModuleDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModuleModifierVisitor(BiPredicate<ASTNode, E> bs) {
		return callModuleModifierVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModuleModifierVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ModuleModifier, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNameQualifiedTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callNameQualifiedTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNameQualifiedTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.NameQualifiedType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNormalAnnotationVisitor(BiPredicate<ASTNode, E> bs) {
		return callNormalAnnotationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNormalAnnotationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.NormalAnnotation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNullLiteralVisitor(BiPredicate<ASTNode, E> bs) {
		return callNullLiteralVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNullLiteralVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.NullLiteral, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNumberLiteralVisitor(BiPredicate<ASTNode, E> bs) {
		return callNumberLiteralVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callNumberLiteralVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.NumberLiteral, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callOpensDirectiveVisitor(BiPredicate<ASTNode, E> bs) {
		return callOpensDirectiveVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callOpensDirectiveVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.OpensDirective, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPackageDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callPackageDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPackageDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.PackageDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callParameterizedTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callParameterizedTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callParameterizedTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ParameterizedType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callParenthesizedExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callParenthesizedExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callParenthesizedExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ParenthesizedExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPatternInstanceofExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callPatternInstanceofExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPatternInstanceofExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.PatternInstanceofExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPostfixExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callPostfixExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPostfixExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.PostfixExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPrefixExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callPrefixExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPrefixExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.PrefixExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callProvidesDirectiveVisitor(BiPredicate<ASTNode, E> bs) {
		return callProvidesDirectiveVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callProvidesDirectiveVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ProvidesDirective, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPrimitiveTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callPrimitiveTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callPrimitiveTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.PrimitiveType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callQualifiedNameVisitor(BiPredicate<ASTNode, E> bs) {
		return callQualifiedNameVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callQualifiedNameVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.QualifiedName, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callQualifiedTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callQualifiedTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callQualifiedTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.QualifiedType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModuleQualifiedNameVisitor(BiPredicate<ASTNode, E> bs) {
		return callModuleQualifiedNameVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callModuleQualifiedNameVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ModuleQualifiedName, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callRequiresDirectiveVisitor(BiPredicate<ASTNode, E> bs) {
		return callRequiresDirectiveVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callRequiresDirectiveVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.RequiresDirective, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callRecordDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callRecordDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callRecordDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.RecordDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callReturnStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callReturnStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callReturnStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ReturnStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSimpleNameVisitor(BiPredicate<ASTNode, E> bs) {
		return callSimpleNameVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSimpleNameVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SimpleName, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSimpleTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callSimpleTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSimpleTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SimpleType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSingleMemberAnnotationVisitor(BiPredicate<ASTNode, E> bs) {
		return callSingleMemberAnnotationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSingleMemberAnnotationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SingleMemberAnnotation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSingleVariableDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callSingleVariableDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSingleVariableDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SingleVariableDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callStringLiteralVisitor(BiPredicate<ASTNode, E> bs) {
		return callStringLiteralVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callStringLiteralVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.StringLiteral, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperConstructorInvocationVisitor(BiPredicate<ASTNode, E> bs) {
		return callSuperConstructorInvocationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperConstructorInvocationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SuperConstructorInvocation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperFieldAccessVisitor(BiPredicate<ASTNode, E> bs) {
		return callSuperFieldAccessVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperFieldAccessVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SuperFieldAccess, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperMethodInvocationVisitor(BiPredicate<ASTNode, E> bs) {
		return callSuperMethodInvocationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperMethodInvocationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SuperMethodInvocation, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters SuperMethodInvocation nodes by method name (consistent with MethodInvocationVisitor).
	 *
	 * @param methodName the name of the super method to filter for
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperMethodInvocationVisitor(String methodName, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodName)
				.build();
		nodetypelist.put(VisitorEnum.SuperMethodInvocation, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters SuperMethodInvocation nodes by method name and allows navigation.
	 *
	 * @param methodName the name of the super method to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperMethodInvocationVisitor(String methodName, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName(methodName)
				.build();
		nodetypelist.put(VisitorEnum.SuperMethodInvocation, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperMethodReferenceVisitor(BiPredicate<ASTNode, E> bs) {
		return callSuperMethodReferenceVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSuperMethodReferenceVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SuperMethodReference, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSwitchCaseVisitor(BiPredicate<ASTNode, E> bs) {
		return callSwitchCaseVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSwitchCaseVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SwitchCase, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSwitchExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callSwitchExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSwitchExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SwitchExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSwitchStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callSwitchStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSwitchStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SwitchStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSynchronizedStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callSynchronizedStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callSynchronizedStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.SynchronizedStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTagElementVisitor(BiPredicate<ASTNode, E> bs) {
		return callTagElementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTagElementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TagElement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTextBlockVisitor(BiPredicate<ASTNode, E> bs) {
		return callTextBlockVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTextBlockVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TextBlock, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTextElementVisitor(BiPredicate<ASTNode, E> bs) {
		return callTextElementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTextElementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TextElement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callThisExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callThisExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callThisExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ThisExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callThrowStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callThrowStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callThrowStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.ThrowStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTryStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callTryStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTryStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TryStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeDeclarationVisitor(BiPredicate<ASTNode, E> bs) {
		return callTypeDeclarationVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeDeclarationVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TypeDeclaration, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 * Filters TypeDeclaration nodes by type name (class or interface name).
	 *
	 * @param typeName the name of the type to filter for
	 * @param bs the predicate to test each matching node
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeDeclarationVisitor(String typeName, BiPredicate<ASTNode, E> bs) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeName(typeName)
				.build();
		nodetypelist.put(VisitorEnum.TypeDeclaration, new NodeHolder(bs, null, config));
		return this;
	}

	/**
	 * Filters TypeDeclaration nodes by type name and allows navigation.
	 *
	 * @param typeName the name of the type to filter for
	 * @param bs the predicate to test each matching node
	 * @param navigate optional function to navigate to a different node before the next visitor in the chain
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeDeclarationVisitor(String typeName, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeName(typeName)
				.build();
		nodetypelist.put(VisitorEnum.TypeDeclaration, new NodeHolder(bs, navigate, config));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeDeclarationStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callTypeDeclarationStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeDeclarationStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TypeDeclarationStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeLiteralVisitor(BiPredicate<ASTNode, E> bs) {
		return callTypeLiteralVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeLiteralVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TypeLiteral, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeMethodReferenceVisitor(BiPredicate<ASTNode, E> bs) {
		return callTypeMethodReferenceVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeMethodReferenceVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TypeMethodReference, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeParameterVisitor(BiPredicate<ASTNode, E> bs) {
		return callTypeParameterVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callTypeParameterVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.TypeParameter, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callUnionTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callUnionTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callUnionTypeVisitor(BiPredicate<ASTNode, E> bs, Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.UnionType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callUsesDirectiveVisitor(BiPredicate<ASTNode, E> bs) {
		return callUsesDirectiveVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callUsesDirectiveVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.UsesDirective, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationExpressionVisitor(BiPredicate<ASTNode, E> bs) {
		return callVariableDeclarationExpressionVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationExpressionVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.VariableDeclarationExpression, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callVariableDeclarationStatementVisitor(bs, null);
	}

	/**
	 * @param class1
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationStatementVisitor(Class<?> class1, BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.VariableDeclarationStatement, new NodeHolder(bs, navigate, VisitorConfigData.builder().typeof(class1).build()));
		return this;
	}

	/**
	 * @param class1
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationStatementVisitor(Class<?> class1, BiPredicate<ASTNode, E> bs) {
		nodetypelist.put(VisitorEnum.VariableDeclarationStatement, new NodeHolder(bs, null, VisitorConfigData.builder().typeof(class1).build()));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.VariableDeclarationStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationFragmentVisitor(BiPredicate<ASTNode, E> bs) {
		return callVariableDeclarationFragmentVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callVariableDeclarationFragmentVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.VariableDeclarationFragment, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callWhileStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callWhileStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callWhileStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.WhileStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callWildcardTypeVisitor(BiPredicate<ASTNode, E> bs) {
		return callWildcardTypeVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callWildcardTypeVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.WildcardType, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param bs
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callYieldStatementVisitor(BiPredicate<ASTNode, E> bs) {
		return callYieldStatementVisitor(bs, null);
	}

	/**
	 *
	 * @param bs
	 * @param navigate
	 * @return a reference to this object.
	 */
	public ASTProcessor<E, V, T> callYieldStatementVisitor(BiPredicate<ASTNode, E> bs,
			Function<ASTNode, ASTNode> navigate) {
		nodetypelist.put(VisitorEnum.YieldStatement, new NodeHolder(bs, navigate));
		return this;
	}

	/**
	 *
	 * @param node
	 */
	public void build(ASTNode node) {
		nodetypekeylist= new LinkedList<>(nodetypelist.keySet());
		process(node, 0);
	}

	void process(ASTNode localnode, final int i) {
		if (i == nodetypekeylist.size()) {
			return;
		}
		final VisitorEnum next= nodetypekeylist.get(i);
		ASTProcessor<E, V, T>.NodeHolder nodeHolder= nodetypelist.get(next);
		BiPredicate<ASTNode, E> biPredicate= nodeHolder.callee;
		BiConsumer<ASTNode, E> bcConsumer= nodeHolder.callee_end;
		HelperVisitor<E, V, T> hv= new HelperVisitor<>(nodesprocessed, dataholder);
		if(bcConsumer!=null) {
			hv.addEnd(next, (node, holder) -> {
				bcConsumer.accept(node, holder);
				if (nodeHolder.navigate != null) {
					process(nodeHolder.navigate.apply(node), i + 1);
				} else {
					process(node, i + 1);
				}
			});
		} else if (nodeHolder.object != null) {
			hv.add(nodeHolder.object, next, (node, holder) -> {
				boolean test= biPredicate.test(node, holder);
				if (nodeHolder.navigate != null) {
					process(nodeHolder.navigate.apply(node), i + 1);
				} else {
					process(node, i + 1);
				}
				return test;
			});
		} else  {
			hv.add(next, (node, holder) -> {
				boolean test= biPredicate.test(node, holder);
				if (nodeHolder.navigate != null) {
					process(nodeHolder.navigate.apply(node), i + 1);
				} else {
					process(node, i + 1);
				}
				return test;
			});
		}
		hv.build(localnode);
	}

}
