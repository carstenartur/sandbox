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

import static org.sandbox.jdt.internal.common.LibStandardNames.METHOD_NEW_BUFFERED_WRITER;

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
 * Transforms {@code Files.newBufferedWriter(Path)} calls to use explicit charset.
 * 
 * <p><strong>Java 8+</strong></p>
 * 
 * <p><strong>Find:</strong> {@code Files.newBufferedWriter(path)} - uses UTF-8 implicitly</p>
 * 
 * <p><strong>Rewrite (KEEP_BEHAVIOR):</strong> {@code Files.newBufferedWriter(path, StandardCharsets.UTF_8)}</p>
 * <p><strong>Rewrite (ENFORCE_UTF8):</strong> {@code Files.newBufferedWriter(path, StandardCharsets.UTF_8)}</p>
 * 
 * <p>The single-parameter {@code newBufferedWriter(Path)} method uses {@code StandardCharsets.UTF_8} 
 * by default (since Java 8). This transformation makes the encoding explicit for clarity.</p>
 * 
 * <p>Note: The method can also accept OpenOption varargs as additional parameters. This transformation
 * inserts the charset parameter in the correct position (second parameter).</p>
 */
public class FilesNewBufferedWriterExplicitEncoding extends AbstractExplicitEncoding<MethodInvocation> {

	@Override
	public void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb) {
		ReferenceHolder<ASTNode, Object> datah = new ReferenceHolder<>();
		getCharsetConstants().clear();
		HelperVisitor.callMethodInvocationVisitor(Files.class, METHOD_NEW_BUFFERED_WRITER, compilationUnit, datah,
				nodesprocessed, (visited, holder) -> processFoundNode(fixcore, operations, cb, visited, holder));
	}

	private static boolean processFoundNode(UseExplicitEncodingFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, ChangeBehavior cb, MethodInvocation visited,
			ReferenceHolder<ASTNode, Object> holder) {
		List<ASTNode> arguments = visited.arguments();
		
		// Handle Files.newBufferedWriter(Path, Charset, OpenOption...) - replace charset if it's a known encoding
		// The charset is the second parameter
		if (arguments.size() >= 2) {
			ASTNode encodingArg = arguments.get(1);
			String encodingValue = getEncodingValue(encodingArg, visited);
			
			if (encodingValue != null && ENCODINGS.contains(encodingValue)) {
				NodeData nd = new NodeData(true, encodingArg, ENCODING_MAP.get(encodingValue));
				holder.put(visited, nd);
				operations.add(fixcore.rewrite(visited, cb, holder));
				return false;
			}
			// If we have 2+ arguments and second is a charset (even if not recognized), don't add another
			return false;
		}
		
		// Handle Files.newBufferedWriter(Path) - only 1 argument, add charset parameter
		// We need to insert the charset as the second parameter
		if (arguments.size() == 1) {
			// Files.newBufferedWriter(Path) uses UTF-8 by default since Java 8
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
		if ("StandardCharsets".equals(qualifier) || qualifier.endsWith(".StandardCharsets")) { //$NON-NLS-1$ //$NON-NLS-2$
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
		if ("StandardCharsets".equals(expression) || expression.endsWith(".StandardCharsets")) { //$NON-NLS-1$ //$NON-NLS-2$
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
			// Insert as second parameter (after Path, before any OpenOptions)
			List<ASTNode> arguments = visited.arguments();
			if (arguments.size() == 1) {
				listRewrite.insertLast(callToCharsetDefaultCharset, group);
			} else {
				// Insert after first parameter
				listRewrite.insertAfter(callToCharsetDefaultCharset, arguments.get(0), group);
			}
		}
		removeUnsupportedEncodingException(visited, group, rewrite, importRewriter);
	}

	@Override
	public String getPreview(boolean afterRefactoring, ChangeBehavior cb) {
		if (afterRefactoring) {
			return "Files.newBufferedWriter(path, " + cb.computeCharsetforPreview() + ");\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return "Files.newBufferedWriter(path);\n"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Files.newBufferedWriter(path)"; //$NON-NLS-1$
	}
}
