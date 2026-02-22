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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_GET_BYTES;

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

import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Find: String.getBytes()
 *
 * Rewrite: String.getBytes(Charset.defaultCharset())
 *
 * Find: String.getBytes("Utf-8")
 *
 * Rewrite: String.getBytes(StandardCharsets.UTF_8)
 */
public class StringGetBytesExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forMethodCall(String.class, METHOD_GET_BYTES)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, ChangeBehavior cb,
			MethodInvocation visited, ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		switch (arguments.size()) {
			case 1:
				if (!(arguments.get(0) instanceof StringLiteral)) {
					return false;
				}
				StringLiteral argstring3= (StringLiteral) arguments.get(0);
				if (!ENCODINGS.contains(argstring3.getLiteralValue().toUpperCase(java.util.Locale.ROOT))) {
					return false;
				}
				NodeData nd= new NodeData(true, argstring3, ENCODING_MAP.get(argstring3.getLiteralValue().toUpperCase(java.util.Locale.ROOT)));
				holder.put(visited, nd);
				break;
			case 0:
				NodeData nd2= new NodeData(false, visited, null);
				holder.put(visited, nd2);
				break;
			default:
				return false;
		}
		operations.add(fixcore.rewrite(visited, cb, holder));
		return false;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final MethodInvocation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		NodeData nodedata= (NodeData) data.get(visited);
		ASTNode callToCharsetDefaultCharset= cb.computeCharsetASTNode(cuRewrite, ast, nodedata.encoding(), getCharsetConstants());
		/**
		 * Register encoding replacement BEFORE removing exception handling.
		 * removeUnsupportedEncodingException may call simplifyEmptyTryStatement
		 * which uses createMoveTarget to move statements out of the try block.
		 * replaceAndRemoveNLS fails silently on nodes that have already been
		 * marked as move targets, so the replacement must be registered first.
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		boolean tryAlreadyUnwrapped= false;
		if (nodedata.replace()) {
			tryAlreadyUnwrapped= replaceArgumentAndRemoveNLS(rewrite, nodedata.visited(), callToCharsetDefaultCharset, group, cuRewrite);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
		if (!tryAlreadyUnwrapped) {
			removeUnsupportedEncodingException(visited, group, rewrite, cuRewrite.getImportRemover());
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "String s=\"asdf\";\n" + //$NON-NLS-1$
					"byte[] bytes= s.getBytes(" + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "String s=\"asdf\";\n" + //$NON-NLS-1$
				"byte[] bytes= s.getBytes();\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "String.getBytes()"; //$NON-NLS-1$
	}
}
