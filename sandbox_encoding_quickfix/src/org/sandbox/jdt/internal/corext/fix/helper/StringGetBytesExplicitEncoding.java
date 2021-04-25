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

import static org.sandbox.jdt.internal.corext.fix.LibStandardNames.METHOD_GET_BYTES;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
/**
 * Find:  String.getBytes()
 *
 * Rewrite: String.getBytes(Charset.defaultCharset())
 *
 */
public class StringGetBytesExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(final MethodInvocation visited) {
				if(nodesprocessed.contains(visited)) {
					return false;
				}

				if (ASTNodes.usesGivenSignature(visited, String.class.getCanonicalName(), METHOD_GET_BYTES)) {
					operations.add(fixcore.rewrite(visited, cb));
					nodesprocessed.add(visited);
					return false;
				}
				return true;
			}
		});
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final MethodInvocation visited, final CompilationUnitRewrite cuRewrite,
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
		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		listRewrite.insertLast(callToCharsetDefaultCharset, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "String s=\"asdf\";\n"+ //$NON-NLS-1$
			"byte[] bytes= s.getBytes("+computeCharsetforPreview(cb)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "String s=\"asdf\";\n"+ //$NON-NLS-1$
		"byte[] bytes= s.getBytes();\n"; //$NON-NLS-1$
	}
}
