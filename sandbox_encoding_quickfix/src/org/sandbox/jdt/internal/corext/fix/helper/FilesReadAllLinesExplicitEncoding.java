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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_READ_ALL_LINES;

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
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;

/**
 * Transforms {@code Files.readAllLines(Path)} calls to use explicit charset.
 * 
 * <p><strong>Java 8+</strong></p>
 * 
 * <p><strong>Find:</strong> {@code Files.readAllLines(path)} - uses UTF-8 by default but should be explicit</p>
 * 
 * <p><strong>Rewrite (KEEP_BEHAVIOR):</strong> {@code Files.readAllLines(path, StandardCharsets.UTF_8)}</p>
 * <p><strong>Rewrite (ENFORCE_UTF8):</strong> {@code Files.readAllLines(path, StandardCharsets.UTF_8)}</p>
 * 
 * <p>The single-parameter {@code readAllLines(Path)} method uses {@code StandardCharsets.UTF_8} 
 * by default (since Java 8). This transformation makes the encoding explicit for clarity
 * and consistency.</p>
 */
public class FilesReadAllLinesExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah = new ReferenceHolder<>();
		getCharsetConstants().clear();
		HelperVisitor.callMethodInvocationVisitor(Files.class, METHOD_READ_ALL_LINES, compilationUnit, datah,
				nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, ChangeBehavior cb, MethodInvocation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments = visited.arguments();
		
		// Handle Files.readAllLines(Path, Charset) - replace charset if it's a known encoding
		if (arguments.size() == 2) {
			ASTNode encodingArg = arguments.get(1);
			String encodingValue = getEncodingValue(encodingArg, visited);
			
			if (encodingValue != null && ENCODINGS.contains(encodingValue)) {
				NodeData nd = new NodeData(true, encodingArg, ENCODING_MAP.get(encodingValue));
				holder.put(visited, nd);
				operations.add(fixcore.rewrite(visited, cb, holder));
				return false;
			}
			// If we have a charset argument but it's not a recognized string literal,
			// don't add another charset parameter
			return false;
		}
		
		// Handle Files.readAllLines(Path) - add charset parameter
		if (arguments.size() == 1) {
			// Files.readAllLines(Path) uses UTF-8 by default since Java 8
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
			listRewrite.insertLast(callToCharsetDefaultCharset, group);
		}
		removeUnsupportedEncodingException(visited, group, rewrite, importRewriter);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Files.readAllLines(path, " + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Files.readAllLines(path);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Files.readAllLines(path)"; //$NON-NLS-1$
	}
}
