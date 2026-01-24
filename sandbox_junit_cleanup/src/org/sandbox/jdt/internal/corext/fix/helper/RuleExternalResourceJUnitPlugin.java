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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

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
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 ExternalResource rules to JUnit 5 extensions.
 */
public class RuleExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.forField()
			.withAnnotation(ORG_JUNIT_RULE)
			.ofType(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		HelperVisitor.forField()
			.withAnnotation(ORG_JUNIT_CLASS_RULE)
			.ofType(ORG_JUNIT_RULES_EXTERNAL_RESOURCE)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		if (!(node instanceof FieldDeclaration fieldDeclaration)) {
			return false;
		}
		JunitHolder mh= new JunitHolder();
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) fieldDeclaration.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			return false;
		}
		ITypeBinding binding= fragment.resolveBinding().getType();
		if (
		(binding == null) || ORG_JUNIT_RULES_TEST_NAME.equals(binding.getQualifiedName())
				|| ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(binding.getQualifiedName())) {
			return false;
		}
		mh.minv= fieldDeclaration;
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration fieldDeclaration= junitHolder.getFieldDeclaration();
		boolean fieldStatic= isFieldAnnotatedWith(fieldDeclaration, ORG_JUNIT_CLASS_RULE);
		CompilationUnit cu= (CompilationUnit) fieldDeclaration.getRoot();
		String classNameFromField= extractClassNameFromField(fieldDeclaration);

		ASTNode node2= getTypeDefinitionForField(fieldDeclaration, cu);

		if (node2 instanceof TypeDeclaration) {
			modifyExternalResourceClass((TypeDeclaration) node2, fieldDeclaration, fieldStatic, rewriter, ast, group,
					importRewriter);
		} else if (node2 instanceof AnonymousClassDeclaration typeNode) {
			refactorAnonymousClassToImplementCallbacks(typeNode, fieldDeclaration, fieldStatic, rewriter, ast, group,
					importRewriter);
		}
		// If no matching type definition found, no action needed
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
