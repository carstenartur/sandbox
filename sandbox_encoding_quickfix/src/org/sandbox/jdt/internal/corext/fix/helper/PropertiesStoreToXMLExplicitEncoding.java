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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_STORE_TO_XML;

import java.util.List;
import java.util.Properties;
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
 * Find:  Properties.storeToXML(java.io.OutputStream,String,"UTF-8")
 *
 * Rewrite: Properties.storeToXML(java.io.OutputStream,StandardCharsets.UTF_8)
 *
 */
public class PropertiesStoreToXMLExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah= new ReferenceHolder<>();
		HelperVisitor.callMethodInvocationVisitor(Properties.class, METHOD_STORE_TO_XML, compilationUnit, datah, nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, nodesprocessed, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb,
			MethodInvocation visited, ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		switch (arguments.size()) {
		case 3:
			if(!(arguments.get(2) instanceof StringLiteral)) {
				return false;
			}
			StringLiteral argstring3= (StringLiteral) arguments.get(2);
			if (!encodings.contains(argstring3.getLiteralValue().toUpperCase())) {
				return false;
			}
			Nodedata nd=new Nodedata();
			nd.encoding=encodingmap.get(argstring3.getLiteralValue().toUpperCase());
			nd.replace=true;
			nd.visited=argstring3;
			holder.put(visited,nd);
			break;
		case 0:
			Nodedata nd2=new Nodedata();
			nd2.encoding=null;
			nd2.replace=false;
			nd2.visited=visited;
			holder.put(visited,nd2);
			break;
		default:
			return false;
		}
		operations.add(fixcore.rewrite(visited, cb, holder));
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
		}
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, ast, cb, ((Nodedata) data.get(visited)).encoding);
		/**
		 * Add Charset.defaultCharset() as second (last) parameter
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		if(((Nodedata)(data.get(visited))).encoding!= null) {
			listRewrite.replace(((Nodedata) data.get(visited)).visited, callToCharsetDefaultCharset, group);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Properties p=\"new Properties()\";\n"+ //$NON-NLS-1$
					"p.storeToXML(java.io.OutputStream,String,"+computeCharsetforPreview(cb)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Properties p=\"new Properties()\";\n"+ //$NON-NLS-1$
		"p.storeToXML(java.io.OutputStream,String,\"UTF-8\");\n"; //$NON-NLS-1$
	}
}