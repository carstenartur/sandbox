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

import java.io.OutputStreamWriter;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
 * Change
 *
 * OutputStreamWriter is=new OutputStreamWriter(..)
 *
 * Charset.defaultCharset() is available since Java 1.5
 *
 */
public class OutputStreamWriterExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {
	
	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
		HelperVisitor.callClassInstanceCreationVisitor(OutputStreamWriter.class, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb,
			ClassInstanceCreation visited, ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		if(nodesprocessed.contains(visited) || (arguments.size()>2)) {
			return false;
		}
		switch (arguments.size()) {
		case 2:
			if(!(arguments.get(1) instanceof StringLiteral)) {
				return false;
			}
			StringLiteral argstring3= (StringLiteral) arguments.get(1);
			if (!encodings.contains(argstring3.getLiteralValue())) {
				return false;
			}
			Nodedata nd=new Nodedata();
			nd.encoding=encodingmap.get(argstring3.getLiteralValue());
			nd.replace=true;
			nd.visited=argstring3;
			holder.put(visited,nd);
//			holder.put(ENCODING,StandardCharsets.UTF_8);
//			holder.put(REPLACE,argstring3);
			break;
		case 1:
			Nodedata nd2=new Nodedata();
			nd2.encoding=null;
			nd2.replace=false;
			nd2.visited=visited;
			holder.put(visited,nd2);
//			holder.put(visited,encodingmap.get("UTF-8")); //$NON-NLS-1$
			break;
		default:
			break;
		}
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
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, ast, cb, ((Nodedata) data.get(visited)).encoding);
		/**
		 * Add Charset.defaultCharset() as second (last) parameter
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
//		listRewrite.insertLast(callToCharsetDefaultCharset, group);
		if(((Nodedata)(data.get(visited))).encoding!= null) {
			listRewrite.replace(((Nodedata) data.get(visited)).visited, callToCharsetDefaultCharset, group);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Writer w = new OutputStreamWriter(out, "+computeCharsetforPreview(cb)+");\nOutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(\"\"), StandardCharsets.UTF_8);\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Writer w = new OutputStreamWriter(out);\nOutputStreamWriter os=new OutputStreamWriter(new FileOutputStream(\"\"), \"UTF-8\");\n"; //$NON-NLS-1$
	}
}
