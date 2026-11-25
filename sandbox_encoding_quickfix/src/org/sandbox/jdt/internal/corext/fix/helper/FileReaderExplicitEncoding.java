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
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Uses FileReader's Charset constructors since Java 11 when the surrounding
 * exception contract permits IOException. Other supported contexts retain the
 * assignment-compatible InputStreamReader/FileInputStream replacement.
 */
public class FileReaderExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		var element= compilationUnit.getJavaElement();
		boolean hasCharsetConstructor= element != null && JavaModelUtil.is11OrHigher(element.getJavaProject());
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forClassInstanceCreation(FileReader.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder, hasCharsetConstructor));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, ChangeBehavior cb,
			ClassInstanceCreation visited, ReferenceHolder<ASTNode, Object> holder, boolean hasCharsetConstructor) {
		IMethodBinding constructor= visited.resolveConstructorBinding();
		if (constructor == null || constructor.isRecovered() || visited.getAnonymousClassDeclaration() != null
				|| !FileReader.class.getName().equals(constructor.getDeclaringClass().getQualifiedName())
				|| constructor.getParameterTypes().length != 1) {
			return false;
		}
		boolean useCharsetConstructor= hasCharsetConstructor && acceptsCharsetConstructor(visited, constructor);
		if (!useCharsetConstructor && !acceptsInputStreamReader(visited)) {
			return false;
		}
		holder.put(visited, Boolean.valueOf(useCharsetConstructor));
		operations.add(fixcore.rewrite(visited, cb, holder));
		return false;
	}

	private static boolean acceptsCharsetConstructor(ClassInstanceCreation creation, IMethodBinding original) {
		for (IMethodBinding candidate : original.getDeclaringClass().getDeclaredMethods()) {
			ITypeBinding[] parameters= candidate.getParameterTypes();
			if (!candidate.isConstructor() || parameters.length != 2
					|| !parameters[0].isEqualTo(original.getParameterTypes()[0])
					|| !Charset.class.getName().equals(parameters[1].getQualifiedName())) {
				continue;
			}
			// The Charset overload declares IOException, not merely FileNotFoundException.
			for (ITypeBinding exception : candidate.getExceptionTypes()) {
				if (!isExceptionHandled(creation, exception)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private static boolean isExceptionHandled(ASTNode node, ITypeBinding exception) {
		for (ASTNode child= node, parent= child.getParent(); parent != null;
				child= parent, parent= parent.getParent()) {
			if (parent instanceof TryStatement statement
					&& (child.getLocationInParent() == TryStatement.BODY_PROPERTY
							|| child.getLocationInParent() == TryStatement.RESOURCES2_PROPERTY)) {
				for (Object item : statement.catchClauses()) {
					Type caught= ((CatchClause) item).getException().getType();
					if (caught instanceof UnionType union) {
						for (Object alternative : union.types()) {
							if (catches(exception, ((Type) alternative).resolveBinding())) {
								return true;
							}
						}
					} else if (catches(exception, caught.resolveBinding())) {
						return true;
					}
				}
			}
			if (parent instanceof LambdaExpression lambda) {
				return declares(lambda.resolveMethodBinding(), exception);
			}
			if (parent instanceof MethodDeclaration method) {
				return declares(method.resolveBinding(), exception);
			}
			if (parent instanceof AbstractTypeDeclaration || parent instanceof AnonymousClassDeclaration) {
				return false;
			}
		}
		return false;
	}

	private static boolean declares(IMethodBinding method, ITypeBinding exception) {
		if (method != null) {
			for (ITypeBinding declared : method.getExceptionTypes()) {
				if (catches(exception, declared)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean catches(ITypeBinding exception, ITypeBinding handler) {
		return handler != null && !handler.isRecovered() && exception.isAssignmentCompatible(handler);
	}

	private static boolean acceptsInputStreamReader(ClassInstanceCreation creation) {
		ITypeBinding reader= creation.resolveTypeBinding();
		if (reader == null || creation.getAnonymousClassDeclaration() != null) {
			return false;
		}
		ITypeBinding replacement= reader.getSuperclass();
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
			rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY)
					.insertLast(callToCharsetDefaultCharset, group);
			return;
		}
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

		cuRewrite.getImportRemover().registerRemovedNode(visited.getType());
		ASTNodes.replaceButKeepComment(rewrite, visited, isrclassInstance, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Reader r=new InputStreamReader(new FileInputStream(inputfile)," + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Reader r=new FileReader(inputfile);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new FileReader(inputfile)"; //$NON-NLS-1$
	}
}
