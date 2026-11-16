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

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.sandbox.jdt.triggerpattern.cleanup.ExceptionCleanupHelper;

/** Adds Charset arguments to PrintStream constructors backed by an OutputStream. */
public class PrintStreamExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {
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
		HelperVisitorFactory.forClassInstanceCreation(PrintStream.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder, hasPriorOperations));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb, ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder, boolean hasPriorOperations) {
		// The file and path Charset overloads add IOException to the checked exception contract.
		var constructor= visited.resolveConstructorBinding();
		var firstParameter= constructor.getParameterTypes()[0];
		if (!OutputStream.class.getName().equals(firstParameter.getQualifiedName())) {
			return false;
		}
		List<?> arguments= visited.arguments();
		int argumentCount= arguments.size();
		if (argumentCount == 1) {
			holder.put(visited, new ConstructorData(null, null, null));
		} else if (argumentCount == 3 && arguments.get(2) instanceof StringLiteral literal && isKnownEncoding(literal)) {
			ITypeBinding[] exceptions= constructor.getExceptionTypes();
			if (exceptions.length != 1 || exceptions[0].isRecovered()
					|| !UnsupportedEncodingException.class.getName().equals(exceptions[0].getQualifiedName())) {
				return false;
			}
			ITypeBinding exception= exceptions[0];
			if (!hasPriorOperations && !ExceptionCleanupHelper.canRemoveExceptionLocally(visited, exception, null)
					&& !ExceptionCleanupHelper.canKeepDeclaredThrows(visited, exception, null)) {
				return false;
			}
			holder.put(visited, new ConstructorData(literal, getEncodingConstantName(literal), exception));
		} else {
			return false;
		}
		operations.add(fixcore.rewrite(visited, cb, holder));
		return false;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ConstructorData nodeData= (ConstructorData) data.get(visited);
		ITypeBinding exception= nodeData.removedException();
		boolean removeCatches= exception != null && ExceptionCleanupHelper.canRemoveExceptionLocally(visited, exception, rewrite);
		if (exception != null && !removeCatches && !ExceptionCleanupHelper.canKeepDeclaredThrows(visited, exception, rewrite)) {
			return;
		}
		ASTNode charset= cb.computeCharsetASTNode(cuRewrite, ast, visited, nodeData.encoding(), getCharsetConstants(cuRewrite));
		if (exception != null) {
			EncodingSourceRewrite.record(cuRewrite, nodeData.encodingArgument());
			rewrite.replace(nodeData.encodingArgument(), charset, group);
			if (removeCatches) {
				ExceptionCleanupHelper.removeCheckedExceptionFromEnclosingTries(visited,
						UnsupportedEncodingException.class.getName(), group, rewrite, cuRewrite.getImportRemover());
			}
			return;
		}
		ListRewrite arguments= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		arguments.insertLast(ast.newBooleanLiteral(false), group);
		arguments.insertLast(charset, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (cb == ChangeBehavior.ENFORCE_UTF8_AGGREGATE) {
			return afterRefactoring
					? "PrintStream stream = new PrintStream(out, false, " + cb.computeCharsetforPreview() + ");\n" //$NON-NLS-1$ //$NON-NLS-2$
					: "PrintStream stream = new PrintStream(out);\n"; //$NON-NLS-1$
		}
		if (afterRefactoring) {
			return "Stream w=new PrintStream(\"out.txt\"," + cb.computeCharsetforPreview() + ");\n" + //$NON-NLS-1$ //$NON-NLS-2$
					"Stream w=new PrintStream(\"out.txt\",StandardCharsets.UTF_8);\n" + //$NON-NLS-1$
					"Stream w=new PrintStream(new File(\"out.txt\"),StandardCharsets.UTF_8);\n"; //$NON-NLS-1$
		}
		return """
				Stream w=new PrintStream("out.txt");
				Stream w=new PrintStream("out.txt","UTF-8");
				Stream w=new PrintStream(new File("out.txt"),"UTF-8");
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new PrintStream(\"out.txt\")"; //$NON-NLS-1$
	}
}
