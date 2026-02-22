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
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
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
 * Handles explicit encoding for {@link java.util.Scanner} constructors.
 * See: https://download.java.net/java/early_access/panama/docs/api/java.base/java/util/Scanner.html
 *
 * <h2>2-argument constructors (replace String encoding with Charset):</h2>
 * <ul>
 *   <li>{@code Scanner(File, "UTF-8")} → {@code Scanner(File, StandardCharsets.UTF_8)}</li>
 *   <li>{@code Scanner(InputStream, "UTF-8")} → {@code Scanner(InputStream, StandardCharsets.UTF_8)}</li>
 *   <li>{@code Scanner(Path, "UTF-8")} → {@code Scanner(Path, StandardCharsets.UTF_8)}</li>
 *   <li>{@code Scanner(ReadableByteChannel, "UTF-8")} → {@code Scanner(ReadableByteChannel, StandardCharsets.UTF_8)}</li>
 * </ul>
 *
 * <h2>1-argument constructors (add Charset.defaultCharset()):</h2>
 * <ul>
 *   <li>{@code Scanner(File)} → {@code Scanner(File, Charset.defaultCharset())}</li>
 *   <li>{@code Scanner(InputStream)} → {@code Scanner(InputStream, Charset.defaultCharset())}</li>
 *   <li>{@code Scanner(Path)} → {@code Scanner(Path, Charset.defaultCharset())}</li>
 *   <li>{@code Scanner(ReadableByteChannel)} → {@code Scanner(ReadableByteChannel, Charset.defaultCharset())}</li>
 * </ul>
 *
 * <h2>Not handled (no charset-accepting variant exists):</h2>
 * <ul>
 *   <li>{@code Scanner(String)} — scans the string directly, no charset parameter</li>
 *   <li>{@code Scanner(Readable)} — no charset parameter</li>
 * </ul>
 */
public class ScannerExplicitEncoding extends AbstractExplicitEncoding<ClassInstanceCreation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		if (!JavaModelUtil.is10OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			/**
			 * For Java 9 and older just do nothing
			 */
			return;
		}
		ReferenceHolder<ASTNode, Object> datah= ReferenceHolder.createForNodes();
		HelperVisitorFactory.forClassInstanceCreation(Scanner.class)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(datah, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore, Set<CompilationUnitRewriteOperation> operations,
			ChangeBehavior cb, ClassInstanceCreation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments= visited.arguments();

		switch (arguments.size()) {
			case 2:
				ASTNode argumentNode= arguments.get(1);

				if (argumentNode instanceof StringLiteral) {
					StringLiteral encodingLiteral= (StringLiteral) argumentNode;
					String encodingValue= encodingLiteral.getLiteralValue().toUpperCase(java.util.Locale.ROOT);

					if (ENCODINGS.contains(encodingValue)) {
						NodeData nd= new NodeData(true, encodingLiteral, ENCODING_MAP.get(encodingValue));
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
	 * Checks whether a 1-argument Scanner constructor uses the platform default encoding
	 * and should be migrated.
	 *
	 * <p>Only these 1-arg constructors use the default encoding:
	 * <ul>
	 *   <li>{@code Scanner(File source)} — uses default charset</li>
	 *   <li>{@code Scanner(InputStream source)} — uses default charset</li>
	 *   <li>{@code Scanner(Path source)} — uses default charset</li>
	 *   <li>{@code Scanner(ReadableByteChannel source)} — uses default charset</li>
	 * </ul>
	 *
	 * <p>These 1-arg constructors do NOT involve encoding and must be skipped:
	 * <ul>
	 *   <li>{@code Scanner(String source)} — scans the string directly, no charset variant exists</li>
	 *   <li>{@code Scanner(Readable source)} — no charset variant exists</li>
	 * </ul>
	 *
	 * @param visited the ClassInstanceCreation node
	 * @return true if this is an encoding-relevant 1-arg Scanner constructor
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
				|| InputStream.class.getCanonicalName().equals(paramTypeName)
				|| Path.class.getCanonicalName().equals(paramTypeName)
				|| ReadableByteChannel.class.getCanonicalName().equals(paramTypeName);
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, final ClassInstanceCreation visited, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		NodeData nodedata= (NodeData) data.get(visited);
		ASTNode callToCharsetDefaultCharset= cb.computeCharsetASTNode(cuRewrite, ast, nodedata.encoding(),getCharsetConstants());
		/**
		 * Add Charset.defaultCharset() as second (last) parameter
		 */
		ListRewrite listRewrite= rewrite.getListRewrite(visited, ClassInstanceCreation.ARGUMENTS_PROPERTY);
		if (nodedata.replace()) {
			replaceArgumentAndRemoveNLS(rewrite, nodedata.visited(), callToCharsetDefaultCharset, group, cuRewrite);
		} else {
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "new java.util.Scanner(\"asdf\",StandardCharsets.UTF_8);\n"; //$NON-NLS-1$
		}
		return "new java.util.Scanner(\"asdf\", \"UTF-8\");\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "new java.util.Scanner()"; //$NON-NLS-1$
	}
}
