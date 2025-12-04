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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
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
public class RuleTemporayFolderJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder= new ReferenceHolder<>();
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_RULE, ORG_JUNIT_RULES_TEMPORARY_FOLDER, compilationUnit,
				dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}
	
	@Override
	void applyRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder mh) {
		FieldDeclaration field= mh.getFieldDeclaration();
		rewriter.remove(field, group);
		TypeDeclaration parentClass= (TypeDeclaration) field.getParent();

		importRewriter.addImport(ORG_JUNIT_JUPITER_API_IO_TEMP_DIR);
		importRewriter.removeImport(ORG_JUNIT_RULE);
		importRewriter.removeImport(ORG_JUNIT_RULES_TEMPORARY_FOLDER);

		VariableDeclarationFragment originalFragment= (VariableDeclarationFragment) field.fragments().get(0);
		String originalName= originalFragment.getName().getIdentifier();

		VariableDeclarationFragment tempDirFragment= ast.newVariableDeclarationFragment();
		tempDirFragment.setName(ast.newSimpleName(originalName));

		FieldDeclaration tempDirField= ast.newFieldDeclaration(tempDirFragment);
		tempDirField.setType(ast.newSimpleType(ast.newName("Path")));

		MarkerAnnotation tempDirAnnotation= ast.newMarkerAnnotation();
		tempDirAnnotation.setTypeName(ast.newName("TempDir"));
		rewriter.getListRewrite(tempDirField, FieldDeclaration.MODIFIERS2_PROPERTY).insertFirst(tempDirAnnotation,
				group);

		rewriter.getListRewrite(parentClass, TypeDeclaration.BODY_DECLARATIONS_PROPERTY).insertFirst(tempDirField,
				group);

		for (MethodDeclaration method : parentClass.getMethods()) {
			method.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation node) {
					if (node.getName().getIdentifier().equals("newFile")) {
						MethodInvocation resolveInvocation= ast.newMethodInvocation();
						resolveInvocation.setExpression(ast.newSimpleName("tempFolder"));
						resolveInvocation.setName(ast.newSimpleName("resolve"));
						resolveInvocation.arguments().addAll(ASTNode.copySubtrees(ast, node.arguments()));

						MethodInvocation toFileInvocation= ast.newMethodInvocation();
						toFileInvocation.setExpression(resolveInvocation);
						toFileInvocation.setName(ast.newSimpleName("toFile"));

						rewriter.replace(node, toFileInvocation, group);
					}
					return super.visit(node);
				}
			});
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
						@TempDir
						Path tempFolder;

						@Test
						public void test3() throws IOException{
							File newFile = tempFolder.resolve("myfile.txt").toFile();
						}
					"""; //$NON-NLS-1$
		}
		return """
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();

					@Test
					public void test3() throws IOException{
						File newFile = tempFolder.newFile("myfile.txt");
					}			;
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTemporaryFolder"; //$NON-NLS-1$
	}
}
