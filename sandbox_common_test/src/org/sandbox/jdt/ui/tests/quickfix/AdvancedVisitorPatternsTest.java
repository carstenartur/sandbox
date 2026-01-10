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

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

/**
 * Tests demonstrating advanced visitor patterns and dynamic behavior.
 * 
 * <p>This test class showcases sophisticated patterns that are difficult or impossible
 * with traditional ASTVisitor, such as:</p>
 * <ul>
 * <li>Combining multiple visitors with logical operators (AND, OR)</li>
 * <li>Dynamically modifying visitors during traversal</li>
 * <li>Coordinating multiple callbacks</li>
 * <li>Conditional visitor removal</li>
 * </ul>
 * 
 * <h2>Advanced Patterns</h2>
 * 
 * <p><b>Pattern 1: Combining Visitors with Logical Operators</b></p>
 * <pre>
 * BiPredicate&lt;Node, Holder&gt; visitor1 = ...;
 * BiPredicate&lt;Node, Holder&gt; visitor2 = ...;
 * BiPredicate&lt;Node, Holder&gt; combined = visitor1.or(visitor2);
 * hv.addMethodInvocation(combined);
 * </pre>
 * 
 * <p><b>Pattern 2: Dynamic Visitor Modification</b></p>
 * <pre>
 * hv.addMethodInvocation((node, holder) -&gt; {
 *     // Process first occurrence
 *     holder.getHelperVisitor().removeVisitor(VisitorEnum.MethodInvocation);
 *     return true;
 * });
 * </pre>
 * 
 * <p><b>Pattern 3: Coordinated Multiple Callbacks</b></p>
 * <pre>
 * // First callback collects data
 * hv.addWhileStatement((node, holder) -&gt; {
 *     collectData(node, holder);
 *     return true;
 * });
 * // Second callback processes collected data
 * hv.addWhileStatement((node, holder) -&gt; {
 *     processData(holder);
 * });
 * </pre>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 * <li>Implementing complex refactoring logic with multiple phases</li>
 * <li>Building adaptive analysis tools that adjust based on findings</li>
 * <li>Creating stateful visitors that change behavior during traversal</li>
 * <li>Implementing early exit strategies for performance</li>
 * </ul>
 * 
 * @author Carsten Hammer
 * @see HelperVisitor
 * @see BiPredicate
 */
public class AdvancedVisitorPatternsTest {

	private static CompilationUnit cunit1;
	private static CompilationUnit cunit2;

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

		cunit2 = createunit(parser,"""
			package test;
			import java.util.*;
			public class Test {
			    void println(String strings) {
			    }
			    void m(List<String> strings, List<String> strings2) {
			        Collections.reverse(strings);
			        Iterator it = strings.iterator();
			        while (it.hasNext()) {
			            Iterator it2 = strings2.iterator();
			            while (it2.hasNext()) {
			                String s2 = (String) it2.next();
			                System.out.println(s2);
			            }
			            // OK
			            System.out.println(it.next());
			            println(it.next());
			        }
			        System.out.println();
			    }
			}
			""", "Test"); //$NON-NLS-1$ //$NON-NLS-2$
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
	 * Demonstrates combining visitor predicates with logical OR.
	 * 
	 * <p>BiPredicate supports functional composition using or(), and(), and negate().
	 * This allows building complex visitor logic from simple building blocks.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * BiPredicate&lt;Node, Holder&gt; combined = predicate1.or(predicate2);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Handling multiple related patterns with shared logic,
	 * or implementing fallback behavior when primary pattern doesn't match.</p>
	 */
	@Test
	public void testCombiningVisitorsWithOr() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, NodeFound>, String, NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> firstVisitor = 
			(node, holder) -> {
				System.out.println("First visitor: " + node);
				return true;
			};
		
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> secondVisitor = 
			(node, holder) -> {
				System.out.println("Second visitor: " + node);
				return true;
			};
		
		// Combine with OR - both visitors will be called
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> combined = 
			firstVisitor.or(secondVisitor);
		
