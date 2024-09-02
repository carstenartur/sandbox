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

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
/**
 * Change
 *
 * Writer fw=new FileWriter("file.txt")
 * Writer fw=new OutputStreamWriter(new FileOutputStream("file.txt"),defaultCharset);
 *
 * Charset.defaultCharset() is available since Java 1.5
 *
 */
public class FileWriterExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
//		compilationUnit.accept(new ASTVisitor() {
//			@Override
//			public boolean visit(final ClassInstanceCreation visited) {
//				if(nodesprocessed.contains(visited)) {
//					return false;
//				}
//				ITypeBinding binding= visited.resolveTypeBinding();
//				if (FileWriter.class.getSimpleName().equals(binding.getName())) {
//					operations.add(fixcore.rewrite(visited, cb, datah));
//					nodesprocessed.add(visited);
//					return false;
//				}
//				return true;
//			}
//		});
		HelperVisitor.callClassInstanceCreationVisitor(FileWriter.class, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, cb, visited, holder));
		}

		private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
				Set<ASTNode> nodesprocessed, ChangeBehavior cb, ClassInstanceCreation visited,
				ReferenceHolder<ASTNode, Object> holder) {
			operations.add(fixcore.rewrite(visited, cb, holder));
			nodesprocessed.add(visited);
			return false;
		}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group,ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		if (!JavaModelUtil.is50OrHigher(cuRewrite.getCu().getJavaProject())) {
			/**
			 * For Java 1.4 and older just do nothing
			 */
			return;
		}
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, ast, cb, (String) data.get(visited));
		/**
		 * new FileInputStream(<filename>)
		 */
		ClassInstanceCreation fosclassInstance= ast.newClassInstanceCreation();
		fosclassInstance.setType(ast.newSimpleType(addImport(FileOutputStream.class.getCanonicalName(), cuRewrite, ast)));
		fosclassInstance.arguments().add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression((ASTNode) visited.arguments().get(0))));
		/**
		 * new InputStreamReader(new FileInputStream(<filename>))
		 */
		ClassInstanceCreation oswclassInstance= ast.newClassInstanceCreation();
		oswclassInstance.setType(ast.newSimpleType(addImport(OutputStreamWriter.class.getCanonicalName(), cuRewrite, ast)));
		oswclassInstance.arguments().add(fosclassInstance);
		oswclassInstance.arguments().add(callToCharsetDefaultCharset);

		ASTNodes.replaceButKeepComment(rewrite, visited, oswclassInstance, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Writer w=new OutputStreamWriter(new FileOutputStream(outputfile),"+computeCharsetforPreview(cb)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Writer w=new FileWriter(outputfile);\n"; //$NON-NLS-1$
	}
}
