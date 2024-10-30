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

import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
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
public class RuleExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder= new ReferenceHolder<>();
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_RULE, ORG_JUNIT_RULES_EXTERNAL_RESOURCE, compilationUnit,
				dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh= new JunitHolder();
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) node.fragments().get(0);
		ITypeBinding binding= fragment.resolveBinding().getType();
		if (
				isAnonymousClass(fragment) || 
				(binding == null)
				|| ORG_JUNIT_RULES_TEST_NAME.equals(binding.getQualifiedName())
				|| ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(binding.getQualifiedName())) {
			return false;
		}
		mh.minv= node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, ReferenceHolder<Integer, JunitHolder> hit,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();
		hit.values().forEach(mh -> {
			FieldDeclaration fieldDeclaration= mh.getFieldDeclaration();
			Optional<ASTNode> innerTypeDeclaration= getInnerTypeDeclaration(fieldDeclaration);
			for (Object fragment : fieldDeclaration.fragments()) {
				VariableDeclarationFragment variable = (VariableDeclarationFragment) fragment;
				String fieldName = variable.getName().getIdentifier();
				migrateRuleToRegisterExtensionAndAdaptHierarchy(innerTypeDeclaration,getParentTypeDeclaration(fieldDeclaration),rewrite, ast, importRewriter,group ,fieldName);
			}
		});
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					ExtendWith(MyTest.MyExternalResource.class)
					public class MyTest {

						final class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
							@Override
							protected void beforeEach() throws Throwable {
							}

							@Override
							protected void afterEach() {
							}
						}
					"""; //$NON-NLS-1$
		}
		return """
				public class MyTest {

					final class MyExternalResource extends ExternalResource {
						@Override
						protected void before() throws Throwable {
						}

						@Override
						protected void after() {
						}
					}

					@Rule
					public ExternalResource er= new MyExternalResource();
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleExternalResource"; //$NON-NLS-1$
	}
}
