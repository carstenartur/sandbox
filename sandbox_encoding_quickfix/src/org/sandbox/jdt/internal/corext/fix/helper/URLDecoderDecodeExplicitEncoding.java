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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_DECODE;

import java.net.URLDecoder;
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
/**
 * Java 10
 *
 * Find:  java.net.URLDecoder.decode("asdf","UTF-8")
 *
 * Rewrite: java.net.URLDecoder.decode("asdf",StandardCharsets.UTF_8)
 *
 * Find:  java.net.URLDecoder.decode("asdf")
 * Without the parameter the default is the file.encoding system property so
 * Charset.defaultCharset()
 * URLDecoder.decode("asdf") is (nearly) the same as URLDecoder.decode("asdf",Charset.defaultCharset())
 * But it is not really better (other than that you can see that it is depending on the default charset)
 *
 * KEEP
 *
 * Rewrite: java.net.URLDecoder.decode("asdf",Charset.defaultCharset())
 *
 * USE_UTF8
 *
 * Rewrite: java.net.URLDecoder.decode("asdf",StandardCharsets.UTF_8)
 * This changes how the code works but it might be the better choice if you want to get rid of
 * depending on environment settings
 */
public class URLDecoderDecodeExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
		HelperVisitor.callMethodInvocationVisitor(URLDecoder.class, METHOD_DECODE, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb,
			MethodInvocation visited, ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		if (ASTNodes.usesGivenSignature(visited, URLDecoder.class.getCanonicalName(), METHOD_DECODE, String.class.getCanonicalName(),String.class.getCanonicalName())) {
			StringLiteral argstring3= (StringLiteral) arguments.get(1);
			if (!encodings.contains(argstring3.getLiteralValue().toUpperCase())) {
				return false;
			}
			Nodedata nd=new Nodedata();
			nd.encoding=encodingmap.get(argstring3.getLiteralValue().toUpperCase());
			nd.replace=true;
			nd.visited=argstring3;
			holder.put(visited,nd);
			operations.add(fixcore.rewrite(visited, cb, holder));
			return false;
		}
		if (ASTNodes.usesGivenSignature(visited, URLDecoder.class.getCanonicalName(), METHOD_DECODE, String.class.getCanonicalName())) {
			Nodedata nd=new Nodedata();
			switch(cb) {
				case KEEP:
					nd.encoding=null;
					break;
				case USE_UTF8:
					nd.encoding="UTF_8"; //$NON-NLS-1$
					break;
				case USE_UTF8_AGGREGATE:
					break;
			}
			nd.replace=false;
			nd.visited=visited;
			holder.put(visited,nd);
			operations.add(fixcore.rewrite(visited, cb, holder));
			return false;
		}
		return false;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final MethodInvocation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group,ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		if (!JavaModelUtil.is10OrHigher(cuRewrite.getCu().getJavaProject())) {
			/**
			 * For Java 9 and older just do nothing
			 */
			return;
		}
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, ast, cb, ((Nodedata) data.get(visited)).encoding);
		/**
		 * Add Charset.defaultCharset() or StandardCharsets.UTF_8 as second (last) parameter
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		if(((Nodedata)(data.get(visited))).replace) {
			listRewrite.replace(((Nodedata) data.get(visited)).visited, callToCharsetDefaultCharset, group);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "java.net.URLDecoder.decode(\"asdf\", StandardCharsets.UTF_8);\n"+ //$NON-NLS-1$
					""; //$NON-NLS-1$
		}
		return "java.net.URLDecoder.decode(\"asdf\", \"UTF-8\");\n"+ //$NON-NLS-1$
		""; //$NON-NLS-1$
	}
}
