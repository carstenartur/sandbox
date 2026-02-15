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
package org.sandbox.jdt.ui.tests.quickfix;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

/**
 * Basic usage tests for the HelperVisitor API.
 * 
 * <p>This test class demonstrates the fundamental patterns for using the HelperVisitor API,
 * which provides a modern, lambda-based approach to visiting AST nodes compared to the
 * traditional Eclipse JDT ASTVisitor pattern.</p>
 * 
 * <h2>Key Concepts</h2>
 * <ul>
 * <li><b>HelperVisitor</b>: A wrapper that allows registering lambda expressions as visitors instead of overriding methods</li>
 * <li><b>ReferenceHolder</b>: A thread-safe map for storing data collected during AST traversal</li>
 * <li><b>VisitorEnum</b>: Enum representing all AST node types for type-safe visitor registration</li>
 * </ul>
 * 
 * <h2>Traditional vs. Modern Approach</h2>
 * 
 * <p><b>Traditional ASTVisitor approach:</b></p>
 * <pre>
 * ASTVisitor visitor = new ASTVisitor() {
 *     {@literal @}Override
 *     public boolean visit(MethodInvocation node) {
 *         // Process method invocation
 *         return true;
 *     }
 * };
 * compilationUnit.accept(visitor);
 * </pre>
 * 
 * <p><b>Modern HelperVisitor approach:</b></p>
 * <pre>
 * HelperVisitor&lt;...&gt; hv = new HelperVisitor&lt;&gt;(null, new ReferenceHolder&lt;&gt;());
 * hv.addMethodInvocation(this::handleMethodInvocation);
 * hv.build(compilationUnit);
 * </pre>
 * 
 * <h2>Advantages of HelperVisitor</h2>
 * <ul>
 * <li>More concise: no need to create anonymous classes</li>
 * <li>Type-safe: method references ensure correct parameter types</li>
 * <li>Composable: easily combine multiple visitor callbacks</li>
 * <li>Filterable: built-in support for filtering by method name, type, etc.</li>
 * <li>Data-sharing: ReferenceHolder provides thread-safe data storage across callbacks</li>
 * </ul>
 * 
 * @author Carsten Hammer
 * @see HelperVisitor
 * @see ReferenceHolder
 * @see VisitorEnum
 */
public class BasicVisitorUsageTest {

	private static CompilationUnit cunit1;

