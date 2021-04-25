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

import static org.sandbox.jdt.internal.corext.fix.LibStandardNames.METHOD_TOSTRING;

import java.io.ByteArrayOutputStream;
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
 * Change from
 *
 * <pre>ByteArrayOutputStream ba=new ByteArrayOutputStream();
 *      String result=ba.toString();
 *  </pre>
 *
 * <pre>ByteArrayOutputStream ba=new ByteArrayOutputStream();
 *      try {
 *         String result=ba.toString(Charset.defaultCharset().displayName());
 *      } catch (UnsupportedEncodingException e1) {
 *         e1.printStackTrace();
 *      }</pre>
 *
 */
public class ByteArrayOutputStreamExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(final MethodInvocation visited) {
				if(nodesprocessed.contains(visited)) {
					return false;
				}
				if (ASTNodes.usesGivenSignature(visited, ByteArrayOutputStream.class.getCanonicalName(), METHOD_TOSTRING)) {
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
		MethodInvocation callToCharsetDefaultCharsetDisplayname= addCharsetStringComputation(cuRewrite, ast, cb);
		/**
		 * Add Charset.defaultCharset().displayName() as second (last) parameter of "toString()" call
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		listRewrite.insertLast(callToCharsetDefaultCharsetDisplayname, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		String insert=""; //$NON-NLS-1$
		switch(cb) {
			case KEEP:
				insert="Charset.defaultCharset().displayName()"; //$NON-NLS-1$
				break;
			case USE_UTF8_AGGREGATE:
//				insert="charset_constant"; //$NON-NLS-1$
				//$FALL-THROUGH$
			case USE_UTF8:
				insert="StandardCharsets.UTF_8.displayName()"; //$NON-NLS-1$
				break;
		}
		if(afterRefactoring) {
			return "ByteArrayOutputStream ba=new ByteArrayOutputStream();\n" //$NON-NLS-1$
					+ "try {\n" //$NON-NLS-1$
					+ "	String result=ba.toString("+insert+");\n" //$NON-NLS-1$ //$NON-NLS-2$
					+ "} catch (UnsupportedEncodingException e1) {\n" //$NON-NLS-1$
					+ "	e1.printStackTrace();\n" //$NON-NLS-1$
					+ "}\n"; //$NON-NLS-1$
		}
		return "ByteArrayOutputStream ba=new ByteArrayOutputStream();\n" //$NON-NLS-1$
				+ "try {\n" //$NON-NLS-1$
				+ "	String result=ba.toString();\n" //$NON-NLS-1$
				+ "} catch (UnsupportedEncodingException e1) {\n" //$NON-NLS-1$
				+ "	e1.printStackTrace();\n" //$NON-NLS-1$
				+ "}\n"; //$NON-NLS-1$
	}
}
