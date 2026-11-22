/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditUtil;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.VariableMapping;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;

/** Restores the original NLS intent after AST-generated arguments change physical lines. */
public final class MethodReuseNlsFix {

	private static final String STATE= MethodReuseNlsFix.class.getName();
	private record Argument(ITrackedNodePosition position, List<NLSElement> literals) { }
	private record Explanation(ITrackedNodePosition position, String text) { }
	private record State(String source, List<NLSElement> literals, List<Argument> arguments,
			List<Explanation> explanations) { }

	private MethodReuseNlsFix() { }

	public static ICleanUpFix create(String name, CompilationUnit root, CompilationUnitRewriteOperation[] operations) {
		return monitor -> {
			try {
				ICompilationUnit unit= (ICompilationUnit) root.getJavaElement();
				State state= new State(unit.getSource(), literals(NLSScanner.scan(unit)), new ArrayList<>(), new ArrayList<>());
				root.setProperty(STATE, state);
				CompilationUnitChange change= new NlsAwareCleanUpFix(name, root, operations).createChange(monitor);
				if (state.arguments().isEmpty() && state.explanations().isEmpty()) return change;
				try {
					return complete(change, state);
				} finally {
					change.dispose();
				}
			} catch (InvalidInputException | BadLocationException exception) {
				throw new CoreException(Status.error("Cannot preserve method-reuse NLS markers", exception)); //$NON-NLS-1$
			} finally {
				root.setProperty(STATE, null);
			}
		};
	}

	static void record(CompilationUnitRewrite rewrite, MethodDeclaration target, MethodInvocation call,
			List<Statement> statements, VariableMapping mapping) {
		if (!(rewrite.getRoot().getProperty(STATE) instanceof State state)) return;
		List<Expression> copied= new ArrayList<>();
		for (int index= 0; index < target.parameters().size(); index++) {
			SingleVariableDeclaration parameter= (SingleVariableDeclaration) target.parameters().get(index);
			Expression original= mapping.getCandidateExpression(parameter.getName().getIdentifier());
			if (original == null) continue;
			copied.add(original);
			List<NLSElement> literals= state.literals().stream().filter(element -> contains(original,
					element.getPosition().getOffset(), element.getPosition().getLength())).toList();
			if (!literals.isEmpty()) state.arguments().add(new Argument(
					rewrite.getASTRewrite().track((ASTNode) call.arguments().get(index)), literals));
		}
		for (Object candidate : rewrite.getRoot().getCommentList()) {
			if (!(candidate instanceof LineComment comment)
					|| statements.stream().noneMatch(statement -> contains(statement, comment.getStartPosition(), comment.getLength()))
					|| copied.stream().anyMatch(expression -> contains(expression, comment.getStartPosition(), comment.getLength()))) continue;
			List<NLSElement> tags= state.literals().stream().filter(element -> element.hasTag()
					&& contains(comment, element.getTagPosition().getOffset(), element.getTagPosition().getLength()))
					.sorted(Comparator.comparingInt((NLSElement element) -> element.getTagPosition().getOffset()).reversed()).toList();
			if (tags.isEmpty()) continue;
			StringBuilder text= new StringBuilder(state.source().substring(comment.getStartPosition(),
					comment.getStartPosition() + comment.getLength()));
			for (NLSElement tag : tags) {
				int start= tag.getTagPosition().getOffset() - comment.getStartPosition();
				text.delete(start, start + tag.getTagPosition().getLength());
			}
			String explanation= text.toString().strip();
			if (explanation.startsWith("//")) explanation= explanation.substring(2).strip(); //$NON-NLS-1$
			if (!explanation.isEmpty()) state.explanations().add(new Explanation(rewrite.getASTRewrite().track(call), explanation));
		}
	}

