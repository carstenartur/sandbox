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
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
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
 * Testing different aspects of the new api
 *
 * @author Carsten Hammer
 *
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

	private void astnodeprocessorend(ASTNode node, ReferenceHolder<String,NodeFound> holder) {
		String x = "End   "+node.getNodeType() + " :" + node; //$NON-NLS-1$ //$NON-NLS-2$
		System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType())); //$NON-NLS-1$
	}

	private Boolean astnodeprocessor(ASTNode node, ReferenceHolder<String,NodeFound> holder) {
		//		NodeFound nodeFound = holder.get(VisitorEnum.fromNodetype(node.getNodeType()));
		String x = "Start "+node.getNodeType() + " :" + node; //$NON-NLS-1$ //$NON-NLS-2$
		System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType())); //$NON-NLS-1$
		return true;
	}

	private boolean handleMethodInvocation(MethodInvocation assignment, ReferenceHolder<String,NodeFound> holder) {
		System.out.println(assignment);
		return true;
	}

	/**
	 * Here the method reference is referring to a method using the right parameter MethodInvocation instead of ASTNode
	 */
	@Test
	public void simpleTest() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation(this::handleMethodInvocation);
		hv.build(cunit1);
	}

	@Test
	public void simpleTest_oldway() {
		ASTVisitor astvisitor=new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				return VisitorTest.this.handleMethodInvocation(node,null);
			}
		};
		cunit1.accept(astvisitor);
	}


	/**
	 * For methodinvocation there is a method that allows to specify the method name.
	 */
	@Test
	public void simpleTest2() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation("add", this::handleMethodInvocation); //$NON-NLS-1$
		hv.build(cunit1);


		//		Function<Integer, Integer> multiply = this::extracted;
		//		BiFunction<Integer, Integer, Integer> add      = this::extracted2;
		//
		//		BiFunction<Integer, Integer, Integer> multiplyThenAdd = add.andThen(multiply);
		//
		//		Integer result2 = multiplyThenAdd.apply(3, 3);
		//		System.out.println(result2);
	}

	@Test
	public void simpleTest2oldway() {
		String name ="add"; //$NON-NLS-1$
		ASTVisitor astvisitor=new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if(!node.getName().getIdentifier().equals(name)) {
					return true;
				}
				return VisitorTest.this.handleMethodInvocation(node,null);
			}
		};
		cunit1.accept(astvisitor);
	}

	//	private Integer extracted2(Integer value,Integer value2) {
	//		return value + value2;
	//	}
	//
	//	private Integer extracted(Integer value) {
	//		return value * 2;
	//	}

	@Test
	public void simpleTest2b() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> bs = this::handleMethodInvocation;
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> after = (mi, rh) -> true;
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> bs2= bs.or(after);
		hv.addMethodInvocation("add", bs2); //$NON-NLS-1$
		hv.build(cunit1);
	}

	@Test
	public void simpleTest2b_oldway() {
		String name ="add"; //$NON-NLS-1$
		ASTVisitor astvisitor=new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if(!node.getName().getIdentifier().equals(name)) {
					return true;
				}
				return VisitorTest.this.handleMethodInvocation(node,null);
			}
		};
		ASTVisitor astvisitor2=new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if(!node.getName().getIdentifier().equals(name)) {
				}
				return true;
			}
		};
		cunit1.accept(astvisitor);
		cunit1.accept(astvisitor2);
	}

	@Test
	public void simpleTest3() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocessor);
		});
		VisitorEnum.stream().forEach(ve -> {
			hv.addEnd(ve, this::astnodeprocessorend);
		});
		hv.build(cunit1);
	}

	@Test
	public void simpleTest3oldway() {
		ASTVisitor astvisitor=new ASTVisitor() {

			@Override
			public void endVisit(AnnotationTypeDeclaration node) {
				VisitorTest.this.astnodeprocessorend(node,null);
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				return VisitorTest.this.astnodeprocessor(node,null);
			}

			@Override
			public void endVisit(AnonymousClassDeclaration node) {
				VisitorTest.this.astnodeprocessorend(node,null);
			}

			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				return VisitorTest.this.astnodeprocessor(node,null);
			}

			// all other method stubs to be added ...
		};
		cunit1.accept(astvisitor);
	}

	/**
	 * Use method reference, one for "visit" and another for "visitend"
	 */
	@Test
	public void simpleTest3b() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocessor,this::astnodeprocessorend);
		});
		hv.build(cunit2);
	}

	@Test
	public void simpleTest3boldway() {
		ASTVisitor astvisitor=new ASTVisitor() {

			@Override
			public void endVisit(AnnotationTypeDeclaration node) {
				VisitorTest.this.astnodeprocessorend(node,null);
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				return VisitorTest.this.astnodeprocessor(node,null);
			}

			@Override
			public void endVisit(AnonymousClassDeclaration node) {
				VisitorTest.this.astnodeprocessorend(node,null);
			}

			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				return VisitorTest.this.astnodeprocessor(node,null);
			}

			// all other method stubs to be added ...
		};
		cunit1.accept(astvisitor);
	}



	/**
	 * Use method reference, you can use the method reference returning boolean needed for "visit" for "visitend" too.
	 * That way you need only one method.
	 */
	@Test
	public void simpleTest3c() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocessor,this::astnodeprocessor);
		});
		hv.build(cunit2);
	}

	@Test
	public void simpleTest3d() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				String x = "Start "+node.getNodeType() + " :" + node; //$NON-NLS-1$ //$NON-NLS-2$
				System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType())); //$NON-NLS-1$
				return true;
			});
		});
		VisitorEnum.stream().forEach(ve -> {
			hv.addEnd(ve, (node, holder) -> {
				String x = "End   "+node.getNodeType() + " :" + node; //$NON-NLS-1$ //$NON-NLS-2$
				System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType())); //$NON-NLS-1$
			});
		});
		hv.build(cunit1);
	}

	/**
	 * Show how to visit a collection of nodes
	 */
	@Test
	public void simpleTest4() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		EnumSet<VisitorEnum> myset = EnumSet.of(
				VisitorEnum.SingleVariableDeclaration,
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment);
		myset.forEach(ve -> {
			hv.add(ve, this::astnodeprocessor,this::astnodeprocessorend);
		});
		hv.build(cunit1);
	}

	@Test
	public void simpleTest4b() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		EnumSet<VisitorEnum> myset = EnumSet.of(
				VisitorEnum.SingleVariableDeclaration,
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment);
		myset.forEach(ve -> {
			addVisitor(hv, ve);
		});
		hv.build(cunit1);
	}

	private void addVisitor(HelperVisitor<ReferenceHolder<String, NodeFound>,String,NodeFound> hv, VisitorEnum ve) {
		hv.add(ve, this::astnodeprocessor,this::astnodeprocessorend);
	}

	@Test
	public void simpleTest4c() {
		EnumSet<VisitorEnum> myset = EnumSet.of(
				VisitorEnum.SingleVariableDeclaration,
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment);
		ReferenceHolder<ASTNode, String> dataholder = new ReferenceHolder<>();
		BiPredicate<ASTNode, ReferenceHolder<ASTNode, String>> bs =(node,holder)->{
			System.out.printf("%-40s %s%n","Start "+node.getNodeType() + " :" + node,ASTNode.nodeClassForType(node.getNodeType())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return false;
		};
		BiConsumer<ASTNode, ReferenceHolder<ASTNode, String>> bc = (node,holder)->{
			System.out.printf("%-40s %s%n","End   "+node.getNodeType() + " :" + node,ASTNode.nodeClassForType(node.getNodeType())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		};
		HelperVisitor.callVisitor(cunit1, myset, dataholder,null, bs, bc);
	}



	/**
	 * Show how to use the ReferenceHolder to access data while visiting the AST.
	 * Here: count nodes and list result
	 */
	@Test
	public void simpleTest5() {
		Set<ASTNode> nodesprocessed = null;
		ReferenceHolder<VisitorEnum, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<VisitorEnum,Integer>,VisitorEnum,Integer> hv = new HelperVisitor<>(nodesprocessed, dataholder);
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
				return true;
			});
		});
		VisitorEnum.stream().forEach(ve -> {
			hv.addEnd(ve, (node, holder) -> {
				holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
			});
		});
		hv.build(cunit1);
		for(VisitorEnum ve: dataholder.keySet()) {
			System.out.println(dataholder.get(ve)+"\t"+ve.name()); //$NON-NLS-1$
		}
	}

	/**
	 * Show how to use the ReferenceHolder to access data while visiting the AST.
	 * Here: count nodes and list result
	 *
	 * Simpler variant compared to the one above only making use of visitend
	 */
	@Test
	public void simpleTest5b() {
		ReferenceHolder<VisitorEnum, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1, EnumSet.allOf(VisitorEnum.class), dataholder,null, this::countVisits);

		/**
		 * Presenting result
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getValue()+"\t"+entry.getKey().name()); //$NON-NLS-1$
		});
	}

	private void countVisits(ASTNode node, ReferenceHolder<VisitorEnum, Integer> holder) {
		holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
	}

	@Test
	public void simpleTest5c() {
		ReferenceHolder<ASTNode, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(cunit1,EnumSet.of(
				VisitorEnum.SingleVariableDeclaration,
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment), dataholder,null, (node,holder)->{
					holder.put(node, node.getStartPosition());
				});

		/**
		 * Presenting result
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getKey()+"\t"+entry.getValue()+"\t"+ASTNode.nodeClassForType(entry.getKey().getNodeType())); //$NON-NLS-1$ //$NON-NLS-2$
		});
	}

	@Test
	public void simpleTest5d() {
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
		 * Presenting result
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getKey()+"\t"+ASTNode.nodeClassForType(entry.getKey().getNodeType())); //$NON-NLS-1$
			System.out.println("===>"+entry.getValue().get("init")); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println();
		});
	}

	@Test
	public void simpleTest5e() {
		ReferenceHolder<ASTNode, Map<String,Object>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVariableDeclarationStatementVisitor(Iterator.class, cunit2, dataholder,null, (init_iterator,holder_a)->{
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
		 * Presenting result
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
				if (mi.getName().toString().equals("iterator")) { //$NON-NLS-1$
					name.add(sn.getIdentifier());
				}
			}
		}
		return name;
	}

	/**
	 * Here we use fluent style to express visiting where the subsequent search is starting at the node the preceding search found.
	 *
	 * This sample finds the 3 nodes related to a while loop based on iterator for you.
	 *
	 * 1) VariableDeclarationStatement
	 * 2) "below 1)" all WhileStatement
	 * 3) "below 2)" all MethodInvocation
	 *
	 * "below" is not meant literally as there is a helper lambda expression that allows to navigate on the found node of
	 * the type searched for to another node as a start for the directly following fluent call.
	 *
	 * That means in this case by the lambda expression "s->s.getParent()" in the call to search for VariableDeclarationStatement
	 * the found node is not directly used as start node for the following call to search for WhileStatement. Instead the parent of this node
	 * is used. Otherwise it would not be possible to find related whilestatements.
	 *
	 * A similar trick is used in the next call to find all related MethodInvocations. As we are only interested in ".next()" calls in the
	 * while loop body we use the lambda expression "s -> ((WhileStatement)s).getBody()" to go on searching for ".next()".
	 */
	@Test
	public void simpleTest5f() {
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

	@Test
	public void simpleTest5g() {
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
	 * This one is not really possible in "normal" visitors. Change visitors while visiting.
	 */
	@Test
	public void modifyTest1() {
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

	@Test
	public void modifyTest2() {
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

	Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}

	@Test
	public void methodinvocationTest() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>,String,NodeFound> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation(IProgressMonitor.class, "beginTask", (methodinvocationnode, myholder)->{ //$NON-NLS-1$
			String x = "Start "+methodinvocationnode.getNodeType() + " :" + methodinvocationnode; //$NON-NLS-1$ //$NON-NLS-2$
			System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(methodinvocationnode.getNodeType())); //$NON-NLS-1$
			return true;
		});
		hv.build(cunit3);
	}

	@Test
	public void methodinvocationTestb() {
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
}
