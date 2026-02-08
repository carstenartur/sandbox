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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.TestLogger;
import org.sandbox.jdt.internal.common.VisitorEnum;

/**
 * Tests demonstrating ReferenceHolder usage for data collection during AST traversal.
 * 
 * <p>The ReferenceHolder is a thread-safe map that allows visitor callbacks to share
 * data during AST traversal. It extends ConcurrentHashMap and provides a type-safe
 * way to collect and aggregate information as you visit nodes.</p>
 * 
 * <h2>Key Concepts</h2>
 * <ul>
 * <li><b>ReferenceHolder</b>: A thread-safe map for storing data collected during traversal</li>
 * <li><b>Type Parameters</b>: ReferenceHolder&lt;K, V&gt; where K is key type, V is value type</li>
 * <li><b>Data Sharing</b>: All callbacks receive the same ReferenceHolder instance</li>
 * </ul>
 * 
 * <h2>Common Patterns</h2>
 * 
 * <p><b>Pattern 1: Counting Nodes</b></p>
 * <pre>
 * ReferenceHolder&lt;VisitorEnum, Integer&gt; counter = new ReferenceHolder&lt;&gt;();
 * hv.add(nodeType, (node, holder) -&gt; {
 *     holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
 *     return true;
 * });
 * </pre>
 * 
 * <p><b>Pattern 2: Collecting Nodes</b></p>
 * <pre>
 * ReferenceHolder&lt;ASTNode, Integer&gt; positions = new ReferenceHolder&lt;&gt;();
 * hv.add(nodeType, (node, holder) -&gt; {
 *     holder.put(node, node.getStartPosition());
 *     return true;
 * });
 * </pre>
 * 
 * <p><b>Pattern 3: Complex Data Structures</b></p>
 * <pre>
 * ReferenceHolder&lt;ASTNode, Map&lt;String, Object&gt;&gt; data = new ReferenceHolder&lt;&gt;();
 * hv.add(nodeType, (node, holder) -&gt; {
 *     Map&lt;String, Object&gt; nodeData = holder.computeIfAbsent(node, k -&gt; new HashMap&lt;&gt;());
 *     nodeData.put("property", value);
 *     return true;
 * });
 * </pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>ReferenceHolder is backed by ConcurrentHashMap, making it safe for concurrent
 * access. However, if you store mutable objects as values, you must ensure thread safety
 * of those objects yourself.</p>
 * 
 * @author Carsten Hammer
 * @see ReferenceHolder
 * @see HelperVisitor
 */
public class ReferenceHolderTest {

	private static CompilationUnit cunit1;

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

