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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.Set;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
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
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_CLASS_RULE, ORG_JUNIT_RULES_EXTERNAL_RESOURCE,
				compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh= new JunitHolder();
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) node.fragments().get(0);
		ITypeBinding binding= fragment.resolveBinding().getType();
		if (
		(binding == null) || ORG_JUNIT_RULES_TEST_NAME.equals(binding.getQualifiedName())
				|| ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(binding.getQualifiedName())) {
			return false;
		}
		mh.minv= node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	void applyRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder mh) {
		FieldDeclaration fieldDeclaration= mh.getFieldDeclaration();
		boolean fieldStatic= isFieldAnnotatedWith(fieldDeclaration, ORG_JUNIT_CLASS_RULE);
		CompilationUnit cu= (CompilationUnit) fieldDeclaration.getRoot();
		String classNameFromField= extractClassNameFromField(fieldDeclaration);

		ASTNode node2= getTypeDefinitionForField(fieldDeclaration, cu);

		if (node2 instanceof TypeDeclaration) {
			System.out.println("TypeDeclaration gefunden: " + ((TypeDeclaration) node2).getName());
			modifyExternalResourceClass((TypeDeclaration) node2, fieldDeclaration, fieldStatic, rewriter, ast, group,
					importRewriter);
		} else if (node2 instanceof AnonymousClassDeclaration typeNode) {
			System.out.println("AnonymousClassDeclaration gefunden." + typeNode);
			refactorAnonymousClassToImplementCallbacks(typeNode, fieldDeclaration, fieldStatic, rewriter, ast, group,
					importRewriter);
		} else {
			System.out.println("Keine passende Typdefinition gefunden." + node2);
		}
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
