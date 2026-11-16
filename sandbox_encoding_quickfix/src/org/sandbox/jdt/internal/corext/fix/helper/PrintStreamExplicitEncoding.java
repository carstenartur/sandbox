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
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/** Adds Charset arguments to PrintStream constructors backed by an OutputStream. */
public class PrintStreamExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		var javaElement= compilationUnit.getJavaElement();
		if (javaElement == null || !JavaModelUtil.is10OrHigher(javaElement.getJavaProject())) {
			return;
		}
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forClassInstanceCreation(PrintStream.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb, ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		// The file and path Charset overloads add IOException to the checked exception contract.
		var firstParameter= visited.resolveConstructorBinding().getParameterTypes()[0];
		if (!OutputStream.class.getName().equals(firstParameter.getQualifiedName())) {
			return false;
		}
		List<?> arguments= visited.arguments();
		int argumentCount= arguments.size();
		if (argumentCount == 1) {
			holder.put(visited, new NodeData(false, visited, null));
		} else if (argumentCount == 3 && arguments.get(2) instanceof StringLiteral literal && isKnownEncoding(literal)) {
			holder.put(visited, new NodeData(true, literal, getEncodingConstantName(literal)));
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
		NodeData nodeData= (NodeData) data.get(visited);
		ASTNode charset= cb.computeCharsetASTNode(cuRewrite, ast, visited, nodeData.encoding(), getCharsetConstants(cuRewrite));
		if (nodeData.replace()) {
			if (!replaceArgumentAndRemoveNLS(rewrite, nodeData.visited(), charset, group, cuRewrite)) {
				removeUnsupportedEncodingException(visited, group, rewrite, cuRewrite.getImportRemover());
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
