/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.ASTProcessor;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

/**
 * Integration tests for the HelperVisitor API.
 * 
 * <p>This class contains integration tests that exercise multiple aspects of the visitor API
 * together. For focused tests and API documentation, see:</p>
 * <ul>
 * <li>{@link BasicVisitorUsageTest} - Basic visitor usage patterns</li>
 * <li>{@link ReferenceHolderTest} - Data collection with ReferenceHolder</li>
 * <li>{@link ASTProcessorTest} - Fluent API and chaining patterns</li>
 * <li>{@link AdvancedVisitorPatternsTest} - Advanced patterns and dynamic behavior</li>
 * <li>{@link VisitorApiDocumentationTest} - Complete API documentation</li>
 * </ul>
 *
 * @author Carsten Hammer
 * @see BasicVisitorUsageTest
 * @see ReferenceHolderTest
 * @see ASTProcessorTest
 * @see AdvancedVisitorPatternsTest
 * @see VisitorApiDocumentationTest
 */
public class VisitorTest {

	private static CompilationUnit cunit1,cunit2,cunit3;

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


		cunit2 =createunit(parser,"""
			package test;
			import java.util.*;
			public class Test {
			    void println(String strings) {
			    }
			    void m(List<String> strings,List<String> strings2) {
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

		cunit3 =createunit(parser,"""
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

	private static CompilationUnit createunit(ASTParser parser,String code, String name) {
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Integration test: Complex data collection with multiple node types.
	 * 
	 * <p>This test demonstrates collecting detailed information about variable declarations
	 * across multiple node types in a single pass. This pattern is commonly used in
	 * refactoring tools that need to analyze variable usage and scope.</p>
	 */
	@Test
	public void testComplexDataCollection() {
		ReferenceHolder<ASTNode, Map<String,Object>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1,EnumSet.of(
				VisitorEnum.SingleVariableDeclaration,
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment), dataholder,null, (node,holder)->{
					Map<String, Object> pernodemap = holder.computeIfAbsent(node, k -> new HashMap<>());
					switch(VisitorEnum.fromNode(node)) {
					case SingleVariableDeclaration:
						SingleVariableDeclaration svd=(SingleVariableDeclaration) node;
						Expression svd_initializer = svd.getInitializer();
						pernodemap.put("init", svd_initializer); //$NON-NLS-1$
						break;
					case VariableDeclarationExpression:
						VariableDeclarationExpression vde=(VariableDeclarationExpression) node;
						ASTNodes.getTypedAncestor(node, Statement.class);
						break;
					case VariableDeclarationStatement:
						VariableDeclarationStatement vds=(VariableDeclarationStatement) node;
						break;
					case VariableDeclarationFragment:
						VariableDeclarationFragment vdf=(VariableDeclarationFragment) node;
						Expression vdf_initializer = vdf.getInitializer();
						pernodemap.put("init", vdf_initializer); //$NON-NLS-1$
						break;
						//$CASES-OMITTED$
					default:
						break;
					}
				});

		/**
		 * Verify results
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getKey()+"\t"+ASTNode.nodeClassForType(entry.getKey().getNodeType())); //$NON-NLS-1$
			System.out.println("===>"+entry.getValue().get("init")); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println();
		});
	}

	/**
	 * Integration test: Nested hierarchical search for Iterator-based while loops.
	 * 
	 * <p>This complex test demonstrates finding related nodes across multiple AST levels:
	 * Iterator declaration → WhileStatement → next() method calls. This pattern is useful
	 * for refactoring tools that need to analyze and transform iterator-based loops.</p>
	 */
	@Test
	public void testNestedHierarchicalSearch() {
		ReferenceHolder<ASTNode, Map<String,Object>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVariableDeclarationStatementVisitor(Iterator.class, cunit2, dataholder,null, (init_iterator,holdera)->{
			List<String> computeVarName = computeVarName(init_iterator);
			HelperVisitor.callWhileStatementVisitor(init_iterator.getParent(), dataholder,null, (whilestatement,holder)->{
				String name = computeNextVarname(whilestatement);
				if(computeVarName.get(0).equals(name)) {
					HelperVisitor.callMethodInvocationVisitor("next", whilestatement.getBody() ,dataholder,null, (mi,holder2)->{ //$NON-NLS-1$
						Map<String, Object> pernodemap2 = holder2.computeIfAbsent(whilestatement, k -> new HashMap<>());
						Expression element2 = mi.getExpression();
						SimpleName sn= ASTNodes.as(element2, SimpleName.class);
						if (sn !=null) {
							String identifier = sn.getIdentifier();
							if(!name.equals(identifier)) {
								return true;
							}
							pernodemap2.put("init", init_iterator); //$NON-NLS-1$
							pernodemap2.put("while", whilestatement); //$NON-NLS-1$
							pernodemap2.put("next", mi); //$NON-NLS-1$
							pernodemap2.put("name", identifier); //$NON-NLS-1$
						}
						return true;
					});
				}
				return true;
			});
			return true;
		});
		
		/**
		 * Verify results
		 */
		System.out.println("#################"); //$NON-NLS-1$
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println("============="); //$NON-NLS-1$
			System.out.println(entry.getKey());
			System.out.println("init ===>"+entry.getValue().get("init")); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println("while ===>"+entry.getValue().get("while")); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println("next ===>"+entry.getValue().get("next")); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println("name ===>"+entry.getValue().get("name")); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println();
		});
	}

