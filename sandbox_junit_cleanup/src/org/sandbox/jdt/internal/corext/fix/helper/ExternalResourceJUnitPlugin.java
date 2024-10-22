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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 *
 * 
 */
public class ExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT = "org.junit.jupiter.api.extension.ExtensionContext";
	private static final String ORG_JUNIT_RULE = "org.junit.Rule";
	private static final String ORG_JUNIT_RULES_EXTERNAL_RESOURCE = "org.junit.rules.ExternalResource";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK = "org.junit.jupiter.api.extension.BeforeEachCallback";
	private static final String ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK = "org.junit.jupiter.api.extension.AfterEachCallback";
	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		HelperVisitor.callTypeDeclarationVisitor(ORG_JUNIT_RULES_EXTERNAL_RESOURCE,compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		mh.minv = node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importrewriter = cuRewrite.getImportRewrite();
		for (Entry<Integer, JunitHolder> entry : hit.entrySet()) {
			JunitHolder mh = entry.getValue();
			TypeDeclaration node = mh.getTypeDeclaration();
			if(modifyExternalResourceClass(node,rewriter,ast,group,importrewriter)) {
			importrewriter.removeImport(ORG_JUNIT_RULE);
			}
		}
	}

	private boolean modifyExternalResourceClass(TypeDeclaration node, ASTRewrite rewriter,AST ast, TextEditGroup group, ImportRewrite importrewriter) {
		ITypeBinding binding = node.resolveBinding();
		if(binding.isAnonymous()) {
			return false;
		}
		if (isExternalResource(binding)&& hasDefaultConstructorOrNoConstructor(node)) {
			if(isDirect(binding)) {
				rewriter.remove(node.getSuperclassType(), group);
				importrewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);
				ListRewrite listRewrite = rewriter.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
				listRewrite.insertLast(ast.newSimpleType(ast.newName("BeforeEachCallback")), group);
				listRewrite.insertLast(ast.newSimpleType(ast.newName("AfterEachCallback")), group);
				importrewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
				importrewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
			}
			for (MethodDeclaration method : node.getMethods()) {
				if (method.getName().getIdentifier().equals("before")) {
					rewriter.replace(method.getName(), ast.newSimpleName("beforeEach"), group);
					extracted(rewriter, ast, method, group,importrewriter);
				}
				if (method.getName().getIdentifier().equals("after")) {
					rewriter.replace(method.getName(), ast.newSimpleName("afterEach"), group);
					extracted(rewriter, ast, method, group,importrewriter);
				}
			}
		}
		return true;
	}

	private boolean isDirect(ITypeBinding fieldTypeBinding) {
		ITypeBinding binding =fieldTypeBinding;
		ITypeBinding superClass = binding.getSuperclass();

		boolean isDirectlyExtendingExternalResource = false;
		boolean isIndirectlyExtendingExternalResource = false;

		// Prüfen, ob die Klasse direkt oder indirekt von ExternalResource erbt
		while (superClass != null) {
			if (superClass.getQualifiedName().equals("org.junit.rules.ExternalResource")) {
				if (binding.getSuperclass().getQualifiedName().equals("org.junit.rules.ExternalResource")) {
					isDirectlyExtendingExternalResource = true;
				} else {
					isIndirectlyExtendingExternalResource = true;
				}
				break;
			}
			superClass = superClass.getSuperclass();
		}
		return isDirectlyExtendingExternalResource;
	}
	
	private void extracted(ASTRewrite rewriter, AST ast, MethodDeclaration method, TextEditGroup group, ImportRewrite importrewriter) {
		ListRewrite listRewrite;
		boolean hasExtensionContext = false;
		for (Object param : method.parameters()) {
			if (param instanceof SingleVariableDeclaration) {
				SingleVariableDeclaration variable = (SingleVariableDeclaration) param;
				if (variable.getType().toString().equals("ExtensionContext")) {
					hasExtensionContext = true;
					break;
				}
			}
		}

		if (!hasExtensionContext) {
			SingleVariableDeclaration newParam = ast.newSingleVariableDeclaration();
			newParam.setType(ast.newSimpleType(ast.newName("ExtensionContext")));
			newParam.setName(ast.newSimpleName("context"));

			listRewrite = rewriter.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY);
			listRewrite.insertLast(newParam, group);
			importrewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		}
	}

	private boolean hasDefaultConstructorOrNoConstructor(TypeDeclaration classNode) {
		boolean hasConstructor = false;
		for (Object bodyDecl : classNode.bodyDeclarations()) {
			if (bodyDecl instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) bodyDecl;
				if (method.isConstructor()) {
					hasConstructor = true;
					if (method.parameters().isEmpty() && method.getBody() != null && method.getBody().statements().isEmpty()) {
						return true;
					}
				}
			}
		}
		return !hasConstructor;
	}

	private boolean isExternalResource(ITypeBinding typeBinding) {
		while (typeBinding != null) {
			if (typeBinding.getQualifiedName().equals(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)) {
				return true;
			}
			typeBinding = typeBinding.getSuperclass();
		}
		return false;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return 
"""
	private String testName;

	@BeforeEach
	void init(TestInfo testInfo) {
		this.testName = testInfo.getDisplayName();
	}
	@Test
	public void test(){
		System.out.println("Test name: " + testName);
	}
"""; //$NON-NLS-1$
		}
		return 
"""
	@Rule
	public TestName tn = new TestName();

	@Test
	public void test(){
		System.out.println("Test name: " + tn.getMethodName());
	}
"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "ExternalResource"; //$NON-NLS-1$
	}
}