	/**
	 * Initialize test compilation unit with sample code.
	 * This method creates an AST from Java source code for testing purposes.
	 */
	@BeforeAll
	public static void init() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);

		cunit1 = createunit(parser,"""
			package test;
			import java.util.Collection;

			public class E {
				public void hui(Collection<String> arr) {
					Collection coll = null;
					for (String var : arr) {
						 coll.add(var);
						 System.out.println(var);
						 System.err.println(var);
					}
					System.out.println(arr);
				}
			}""", "E"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Helper method to create a CompilationUnit from source code.
	 * 
	 * @param parser the AST parser to use
	 * @param code the Java source code
	 * @param name the unit name
	 * @return the parsed CompilationUnit
	 */
	private static CompilationUnit createunit(ASTParser parser, String code, String name) {
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Callback for handling MethodInvocation nodes.
	 * 
	 * @param invocation the method invocation node
	 * @param holder the data holder for sharing data across callbacks
	 * @return true to continue visiting child nodes, false to skip them
	 */
	private boolean handleMethodInvocation(MethodInvocation invocation, ReferenceHolder<String, NodeFound> holder) {
		System.out.println("Found method invocation: " + invocation);
		return true;
	}

	/**
	 * Demonstrates basic usage of HelperVisitor with a method reference.
	 * 
	 * <p>This test shows how to visit MethodInvocation nodes using a method reference
	 * instead of creating an anonymous ASTVisitor class. The method reference points to
	 * a method with the correct signature: (MethodInvocation, ReferenceHolder) → boolean</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <ol>
	 * <li>Create HelperVisitor instance with ReferenceHolder</li>
	 * <li>Register callback using addMethodInvocation()</li>
	 * <li>Build the visitor and traverse the AST</li>
	 * </ol>
	 */
	@Test
	public void testBasicMethodReference() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation(this::handleMethodInvocation);
		hv.build(cunit1);
	}

	/**
	 * Shows the equivalent traditional ASTVisitor approach for comparison.
	 * 
	 * <p>This is how you would typically handle method invocations using the
	 * traditional Eclipse JDT visitor pattern. Compare this with {@link #testBasicMethodReference()}
	 * to see the difference in verbosity and clarity.</p>
	 * 
	 * <p><b>Drawbacks of this approach:</b></p>
	 * <ul>
	 * <li>More boilerplate code (anonymous class creation)</li>
	 * <li>Less composable (hard to combine multiple visitors)</li>
	 * <li>Type safety relies on override annotation</li>
	 * </ul>
	 */
	@Test
	public void testTraditionalVisitorApproach() {
		ASTVisitor astvisitor = new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				return BasicVisitorUsageTest.this.handleMethodInvocation(node, null);
			}
		};
		cunit1.accept(astvisitor);
	}

	/**
	 * Demonstrates filtering method invocations by method name.
	 * 
	 * <p>The HelperVisitor API provides convenient filtering capabilities. Here we only
	 * process MethodInvocation nodes where the method name is "add".</p>
	 * 
	 * <p><b>Use case:</b> When you're only interested in specific method calls (e.g., 
	 * finding all calls to Collection.add() for migration to Stream API).</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * hv.addMethodInvocation("methodName", callback);
	 * </pre>
	 */
	@Test
	public void testMethodNameFiltering() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation("add", this::handleMethodInvocation); //$NON-NLS-1$
		hv.build(cunit1);
	}

	/**
	 * Shows how to implement method name filtering with traditional ASTVisitor.
	 * 
	 * <p>Compare this with {@link #testMethodNameFiltering()} to see how HelperVisitor
	 * reduces boilerplate. With traditional visitor, you must manually check the method name
	 * inside the visit method.</p>
	 */
	@Test
	public void testMethodNameFilteringTraditional() {
		String name = "add"; //$NON-NLS-1$
		ASTVisitor astvisitor = new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (!node.getName().getIdentifier().equals(name)) {
					return true;
				}
				return BasicVisitorUsageTest.this.handleMethodInvocation(node, null);
			}
		};
		cunit1.accept(astvisitor);
	}

	/**
	 * Demonstrates using lambda expressions directly instead of method references.
	 * 
	 * <p>Instead of defining a separate method, you can provide a lambda expression inline.
	 * This is useful for simple, one-off visitors that don't need to be reused.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * hv.addMethodInvocation((node, holder) -&gt; {
	 *     // Process node
	 *     return true;
	 * });
	 * </pre>
	 * 
	 * <p><b>When to use:</b></p>
	 * <ul>
	 * <li>Simple processing logic that fits in a few lines</li>
	 * <li>Logic that doesn't need to be reused in other tests</li>
	 * <li>Quick experiments or debugging</li>
	 * </ul>
	 */
	@Test
	public void testLambdaExpression() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation((node, holder) -> {
			System.out.println("Lambda found: " + node);
			return true;
		});
		hv.build(cunit1);
	}

	/**
	 * Demonstrates visiting multiple node types at once using VisitorEnum.
	 * 
	 * <p>You can register visitors for multiple AST node types by iterating over
	 * an EnumSet of VisitorEnum values. This is powerful for operations that need
	 * to process many different node types uniformly.</p>
	 * 
	 * <p><b>Use case:</b> Collecting statistics across all node types, or applying
	 * a common transformation to multiple node kinds.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * EnumSet&lt;VisitorEnum&gt; nodeTypes = EnumSet.of(
	 *     VisitorEnum.MethodInvocation,
	 *     VisitorEnum.FieldDeclaration
	 * );
	 * nodeTypes.forEach(ve -&gt; hv.add(ve, callback));
	 * </pre>
	 */
	@Test
	public void testMultipleNodeTypes() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		EnumSet<VisitorEnum> nodeTypes = EnumSet.of(
			VisitorEnum.MethodInvocation,
			VisitorEnum.VariableDeclarationFragment
		);
		
		nodeTypes.forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				System.out.println("Processing " + ve.name() + ": " + node);
				return true;
			});
		});
		
		hv.build(cunit1);
	}

	/**
	 * Demonstrates the visitEnd callback pattern.
	 * 
	 * <p>Similar to ASTVisitor's endVisit() methods, HelperVisitor supports callbacks
	 * that are invoked after a node and all its children have been visited. This is useful
	 * for cleanup operations or collecting aggregate information.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * hv.add(visitorEnum, visitCallback, endVisitCallback);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Computing aggregate statistics after processing all children,
	 * or performing cleanup after visiting a subtree.</p>
	 */
	@Test
	public void testVisitEndCallback() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.add(VisitorEnum.MethodInvocation,
			(node, holder) -> {
				System.out.println("Visiting: " + node.getNodeType());
				return true;
			},
			(node, holder) -> {
				System.out.println("End visiting: " + node.getNodeType());
			});
		
		hv.build(cunit1);
	}

	/**
	 * Demonstrates how to skip visiting child nodes.
	 * 
	 * <p>By returning false from a visitor callback, you can prevent the visitor from
	 * descending into child nodes. This is useful for performance optimization when you
	 * know you don't need to process a subtree.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * hv.addMethodInvocation((node, holder) -&gt; {
	 *     // Process node
	 *     return false; // Don't visit children
	 * });
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Performance optimization by pruning irrelevant subtrees.</p>
	 */
	@Test
	public void testSkipChildNodes() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addMethodInvocation((node, holder) -> {
			System.out.println("Processing method, but not its children");
			return false; // Skip children
		});
		
		hv.build(cunit1);
	}
}
