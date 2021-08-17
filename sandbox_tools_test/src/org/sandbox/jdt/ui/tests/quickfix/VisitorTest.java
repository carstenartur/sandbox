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

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
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
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

public class VisitorTest {

	private static CompilationUnit result;
	private static CompilationUnit result2;

	@BeforeAll
	public static void init() {
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		String code ="package test;\n"
				+"import java.util.Collection;\n"
				+ "\n"  
				+ "public class E {\n"
				+ "	public void hui(Collection<String> arr) {\n"
				+ "		Collection coll = null;\n"
				+ "		for (String var : arr) {\n"
				+ "			 coll.add(var);\n"
				+ "			 System.out.println(var);\n"
				+ "			 System.err.println(var);\n"
				+ "		}\n"
				+ "		System.out.println(arr);\n"
				+ "	}\n"
				+ "}";
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
		parser.setCompilerOptions(options);
		parser.setUnitName("E");
		parser.setSource(code.toCharArray());
		result = (CompilationUnit) parser.createAST(null);
		
		
		String code2="package test;\n"
        + "import java.util.*;\n"
        + "public class Test {\n"
        + "    void m(List<String> strings,List<String> strings2) {\n"
        + "        Collections.reverse(strings);\n"
        + "        Iterator it = strings.iterator();\n"
        + "        while (it.hasNext()) {\n"         
        + "            Iterator it2 = strings2.iterator();\n"
        + "            while (it2.hasNext()) {\n"
        + "                String s2 = (String) it2.next();\n"
        + "                System.out.println(s2);\n"
        + "            }\n"
        + "            // OK\n"
        + "            System.out.println(it.next());\n"
        + "        }\n"
        + "        System.out.println();\n"
        + "    }\n"
        + "}\n";
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName("Test");
		parser.setSource(code2.toCharArray());
		result2 = (CompilationUnit) parser.createAST(null);
//		System.out.println(result.toString());
	}

	private void astnodeprocessorend(ASTNode node, ReferenceHolder<String,NodeFound> holder) {
		String x = "End   "+node.getNodeType() + " :" + node;
		System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType()));
	}

	private Boolean astnodeprocesser(ASTNode node, ReferenceHolder<String,NodeFound> holder) {
//		NodeFound nodeFound = holder.get(VisitorEnum.fromNodetype(node.getNodeType()));
		String x = "Start "+node.getNodeType() + " :" + node;
		System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType()));
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
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation(this::handleMethodInvocation);
		hv.build(result);
	}

	/**
	 * For methodinvocation there is a method that allows to specify the method name.
	 */
	@Test
	public void simpleTest2() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation("add", this::handleMethodInvocation);
		hv.build(result);
		
		
