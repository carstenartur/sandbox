/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceEdits.Edit;

/** Adds NLS edits to the completed AST edit tree, including moved source ranges. */
public final class EncodingSourceRewrite {

	private static final String REPLACEMENTS= EncodingSourceRewrite.class.getName() + ".replacements"; //$NON-NLS-1$

	private record Replacements(CompilationUnitRewrite rewrite, Set<ASTNode> nodes) {
	}

	private EncodingSourceRewrite() {
	}

	public static void clear(CompilationUnit root) {
		root.setProperty(REPLACEMENTS, null);
	}

	public static void record(CompilationUnitRewrite cuRewrite, ASTNode argument) {
		Object stored= cuRewrite.getRoot().getProperty(REPLACEMENTS);
		Replacements replacements;
		if (stored instanceof Replacements previous && previous.rewrite() == cuRewrite) {
			replacements= previous;
		} else {
			replacements= new Replacements(cuRewrite, Collections.newSetFromMap(new IdentityHashMap<>()));
			cuRewrite.getRoot().setProperty(REPLACEMENTS, replacements);
		}
		replacements.nodes().add(argument);
	}

	public static void complete(CompilationUnit root, CompilationUnitChange change) throws JavaModelException {
		if (!(root.getProperty(REPLACEMENTS) instanceof Replacements replacements)) {
			return;
		}
		CompilationUnitRewrite cuRewrite= replacements.rewrite();
		String buffer= cuRewrite.getCu().getBuffer().getContents();
		Map<LineComment, List<Edit>> commentEdits= new LinkedHashMap<>();
		for (NLSLine line : scan(cuRewrite, buffer)) {
			int removed= 0;
			NLSElement[] elements= line.getElements();
			for (int index= 0; index < elements.length; index++) {
				NLSElement element= elements[index];
				boolean deleted= isRemoved(element.getPosition().getOffset(), element.getPosition().getLength(), replacements.nodes());
				if (deleted) {
					removed++;
				}
				if (!element.hasTag() || (!deleted && removed == 0)) {
					continue;
				}
				int tagStart= element.getTagPosition().getOffset();
				int tagLength= element.getTagPosition().getLength();
				if (isRemoved(tagStart, tagLength, replacements.nodes())) {
					continue;
				}
				LineComment comment= containingComment(root, tagStart, tagLength);
				String tag= deleted ? "" : NLSElement.createTagText(index + 1 - removed); //$NON-NLS-1$
				commentEdits.computeIfAbsent(comment, key -> new ArrayList<>())
						.add(new Edit(tagStart - comment.getStartPosition(), tagLength, tag));
			}
		}
		commentEdits.forEach((comment, tags) -> {
			int start= comment.getStartPosition();
			int end= start + comment.getLength();
			while (end > start && (buffer.charAt(end - 1) == '\n' || buffer.charAt(end - 1) == '\r')) {
				end--;
			}
			String text= EncodingSourceEdits.rewriteComment(buffer.substring(start, end), tags);
			if (text.isEmpty()) {
				while (start > 0 && EncodingSourceEdits.isHorizontalSpace(buffer.charAt(start - 1))) {
					start--;
				}
			}
			ReplaceEdit edit= new ReplaceEdit(start, end - start, text);
			// Inserting into the enclosing MoveSourceEdit applies the tag change to
			// the moved text too. Adding it only at the root would lose that change.
			TextEditUtil.insert(change.getEdit(), edit);
			change.addTextEditGroup(new TextEditGroup(change.getName(), edit));
		});
	}

	private static NLSLine[] scan(CompilationUnitRewrite cuRewrite, String buffer) throws JavaModelException {
		try {
			NLSLine[] lines= NLSScanner.scan(cuRewrite.getCu());
			if (!buffer.equals(cuRewrite.getCu().getBuffer().getContents())) {
				throw new IllegalArgumentException("Source changed while scanning encoding NLS tags"); //$NON-NLS-1$
			}
			return lines;
		} catch (InvalidInputException | BadLocationException exception) {
			throw new IllegalArgumentException("Cannot safely determine encoding NLS tag positions", exception); //$NON-NLS-1$
		}
	}

	private static boolean isRemoved(int offset, int length, Set<ASTNode> replacements) {
		return replacements.stream().anyMatch(node -> offset >= node.getStartPosition()
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
