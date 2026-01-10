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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.ASTProcessor;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Tests demonstrating the ASTProcessor fluent API for chaining visitor operations.
 * 
 * <p>The ASTProcessor provides a fluent, chainable API for setting up complex
 * visitor patterns. Unlike HelperVisitor which requires explicit build() calls,
 * ASTProcessor allows you to chain multiple visitor registrations in a single
 * expression.</p>
 * 
 * <h2>Key Concepts</h2>
 * <ul>
 * <li><b>Fluent API</b>: Chain method calls for more readable code</li>
 * <li><b>Navigation Functions</b>: Control where the next visitor starts searching</li>
 * <li><b>Hierarchical Processing</b>: Search within subtrees of previously found nodes</li>
 * </ul>
 * 
 * <h2>Basic Pattern</h2>
 * <pre>
 * ASTProcessor&lt;...&gt; processor = new ASTProcessor&lt;&gt;(dataHolder, processedNodes);
 * processor
 *     .callMethodInvocationVisitor((node, holder) -&gt; { ... return true; })
 *     .callFieldDeclarationVisitor((node, holder) -&gt; { ... return true; })
 *     .build(compilationUnit);
 * </pre>
 * 
 * <h2>Advanced Pattern: Navigation Functions</h2>
 * <p>Navigation functions allow you to control where the next visitor in the chain
 * starts searching. This enables powerful hierarchical search patterns.</p>
 * 
 * <pre>
 * processor
 *     .callVariableDeclarationStatementVisitor(Iterator.class,
 *         (node, holder) -&gt; { ... },
 *         ASTNode::getParent)  // Navigate to parent before next search
 *     .callWhileStatementVisitor(
 *         (node, holder) -&gt; { ... },
 *         s -&gt; ((WhileStatement)s).getBody())  // Navigate to body before next search
 *     .build(compilationUnit);
 * </pre>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 * <li>Finding patterns spanning multiple related nodes</li>
 * <li>Analyzing code structure across different AST levels</li>
 * <li>Implementing complex refactoring patterns</li>
 * <li>Building analysis tools that need hierarchical searches</li>
 * </ul>
 * 
 * @author Carsten Hammer
 * @see ASTProcessor
 * @see ReferenceHolder
 */
public class ASTProcessorTest {

	private static CompilationUnit cunit2;
	private static CompilationUnit cunit3;