		hv.addMethodInvocation("add", combined); //$NON-NLS-1$
		hv.build(cunit1);
	}

	/**
	 * Demonstrates combining visitor predicates with logical AND.
	 * 
	 * <p>Using and() allows implementing guard conditions or filters that must
	 * all pass before processing.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * BiPredicate&lt;Node, Holder&gt; filtered = filter.and(processor);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Implementing multi-stage validation or processing
	 * where all conditions must be satisfied.</p>
	 */
	@Test
	public void testCombiningVisitorsWithAnd() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, NodeFound>, String, NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> filter = 
			(node, holder) -> {
				// Only process if method name starts with 'p'
				boolean passes = node.getName().getIdentifier().startsWith("p");
				System.out.println("Filter " + (passes ? "passed" : "failed") + " for: " + node.getName());
				return passes;
			};
		
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> processor = 
			(node, holder) -> {
				System.out.println("Processing: " + node);
				return true;
			};
		
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> combined = 
			filter.and(processor);
		
		hv.addMethodInvocation(combined);
		hv.build(cunit1);
	}

	/**
	 * Demonstrates dynamically removing visitors during traversal.
	 * 
	 * <p>This advanced pattern allows visitors to modify the visitor chain itself
	 * while traversing the AST. This is impossible with traditional ASTVisitor.</p>
	 * 
	 * <p><b>Use case:</b> Implementing "process only first occurrence" logic,
	 * or adaptive analysis that stops once a condition is met.</p>
	 * 
	 * <p><b>Warning:</b> Use with caution as it makes visitor behavior less predictable.</p>
	 */
	@Test
	public void testDynamicVisitorRemoval() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, NodeFound>, String, NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		// First callback processes first method invocation
		hv.addMethodInvocation("println", (node, holder) -> { //$NON-NLS-1$
			System.out.println("Processing first println: " + node);
			return true;
		});
		
		// Second callback removes the visitor after first invocation
		hv.addMethodInvocation((node, holder) -> {
			System.out.println("Removing MethodInvocation visitor after: " + node);
			holder.getHelperVisitor().removeVisitor(VisitorEnum.MethodInvocation);
		});
		
		hv.build(cunit1);
	}

	/**
	 * Demonstrates coordinated multi-phase processing with multiple callbacks.
	 * 
	 * <p>This pattern uses multiple callbacks that execute in sequence, with later
	 * callbacks processing data collected by earlier ones. This enables implementing
	 * multi-phase analysis or transformation logic.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <ol>
	 * <li>First callback: collect data in ReferenceHolder</li>
	 * <li>Second callback: process collected data</li>
	 * <li>Third callback: cleanup or finalize</li>
	 * </ol>
	 * 
	 * <p><b>Use case:</b> Two-pass analysis where first pass collects information
	 * and second pass uses it for decisions.</p>
	 */
	@Test
	public void testCoordinatedMultiPhaseProcessing() {
		Set<ASTNode> nodesprocessed = null;
		ExpectationTracer dataholder = new ExpectationTracer();
		dataholder.stack.push(null);
		HelperVisitor<ExpectationTracer, ASTNode, SimpleName> hv = 
			new HelperVisitor<>(nodesprocessed, dataholder);
		
		Set<SimpleName> names = new HashSet<>();
		Set<ASTNode> nodes = new HashSet<>();
		
		// Phase 1: Collect variable names
		hv.addSingleVariableDeclaration((node, holder) -> {
			names.add(node.getName());
			System.out.println("Phase 1: Collected variable name: " + node.getName());
			return true;
		});
		
		hv.addVariableDeclarationFragment((node, holder) -> {
			names.add(node.getName());
			System.out.println("Phase 1: Collected fragment name: " + node.getName());
			return true;
		});
		
		// Phase 2: Collect while statement nodes
		hv.addWhileStatement((node, holder) -> {
			nodes.add(node);
			System.out.println("Phase 2: Collected while statement");
			return true;
		});
		
		// Phase 3: Process collected data
		hv.addWhileStatement((node, holder) -> {
			nodes.remove(node);
			Collection<String> usedVarNames = getUsedVariableNames(node.getBody());
			System.out.println("Phase 3: Used variables in while: " + usedVarNames);
			System.out.println("Phase 3: Collected " + names.size() + " variable names");
			// Check if any collected names are used in the while body
			for (SimpleName name : names) {
				if (usedVarNames.contains(name.getIdentifier())) {
					System.out.println("Phase 3: Variable '" + name.getIdentifier() + "' is used in while body");
				}
			}
		});
		
		hv.build(cunit2);
	}

	/**
	 * Demonstrates tracking processed nodes to avoid duplicate processing.
	 * 
	 * <p>The HelperVisitor constructor accepts a Set of processed nodes that can
	 * be used to track which nodes have been visited and avoid processing them
	 * multiple times.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * Set&lt;ASTNode&gt; processed = new HashSet&lt;&gt;();
	 * HelperVisitor&lt;...&gt; hv = new HelperVisitor&lt;&gt;(processed, dataHolder);
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Complex multi-pass analysis where you need to ensure
	 * nodes are only processed once despite multiple visitor registrations.</p>
	 */
	@Test
	public void testTrackingProcessedNodes() {
		Set<ASTNode> processed = new HashSet<>();
		HelperVisitor<ReferenceHolder<String, NodeFound>, String, NodeFound> hv = 
			new HelperVisitor<>(processed, new ReferenceHolder<>());
		
		hv.addMethodInvocation((node, holder) -> {
			if (processed.contains(node)) {
				System.out.println("Already processed: " + node);
			} else {
				System.out.println("Processing: " + node);
				processed.add(node);
			}
			return true;
		});
		
		hv.build(cunit1);
		
		System.out.println("Total processed nodes: " + processed.size());
	}

	/**
	 * Demonstrates visitor behavior modification based on holder state.
	 * 
	 * <p>Visitors can check the ReferenceHolder state to make decisions about
	 * whether to process a node. This enables implementing stateful visitors that
	 * adapt their behavior based on what they've seen.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * hv.add(nodeType, (node, holder) -&gt; {
	 *     if (holder.containsKey("stopCondition")) {
	 *         return false; // Skip this node
	 *     }
	 *     // Process normally
	 *     return true;
	 * });
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Implementing early-exit conditions, adaptive analysis,
	 * or context-sensitive processing.</p>
	 */
	@Test
	public void testStatefulVisitorBehavior() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
			new HelperVisitor<>(null, dataholder);
		
		hv.addMethodInvocation((node, holder) -> {
			int count = (Integer) holder.getOrDefault("count", 0); //$NON-NLS-1$
			
			if (count >= 3) {
				System.out.println("Stopping after 3 invocations");
				return false; // Stop visiting children
			}
			
			System.out.println("Processing invocation #" + (count + 1) + ": " + node);
			holder.put("count", count + 1); //$NON-NLS-1$
			return true;
		});
		
		hv.build(cunit1);
		
		System.out.println("Total processed: " + dataholder.getOrDefault("count", 0)); //$NON-NLS-1$
	}

	/**
	 * Demonstrates negate() for inverse filtering.
	 * 
	 * <p>The negate() method allows reversing a predicate's logic, useful for
	 * excluding certain patterns.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * BiPredicate&lt;Node, Holder&gt; exclude = filter.negate();
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Processing all nodes except those matching a pattern.</p>
	 */
	@Test
	public void testNegateFilter() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, NodeFound>, String, NodeFound> hv = 
			new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> isPrintln = 
			(node, holder) -> node.getName().getIdentifier().equals("println");
		
		// Process everything EXCEPT println
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> isNotPrintln = 
			isPrintln.negate();
		
		hv.addMethodInvocation(isNotPrintln.and((node, holder) -> {
			System.out.println("Processing non-println: " + node.getName());
			return true;
		}));
		
		hv.build(cunit1);
	}

	// Helper method for scope analysis
	private Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root = (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}
}