	private static CompilationUnitChange complete(CompilationUnitChange change, State state)
			throws CoreException, BadLocationException, InvalidInputException {
		Document document= new Document(state.source());
		MultiTextEdit preview= new MultiTextEdit();
		preview.addChild(change.getEdit());
		List<RangeMarker> originalTags= new ArrayList<>();
		for (NLSElement element : state.literals()) {
			if (!element.hasTag()) continue;
			RangeMarker marker= new RangeMarker(element.getPosition().getOffset(), element.getPosition().getLength());
			TextEditUtil.insert(preview, marker);
			originalTags.add(marker);
		}
		// Apply this disposable edit tree, rather than a copy, so JDT updates the
		// argument positions. Every createChange invocation builds fresh tracking data.
		preview.apply(document, TextEdit.UPDATE_REGIONS);
		List<NLSElement> generated;
		ICompilationUnit trial= change.getCompilationUnit().getWorkingCopy(new WorkingCopyOwner() { }, null);
		try {
			trial.getBuffer().setContents(document.get());
			generated= literals(NLSScanner.scan(trial));
		} finally {
			trial.discardWorkingCopy();
		}
		Set<Integer> tagged= new HashSet<>();
		Map<Integer, Integer> literalLengths= new HashMap<>();
		generated.forEach(element -> literalLengths.put(element.getPosition().getOffset(), element.getPosition().getLength()));
		for (RangeMarker marker : originalTags) {
			if (!marker.isDeleted() && literalLengths.getOrDefault(marker.getOffset(), -1) == marker.getLength()) {
				tagged.add(marker.getOffset());
			}
		}
		for (Argument argument : state.arguments()) {
			List<NLSElement> moved= generated.stream().filter(element -> contains(argument.position().getStartPosition(),
					argument.position().getLength(), element.getPosition().getOffset(), element.getPosition().getLength())).toList();
			if (moved.size() != argument.literals().size()) {
				throw new CoreException(Status.error("Cannot track relocated method-reuse string literals")); //$NON-NLS-1$
			}
			for (int index= 0; index < moved.size(); index++) {
				if (argument.literals().get(index).hasTag()) tagged.add(moved.get(index).getPosition().getOffset());
			}
		}
		MultiTextEdit markers= new MultiTextEdit();
		Map<Integer, StringBuilder> suffixes= new LinkedHashMap<>();
		for (NLSElement element : generated) {
			boolean wanted= tagged.contains(element.getPosition().getOffset());
			if (wanted && !element.hasTag()) append(suffixes, lineEnd(document,
					element.getPosition().getOffset() + element.getPosition().getLength() - 1), ' ' + element.getTagText());
			else if (!wanted && element.hasTag()) {
				// Keep a comment introducer if an old ordinal now identifies an untagged
				// literal; explanatory text after that marker must remain a comment.
				markers.addChild(new ReplaceEdit(element.getTagPosition().getOffset(), element.getTagPosition().getLength(), "//")); //$NON-NLS-1$
			}
		}
		for (Explanation explanation : state.explanations()) append(suffixes,
				lineEnd(document, explanation.position().getStartPosition() + explanation.position().getLength() - 1),
				" // " + explanation.text()); //$NON-NLS-1$
		suffixes.forEach((offset, text) -> markers.addChild(new InsertEdit(offset, text.toString())));
		markers.apply(document);
		String result= document.get();
		int start= 0;
		while (start < state.source().length() && start < result.length() && state.source().charAt(start) == result.charAt(start)) start++;
		int oldEnd= state.source().length(), newEnd= result.length();
		while (oldEnd > start && newEnd > start && state.source().charAt(oldEnd - 1) == result.charAt(newEnd - 1)) { oldEnd--; newEnd--; }
		ReplaceEdit edit= new ReplaceEdit(start, oldEnd - start, result.substring(start, newEnd));
		CompilationUnitChange completed= new CompilationUnitChange(change.getName(), change.getCompilationUnit());
		completed.setSaveMode(change.getSaveMode());
		completed.setEdit(edit);
		completed.addTextEditGroup(new TextEditGroup(change.getName(), edit));
		return completed;
	}

	private static List<NLSElement> literals(NLSLine[] lines) {
		return Arrays.stream(lines).flatMap(line -> Arrays.stream(line.getElements())).toList();
	}

	private static int lineEnd(Document document, int offset) throws BadLocationException {
		var line= document.getLineInformationOfOffset(offset);
		return line.getOffset() + line.getLength();
	}

	private static void append(Map<Integer, StringBuilder> suffixes, int offset, String text) {
		suffixes.computeIfAbsent(offset, key -> new StringBuilder()).append(text);
	}

	private static boolean contains(ASTNode node, int start, int length) {
		return contains(node.getStartPosition(), node.getLength(), start, length);
	}

	private static boolean contains(int outerStart, int outerLength, int start, int length) {
		return outerStart >= 0 && start >= outerStart && start + length <= outerStart + outerLength;
	}
}
