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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
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
		String code ="import java.util.Collection;\n"
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
		parser.setSource(code.toCharArray());
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
		parser.setCompilerOptions(options);
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
		parser.setSource(code2.toCharArray());
		result2 = (CompilationUnit) parser.createAST(null);
//		System.out.println(result.toString());
	}

	@Test
	public void simpleTest() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder());
		hv.addMethodInvocation(this::handleMethodInvocation);
		hv.build(result);
	}

	@Test
	public void simpleTest2() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder());
		hv.addMethodInvocation("add", this::handleMethodInvocation);
		hv.build(result);
	}
	
	private boolean handleMethodInvocation(MethodInvocation assignment, ReferenceHolder holder) {
		System.out.println(assignment);
		return true;
	}
	
	@Test
	public void simpleTest3() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder());
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
	
	@Test
	public void simpleTest3b() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder());
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
		hv.build(result2);
	}
	
	@Test
	public void simpleTest4() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder());
		EnumSet<VisitorEnum> myset = EnumSet.of(VisitorEnum.Assignment,VisitorEnum.SingleVariableDeclaration, VisitorEnum.VariableDeclarationStatement,VisitorEnum.VariableDeclarationFragment);
		myset.forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				String x = "Start "+node.getNodeType() + " :" + node;
				System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType()));
				return true;
			});
		});
		myset.forEach(ve -> {
			hv.add(ve, (node, holder) -> {
				String x = "End   "+node.getNodeType() + " :" + node;
				System.out.printf("%-40s %s%n",x,ASTNode.nodeClassForType(node.getNodeType()));
			});
		});
		hv.build(result);
	}

	@Test
	public void modifyTest1() {
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new ReferenceHolder());
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
}
