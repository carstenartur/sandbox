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

import java.io.InputStreamReader;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
/**
 * InputStreamReader(InputStream in, Charset cs) is available since Java 1.4
 * Charset.defaultCharset() is available since Java 1.5
 *
 */
public class InputStreamReaderExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(final ClassInstanceCreation visited) {
				if(nodesprocessed.contains(visited) || (visited.arguments().size()>1)) {
					return false;
				}
				ITypeBinding binding= visited.resolveTypeBinding();
				if (InputStreamReader.class.getSimpleName().equals(binding.getName())) {
					operations.add(fixcore.rewrite(visited, cb));
					nodesprocessed.add(visited);
					return false;
				}
				return true;
			}
		});
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group,ChangeBehavior cb) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		if (!JavaModelUtil.is50OrHigher(cuRewrite.getCu().getJavaProject())) {
			/**
			 * For Java 1.4 and older just do nothing
			 */
			return;
		}
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, cb, ast);
		/**
		 * Add Charset.defaultCharset() as second (last) parameter
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		listRewrite.insertLast(callToCharsetDefaultCharset, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Reader r=new InputStreamReader(in, "+computeCharsetforPreview(cb)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Reader r=new InputStreamReader(in);\n"; //$NON-NLS-1$
	}
}
