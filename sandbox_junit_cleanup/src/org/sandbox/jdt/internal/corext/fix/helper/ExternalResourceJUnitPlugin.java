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

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder= new ReferenceHolder<>();
		HelperVisitor.callTypeDeclarationVisitor(ORG_JUNIT_RULES_EXTERNAL_RESOURCE, compilationUnit, dataholder,
				nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();

		hit.values().forEach(holder -> {
			TypeDeclaration node= holder.getTypeDeclaration();
			if (modifyExternalResourceClass(node, rewriter, ast, group, importRewriter)) {
				importRewriter.removeImport(ORG_JUNIT_RULE);
			}
		});
	}

	private boolean modifyExternalResourceClass(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		ITypeBinding binding= node.resolveBinding();

		if (binding.isAnonymous() || !isExternalResource(binding) || !hasDefaultConstructorOrNoConstructor(node)) {
			return false;
		}

		if (isDirectlyExtendingExternalResource(binding)) {
			refactorToImplementCallbacks(node, rewriter, ast, group, importRewriter);
		}

		for (MethodDeclaration method : node.getMethods()) {
			if (isLifecycleMethod(method, "before")) {
				refactorMethod(rewriter, ast, method, "beforeEach", group, importRewriter);
			} else if (isLifecycleMethod(method, "after")) {
				refactorMethod(rewriter, ast, method, "afterEach", group, importRewriter);
			}
		}
		return true;
	}

	private void refactorToImplementCallbacks(TypeDeclaration node, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		rewriter.remove(node.getSuperclassType(), group);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXTERNAL_RESOURCE);

		ListRewrite listRewrite= rewriter.getListRewrite(node, TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY);
		addInterfaceCallback(listRewrite, ast, "BeforeEachCallback", group);
		addInterfaceCallback(listRewrite, ast, "AfterEachCallback", group);

		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_BEFORE_EACH_CALLBACK);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_AFTER_EACH_CALLBACK);
	}

	private void addInterfaceCallback(ListRewrite listRewrite, AST ast, String callbackName, TextEditGroup group) {
		listRewrite.insertLast(ast.newSimpleType(ast.newName(callbackName)), group);
	}

	private boolean isLifecycleMethod(MethodDeclaration method, String methodName) {
		return method.getName().getIdentifier().equals(methodName);
	}

	private void refactorMethod(ASTRewrite rewriter, AST ast, MethodDeclaration method, String newMethodName,
			TextEditGroup group, ImportRewrite importRewriter) {
		rewriter.replace(method.getName(), ast.newSimpleName(newMethodName), group);
		ensureExtensionContextParameter(rewriter, ast, method, group, importRewriter);
	}

	private void ensureExtensionContextParameter(ASTRewrite rewriter, AST ast, MethodDeclaration method,
			TextEditGroup group, ImportRewrite importRewriter) {
		boolean hasExtensionContext= method.parameters().stream()
				.anyMatch(param -> param instanceof SingleVariableDeclaration
						&& ((SingleVariableDeclaration) param).getType().toString().equals("ExtensionContext"));

		if (!hasExtensionContext) {
			SingleVariableDeclaration newParam= ast.newSingleVariableDeclaration();
			newParam.setType(ast.newSimpleType(ast.newName("ExtensionContext")));
			newParam.setName(ast.newSimpleName("context"));

			rewriter.getListRewrite(method, MethodDeclaration.PARAMETERS_PROPERTY).insertLast(newParam, group);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTENSION_CONTEXT);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
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
		return """
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
