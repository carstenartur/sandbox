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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.sandbox.jdt.triggerpattern.cleanup.ExceptionCleanupHelper;

/**
 * Adds a Charset to file-backed PrintWriters without widening their checked exceptions.
 * Writer and stream overloads retain their existing encoding and autoflush semantics.
 */
public class PrintWriterExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	private record ConstructorData(StringLiteral encodingArgument, String encoding, ITypeBinding removedException) {
	}

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		var javaElement= compilationUnit.getJavaElement();
		if (javaElement == null || !JavaModelUtil.is10OrHigher(javaElement.getJavaProject())) {
			return;
		}
		boolean hasPriorOperations= !operations.isEmpty();
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forClassInstanceCreation(PrintWriter.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder, hasPriorOperations));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb, ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder, boolean hasPriorOperations) {
		var constructor= visited.resolveConstructorBinding();
		if (constructor == null || constructor.isRecovered()
				|| !PrintWriter.class.getName().equals(constructor.getDeclaringClass().getQualifiedName())) {
			return false;
		}
		var parameters= constructor.getParameterTypes();
		if (parameters.length == 0 || (!File.class.getName().equals(parameters[0].getQualifiedName())
				&& !String.class.getName().equals(parameters[0].getQualifiedName()))) {
			// In particular, an OutputStream can be a PrintStream whose charset is inherited.
			return false;
		}
		List<?> arguments= visited.arguments();
		if (arguments.size() == 1) {
			holder.put(visited, new ConstructorData(null, null, null));
		} else if (arguments.size() == 2 && parameters.length == 2 && String.class.getName().equals(parameters[1].getQualifiedName())
				&& arguments.get(1) instanceof StringLiteral literal && isKnownEncoding(literal)) {
			ITypeBinding exception= Arrays.stream(constructor.getExceptionTypes())
					.filter(type -> !type.isRecovered() && UnsupportedEncodingException.class.getName().equals(type.getQualifiedName()))
					.findFirst().orElse(null);
			if (exception == null || (!hasPriorOperations && !ExceptionCleanupHelper.canRemoveExceptionLocally(visited, exception, null)
					&& !canKeepDeclaredThrows(visited, exception, null))) {
				return false;
			}
			holder.put(visited, new ConstructorData(literal, getEncodingConstantName(literal), exception));
		} else {
			return false;
		}
		operations.add(fixcore.rewrite(visited, cb, holder));
		return false;
	}

	private static boolean canKeepDeclaredThrows(ClassInstanceCreation visited, ITypeBinding removed, ASTRewrite rewrite) {
		if (ExceptionCleanupHelper.canKeepDeclaredThrows(visited, removed, rewrite)) {
			return true;
		}
		ASTNode scope= ASTNodes.getFirstAncestorOrNull(visited, BodyDeclaration.class, LambdaExpression.class);
		if (!(scope instanceof MethodDeclaration method)) {
			return false;
		}
		// The new FileOutputStream still throws FileNotFoundException. Without an
		// enclosing try, that exception keeps a broader throws declaration necessary.
		for (ASTNode parent= visited.getParent(); parent != method; parent= parent.getParent()) {
			if (parent instanceof TryStatement) {
				return false;
			}
		}
		var declaration= method.resolveBinding();
		ITypeBinding fileException= Arrays.stream(visited.resolveConstructorBinding().getExceptionTypes())
				.filter(type -> !type.isRecovered() && FileNotFoundException.class.getName().equals(type.getQualifiedName()))
				.findFirst().orElse(null);
		if (declaration == null || declaration.isRecovered() || fileException == null) {
			return false;
		}
		List<ITypeBinding> affected= Arrays.stream(declaration.getExceptionTypes())
				.filter(type -> removed.isAssignmentCompatible(type) || type.isAssignmentCompatible(removed)).toList();
		return !affected.isEmpty() && affected.stream()
				.allMatch(type -> !type.isRecovered() && fileException.isAssignmentCompatible(type));
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ConstructorData nodeData= (ConstructorData) data.get(visited);
		ITypeBinding exception= nodeData.removedException();
		boolean removeCatches= exception != null && ExceptionCleanupHelper.canRemoveExceptionLocally(visited, exception, rewrite);
		if (exception != null && !removeCatches && !canKeepDeclaredThrows(visited, exception, rewrite)) {
			return;
		}
		ASTNode charset= cb.computeCharsetASTNode(cuRewrite, ast, visited, nodeData.encoding(), getCharsetConstants(cuRewrite));
		// The direct File/String + Charset overload throws IOException. Keeping the file
		// opening separate preserves FileNotFoundException and PrintWriter's buffering.
		ClassInstanceCreation stream= ast.newClassInstanceCreation();
		stream.setType(ast.newSimpleType(addImport(FileOutputStream.class.getCanonicalName(), cuRewrite, ast)));
		ASTNode source= (ASTNode) visited.arguments().get(0);
		stream.arguments().add(ASTNodes.createMoveTarget(rewrite, source));
		ListRewrite arguments= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		arguments.replace(source, stream, group);
		if (nodeData.encodingArgument() != null) {
			EncodingSourceRewrite.record(cuRewrite, nodeData.encodingArgument());
			arguments.replace(nodeData.encodingArgument(), ast.newBooleanLiteral(false), group);
		} else {
			arguments.insertLast(ast.newBooleanLiteral(false), group);
		}
		arguments.insertLast(charset, group);
		if (removeCatches) {
			ExceptionCleanupHelper.removeCheckedExceptionFromEnclosingTries(visited,
					UnsupportedEncodingException.class.getName(), group, rewrite, cuRewrite.getImportRemover());
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Writer w=new PrintWriter(new FileOutputStream(outputfile), false, " + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Writer w=new PrintWriter(outputfile);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new PrintWriter(outputfile)"; //$NON-NLS-1$
	}
}