	@BeforeAll
	public static void init() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);

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

		cunit3 = createunit(parser,"""
			package test;
			import java.util.*;
			import org.eclipse.core.runtime.CoreException;
			import org.eclipse.core.runtime.IProgressMonitor;
			import org.eclipse.core.runtime.SubProgressMonitor;
			import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
			public class Test extends ArrayList<String> {
			    public void createPackageFragmentRoot(IProgressMonitor monitor) throws CoreException, InterruptedException {
					monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
					IProgressMonitor subProgressMonitor= new SubProgressMonitor(monitor, 1);
					IProgressMonitor subProgressMonitor2= new SubProgressMonitor(monitor, 2);
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
	 * Demonstrates basic fluent API usage with simple chaining.
	 * 
	 * <p>This test shows the most basic usage of ASTProcessor: chaining
	 * multiple visitor registrations and then building the visitor.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * processor
	 *     .callVisitorType1(callback1)
	 *     .callVisitorType2(callback2)
	 *     .build(ast);
	 * </pre>
	 */
	@Test
	public void testBasicChaining() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>, String, Object> astp = 
			new ASTProcessor<>(dataholder, null);
		
		astp.callVariableDeclarationStatementVisitor(Iterator.class, (node, holder) -> {
			holder.put("found_iterator_declaration", node); //$NON-NLS-1$
			System.out.println("Found Iterator declaration: " + node);
			return true;
		}).build(cunit2);

		System.out.println("=== Results ===");
		System.out.println("Found: " + dataholder.containsKey("found_iterator_declaration")); //$NON-NLS-1$
	}

	/**
	 * Demonstrates hierarchical search using navigation functions.
	 * 
	 * <p>This test shows how to use navigation functions to search within
	 * subtrees of previously found nodes. The pattern finds Iterator declarations,
	 * then searches for WhileStatements in the parent scope, then searches for
	 * MethodInvocations within the WhileStatement body.</p>
	 * 
	 * <p><b>Pattern Explanation:</b></p>
	 * <ol>
	 * <li>Find all VariableDeclarationStatements of type Iterator</li>
	 * <li>For each found, navigate to its parent before next search</li>
	 * <li>Find WhileStatements in that parent scope</li>
	 * <li>For each WhileStatement, navigate to its body</li>
	 * <li>Find MethodInvocations named "next" in that body</li>
	 * </ol>
	 * 
	 * <p><b>Use case:</b> Finding while-loop patterns that iterate over collections,
	 * which is a common pattern to convert to enhanced for-loops or streams.</p>
	 */
	@Test
	public void testHierarchicalSearch() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>, String, Object> astp = 
			new ASTProcessor<>(dataholder, null);
		
		astp
		.callVariableDeclarationStatementVisitor(Iterator.class, (node, holder) -> {
			/**
			 * Find Iterator variable declarations
			 */
			holder.put("init", node); //$NON-NLS-1$
			List<String> varName = computeVarName((VariableDeclarationStatement) node);
			holder.put("initvarname", varName.get(0)); //$NON-NLS-1$
			System.out.println("Step 1: Found Iterator: " + varName.get(0));
			return true;
		}, ASTNode::getParent)  // Navigate to parent for next search
		.callWhileStatementVisitor((node, holder) -> {
			/**
			 * Find WhileStatements in the parent scope
			 */
			holder.put("while", node); //$NON-NLS-1$
			String name = computeNextVarname((WhileStatement) node);
			holder.put("whilevarname", name); //$NON-NLS-1$
			System.out.println("Step 2: Found WhileStatement with variable: " + name);
			return true;
		}, s -> ((WhileStatement) s).getBody())  // Navigate to body for next search
		.callMethodInvocationVisitor("next", (node, holder) -> { //$NON-NLS-1$
			/**
			 * Find "next()" calls in the while body
			 */
			String initVarName = (String) holder.get("initvarname"); //$NON-NLS-1$
			String whileVarName = (String) holder.get("whilevarname"); //$NON-NLS-1$
			
			Expression element = ((MethodInvocation) node).getExpression();
			SimpleName sn = ASTNodes.as(element, SimpleName.class);
			if (sn != null) {
				String identifier = sn.getIdentifier();
				if (initVarName.equals(identifier) && initVarName.equals(whileVarName)) {
					System.out.println("Step 3: Found matching next() call: " + node);
					System.out.println("=== Complete pattern found! ===");
					System.out.println("Iterator: " + holder.get("init")); //$NON-NLS-1$
					System.out.println("While: " + holder.get("while")); //$NON-NLS-1$
					System.out.println("Next: " + node);
				}
			}
			return true;
		}).build(cunit2);
	}

	/**
	 * Demonstrates filtering by method receiver type.
	 * 
	 * <p>This test shows how to find method invocations on specific receiver types.
	 * This is useful for finding API usage patterns or migration targets.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * processor.callMethodInvocationVisitor(ReceiverType.class, "methodName", callback)
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Finding deprecated API usage, identifying migration
	 * candidates, analyzing framework adoption.</p>
	 */
	@Test
	public void testMethodInvocationFiltering() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>, String, Object> astp = 
			new ASTProcessor<>(dataholder, null);
		
		astp.callMethodInvocationVisitor(IProgressMonitor.class, "beginTask", (node, holder) -> { //$NON-NLS-1$
			System.out.println("Found IProgressMonitor.beginTask() call: " + node);
			holder.merge("count", 1, (a, b) -> (Integer) a + (Integer) b); //$NON-NLS-1$
			return true;
		}).build(cunit3);

		System.out.println("=== Results ===");
		System.out.println("Total beginTask calls: " + dataholder.getOrDefault("count", 0)); //$NON-NLS-1$
	}

	/**
	 * Demonstrates chaining with navigation to ancestor nodes.
	 * 
	 * <p>This test shows how to navigate upward in the AST tree using
	 * ASTNodes.getTypedAncestor(). This is useful for finding the broader
	 * context of a code pattern.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * processor
	 *     .callMethodInvocationVisitor(callback, 
	 *         s -&gt; ASTNodes.getTypedAncestor(s, Block.class))
	 * </pre>
	 * 
	 * <p><b>Use case:</b> Finding the enclosing method or block for contextual
	 * analysis or refactoring.</p>
	 */
	@Test
	public void testAncestorNavigation() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>, String, Object> astp = 
			new ASTProcessor<>(dataholder, null);
		
		astp
		.callMethodInvocationVisitor(IProgressMonitor.class, "beginTask", (node, holder) -> { //$NON-NLS-1$
			System.out.println("Found method invocation: " + node);
			holder.put("method", node); //$NON-NLS-1$
			return true;
		}, s -> ASTNodes.getTypedAncestor(s, Block.class))  // Navigate to enclosing block
		.callClassInstanceCreationVisitor((node, holder) -> {
			System.out.println("Found class instance creation in same block: " + node);
			holder.put("creation", node); //$NON-NLS-1$
			return true;
		}).build(cunit3);

		System.out.println("=== Results ===");
		System.out.println("Found method: " + dataholder.containsKey("method")); //$NON-NLS-1$
		System.out.println("Found creation: " + dataholder.containsKey("creation")); //$NON-NLS-1$
	}

	/**
	 * Demonstrates using ASTProcessor for simple single-visitor scenarios.
	 * 
	 * <p>Even when you only need one visitor, ASTProcessor can still be useful
	 * for its concise syntax.</p>
	 * 
	 * <p><b>Pattern:</b></p>
	 * <pre>
	 * new ASTProcessor&lt;&gt;(dataHolder, null)
	 *     .callVisitor(callback)
	 *     .build(ast);
	 * </pre>
	 */
	@Test
	public void testSingleVisitor() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>, String, Object> astp = 
			new ASTProcessor<>(dataholder, null);
		
		astp.callVariableDeclarationStatementVisitor(Iterator.class, (node, holder) -> {
			System.out.println("Found: " + node);
			holder.merge("count", 1, (a, b) -> (Integer) a + (Integer) b); //$NON-NLS-1$
			return true;
		}).build(cunit2);

		System.out.println("Total found: " + dataholder.getOrDefault("count", 0)); //$NON-NLS-1$
	}

	// Helper methods for analyzing Iterator patterns

	private static String computeNextVarname(WhileStatement whilestatement) {
		String name = null;
		Expression exp = whilestatement.getExpression();
		if (exp instanceof MethodInvocation mi) {
			Expression expression = mi.getExpression();
			if (mi.getName().getIdentifier().equals("hasNext")) { //$NON-NLS-1$
				SimpleName variable = ASTNodes.as(expression, SimpleName.class);
				if (variable != null) {
					IBinding resolveBinding = variable.resolveBinding();
					name = resolveBinding.getName();
				}
			}
		}
		return name;
	}

	private static List<String> computeVarName(VariableDeclarationStatement node_a) {
		List<String> name = new ArrayList<>();
		VariableDeclarationFragment bli = (VariableDeclarationFragment) node_a.fragments().get(0);
		name.add(bli.getName().getIdentifier());
		Expression exp = bli.getInitializer();
		if (exp instanceof MethodInvocation mi) {
			Expression element = mi.getExpression();
			if (element instanceof SimpleName sn) {
				if ("iterator".equals(mi.getName().toString())) { //$NON-NLS-1$
					name.add(sn.getIdentifier());
				}
			}
		}
		return name;
	}
}