//		Function<Integer, Integer> multiply = this::extracted;
//		BiFunction<Integer, Integer, Integer> add      = this::extracted2;
//
//		BiFunction<Integer, Integer, Integer> multiplyThenAdd = add.andThen(multiply);
//
//		Integer result2 = multiplyThenAdd.apply(3, 3);
//		System.out.println(result2);
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
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> bs = this::handleMethodInvocation;
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> after = (mi,mi2)->{
			return true;
		};
		BiPredicate<MethodInvocation, ReferenceHolder<String, NodeFound>> bs2= bs.or(after);
		hv.addMethodInvocation("add", bs2);
		hv.build(result);
	}
	
	@Test
	public void simpleTest3() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocesser);
		});
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocessorend);
		});
		hv.build(result);
	}
	
	/**
	 * Use method reference, one for "visit" and another for "visitend"
	 */
	@Test
	public void simpleTest3b() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocesser,this::astnodeprocessorend);
		});
		hv.build(result2);
	}
	
	/**
	 * Use method reference, you can use the method reference returning boolean needed for "visit" for "visitend" too.
	 * That way you need only one method.
	 */
	@Test
	public void simpleTest3c() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, this::astnodeprocesser,this::astnodeprocesser);
		});
		hv.build(result2);
	}
	
	@Test
	public void simpleTest3d() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<String, NodeFound>());
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				String x = "Start "+node.getNodeType() + " :" + node;
				System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType()));
				return true;
			});
		});
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				String x = "End   "+node.getNodeType() + " :" + node;
				System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType()));
			});
		});
		hv.build(result);
	}

	/**
	 * Show how to visit a collection of nodes
	 */
	@Test
	public void simpleTest4() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		EnumSet<VisitorEnum> myset = EnumSet.of(
				VisitorEnum.SingleVariableDeclaration, 
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment);
		myset.forEach(ve -> {
			hv.add(ve, this::astnodeprocesser,this::astnodeprocessorend);
		});
		hv.build(result);
	}
	
	@Test
	public void simpleTest4b() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		EnumSet<VisitorEnum> myset = EnumSet.of(
				VisitorEnum.SingleVariableDeclaration, 
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment);
		myset.forEach(ve -> {
			addVisitor(hv, ve);
		});
		hv.build(result);
	}

	private void addVisitor(HelperVisitor<ReferenceHolder<String, NodeFound>> hv, VisitorEnum ve) {
		hv.add(ve, this::astnodeprocesser,this::astnodeprocessorend);
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
			System.out.printf("%-40s %s%n","Start "+node.getNodeType() + " :" + node,ASTNode.nodeClassForType(node.getNodeType()));
			return false;
		};
		BiConsumer<ASTNode, ReferenceHolder<ASTNode, String>> bc = (node,holder)->{
			System.out.printf("%-40s %s%n","End   "+node.getNodeType() + " :" + node,ASTNode.nodeClassForType(node.getNodeType()));
		};
		HelperVisitor.callVisitor(result, myset, dataholder, bs, bc);
	}

	
	
	/**
	 * Show how to use the ReferenceHolder to access data while visiting the AST.
	 * Here: count nodes and list result
	 */
	@Test
	public void simpleTest5() {
		Set<ASTNode> nodesprocessed = null;
		ReferenceHolder<VisitorEnum, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor<ReferenceHolder<VisitorEnum,Integer>> hv = new HelperVisitor<>(nodesprocessed, dataholder);
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
				return true;
			});
		});
		VisitorEnum.stream().forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
			});
		});
		hv.build(result);
		for(VisitorEnum ve: dataholder.keySet()) {
			System.out.println(dataholder.get(ve)+"\t"+ve.name());
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
		HelperVisitor.callVisitor(result, EnumSet.allOf(VisitorEnum.class), dataholder, this::countVisits);

		/**
		 * Presenting result
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getValue()+"\t"+entry.getKey().name());
		});
	}

	private void countVisits(ASTNode node, ReferenceHolder<VisitorEnum, Integer> holder) {
		holder.merge(VisitorEnum.fromNode(node), 1, Integer::sum);
	}

	@Test
	public void simpleTest5c() {
		ReferenceHolder<ASTNode, Integer> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(result,EnumSet.of(
				VisitorEnum.SingleVariableDeclaration, 
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment), dataholder, (node,holder)->{
			holder.put(node, node.getStartPosition());
		});

		/**
		 * Presenting result
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getKey()+"\t"+entry.getValue()+"\t"+ASTNode.nodeClassForType(entry.getKey().getNodeType()));
		});
	}
	
	@Test
	public void simpleTest5d() {
		ReferenceHolder<ASTNode, Map<String,Object>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVisitor(result,EnumSet.of(
				VisitorEnum.SingleVariableDeclaration, 
				VisitorEnum.VariableDeclarationExpression,
				VisitorEnum.VariableDeclarationStatement,
				VisitorEnum.VariableDeclarationFragment), dataholder, (node,holder)->{
			Map<String, Object> pernodemap = holder.computeIfAbsent(node, k -> new HashMap<>());
			switch(VisitorEnum.fromNode(node)) {
			case SingleVariableDeclaration:
				SingleVariableDeclaration svd=(SingleVariableDeclaration) node;
				Expression svd_initializer = svd.getInitializer();
				pernodemap.put("init", svd_initializer);
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
				pernodemap.put("init", vdf_initializer);
				break;
			default:
				break;
			}
		});

		/**
		 * Presenting result
		 */
		dataholder.entrySet().stream().forEach(entry->{
			System.out.println(entry.getKey()+"\t"+ASTNode.nodeClassForType(entry.getKey().getNodeType()));
			System.out.println("===>"+entry.getValue().get("init"));
			System.out.println();
		});
	}
	
	@Test
	public void simpleTest5e() {
		ReferenceHolder<ASTNode, Map<String,Object>> dataholder = new ReferenceHolder<>();
		HelperVisitor.callVariableDeclarationStatementVisitor(result2, dataholder, (node_a,holder_a)->{
			System.out.println(">>>>>>>>>>>>>");
			System.out.println(node_a);
			List<String> computeVarName = computeVarName(node_a, Iterator.class);
			System.out.print(computeVarName.get(0)+",");
			if(computeVarName.size()>1)
				System.out.println(computeVarName.get(1));
			else
				System.out.println();
			HelperVisitor.callWhileStatementVisitor(node_a.getParent(), dataholder, (whilestatement,holder)->{
				Map<String, Object> pernodemap = holder.computeIfAbsent(whilestatement, k -> new HashMap<>());
				Expression svd_initializer = whilestatement.getExpression();
				pernodemap.put("init", svd_initializer);
				System.out.println("###############");
				System.out.println(whilestatement);
				String name = computeNextVarname(whilestatement);
				System.out.println(name);
				if(computeVarName.get(0).equals(name)) {
					HelperVisitor.callVariableDeclarationStatementVisitor(whilestatement,dataholder, (vds,holder2)->{
						Map<String, Object> pernodemap2 = holder2.computeIfAbsent(vds, k -> new HashMap<>());
						VariableDeclarationFragment vdf=(VariableDeclarationFragment) vds.fragments().get(0);
						pernodemap2.put("init", vdf);
						System.out.println("=============");
						System.out.println(vds);
						return true;
					});
				}
				return true;
			});
			return true;
		});
	}

	private static String computeNextVarname(WhileStatement whilestatement) {
		String name = null;
		Expression exp = whilestatement.getExpression();
//		Collection<String> usedVarNames= getUsedVariableNames(whilestatement.getBody());
		if (exp instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) exp;
			Expression expression = mi.getExpression();
			if (mi.getName().getIdentifier().equals("hasNext")) { //$NON-NLS-1$
//				ITypeBinding resolveTypeBinding = expression.resolveTypeBinding();
				SimpleName variable= ASTNodes.as(expression, SimpleName.class);
				if (variable != null) {
					IBinding resolveBinding = variable.resolveBinding();
					name = resolveBinding.getName();
				}
//				mytype = resolveTypeBinding.getErasure().getQualifiedName();
			}
		}
		return name;
	}

	private static List<String> computeVarName(VariableDeclarationStatement node_a, Class<?> class1) {
		List<String> name = new ArrayList<>();
		VariableDeclarationFragment bli = (VariableDeclarationFragment) node_a.fragments().get(0);
		name.add(bli.getName().getIdentifier());
		IVariableBinding resolveBinding = bli.resolveBinding();
		if(resolveBinding!=null) {
			String qualifiedName = resolveBinding.getType().getErasure().getQualifiedName();
			if (class1.getCanonicalName().equals(qualifiedName)) {
				Expression exp = bli.getInitializer();
				if (exp instanceof MethodInvocation) {
					MethodInvocation mi = (MethodInvocation) exp;
					Expression element = mi.getExpression();{
						if (element instanceof SimpleName) {
							SimpleName sn = (SimpleName) element;
							//						System.out.println(mi.getName());
							if (mi.getName().toString().equals("iterator")) { //$NON-NLS-1$
								name.add(sn.getIdentifier());
							}
						}
					}
				}
			}
		}
		return name;
	}
	
	/**
	 * This one is not really possible in "normal" visitors. Change visitors while visiting.
	 */
	@Test
	public void modifyTest1() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String,NodeFound>> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addMethodInvocation("println",(node, holder) -> {
			System.out.println("Start "+node.getNodeType() + " :" + node);
			return true;
		});
		hv.addMethodInvocation((node, holder) -> {
			System.out.println("End "+node.getNodeType() + " :" + node);
			holder.getHelperVisitor().removeVisitor(VisitorEnum.MethodInvocation);
		});
		hv.build(result);
	}

	@Test
	public void modifyTest2() {
		Set<ASTNode> nodesprocessed = null;
		ExpectationTracer dataholder = new ExpectationTracer();
		dataholder.stack.push(null);
		HelperVisitor<ExpectationTracer> hv = new HelperVisitor<>(nodesprocessed, dataholder);
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
		hv.addMethodInvocation("next",(methodinvocationnode, myholder) -> {
			String x = "Start "+methodinvocationnode.getNodeType() + " :" + methodinvocationnode;
			System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(methodinvocationnode.getNodeType()));
			return true;
		});
		hv.build(result2);
	}
	
	Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		Collection<String> res= (new ScopeAnalyzer(root)).getUsedVariableNames(node.getStartPosition(), node.getLength());
		return res;
	}
}
