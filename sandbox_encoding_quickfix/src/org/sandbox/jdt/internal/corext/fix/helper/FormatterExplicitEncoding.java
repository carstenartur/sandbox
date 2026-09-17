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

import java.io.File;
import java.io.OutputStream;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Java 10 Formatter explicit-encoding modernization.
 * Charset overloads are three-argument constructors and must retain the
 * default FORMAT locale used by the legacy String/default-charset overloads.
 */
public class FormatterExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		if (!JavaModelUtil.is10OrHigher(compilationUnit.getJavaElement().getJavaProject())) return;
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forClassInstanceCreation(Formatter.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb,
			ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();
		switch (arguments.size()) {
			case 2:
			case 3:
				if (arguments.get(1) instanceof StringLiteral argString) {
					String encodingKey= argString.getLiteralValue().toUpperCase(java.util.Locale.ROOT);
					if (ENCODINGS.contains(encodingKey)) {
						holder.put(visited, new NodeData(true, argString, ENCODING_MAP.get(encodingKey)));
						operations.add(fixcore.rewrite(visited, cb, holder));
					}
				}
				break;
			case 1:
				if (isEncodingRelevantSingleArgConstructor(visited)) {
					holder.put(visited, new NodeData(false, visited, null));
					operations.add(fixcore.rewrite(visited, cb, holder));
				}
				break;
			default:
				break;
		}
		return false;
	}

	private static boolean isEncodingRelevantSingleArgConstructor(ClassInstanceCreation visited) {
		IMethodBinding binding= visited.resolveConstructorBinding();
		if (binding == null) return false;
		ITypeBinding[] paramTypes= binding.getParameterTypes();
		if (paramTypes.length != 1) return false;
		String paramTypeName= paramTypes[0].getQualifiedName();
		return File.class.getCanonicalName().equals(paramTypeName)
				|| String.class.getCanonicalName().equals(paramTypeName)
				|| OutputStream.class.getCanonicalName().equals(paramTypeName);
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		NodeData nodedata= (NodeData) data.get(visited);
		ASTNode callToCharsetDefaultCharset= cb.computeCharsetASTNode(cuRewrite, ast, nodedata.encoding(), getCharsetConstants());
		ListRewrite listRewrite= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		boolean tryAlreadyUnwrapped= false;
		if (nodedata.replace()) {
			if (visited.arguments().size() == 2) {
				listRewrite.replace(nodedata.visited(), callToCharsetDefaultCharset, group);
				listRewrite.insertLast(createLocaleGetDefault(cuRewrite, ast), group);
			} else {
				tryAlreadyUnwrapped= replaceArgumentAndRemoveNLS(rewrite, nodedata.visited(), callToCharsetDefaultCharset, group, cuRewrite);
			}
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
			listRewrite.insertLast(createLocaleGetDefault(cuRewrite, ast), group);
		}
		if (!tryAlreadyUnwrapped) removeUnsupportedEncodingException(visited, group, rewrite, cuRewrite.getImportRemover());
	}

	/** Create {@code Locale.getDefault(Locale.Category.FORMAT)}. */
	private static MethodInvocation createLocaleGetDefault(final CompilationUnitRewrite cuRewrite, AST ast) {
		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		String localeName= importRewrite.addImport(Locale.class.getCanonicalName());
		MethodInvocation localeCall= ast.newMethodInvocation();
		localeCall.setExpression(ASTNodeFactory.newName(ast, localeName));
		localeCall.setName(ast.newSimpleName("getDefault")); //$NON-NLS-1$
		localeCall.arguments().add(ast.newName(localeName + ".Category.FORMAT")); //$NON-NLS-1$
		return localeCall;
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Formatter r=new java.util.Formatter(out, " + cb.computeCharsetforPreview() + ", Locale.getDefault(Locale.Category.FORMAT));\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Formatter r=new java.util.Formatter(out);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new java.util.Formatter(out)"; //$NON-NLS-1$
	}
}
