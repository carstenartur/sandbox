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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 TemporaryFolder rule to JUnit 5 @TempDir.
 */
public class RuleTemporayFolderJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_RULE, ORG_JUNIT_RULES_TEMPORARY_FOLDER, compilationUnit,
				dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) node.fragments().get(0);
		ITypeBinding binding= fragment.resolveBinding().getType();
		if (binding != null && ORG_JUNIT_RULES_TEMPORARY_FOLDER.equals(binding.getQualifiedName())) {
			mh.minv= node;
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		return false;
	}
	
	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration field= junitHolder.getFieldDeclaration();
		rewriter.remove(field, group);
		TypeDeclaration parentClass= ASTNodes.getParent(field, TypeDeclaration.class);

		VariableDeclarationFragment originalFragment= (VariableDeclarationFragment) field.fragments().get(0);
		String originalName= originalFragment.getName().getIdentifier();

		// Check which methods are being called to determine if Files import is needed
		final boolean[] needsFilesImport = {false};
		for (MethodDeclaration method : parentClass.getMethods()) {
			method.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation node) {
					if (node.getExpression() == null) {
						return super.visit(node);
					}
					String expressionName = node.getExpression().toString();
					if (!originalName.equals(expressionName)) {
						return super.visit(node);
					}
					String methodName = node.getName().getIdentifier();
					if ("newFile".equals(methodName) || "newFolder".equals(methodName)) {
						needsFilesImport[0] = true;
					}
					return super.visit(node);
				}
			});
		}

		// Add JUnit 5 imports and remove JUnit 4 imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_IO_TEMP_DIR);
		importRewriter.addImport("java.nio.file.Path");
		if (needsFilesImport[0]) {
			importRewriter.addImport("java.nio.file.Files");
		}
		importRewriter.removeImport(ORG_JUNIT_RULE);
		importRewriter.removeImport(ORG_JUNIT_RULES_TEMPORARY_FOLDER);

		// Create new field: @TempDir Path fieldName;
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

		// Transform method invocations
		for (MethodDeclaration method : parentClass.getMethods()) {
			method.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation node) {
					if (node.getExpression() == null) {
						return super.visit(node);
					}
					
					String expressionName = node.getExpression().toString();
					if (!originalName.equals(expressionName)) {
						return super.visit(node);
					}

					String methodName = node.getName().getIdentifier();
					
					// Handle newFile() and newFile(String)
					if ("newFile".equals(methodName)) {
						if (node.arguments().isEmpty()) {
							// newFile() with no args -> Files.createTempFile(tempDir, "", null).toFile()
							MethodInvocation createTempFileInvocation= ast.newMethodInvocation();
							createTempFileInvocation.setExpression(ast.newName("Files"));
							createTempFileInvocation.setName(ast.newSimpleName("createTempFile"));
							createTempFileInvocation.arguments().add(ast.newSimpleName(originalName));
							createTempFileInvocation.arguments().add(ast.newStringLiteral());
							createTempFileInvocation.arguments().add(ast.newNullLiteral());
							
							MethodInvocation toFileInvocation= ast.newMethodInvocation();
							toFileInvocation.setExpression(createTempFileInvocation);
							toFileInvocation.setName(ast.newSimpleName("toFile"));
							
							rewriter.replace(node, toFileInvocation, group);
						} else {
							// newFile(String) -> Files.createFile(tempDir.resolve(...)).toFile()
							MethodInvocation createFileInvocation= ast.newMethodInvocation();
							createFileInvocation.setExpression(ast.newName("Files"));
							createFileInvocation.setName(ast.newSimpleName("createFile"));
							
							MethodInvocation resolveInvocation= ast.newMethodInvocation();
							resolveInvocation.setExpression(ast.newSimpleName(originalName));
							resolveInvocation.setName(ast.newSimpleName("resolve"));
							resolveInvocation.arguments().addAll(ASTNode.copySubtrees(ast, node.arguments()));
							
							createFileInvocation.arguments().add(resolveInvocation);
							
							MethodInvocation toFileInvocation= ast.newMethodInvocation();
							toFileInvocation.setExpression(createFileInvocation);
							toFileInvocation.setName(ast.newSimpleName("toFile"));
							
							rewriter.replace(node, toFileInvocation, group);
						}
					}
					// Handle newFolder() and newFolder(String...)
					else if ("newFolder".equals(methodName)) {
						if (node.arguments().isEmpty()) {
							// newFolder() with no args -> Files.createTempDirectory(tempDir, "").toFile()
							MethodInvocation createTempDirInvocation= ast.newMethodInvocation();
							createTempDirInvocation.setExpression(ast.newName("Files"));
							createTempDirInvocation.setName(ast.newSimpleName("createTempDirectory"));
							createTempDirInvocation.arguments().add(ast.newSimpleName(originalName));
							createTempDirInvocation.arguments().add(ast.newStringLiteral());
							
							MethodInvocation toFileInvocation= ast.newMethodInvocation();
							toFileInvocation.setExpression(createTempDirInvocation);
							toFileInvocation.setName(ast.newSimpleName("toFile"));
							
							rewriter.replace(node, toFileInvocation, group);
						} else {
							// newFolder(String...) -> Files.createDirectories(tempDir.resolve(...)).toFile()
							MethodInvocation createDirInvocation= ast.newMethodInvocation();
							createDirInvocation.setExpression(ast.newName("Files"));
							createDirInvocation.setName(ast.newSimpleName("createDirectories"));
							
							MethodInvocation resolveInvocation= ast.newMethodInvocation();
							resolveInvocation.setExpression(ast.newSimpleName(originalName));
							resolveInvocation.setName(ast.newSimpleName("resolve"));
							resolveInvocation.arguments().addAll(ASTNode.copySubtrees(ast, node.arguments()));
							
							createDirInvocation.arguments().add(resolveInvocation);
							
							MethodInvocation toFileInvocation= ast.newMethodInvocation();
							toFileInvocation.setExpression(createDirInvocation);
							toFileInvocation.setName(ast.newSimpleName("toFile"));
							
							rewriter.replace(node, toFileInvocation, group);
						}
					}
					// Handle getRoot()
					else if ("getRoot".equals(methodName)) {
						// tempDir.toFile()
						MethodInvocation toFileInvocation= ast.newMethodInvocation();
						toFileInvocation.setExpression(ast.newSimpleName(originalName));
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
							File newFile = Files.createFile(tempFolder.resolve("myfile.txt")).toFile();
						}
					"""; //$NON-NLS-1$
		}
		return """
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();

					@Test
					public void test3() throws IOException{
						File newFile = tempFolder.newFile("myfile.txt");
					}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTemporaryFolder"; //$NON-NLS-1$
	}
}
