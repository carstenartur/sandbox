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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Region;
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

	/** Finds candidate literals whose source positions and NLS tags the scanner accounts for. */
	public static List<StringLiteral> literalsWithUnambiguousTags(CompilationUnit root, ICompilationUnit unit,
			List<StringLiteral> candidates) throws JavaModelException {
		String buffer= unit.getBuffer().getContents();
		NLSLine[] lines;
		try {
			lines= scan(unit, buffer);
		} catch (IllegalArgumentException exception) {
			return List.of();
		}
		Map<Integer, List<LineComment>> comments= new HashMap<>();
		for (Object node : root.getCommentList()) {
			if (node instanceof LineComment comment) {
				int line= root.getLineNumber(comment.getStartPosition());
				comments.computeIfAbsent(line, key -> new ArrayList<>()).add(comment);
			}
		}
		Map<Integer, List<Region>> literalsByLine= stringLiteralPositions(root);
		Map<Integer, Integer> literalLengths= new HashMap<>();
		for (NLSLine line : lines) {
			int lineNumber= line.getLineNumber() + 1;
			List<LineComment> lineComments= comments.getOrDefault(lineNumber, List.of());
			NLSElement[] elements= line.getElements();
			if (lineComments.size() <= 1 && hasUnambiguousTags(buffer, elements,
					lineComments.isEmpty() ? null : lineComments.getFirst(), literalsByLine.getOrDefault(lineNumber, List.of()))) {
				for (NLSElement element : elements) {
					literalLengths.put(element.getPosition().getOffset(), element.getPosition().getLength());
				}
			}
		}
		return candidates.stream().filter(literal ->
				literalLengths.getOrDefault(literal.getStartPosition(), -1) == literal.getLength()).toList();
	}

	private static Map<Integer, List<Region>> stringLiteralPositions(CompilationUnit root) {
		Map<Integer, List<Region>> positions= new HashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(StringLiteral literal) {
				add(literal, literal.getStartPosition());
				return false;
			}

			@Override
			public boolean visit(TextBlock block) {
				add(block, block.getStartPosition() + block.getLength() - 1);
				return false;
			}

			@Override
			public boolean visit(NormalAnnotation annotation) {
				return false;
			}

			@Override
			public boolean visit(SingleMemberAnnotation annotation) {
				return false;
			}

			@Override
			public boolean visit(AnnotationTypeMemberDeclaration declaration) {
				return false;
			}

			private void add(ASTNode literal, int positionOnLine) {
				int line= root.getLineNumber(positionOnLine);
				positions.computeIfAbsent(line, key -> new ArrayList<>())
						.add(new Region(literal.getStartPosition(), literal.getLength()));
			}
		});
		return positions;
	}

	private static boolean hasUnambiguousTags(String buffer, NLSElement[] elements, LineComment comment,
			List<Region> literalPositions) {
		if (comment == null) {
			return true;
		}
		int commentStart= comment.getStartPosition();
		int commentEnd= commentStart + comment.getLength();
		String rawComment= buffer.substring(commentStart, commentEnd);
		if (rawComment.contains("\\u")) { //$NON-NLS-1$
			// Unicode escapes can shift the scanner's comment offsets or conceal a tag.
			return false;
		}
		if (!rawComment.contains(NLSElement.TAG_PREFIX)) {
			return true;
		}
		// Removing a tag and its leading space could join a backslash to Unicode escape digits.
		if (rawComment.indexOf('\\') >= 0) {
			return false;
		}
		// The tooling scanner and compiler can count different literals, for example in arrow-style switch rules.
		if (literalPositions.size() != elements.length) {
			return false;
		}
		int tagCount= 0;
		for (int i= 0; i < elements.length; i++) {
			NLSElement element= elements[i];
			if (!literalPositions.get(i).equals(element.getPosition())) {
				return false;
			}
			if (element.hasTag()) {
				int start= element.getTagPosition().getOffset();
				String tag= NLSElement.createTagText(i + 1);
				if (start < commentStart || start + tag.length() > commentEnd
						|| element.getTagPosition().getLength() != tag.length() || !buffer.startsWith(tag, start)) {
					return false;
				}
				tagCount++;
			}
		}
		// The NLS scanner retains one tag per literal and stops at a malformed tag.
		int sourceTagCount= 0;
		for (int offset= rawComment.indexOf(NLSElement.TAG_PREFIX); offset >= 0;
				offset= rawComment.indexOf(NLSElement.TAG_PREFIX, offset + NLSElement.TAG_PREFIX_LENGTH)) {
			sourceTagCount++;
		}
		return tagCount == sourceTagCount;
	}

	public static void complete(CompilationUnit root, CompilationUnitChange change) throws JavaModelException {
		if (!(root.getProperty(REPLACEMENTS) instanceof Replacements replacements)) {
			return;
		}
		CompilationUnitRewrite cuRewrite= replacements.rewrite();
		String buffer= cuRewrite.getCu().getBuffer().getContents();
		Map<LineComment, List<Edit>> commentEdits= new LinkedHashMap<>();
		for (NLSLine line : scan(cuRewrite.getCu(), buffer)) {
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

	private static NLSLine[] scan(ICompilationUnit unit, String buffer) throws JavaModelException {
		try {
			NLSLine[] lines= NLSScanner.scan(unit);
			if (!buffer.equals(unit.getBuffer().getContents())) {
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
