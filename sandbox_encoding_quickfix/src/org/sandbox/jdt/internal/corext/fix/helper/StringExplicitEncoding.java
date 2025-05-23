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

import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 *
 * Java 10
 *
 * Change
 *
 * Find: String s=new String(byte[],"UTF-8")
 *
 * Rewrite: String s=new String(byte[],StandardCharsets.UTF_8);
 *
 * Find: String s=new String(byte[],int, int, "UTF-8")
 *
 * Rewrite: String s=new String(byte[],int, int, StandardCharsets.UTF_8)
 *
 */
public class StringExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		if (!JavaModelUtil.is10OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			/**
			 * For Java 9 and older just do nothing
			 */
			return;
		}
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
		HelperVisitor.callClassInstanceCreationVisitor(String.class, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb, ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		switch (arguments.size()) {
			case 4:
				if (!(arguments.get(3) instanceof StringLiteral)) {
					return false;
				}
				StringLiteral argstring4= (StringLiteral) arguments.get(3);
				if (!encodings.contains(argstring4.getLiteralValue().toUpperCase())) {
					return false;
				}
				Nodedata nd= new Nodedata();
				nd.encoding= encodingmap.get(argstring4.getLiteralValue().toUpperCase());
				nd.replace= true;
				nd.visited= argstring4;
				holder.put(visited, nd);
				operations.add(fixcore.rewrite(visited, cb, holder));
				break;
			case 2:
				if (!(arguments.get(1) instanceof StringLiteral)) {
					return false;
				}
				StringLiteral argstring3= (StringLiteral) arguments.get(1);
				if (!encodings.contains(argstring3.getLiteralValue().toUpperCase())) {
					return false;
				}
				Nodedata nd2= new Nodedata();
				nd2.encoding= encodingmap.get(argstring3.getLiteralValue().toUpperCase());
				nd2.replace= true;
				nd2.visited= argstring3;
				holder.put(visited, nd2);
				operations.add(fixcore.rewrite(visited, cb, holder));
				break;
			case 1:
			default:
				break;
		}

		return false;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();
		Nodedata nodedata= (Nodedata) data.get(visited);
		ASTNode callToCharsetDefaultCharset= cb.computeCharsetASTNode(cuRewrite, ast, nodedata.encoding,Nodedata.charsetConstants);
		/**
		 * Add Charset.defaultCharset() as second (last) parameter
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		if (nodedata.replace) {
			listRewrite.replace(nodedata.visited, callToCharsetDefaultCharset, group);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
		removeUnsupportedEncodingException(visited, group, rewrite, importRewriter);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "String s=new String(byte[]," + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "String s=new String(byte[],\"UTF-8\");\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new String(byte[])"; //$NON-NLS-1$
	}
}
