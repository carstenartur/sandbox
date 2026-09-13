/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.sandbox.jdt.internal.corext.fix.helper.EncodingSourceEdits.Edit;

/** Preserves statement source while updating tags for exactly the removed literals. */
final class EncodingSourceRewrite {

	private static final String REPLACEMENTS= EncodingSourceRewrite.class.getName() + ".replacements"; //$NON-NLS-1$
	private static final String SCAN= EncodingSourceRewrite.class.getName() + ".scan"; //$NON-NLS-1$

	private record Replacements(ASTRewrite rewrite, Map<ASTNode, String> values) {
	}

	private record Scan(ASTRewrite rewrite, String source, NLSLine[] lines) {
	}

	private EncodingSourceRewrite() {
	}

	static void record(CompilationUnitRewrite cuRewrite, ASTNode statement, ASTNode argument, ASTNode replacement) {
		replacements(cuRewrite, statement).put(argument, replacement.toString());
	}

	static String source(CompilationUnitRewrite cuRewrite, ASTNode statement) throws JavaModelException {
		String buffer= cuRewrite.getCu().getBuffer().getContents();
		CompilationUnit root= cuRewrite.getRoot();
		int start= root.getExtendedStartPosition(statement);
		int length= root.getExtendedLength(statement);
		Map<ASTNode, String> replacements= replacements(cuRewrite, statement);
		List<Edit> edits= new ArrayList<>();
		replacements.forEach((node, text) -> edits.add(new Edit(node.getStartPosition(), node.getLength(), text)));

		if (!replacements.isEmpty()) {
			Map<LineComment, List<Edit>> commentEdits= new LinkedHashMap<>();
			for (NLSLine line : scan(cuRewrite, buffer)) {
				int removed= 0;
				NLSElement[] elements= line.getElements();
				for (int index= 0; index < elements.length; index++) {
					NLSElement element= elements[index];
					boolean deleted= isRemoved(element.getPosition().getOffset(), element.getPosition().getLength(), replacements);
					if (deleted) {
						removed++;
					}
					if (!element.hasTag() || (!deleted && removed == 0)) {
						continue;
					}
					int tagStart= element.getTagPosition().getOffset();
					int tagLength= element.getTagPosition().getLength();
					if (isRemoved(tagStart, tagLength, replacements)) {
						continue;
					}
					LineComment comment= containingComment(root, tagStart, tagLength);
					String tag= deleted ? "" : NLSElement.createTagText(index + 1 - removed); //$NON-NLS-1$
					commentEdits.computeIfAbsent(comment, key -> new ArrayList<>())
							.add(new Edit(tagStart - comment.getStartPosition(), tagLength, tag));
				}
			}
			commentEdits.forEach((comment, tags) -> {
				int commentStart= comment.getStartPosition();
				int commentEnd= commentStart + comment.getLength();
				// Keep the original line separator even when the comment becomes empty.
				while (commentEnd > commentStart && (buffer.charAt(commentEnd - 1) == '\n'
						|| buffer.charAt(commentEnd - 1) == '\r')) {
					commentEnd--;
				}
				String text= EncodingSourceEdits.rewriteComment(buffer.substring(commentStart, commentEnd), tags);
				if (text.isEmpty()) {
					while (commentStart > start && EncodingSourceEdits.isHorizontalSpace(buffer.charAt(commentStart - 1))) {
						commentStart--;
					}
				}
				edits.add(new Edit(commentStart, commentEnd - commentStart, text));
			});
		}
		String source= EncodingSourceEdits.apply(buffer, start, length, edits);
		return EncodingSourceEdits.relativeIndent(buffer, start, source);
	}

	private static Map<ASTNode, String> replacements(CompilationUnitRewrite cuRewrite, ASTNode statement) {
		Object stored= statement.getProperty(REPLACEMENTS);
		if (stored instanceof Replacements replacements && replacements.rewrite() == cuRewrite.getASTRewrite()) {
			return replacements.values();
		}
		Map<ASTNode, String> values= new IdentityHashMap<>();
		statement.setProperty(REPLACEMENTS, new Replacements(cuRewrite.getASTRewrite(), values));
		return values;
	}

	private static NLSLine[] scan(CompilationUnitRewrite cuRewrite, String buffer) throws JavaModelException {
		Object stored= cuRewrite.getRoot().getProperty(SCAN);
		if (stored instanceof Scan scan && scan.rewrite() == cuRewrite.getASTRewrite() && scan.source().equals(buffer)) {
			return scan.lines();
		}
		try {
			NLSLine[] lines= NLSScanner.scan(cuRewrite.getCu());
			if (!buffer.equals(cuRewrite.getCu().getBuffer().getContents())) {
				throw new IllegalArgumentException("Source changed while scanning encoding NLS tags"); //$NON-NLS-1$
			}
			cuRewrite.getRoot().setProperty(SCAN, new Scan(cuRewrite.getASTRewrite(), buffer, lines));
			return lines;
		} catch (InvalidInputException | BadLocationException exception) {
			throw new IllegalArgumentException("Cannot safely determine encoding NLS tag positions", exception); //$NON-NLS-1$
		}
	}

	private static boolean isRemoved(int offset, int length, Map<ASTNode, String> replacements) {
		return replacements.keySet().stream().anyMatch(node -> offset >= node.getStartPosition()
				&& offset + length <= node.getStartPosition() + node.getLength());
	}

	private static LineComment containingComment(CompilationUnit root, int offset, int length) {
		for (Object candidate : root.getCommentList()) {
			if (candidate instanceof LineComment comment && offset >= comment.getStartPosition()
					&& offset + length <= comment.getStartPosition() + comment.getLength()) {
				return comment;
			}
		}
		throw new IllegalArgumentException("NLS tag is not inside a source line comment"); //$NON-NLS-1$
	}
}
