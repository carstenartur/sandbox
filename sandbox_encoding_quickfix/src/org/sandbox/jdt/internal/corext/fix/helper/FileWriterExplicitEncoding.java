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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Adds a typed Charset to Java 11 FileWriter constructors. Older source levels
 * use an OutputStreamWriter only when the existing target type accepts it.
 */
public class FileWriterExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		var element= compilationUnit.getJavaElement();
		boolean hasCharsetConstructor= element != null && JavaModelUtil.is11OrHigher(element.getJavaProject());
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forClassInstanceCreation(FileWriter.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder, hasCharsetConstructor));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb, ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder, boolean hasCharsetConstructor) {
		var constructor= visited.resolveConstructorBinding();
		if (constructor == null || constructor.isRecovered() || visited.getAnonymousClassDeclaration() != null
				|| !FileWriter.class.getName().equals(constructor.getDeclaringClass().getQualifiedName())) {
			return false;
		}
		ITypeBinding[] parameters= constructor.getParameterTypes();
		if (parameters.length < 1 || parameters.length > 2
				|| (!File.class.getName().equals(parameters[0].getQualifiedName())
						&& !String.class.getName().equals(parameters[0].getQualifiedName()))
				|| (parameters.length == 2 && !"boolean".equals(parameters[1].getQualifiedName()))) { //$NON-NLS-1$
			return false;
		}
		if (!hasCharsetConstructor && !acceptsOutputStreamWriter(visited)) {
			return false;
		}
		holder.put(visited, Boolean.valueOf(hasCharsetConstructor));
		operations.add(fixcore.rewrite(visited, cb, holder));
		return false;
	}

	private static boolean acceptsOutputStreamWriter(ClassInstanceCreation creation) {
		ITypeBinding writer= creation.resolveTypeBinding();
		ITypeBinding replacement= writer == null ? null : writer.getSuperclass();
		Expression expression= creation;
		while (expression.getParent() instanceof ParenthesizedExpression parentheses) {
			expression= parentheses;
		}
		ITypeBinding required;
		if (expression.getParent() instanceof VariableDeclarationFragment fragment && fragment.getInitializer() == expression) {
			var binding= fragment.resolveBinding();
			required= binding == null ? null : binding.getType();
		} else {
			required= ASTNodes.getTargetType(expression);
		}
		return replacement != null && required != null && !required.isRecovered()
				&& replacement.isAssignmentCompatible(required);
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ASTNode callToCharsetDefaultCharset= cb.computeCharsetASTNode(cuRewrite, ast, visited, null, getCharsetConstants(cuRewrite));
		if (Boolean.TRUE.equals(data.get(visited))) {
			// FileWriter(File/String, Charset[, boolean append]) is available since Java 11.
			rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY)
					.insertAt(callToCharsetDefaultCharset, 1, group);
			return;
		}
		// The legacy wrapper must preserve the optional append argument too.
		ClassInstanceCreation fosclassInstance= ast.newClassInstanceCreation();
		fosclassInstance.setType(ast.newSimpleType(addImport(FileOutputStream.class.getCanonicalName(), cuRewrite, ast)));
		for (Object argument : visited.arguments()) {
			fosclassInstance.arguments().add(ASTNodes.createMoveTarget(rewrite, (ASTNode) argument));
		}
		ClassInstanceCreation oswclassInstance= ast.newClassInstanceCreation();
		oswclassInstance.setType(ast.newSimpleType(addImport(OutputStreamWriter.class.getCanonicalName(), cuRewrite, ast)));
		oswclassInstance.arguments().add(fosclassInstance);
		oswclassInstance.arguments().add(callToCharsetDefaultCharset);

		cuRewrite.getImportRemover().registerRemovedNode(visited.getType());
		ASTNodes.replaceButKeepComment(rewrite, visited, oswclassInstance, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Writer w=new OutputStreamWriter(new FileOutputStream(outputfile)," + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Writer w=new FileWriter(outputfile);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new FileWriter(outputfile)"; //$NON-NLS-1$
	}
}
