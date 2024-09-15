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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_FOR_NAME;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;


import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
/**
 * Java 18
 *
 * Find:  Charset.forName("UTF-8")
 *
 * Rewrite: StandardCharsets.UTF_8
 *
 * Find:  Charset.forName("UTF-16")
 *
 * Rewrite: StandardCharsets.UTF_16
 */
public class CharsetForNameExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		if (!JavaModelUtil.is18OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			/**
			 * For Java 17 and older just do nothing
			 */
			return;
		}
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
		HelperVisitor.callMethodInvocationVisitor(Charset.class, METHOD_FOR_NAME, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, ChangeBehavior cb,
			MethodInvocation visited, ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		if (!ASTNodes.usesGivenSignature(visited, Charset.class.getCanonicalName(), METHOD_FOR_NAME, String.class.getCanonicalName())) {
			return true;
		}
		StringLiteral argstring3= (StringLiteral) arguments.get(0);
		if (!encodings.contains(argstring3.getLiteralValue().toUpperCase())) {
			return false;
		}
		holder.put(visited,encodingmap.get(argstring3.getLiteralValue().toUpperCase()));
		operations.add(fixcore.rewrite(visited, cb, holder));
		return false;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final MethodInvocation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group,ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		if (!JavaModelUtil.is18OrHigher(cuRewrite.getCu().getJavaProject())) {
			/**
			 * For Java 17 and older just do nothing
			 */
			return;
		}
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, ast, cb, (String) data.get(visited));
		ASTNodes.replaceButKeepComment(rewrite, visited, callToCharsetDefaultCharset, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Charset s=StandardCharsets.UTF_8;\n"+ //$NON-NLS-1$
					""; //$NON-NLS-1$
		}
		return "Charset s=Charset.forName(\"UTF-8\");\n"+ //$NON-NLS-1$
		""; //$NON-NLS-1$
	}
}