	private static String computeNextVarname(WhileStatement whilestatement) {
		String name = null;
		Expression exp = whilestatement.getExpression();
		//		Collection<String> usedVarNames= getUsedVariableNames(whilestatement.getBody());
		if (exp instanceof MethodInvocation mi) {
			Expression expression = mi.getExpression();
			if (mi.getName().getIdentifier().equals("hasNext")) { //$NON-NLS-1$
				//				ITypeBinding resolveTypeBinding = expression.resolveTypeBinding();
				SimpleName variable= ASTNodes.as(expression, SimpleName.class);
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

	/**
	 * Integration test: Fluent API with hierarchical navigation.
	 * 
	 * <p>This test demonstrates the ASTProcessor fluent API for building complex
	 * search chains. The fluent style with navigation functions enables elegant
	 * expression of hierarchical search patterns.</p>
	 */
	@Test
	public void testFluentApiWithNavigation() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>, String, Object> astp=new ASTProcessor<>(dataholder, null);
		astp
		.callVariableDeclarationStatementVisitor(Iterator.class,(node,holder) -> {
			/**
			 * This lambda expression is called for all VariableDeclarationStatement of type Iterator
			 */
			holder.put("init", node); //$NON-NLS-1$
			List<String> computeVarName = computeVarName((VariableDeclarationStatement)node);
			holder.put("initvarname", computeVarName.get(0)); //$NON-NLS-1$
			return true;
		},ASTNode::getParent)
		.callWhileStatementVisitor((node,holder) -> {
			/**
			 * This lambda expression is called for all WhileStatements below the parent of each VariableDeclarationStatement
			 */
			holder.put("while", node); //$NON-NLS-1$
			String name = computeNextVarname((WhileStatement)node);
			holder.put("whilevarname", name); //$NON-NLS-1$
			return true;
		}, s -> ((WhileStatement)s).getBody())
		.callMethodInvocationVisitor("next",(node,holder) -> { //$NON-NLS-1$
			/**
			 * This lambda expression is called for all MethodInvocations "next()" in each Body of WhileStatements found above
			 */
			String name=(String) holder.get("initvarname"); //$NON-NLS-1$
			Expression element2 = ((MethodInvocation)node).getExpression();
			SimpleName sn= ASTNodes.as(element2, SimpleName.class);
			if (sn !=null) {
				String identifier = sn.getIdentifier();
				if(!name.equals(identifier)) {
					return true;
				}
				if(name.equals(holder.get("whilevarname"))) { //$NON-NLS-1$
					System.out.println("====================="); //$NON-NLS-1$
					System.out.println("iterator: "+holder.get("init").toString().trim()); //$NON-NLS-1$ //$NON-NLS-2$
					System.out.println("while: "+holder.get("while").toString().trim()); //$NON-NLS-1$ //$NON-NLS-2$
					System.out.println("next: "+node.toString().trim()); //$NON-NLS-1$
				}
			}
			return true;
		}).build(cunit2);
	}

	/**
	 * Integration test: Simple fluent API usage.
	 * 
	 * <p>Demonstrates basic ASTProcessor usage for finding specific patterns.</p>
	 */
	@Test
	public void testSimpleFluentApi() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>,String,Object> astp=new ASTProcessor<>(dataholder, null);
		astp.callVariableDeclarationStatementVisitor(Iterator.class,(node,holder) -> {
			holder.put("init", node); //$NON-NLS-1$
			List<String> computeVarName = computeVarName((VariableDeclarationStatement)node);
			holder.put("initvarname", computeVarName.get(0)); //$NON-NLS-1$
			System.out.println("init "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}).build(cunit2);
	}

	/**
	 * Integration test: Dynamic visitor modification during traversal.
	 * 
	 * <p>This advanced test demonstrates modifying the visitor configuration dynamically
	 * during AST traversal - a feature that's not possible with traditional ASTVisitor.</p>
	 */
	@Test
	public void testDynamicVisitorModification() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation("println",(node, holder) -> { //$NON-NLS-1$
			System.out.println("Start "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		});
		hv.addMethodInvocation((node, holder) -> {
			System.out.println("End "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			holder.getHelperVisitor().removeVisitor(VisitorEnum.MethodInvocation);
		});
		hv.build(cunit1);
	}

	/**
	 * Integration test: Multi-phase processing with coordinated callbacks.
	 * 
	 * <p>Demonstrates using multiple callbacks that execute in phases: first collecting
	 * data, then processing it. This pattern is useful for analyses that need to gather
	 * information before making decisions.</p>
	 */
	@Test
	public void testMultiPhaseProcessing() {
		Set<ASTNode> nodesprocessed = null;
		ExpectationTracer dataholder = new ExpectationTracer();
		dataholder.stack.push(null);
		HelperVisitor<ExpectationTracer,ASTNode, SimpleName> hv = new HelperVisitor<>(nodesprocessed, dataholder);
		Set<SimpleName> names = new HashSet<>();
		Set<ASTNode> nodes = new HashSet<>();
		hv.addSingleVariableDeclaration((node, holder) -> {
			names.add(node.getName());
			return true;
		});
		hv.addVariableDeclarationFragment((node, holder) -> {
			names.add(node.getName());
			return true;
		});
		hv.addWhileStatement((node, holder) -> {
			nodes.add(node);
			return true;
		});
		hv.addWhileStatement((node, holder) -> {
			nodes.remove(node);
			Collection<String> usedVarNames= getUsedVariableNames(node.getBody());
			System.out.println(usedVarNames);
		});
		hv.addMethodInvocation("next",(methodinvocationnode, myholder) -> { //$NON-NLS-1$
			String x = "Start "+methodinvocationnode.getNodeType() + " :" + methodinvocationnode; //$NON-NLS-1$ //$NON-NLS-2$
			System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(methodinvocationnode.getNodeType())); //$NON-NLS-1$
			return true;
		});
		hv.build(cunit2);
	}

	/**
	 * Integration test: Method invocation filtering by receiver type.
	 * 
	 * <p>Demonstrates filtering method invocations based on receiver type, useful for
	 * finding API usage patterns or implementing migrations.</p>
	 */
	@Test
	public void testMethodInvocationByType() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation(IProgressMonitor.class, "beginTask", (methodinvocationnode, myholder)->{ //$NON-NLS-1$
			String x = "Start "+methodinvocationnode.getNodeType() + " :" + methodinvocationnode; //$NON-NLS-1$ //$NON-NLS-2$
			System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(methodinvocationnode.getNodeType())); //$NON-NLS-1$
			return true;
		});
		hv.build(cunit3);
	}

	/**
	 * Integration test: Fluent API with method invocation filtering and ancestor navigation.
	 * 
	 * <p>Combines method invocation filtering with navigation to enclosing blocks,
	 * demonstrating how to find related code patterns across AST levels.</p>
	 */
	@Test
	public void testFluentApiWithMethodFiltering() {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>,String,Object> astp=new ASTProcessor<>(dataholder, null);
		astp
		.callMethodInvocationVisitor(IProgressMonitor.class,"beginTask",(node,holder) -> { //$NON-NLS-1$
			System.out.println("init "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		},s -> ASTNodes.getTypedAncestor(s, Block.class))
		.callClassInstanceCreationVisitor((node,holder) -> {
			System.out.println("init "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}).build(cunit3);
	}

	// Helper methods for analyzing code patterns

	private Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}
}