	private static CompilationUnit createunit(ASTParser parser, String code, String name) {
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Demonstrates counting AST nodes by type using ReferenceHolder.
	 * 
	 * <p>This test shows how to use ReferenceHolder to count occurrences of each
	 * AST node type. The merge() method atomically increments the count.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * holder.merge(key, 1, Integer::sum);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Gathering statistics about AST structure, identifying
	 * patterns in code for refactoring decisions.</p>
	 */
	@Test
	public void testCountingNodes() {
		Set<ASTNode> nodesprocessed = null;
		ReferenceHolder<VisitorEnum, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<VisitorEnum,Integer>,VisitorEnum,Integer> hv = 
			new HelperVisitor<>(nodesprocessed, dataholder);
		
		// Register visitor for all node types
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
				return true;
			});
		});
		
		hv.build(cunit1);
		
		// Print results
		TestLogger.println("=== Node Count Statistics ===");
		for (VisitorEnum ve : dataholder.keySet()) {
			TestLogger.println(ve.name() + ": " + dataholder.get(ve));
		}
	}

	/**
	 * Demonstrates a simpler approach using callVisitor static method.
	 * 
	 * <p>The HelperVisitor.callVisitor() static method provides a more concise
	 * way to set up visitors when you don't need the full HelperVisitor API.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * HelperVisitor.callVisitor(astNode, nodeTypes, dataHolder, null, callback);
	 * </pre>
	 * 
	 * <p><b>When to use:</b> Simple one-off visitors that don't need complex setup.</p>
	 */
	@Test
	public void testCountingWithStaticMethod() {
		ReferenceHolder<VisitorEnum, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1, EnumSet.allOf(VisitorEnum.class), dataholder, null, this::countVisits);

		// Print results
		TestLogger.println("=== Node Count Statistics (Static Method) ===");
		dataholder.entrySet().stream().forEach(entry -> {
			TestLogger.println(entry.getKey().name() + ": " + entry.getValue());
		});
	}

	/**
	 * Helper method for counting node visits.
	 * 
	 * @param node the node being visited
	 * @param holder the reference holder for storing counts
	 */
	private void countVisits(ASTNode node, ReferenceHolder<VisitorEnum, Integer> holder) {
		holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
	}

	/**
	 * Demonstrates collecting node positions using ReferenceHolder.
	 * 
	 * <p>This test shows how to map AST nodes to their source positions. This is
	 * useful for navigation, highlighting, or generating code modification proposals.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * holder.put(node, node.getStartPosition());
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Building navigation features, generating quick fixes,
	 * creating source code index.</p>
	 */
	@Test
	public void testCollectingNodePositions() {
		ReferenceHolder<ASTNode, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1, EnumSet.of(
			VisitorEnum.SingleVariableDeclaration,
			VisitorEnum.VariableDeclarationExpression,
			VisitorEnum.VariableDeclarationStatement,
			VisitorEnum.VariableDeclarationFragment), dataholder, null, (node, holder) -> {
				holder.put(node, node.getStartPosition());
			});

		// Print results
		TestLogger.println("=== Variable Declaration Positions ===");
		dataholder.entrySet().stream().forEach(entry -> {
			TestLogger.println("Position " + entry.getValue() + ": " + 
				ASTNode.nodeClassForType(entry.getKey().getNodeType()));
		});
	}

	/**
	 * Demonstrates storing complex data structures per node.
	 * 
	 * <p>This test shows how to associate multiple properties with each AST node
	 * using a Map as the value type. This pattern is powerful for collecting
	 * rich information during traversal.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * Map&lt;String, Object&gt; nodeData = holder.computeIfAbsent(node, k -&gt; new HashMap&lt;&gt;());
	 * nodeData.put("property1", value1);
	 * nodeData.put("property2", value2);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Complex analyses that need to track multiple properties
	 * per node (e.g., type, scope, usage count, modification proposals).</p>
	 */
	@Test
	public void testComplexDataStructures() {
		ReferenceHolder<ASTNode, Map<String, Object>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1, EnumSet.of(
			VisitorEnum.SingleVariableDeclaration,
			VisitorEnum.VariableDeclarationExpression,
			VisitorEnum.VariableDeclarationStatement,
			VisitorEnum.VariableDeclarationFragment), dataholder, null, (node, holder) -> {
				Map<String, Object> pernodemap = holder.computeIfAbsent(node, k -> new HashMap<>());
				switch (VisitorEnum.fromNode(node)) {
					case SingleVariableDeclaration:
						SingleVariableDeclaration svd = (SingleVariableDeclaration) node;
						Expression svd_initializer = svd.getInitializer();
						pernodemap.put("init", svd_initializer); //$NON-NLS-1$
						pernodemap.put("name", svd.getName().getIdentifier()); //$NON-NLS-1$
						break;
					case VariableDeclarationExpression:
						VariableDeclarationExpression vde = (VariableDeclarationExpression) node;
						Statement parent = ASTNodes.getTypedAncestor(node, Statement.class);
						pernodemap.put("parent", parent); //$NON-NLS-1$
						break;
					case VariableDeclarationStatement:
						VariableDeclarationStatement vds = (VariableDeclarationStatement) node;
						pernodemap.put("fragments", vds.fragments().size()); //$NON-NLS-1$
						break;
					case VariableDeclarationFragment:
						VariableDeclarationFragment vdf = (VariableDeclarationFragment) node;
						Expression vdf_initializer = vdf.getInitializer();
						pernodemap.put("init", vdf_initializer); //$NON-NLS-1$
						pernodemap.put("name", vdf.getName().getIdentifier()); //$NON-NLS-1$
						break;
					//$CASES-OMITTED$
					default:
						break;
				}
			});

		// Print results
		TestLogger.println("=== Complex Node Data ===");
		dataholder.entrySet().stream().forEach(entry -> {
			TestLogger.println(ASTNode.nodeClassForType(entry.getKey().getNodeType()));
			TestLogger.println("  Data: " + entry.getValue());
		});
	}

	/**
	 * Demonstrates using ReferenceHolder with multiple callbacks.
	 * 
	 * <p>Multiple visitor callbacks can share the same ReferenceHolder instance,
	 * allowing them to cooperate in building up complex data structures.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * ReferenceHolder&lt;String, Object&gt; shared = new ReferenceHolder&lt;&gt;();
	 * hv.add(type1, (node, holder) -&gt; { holder.put("key1", value1); return true; });
	 * hv.add(type2, (node, holder) -&gt; { holder.put("key2", value2); return true; });
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Building analysis results that require coordination
	 * between different visitor callbacks.</p>
	 */
	@Test
	public void testSharedDataBetweenCallbacks() {
		Set<ASTNode> nodesprocessed = null;
		ReferenceHolder<String, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Integer>, String, Integer> hv = 
			new HelperVisitor<>(nodesprocessed, dataholder);

		// First callback counts method invocations
		hv.addMethodInvocation((node, holder) -> {
			holder.merge("methodCount", 1, Integer::sum); //$NON-NLS-1$
			return true;
		});

		// Second callback counts variable declarations
		hv.addVariableDeclarationFragment((node, holder) -> {
			holder.merge("variableCount", 1, Integer::sum); //$NON-NLS-1$
			return true;
		});

		hv.build(cunit1);

		// Print combined results
		TestLogger.println("=== Combined Statistics ===");
		TestLogger.println("Methods: " + dataholder.getOrDefault("methodCount", 0)); //$NON-NLS-1$
		TestLogger.println("Variables: " + dataholder.getOrDefault("variableCount", 0)); //$NON-NLS-1$
	}

	/**
	 * Demonstrates using computeIfAbsent for lazy initialization.
	 * 
	 * <p>The computeIfAbsent method is useful when you want to lazily create
	 * values only when needed. This is more efficient than checking for null
	 * and then creating the value.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * List&lt;ASTNode&gt; nodes = holder.computeIfAbsent(key, k -&gt; new ArrayList&lt;&gt;());
	 * nodes.add(node);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Grouping nodes by category without pre-initializing empty groups.</p>
	 */
	@Test
	public void testLazyInitialization() {
		ReferenceHolder<String, java.util.List<ASTNode>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1, EnumSet.allOf(VisitorEnum.class), dataholder, null, (node, holder) -> {
			String category = VisitorEnum.fromNode(node).name();
			java.util.List<ASTNode> nodes = holder.computeIfAbsent(category, k -> new java.util.ArrayList<>());
			nodes.add(node);
		});

		// Print results
		TestLogger.println("=== Nodes Grouped by Type ===");
		dataholder.forEach((category, nodes) -> {
			TestLogger.println(category + ": " + nodes.size() + " nodes");
		});
	}
}
