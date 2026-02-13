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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

/**
 * Comprehensive API documentation through executable examples.
 * 
 * <h1>HelperVisitor API - Complete Guide</h1>
 * 
 * <p>The HelperVisitor API provides a modern, functional approach to AST traversal
 * in Eclipse JDT. This test class serves as both documentation and validation of
 * the API's capabilities.</p>
 * 
 * <h2>Table of Contents</h2>
 * <ol>
 * <li>{@link #testApiOverview()} - API Overview and Core Concepts</li>
 * <li>{@link #testBasicUsagePattern()} - Basic Usage Pattern</li>
 * <li>{@link #testVisitorRegistration()} - Visitor Registration Methods</li>
 * <li>{@link #testDataSharingPatterns()} - Data Sharing with ReferenceHolder</li>
 * <li>{@link #testCallbackSignatures()} - Callback Method Signatures</li>
 * <li>{@link #testVisitEndCallbacks()} - Visit and VisitEnd Callbacks</li>
 * <li>{@link #testStaticHelperMethods()} - Static Helper Methods</li>
 * </ol>
 * 
 * <h2>Core Concepts</h2>
 * 
 * <h3>1. HelperVisitor</h3>
 * <p>A builder for creating AST visitors using lambda expressions and method references
 * instead of overriding methods in anonymous classes.</p>
 * 
 * <h3>2. ReferenceHolder</h3>
 * <p>A thread-safe map (extends ConcurrentHashMap) for sharing data between visitor
 * callbacks during AST traversal.</p>
 * 
 * <h3>3. VisitorEnum</h3>
 * <p>An enum representing all AST node types. Used for type-safe visitor registration.</p>
 * 
 * <h3>4. BiPredicate Callbacks</h3>
 * <p>Visitor callbacks are BiPredicate&lt;NodeType, DataHolder&gt; that return true to
 * continue visiting children, false to skip them.</p>
 * 
 * <h2>Comparison with Traditional ASTVisitor</h2>
 * 
 * <table border="1">
 * <tr>
 *   <th>Feature</th>
 *   <th>Traditional ASTVisitor</th>
 *   <th>HelperVisitor</th>
 * </tr>
 * <tr>
 *   <td>Syntax</td>
 *   <td>Anonymous class with overridden methods</td>
 *   <td>Lambda expressions or method references</td>
 * </tr>
 * <tr>
 *   <td>Type Safety</td>
 *   <td>Relies on @Override annotation</td>
 *   <td>Compile-time type checking</td>
 * </tr>
 * <tr>
 *   <td>Data Sharing</td>
 *   <td>Instance variables or final locals</td>
 *   <td>ReferenceHolder parameter</td>
 * </tr>
 * <tr>
 *   <td>Composition</td>
 *   <td>Difficult (multiple classes)</td>
 *   <td>Easy (BiPredicate.and/or/negate)</td>
 * </tr>
 * <tr>
 *   <td>Filtering</td>
 *   <td>Manual if-checks in visit methods</td>
 *   <td>Built-in (by method name, type, etc.)</td>
 * </tr>
 * <tr>
 *   <td>Dynamic Behavior</td>
 *   <td>Not possible</td>
 *   <td>Can modify visitors during traversal</td>
 * </tr>
 * </table>
 * 
 * @author Carsten Hammer
 * @see HelperVisitor
 * @see ReferenceHolder
 * @see VisitorEnum
 */
public class VisitorApiDocumentationTest {

	private static CompilationUnit cunit;

