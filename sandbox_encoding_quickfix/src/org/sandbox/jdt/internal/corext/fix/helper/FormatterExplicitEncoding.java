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
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;

/**
 * Java 10
 *
 * Find: new java.util.Formatter(new File(), String cs) throws UnsupportedEncodingException
 *
 * Rewrite: new java.util.Formatter(new File(), Charset cs, Locale.getDefault())
 *
 * Find: new java.util.Formatter(new File(), String cs, new java.util.Locale())
 *
 * Rewrite: new java.util.Formatter(new File(), Charset cs, new java.util.Locale())
 *
 * Find: new java.util.Formatter(new java.io.OutputStream(), String cs)
 *
 * Rewrite: new java.util.Formatter(new java.io.OutputStream(), Charset cs, Locale.getDefault())
 *
 * Find: new java.util.Formatter(new java.io.OutputStream(), String cs, new java.util.Locale())
 *
 * Rewrite: new java.util.Formatter(new java.io.OutputStream(), Charset cs, new java.util.Locale())
 *
 * Find: new java.util.Formatter(new String(), String cs)
 *
 * Rewrite: new java.util.Formatter(new String(), Charset cs, Locale.getDefault())
 *
 * Find: new java.util.Formatter(new String(), String cs, new java.util.Locale())
 *
 * Rewrite: new java.util.Formatter(new String(), Charset cs, new java.util.Locale())
 *
 * Find: new java.util.Formatter(new File())
 *
 * Rewrite: new java.util.Formatter(new File(), Charset.defaultCharset(), Locale.getDefault())
 *
 * Find: new java.util.Formatter(fileName)
 *
 * Rewrite: new java.util.Formatter(fileName, Charset.defaultCharset(), Locale.getDefault())
 *
 * Find: new java.util.Formatter(outputStream)
 *
 * Rewrite: new java.util.Formatter(outputStream, Charset.defaultCharset(), Locale.getDefault())
 *
 * Note: The Formatter constructors accepting Charset are all 3-argument constructors
 * (File/String/OutputStream, Charset, Locale) and were introduced in Java 10.
 * There is no 2-argument Formatter(File/String/OutputStream, Charset) constructor.
 * Formatter(Appendable), Formatter(Locale), and Formatter(PrintStream) do NOT involve
 * encoding and are intentionally NOT handled.
 * See https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/util/Formatter.html
 *
 */
