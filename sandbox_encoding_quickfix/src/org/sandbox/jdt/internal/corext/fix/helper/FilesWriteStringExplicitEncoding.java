/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_WRITE_STRING;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;

/**
 * Transforms {@code Files.writeString(Path, CharSequence)} calls to use explicit charset.
 * 
 * <p><strong>Java 11+</strong></p>
 * 
 * <p><strong>Find:</strong> {@code Files.writeString(path, content)} - uses UTF-8 implicitly</p>
 * 
 * <p><strong>Rewrite (KEEP_BEHAVIOR):</strong> {@code Files.writeString(path, content, StandardCharsets.UTF_8)}</p>
 * <p><strong>Rewrite (ENFORCE_UTF8):</strong> {@code Files.writeString(path, content, StandardCharsets.UTF_8)}</p>
 * 
 * <p>The {@code writeString(Path, CharSequence)} method was introduced in Java 11 and uses 
 * {@code StandardCharsets.UTF_8} by default. This transformation makes the encoding explicit for clarity.</p>
 * 
 * <p>Note: The method can also accept OpenOption varargs as additional parameters. This transformation
 * inserts the charset parameter in the correct position (third parameter, after Path and CharSequence).</p>
 */
public class FilesWriteStringExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		if (!JavaModelUtil.is11OrHigher(compilationUnit.getJavaElement().getJavaProject())) {
			// For Java 10 and older, Files.writeString() is not available
			return;
		}
		ReferenceHolder<ASTNode, Object> datah = new ReferenceHolder<>();
		getCharsetConstants().clear();
		HelperVisitor.callMethodInvocationVisitor(Files.class, METHOD_WRITE_STRING, compilationUnit, datah,
				nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, ChangeBehavior cb, MethodInvocation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments = visited.arguments();
		
		// Handle Files.writeString(Path, CharSequence, Charset, OpenOption...) - replace charset if it's a known encoding
		// The charset is the third parameter
		if (arguments.size() >= 3) {
			ASTNode encodingArg = arguments.get(2);
			String encodingValue = getEncodingValue(encodingArg, visited);
			
			if (encodingValue != null && ENCODINGS.contains(encodingValue)) {
				NodeData nd = new NodeData(true, encodingArg, ENCODING_MAP.get(encodingValue));
				holder.put(visited, nd);
				operations.add(fixcore.rewrite(visited, cb, holder));
				return false;
			}
			// If we have 3+ arguments and third is a charset (even if not recognized), don't add another
			return false;
		}
		
		// Handle Files.writeString(Path, CharSequence) - only 2 arguments, add charset parameter
		// Add charset parameter as third parameter
		if (arguments.size() == 2) {
			// Files.writeString(Path, CharSequence) uses UTF-8 by default since Java 11
			// In all modes, we should use UTF-8 to preserve the original behavior
			String encoding = "UTF_8"; //$NON-NLS-1$
			NodeData nd = new NodeData(false, visited, encoding);
			holder.put(visited, nd);
			operations.add(fixcore.rewrite(visited, cb, holder));
			return false;
		}
		
		return false;
	}

	/**
	 * Extracts the encoding value from various AST node types representing charset arguments.
	 * 
	 * @param encodingArg the AST node representing the charset argument
	 * @param context the method invocation context for variable resolution
	 * @return the uppercase encoding string (e.g., "UTF-8"), or null if not determinable
	 */
	private static String getEncodingValue(ASTNode encodingArg, MethodInvocation context) {
		if (encodingArg instanceof StringLiteral literal) {
			return literal.getLiteralValue().toUpperCase(java.util.Locale.ROOT);
		} else if (encodingArg instanceof SimpleName simpleName) {
			return findVariableValue(simpleName, context);
		} else if (encodingArg instanceof QualifiedName qualifiedName) {
			// Handle StandardCharsets.UTF_8 pattern
			return extractStandardCharsetName(qualifiedName);
		} else if (encodingArg instanceof FieldAccess fieldAccess) {
			// Handle java.nio.charset.StandardCharsets.UTF_8 pattern
			return extractStandardCharsetName(fieldAccess);
		}
		return null;
	}

	/**
	 * Extracts charset name from QualifiedName like StandardCharsets.UTF_8.
	 */
	private static String extractStandardCharsetName(QualifiedName qualifiedName) {
		String qualifier = qualifiedName.getQualifier().toString();
		if ("StandardCharsets".equals(qualifier) || qualifier.endsWith(".StandardCharsets")) {
			String fieldName = qualifiedName.getName().getIdentifier();
			// Convert field name format (UTF_8) to charset name format (UTF-8)
			return fieldName.replace('_', '-');
		}
		return null;
	}

	/**
	 * Extracts charset name from FieldAccess like StandardCharsets.UTF_8.
	 */
	private static String extractStandardCharsetName(FieldAccess fieldAccess) {
		String expression = fieldAccess.getExpression().toString();
		if ("StandardCharsets".equals(expression) || expression.endsWith(".StandardCharsets")) {
			String fieldName = fieldAccess.getName().getIdentifier();
			// Convert field name format (UTF_8) to charset name format (UTF-8)
			return fieldName.replace('_', '-');
		}
		return null;
	}

	@Override
	public void rewrite(UseExplicitEncodingFixCore upp, MethodInvocation visited, CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter = cuRewrite.getImportRewrite();
		NodeData nodedata = (NodeData) data.get(visited);
		ASTNode callToCharsetDefaultCharset = cb.computeCharsetASTNode(cuRewrite, ast, nodedata.encoding(),
				getCharsetConstants());

		ListRewrite listRewrite = rewrite.getListRewrite(visited, MethodInvocation.ARGUMENTS_PROPERTY);
		if (nodedata.replace()) {
			listRewrite.replace(nodedata.visited(), callToCharsetDefaultCharset, group);
		} else {
			// Insert as third parameter (after Path and CharSequence, before any OpenOptions)
			List<ASTNode> arguments = visited.arguments();
			if (arguments.size() == 2) {
				listRewrite.insertLast(callToCharsetDefaultCharset, group);
			} else {
				// Insert after second parameter
				listRewrite.insertAfter(callToCharsetDefaultCharset, arguments.get(1), group);
			}
		}
		removeUnsupportedEncodingException(visited, group, rewrite, importRewriter);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Files.writeString(path, content, " + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Files.writeString(path, content);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Files.writeString(path, content)"; //$NON-NLS-1$
	}
}
