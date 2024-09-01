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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_NEW_READER;

import java.nio.channels.Channels;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
/**
 * Find:  Channels.newReader(ch,"UTF-8")
 *
 * Rewrite: Channels.newReader(ch,StandardCharsets.UTF_8)
 *
 */
public class ChannelsNewReaderExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
		HelperVisitor.callMethodInvocationVisitor(Channels.class, METHOD_NEW_READER, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb,
			MethodInvocation visited, ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		if(nodesprocessed.contains(visited) || (arguments.size()>1)) {
			return false;
		}
		switch (arguments.size()) {
		case 1:
			if(!(arguments.get(0) instanceof StringLiteral)) {
				return false;
			}
			StringLiteral argstring3= (StringLiteral) arguments.get(0);
			if (!encodings.contains(argstring3.getLiteralValue())) {
				return false;
			}
			holder.put(argstring3,encodingmap.get(argstring3.getLiteralValue()));
//			holder.put(ENCODING,StandardCharsets.UTF_8);
//			holder.put(REPLACE,argstring3);
			break;
		case 0:
			break;
		default:
			return false;
		}
		operations.add(fixcore.rewrite(visited, cb, holder));
		nodesprocessed.add(visited);
		return false;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final MethodInvocation visited, final CompilationUnitRewrite cuRewrite,
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
//		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
//		listRewrite.insertLast(callToCharsetDefaultCharset, group);

		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		if(data.get(ENCODING)!= null) {
			listRewrite.replace((ASTNode) data.get(REPLACE), callToCharsetDefaultCharset, group);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Reader r=\"Channels.newReader(ch,StandardCharsets.UTF_8)\";\n"+ //$NON-NLS-1$
					"byte[] bytes= s.getBytes("+computeCharsetforPreview(cb)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Reader r=\"Channels.newReader(ch,\"UTF-8\")\";\n"+ //$NON-NLS-1$
		"byte[] bytes= s.getBytes();\n"; //$NON-NLS-1$
	}
}