public class FormatterExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		if (!JavaModelUtil.is10OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			/**
			 * For Java 9 and older just do nothing.
			 * The Formatter constructors accepting Charset were introduced in Java 10.
			 */
			return;
		}
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
				if (arguments.get(1) instanceof StringLiteral) {
					StringLiteral argString= (StringLiteral) arguments.get(1);
					String encodingKey= argString.getLiteralValue().toUpperCase(java.util.Locale.ROOT);

					if (ENCODINGS.contains(encodingKey)) {
						NodeData nd= new NodeData(true, argString, ENCODING_MAP.get(encodingKey));
						holder.put(visited, nd);
						operations.add(fixcore.rewrite(visited, cb, holder));
					}
				}
				break;
			case 1:
				if (isEncodingRelevantSingleArgConstructor(visited)) {
					NodeData nd2= new NodeData(false, visited, null);
					holder.put(visited, nd2);
					operations.add(fixcore.rewrite(visited, cb, holder));
				}
				break;
			default:
				break;
		}
		return false;
	}

	/**
	 * Checks whether a 1-argument Formatter constructor uses the platform default encoding
	 * and should be migrated.
	 *
	 * <p>Only these 1-arg constructors use the default encoding:
	 * <ul>
	 *   <li>{@code Formatter(String fileName)} — uses default charset</li>
	 *   <li>{@code Formatter(File file)} — uses default charset</li>
	 *   <li>{@code Formatter(OutputStream os)} — uses default charset</li>
	 * </ul>
	 *
	 * <p>These 1-arg constructors do NOT involve encoding and must be skipped:
	 * <ul>
	 *   <li>{@code Formatter(Appendable a)}</li>
	 *   <li>{@code Formatter(Locale l)}</li>
	 *   <li>{@code Formatter(PrintStream ps)}</li>
	 * </ul>
	 *
	 * @param visited the ClassInstanceCreation node
	 * @return true if this is an encoding-relevant 1-arg Formatter constructor
	 */
	private static boolean isEncodingRelevantSingleArgConstructor(ClassInstanceCreation visited) {
		IMethodBinding binding= visited.resolveConstructorBinding();
		if (binding == null) {
			return false;
		}
		ITypeBinding[] paramTypes= binding.getParameterTypes();
		if (paramTypes.length != 1) {
			return false;
		}
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
		ASTNode callToCharsetDefaultCharset= cb.computeCharsetASTNode(cuRewrite, ast, nodedata.encoding(),getCharsetConstants());
		/**
		 * Register encoding replacement BEFORE removing exception handling.
		 * removeUnsupportedEncodingException may call simplifyEmptyTryStatement
		 * which uses createMoveTarget to move statements out of the try block.
		 * replaceAndRemoveNLS fails silently on nodes that have already been
		 * marked as move targets, so the replacement must be registered first.
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		boolean tryAlreadyUnwrapped= false;
		if (nodedata.replace()) {
			if (visited.arguments().size() == 2) {
				/**
				 * 2-arg case: Formatter(X, String) -> Formatter(X, Charset, Locale.getDefault())
				 * There is no Formatter(X, Charset) 2-arg constructor.
				 * All Charset constructors are 3-arg: Formatter(X, Charset, Locale).
				 *
				 * IMPORTANT: Cannot use replaceAndRemoveNLS here because it conflicts
				 * with ListRewrite operations (insertLast silently fails).
				 * Use listRewrite.replace() + listRewrite.insertLast() instead.
				 * See https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/121
				 */
				listRewrite.replace(nodedata.visited(), callToCharsetDefaultCharset, group);
				listRewrite.insertLast(createLocaleGetDefault(cuRewrite, ast), group);
			} else {
				/**
				 * 3-arg case: Formatter(X, String, Locale) -> Formatter(X, Charset, Locale)
				 * Only replace needed, no insertion.
				 */
				tryAlreadyUnwrapped= replaceArgumentAndRemoveNLS(rewrite, nodedata.visited(), callToCharsetDefaultCharset, group, cuRewrite);
			}
		} else {
			/**
			 * 1-arg case: Formatter(X) -> Formatter(X, Charset.defaultCharset(), Locale.getDefault())
			 * There is no Formatter(X, Charset) 2-arg constructor.
			 */
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
			listRewrite.insertLast(createLocaleGetDefault(cuRewrite, ast), group);
		}
		if (!tryAlreadyUnwrapped) {
			removeUnsupportedEncodingException(visited, group, rewrite, cuRewrite.getImportRemover());
		}
	}

	/**
	 * Create call to Locale.getDefault()
	 *
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast AST
	 * @return MethodInvocation that returns Locale.getDefault()
	 */
	private static MethodInvocation createLocaleGetDefault(final CompilationUnitRewrite cuRewrite, AST ast) {
		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		importRewrite.addImport(Locale.class.getCanonicalName());
		MethodInvocation localeCall= ast.newMethodInvocation();
		localeCall.setExpression(ASTNodeFactory.newName(ast, Locale.class.getSimpleName()));
		localeCall.setName(ast.newSimpleName("getDefault")); //$NON-NLS-1$
		return localeCall;
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Formatter r=new java.util.Formatter(out, " + cb.computeCharsetforPreview() + ", Locale.getDefault());\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Formatter r=new java.util.Formatter(out);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new java.util.Formatter(out)"; //$NON-NLS-1$
	}
}