	@BeforeAll
	public static void init() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);

		cunit = createunit(parser,"""
			package test;
			import java.util.Collection;
			import java.util.List;

			public class Example {
				private int field = 0;
				
				public void method(Collection<String> arr) {
					Collection coll = null;
					for (String var : arr) {
						 coll.add(var);
						 System.out.println(var);
					}
					List<String> list = null;
				}
			}""", "Example"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static CompilationUnit createunit(ASTParser parser, String code, String name) {
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * <h2>API Overview</h2>
	 * 
	 * <p>The HelperVisitor API consists of three main components:</p>
	 * 
	 * <h3>Component 1: HelperVisitor (Builder)</h3>
	 * <pre>
	 * HelperVisitor&lt;DataHolder, KeyType, ValueType&gt; hv = 
	 *     new HelperVisitor&lt;&gt;(processedNodes, dataHolder);
	 * </pre>
	 * 
	 * <h3>Component 2: Visitor Registration</h3>
	 * <pre>
	 * hv.addMethodInvocation(callback);
	 * hv.add(VisitorEnum.FieldDeclaration, callback);
	 * </pre>
	 * 
	 * <h3>Component 3: Build and Execute</h3>
	 * <pre>
	 * hv.build(compilationUnit);
	 * </pre>
	 */
	@Test
	public void testApiOverview() {
		System.out.println("=== API Overview ===");
		System.out.println("See class documentation for details");
		
		// Example: Complete workflow
		ReferenceHolder<String, Integer> data = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Integer>, String, Integer> hv = 
			new HelperVisitor<>(null, data);
		
		hv.addMethodInvocation((node, holder) -> {
			holder.merge("count", 1, Integer::sum); //$NON-NLS-1$
			return true;
		});
		
		hv.build(cunit);
		System.out.println("Found " + data.get("count") + " method invocations"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * <h2>Basic Usage Pattern</h2>
	 * 
	 * <p>The most common usage pattern follows these steps:</p>
	 * 
	 * <h3>Step 1: Create ReferenceHolder</h3>
	 * <pre>
	 * ReferenceHolder&lt;KeyType, ValueType&gt; data = new ReferenceHolder&lt;&gt;();
	 * </pre>
	 * <p>The ReferenceHolder stores data collected during traversal. Choose key and
	 * value types based on what you need to track.</p>
	 * 
	 * <h3>Step 2: Create HelperVisitor</h3>
	 * <pre>
	 * HelperVisitor&lt;...&gt; hv = new HelperVisitor&lt;&gt;(processedNodes, data);
	 * </pre>
	 * <p>Pass null for processedNodes unless you need to track which nodes have been
	 * visited (e.g., to avoid duplicate processing in complex multi-pass scenarios).</p>
	 * 
	 * <h3>Step 3: Register Callbacks</h3>
	 * <pre>
	 * hv.addMethodInvocation((node, holder) -&gt; {
	 *     // Process node
	 *     return true; // or false to skip children
	 * });
	 * </pre>
	 * 
	 * <h3>Step 4: Build and Traverse</h3>
	 * <pre>
	 * hv.build(compilationUnit);
	 * </pre>
	 * 
	 * <h3>Step 5: Use Collected Data</h3>
	 * <pre>
	 * System.out.println("Results: " + data);
	 * </pre>
	 */
	@Test
	public void testBasicUsagePattern() {
		System.out.println("=== Basic Usage Pattern ===");
		
		// Step 1
		ReferenceHolder<String, Object> data = new ReferenceHolder<>();
		
		// Step 2
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
			new HelperVisitor<>(null, data);
		
		// Step 3
		hv.addFieldDeclaration((node, holder) -> {
			holder.put("field_found", true); //$NON-NLS-1$
			System.out.println("Found field: " + node);
			return true;
		});
		
		// Step 4
		hv.build(cunit);
		
		// Step 5
		System.out.println("Field found: " + data.get("field_found")); //$NON-NLS-1$
	}

	/**
	 * <h2>Visitor Registration Methods</h2>
	 * 
	 * <p>HelperVisitor provides multiple ways to register visitor callbacks:</p>
	 * 
	 * <h3>Method 1: Type-Specific Methods</h3>
	 * <pre>
	 * hv.addMethodInvocation(callback);
	 * hv.addFieldDeclaration(callback);
	 * hv.addWhileStatement(callback);
	 * </pre>
	 * <p>Each AST node type has a corresponding addXxx() method.</p>
	 * 
	 * <h3>Method 2: Generic add() with VisitorEnum</h3>
	 * <pre>
	 * hv.add(VisitorEnum.MethodInvocation, callback);
	 * hv.add(VisitorEnum.FieldDeclaration, callback);
	 * </pre>
	 * <p>Useful when iterating over multiple node types.</p>
	 * 
	 * <h3>Method 3: Filtered by Name</h3>
	 * <pre>
	 * hv.addMethodInvocation("println", callback);
	 * </pre>
	 * <p>Only invokes callback for methods with specific name.</p>
	 * 
	 * <h3>Method 4: Filtered by Type</h3>
	 * <pre>
	 * hv.addMethodInvocation(System.class, "println", callback);
	 * </pre>
	 * <p>Only invokes callback for methods on specific receiver type.</p>
	 */
	@Test
	public void testVisitorRegistration() {
		System.out.println("=== Visitor Registration Methods ===");
		
		ReferenceHolder<String, Integer> data = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Integer>, String, Integer> hv = 
			new HelperVisitor<>(null, data);
		
		// Method 1: Type-specific
		hv.addMethodInvocation((node, holder) -> {
			holder.merge("method1", 1, Integer::sum); //$NON-NLS-1$
			return true;
		});
		
		// Method 2: Generic with enum
		hv.add(VisitorEnum.FieldDeclaration, (node, holder) -> {
			holder.merge("method2", 1, Integer::sum); //$NON-NLS-1$
			return true;
		});
		
		// Method 3: Filtered by name
		hv.addMethodInvocation("println", (node, holder) -> { //$NON-NLS-1$
			holder.merge("method3", 1, Integer::sum); //$NON-NLS-1$
			return true;
		});
		
		hv.build(cunit);
		
		System.out.println("Method 1 count: " + data.getOrDefault("method1", 0)); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println("Method 2 count: " + data.getOrDefault("method2", 0)); //$NON-NLS-1$ //$NON-NLS-2$
		System.out.println("Method 3 count: " + data.getOrDefault("method3", 0)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * <h2>Data Sharing Patterns</h2>
	 * 
	 * <p>ReferenceHolder enables various data sharing patterns:</p>
	 * 
	 * <h3>Pattern 1: Simple Counters</h3>
	 * <pre>
	 * holder.merge(key, 1, Integer::sum);
	 * </pre>
	 * 
	 * <h3>Pattern 2: Collecting Nodes</h3>
	 * <pre>
	 * List&lt;ASTNode&gt; nodes = holder.computeIfAbsent(key, k -&gt; new ArrayList&lt;&gt;());
	 * nodes.add(node);
	 * </pre>
	 * 
	 * <h3>Pattern 3: Building Maps</h3>
	 * <pre>
	 * Map&lt;String, Object&gt; props = holder.computeIfAbsent(node, k -&gt; new HashMap&lt;&gt;());
	 * props.put("property", value);
	 * </pre>
	 * 
	 * <h3>Pattern 4: Shared State</h3>
	 * <pre>
	 * boolean found = (Boolean) holder.getOrDefault("found", false);
	 * if (!found) {
	 *     // Process first occurrence
	 *     holder.put("found", true);
	 * }
	 * </pre>
	 */
	@Test
	public void testDataSharingPatterns() {
		System.out.println("=== Data Sharing Patterns ===");
		
		ReferenceHolder<String, Object> data = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
			new HelperVisitor<>(null, data);
		
		// Pattern 1: Simple counter
		hv.addMethodInvocation((node, holder) -> {
			holder.merge("count", 1, (a, b) -> (Integer)a + (Integer)b); //$NON-NLS-1$
			return true;
		});
		
		// Pattern 4: Shared state (only process first)
		hv.addFieldDeclaration((node, holder) -> {
			boolean found = (Boolean) holder.getOrDefault("firstField", false); //$NON-NLS-1$
			if (!found) {
				System.out.println("First field: " + node);
				holder.put("firstField", true); //$NON-NLS-1$
			}
			return true;
		});
		
		hv.build(cunit);
		
		System.out.println("Total method invocations: " + data.get("count")); //$NON-NLS-1$
		System.out.println("Found first field: " + data.get("firstField")); //$NON-NLS-1$
	}

	/**
	 * <h2>Callback Method Signatures</h2>
	 * 
	 * <p>Understanding callback signatures is key to using the API:</p>
	 * 
	 * <h3>BiPredicate Signature</h3>
	 * <pre>
	 * BiPredicate&lt;NodeType, ReferenceHolder&lt;K, V&gt;&gt;
	 * </pre>
	 * 
	 * <h3>Method Reference Requirements</h3>
	 * <p>For method references to work, your method must match:</p>
	 * <pre>
	 * boolean methodName(NodeType node, ReferenceHolder&lt;K, V&gt; holder) {
	 *     // Process node
	 *     return true; // or false
	 * }
	 * </pre>
	 * 
	 * <h3>Lambda Expression Format</h3>
	 * <pre>
	 * (node, holder) -&gt; {
	 *     // Process node
	 *     return true; // or false
	 * }
	 * </pre>
	 * 
	 * <h3>Return Value Meaning</h3>
	 * <ul>
	 * <li><code>true</code> - Continue visiting child nodes</li>
	 * <li><code>false</code> - Skip child nodes (prune subtree)</li>
	 * </ul>
	 */
	@Test
	public void testCallbackSignatures() {
		System.out.println("=== Callback Signatures ===");
		
		ReferenceHolder<String, Object> data = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
			new HelperVisitor<>(null, data);
		
		// Lambda expression
		hv.addMethodInvocation((node, holder) -> {
			System.out.println("Lambda: " + node.getName());
			return true;
		});
		
		// Method reference
		hv.addFieldDeclaration(this::handleFieldDeclaration);
		
		hv.build(cunit);
	}

	private boolean handleFieldDeclaration(
			org.eclipse.jdt.core.dom.FieldDeclaration node, 
			ReferenceHolder<String, Object> holder) {
		System.out.println("Method reference: " + node);
		return true;
	}

	/**
	 * <h2>Visit and VisitEnd Callbacks</h2>
	 * 
	 * <p>Similar to ASTVisitor's visit() and endVisit() methods, HelperVisitor
	 * supports callbacks both before and after visiting a node's children.</p>
	 * 
	 * <h3>Registration Pattern</h3>
	 * <pre>
	 * hv.add(VisitorEnum.MethodDeclaration,
	 *     visitCallback,    // Called before children
	 *     endVisitCallback  // Called after children
	 * );
	 * </pre>
	 * 
	 * <h3>EndVisit Callback Signature</h3>
	 * <pre>
	 * BiConsumer&lt;NodeType, ReferenceHolder&lt;K, V&gt;&gt;
	 * </pre>
	 * <p>Note: EndVisit callbacks don't return a value (they're BiConsumer, not BiPredicate).</p>
	 * 
	 * <h3>Use Cases</h3>
	 * <ul>
	 * <li>Computing aggregate statistics after processing children</li>
	 * <li>Cleanup operations</li>
	 * <li>Building hierarchical data structures</li>
	 * </ul>
	 */
	@Test
	public void testVisitEndCallbacks() {
		System.out.println("=== Visit and VisitEnd Callbacks ===");
		
		ReferenceHolder<String, Integer> data = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Integer>, String, Integer> hv = 
			new HelperVisitor<>(null, data);
		
		hv.add(VisitorEnum.MethodDeclaration,
			(node, holder) -> {
				System.out.println("Entering method: " + node);
				holder.put("depth", holder.getOrDefault("depth", 0) + 1); //$NON-NLS-1$ //$NON-NLS-2$
				return true;
			},
			(node, holder) -> {
				System.out.println("Leaving method: " + node);
				holder.put("depth", holder.get("depth") - 1); //$NON-NLS-1$ //$NON-NLS-2$
			}
		);
		
		hv.build(cunit);
	}

	/**
	 * <h2>Static Helper Methods</h2>
	 * 
	 * <p>HelperVisitor provides static methods for common scenarios where you
	 * don't need the full builder API.</p>
	 * 
	 * <h3>Method: callVisitor()</h3>
	 * <pre>
	 * HelperVisitorFactory.callVisitor(
	 *     astNode,              // Where to start
	 *     nodeTypes,            // EnumSet of types to visit
	 *     dataHolder,           // ReferenceHolder for data
	 *     nodesProcessed,       // Optional set of processed nodes
	 *     endVisitCallback      // Callback (BiConsumer)
	 * );
	 * </pre>
	 * 
	 * <h3>When to Use</h3>
	 * <ul>
	 * <li>Simple one-off visitors</li>
	 * <li>Quick prototyping or experimentation</li>
	 * <li>When you only need endVisit callback</li>
	 * </ul>
	 * 
	 * <h3>Type-Specific Static Methods</h3>
	 * <pre>
	 * HelperVisitorFactory.callMethodInvocationVisitor(name, astNode, data, null, callback);
	 * HelperVisitorFactory.callWhileStatementVisitor(astNode, data, null, callback);
	 * HelperVisitorFactory.callVariableDeclarationStatementVisitor(type, astNode, data, null, callback);
	 * </pre>
	 */
	@Test
	public void testStaticHelperMethods() {
		System.out.println("=== Static Helper Methods ===");
		
		ReferenceHolder<String, Integer> data = new ReferenceHolder<>();
		
		// Static method approach
		HelperVisitorFactory.callVisitor(
			cunit,
			EnumSet.of(VisitorEnum.MethodInvocation, VisitorEnum.FieldDeclaration),
			data,
			null,
			(node, holder) -> {
				holder.merge("count", 1, Integer::sum); //$NON-NLS-1$
			}
		);
		
		System.out.println("Found " + data.get("count") + " nodes using static method"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
