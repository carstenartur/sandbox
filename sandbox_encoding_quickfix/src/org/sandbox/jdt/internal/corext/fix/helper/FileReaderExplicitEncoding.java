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

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
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
 * Reader is=new FileReader("file.txt")
 * Reader is=new InputStreamReader(new FileInputStream("file.txt"),Charset.defaultCharset());
 *
 * Charset.defaultCharset() is available since Java 1.5
 *
 */
public class FileReaderExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,ChangeBehavior cb) {
		HelperVisitor.callClassInstanceCreationVisitor(FileReader.class, compilationUnit, datah, nodesprocessed, (visited, holder_a) -> {
			List<ASTNode> arguments= visited.arguments();
			if(nodesprocessed.contains(visited) || (arguments.size()>2)) {
				return false;
			}
			switch (arguments.size()) {
			case 1:
				break;
			case 2:
				if(!(arguments.get(1) instanceof StringLiteral)) return false;
				StringLiteral argstring3= (StringLiteral) arguments.get(1);
				if (!("UTF-8".equals(argstring3.getLiteralValue()))) { //$NON-NLS-1$
					return false;
				}
				holder_a.put(ENCODING,StandardCharsets.UTF_8);
				holder_a.put(REPLACE,argstring3);
				break;
			default:
				return false;
			}
			operations.add(fixcore.rewrite(visited, cb, holder_a));
			nodesprocessed.add(visited);
			return false;
		});
		
//		compilationUnit.accept(new ASTVisitor() {
//			@Override
//			public boolean visit(final ClassInstanceCreation visited) {
//				if(nodesprocessed.contains(visited)) {
//					return false;
//				}
//				ITypeBinding binding= visited.resolveTypeBinding();
//				if (FileReader.class.getSimpleName().equals(binding.getName())) {
//					operations.add(fixcore.rewrite(visited, cb, datah));
//					nodesprocessed.add(visited);
//					return false;
//				}
//				return true;
//			}
//		});
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp,final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group,ChangeBehavior cb, ReferenceHolder<String, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		if (!JavaModelUtil.is50OrHigher(cuRewrite.getCu().getJavaProject())) {
			/**
			 * For Java 1.4 and older just do nothing
			 */
			return;
		}
		ASTNode callToCharsetDefaultCharset= computeCharsetASTNode(cuRewrite, cb, ast, (Charset) data.get(ENCODING));
		/**
		 * new FileInputStream(<filename>)
		 */
		ClassInstanceCreation fisclassInstance= ast.newClassInstanceCreation();
		fisclassInstance.setType(ast.newSimpleType(addImport(FileInputStream.class.getCanonicalName(), cuRewrite, ast)));
		fisclassInstance.arguments().add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression((ASTNode) visited.arguments().get(0))));
		/**
		 * new InputStreamReader(new FileInputStream(<filename>))
		 */
		ClassInstanceCreation isrclassInstance= ast.newClassInstanceCreation();
		isrclassInstance.setType(ast.newSimpleType(addImport(InputStreamReader.class.getCanonicalName(), cuRewrite, ast)));
		isrclassInstance.arguments().add(fisclassInstance);
		isrclassInstance.arguments().add(callToCharsetDefaultCharset);

		ASTNodes.replaceButKeepComment(rewrite, visited, isrclassInstance, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring,ChangeBehavior cb) {
		if(afterRefactoring) {
			return "Reader r=new InputStreamReader(new FileInputStream(inputfile),"+computeCharsetforPreview(cb)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Reader r=new FileReader(inputfile);\n"; //$NON-NLS-1$
	}
}
