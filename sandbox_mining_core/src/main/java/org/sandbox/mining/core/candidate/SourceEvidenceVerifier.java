/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.mining.core.candidate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffAlgorithm.SupportedAlgorithm;
import org.eclipse.jgit.diff.Sequence;
import org.eclipse.jgit.diff.SequenceComparator;
import org.eclipse.jgit.patch.Patch;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.llm.SourceEvidence;

/** Local consistency gate for the original source evidence; not a semantic proof. */
public final class SourceEvidenceVerifier {
	public record Result(SourceEvidence evidence, String problem) {}

	private SourceEvidenceVerifier() {}

	/** Checks changed spans and replacement literals; returns the locally copied hunk. */
	public static Result verify(SourceEvidence evidence, String dsl, String diff) {
		if (evidence == null || !shortText(evidence.path()) || !evidence.path().endsWith(".java")
				|| !shortText(evidence.before()) || !shortText(evidence.after())) {
			return failure("Missing or invalid source evidence (Java path and spans up to 512 characters)");
		}
		if (diff == null || diff.isBlank()) {
			return failure("Original changed hunk is missing");
		}
		try {
			List<Token> before = tokens(evidence.before());
			List<Token> after = tokens(evidence.after());
			if (before.isEmpty() || after.isEmpty() || before.equals(after)) {
				return failure("Source evidence must identify a changed Java expression or statement");
			}
			var rules = new HintFileParser().parse(dsl).getRules();
			if (rules.size() != 1 || rules.get(0).alternatives().size() != 1) {
				return failure("Source evidence requires one textual rewrite alternative");
			}
			var alternative = rules.get(0).alternatives().get(0);
			if (!alternative.hasTextReplacement() || alternative.hasStructuredActions() || alternative.isEmbeddedFix()) {
				return failure("Source evidence requires a textual replacement");
			}
			for (Token token : tokens(alternative.replacementPattern())) {
				if (token.literal() && !after.contains(token)) {
					return failure("Replacement literal is absent from the original after span");
				}
			}
			byte[] bytes = diff.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
			Patch patch = new Patch();
			patch.parse(bytes, 0, bytes.length);
			if (!patch.getErrors().isEmpty()) {
				return failure("Original diff is malformed or incomplete");
			}
			for (var file : patch.getFiles()) {
				if (!evidence.path().equals(file.getNewPath())) {
					continue;
				}
				for (var hunk : file.getHunks()) {
					String text = new String(bytes, hunk.getStartOffset(),
							hunk.getEndOffset() - hunk.getStartOffset(), StandardCharsets.UTF_8);
					if (matchesEdit(text, before, after)) {
						String header = new String(bytes, file.getStartOffset(),
								file.getHunks().get(0).getStartOffset() - file.getStartOffset(), StandardCharsets.UTF_8);
						return new Result(new SourceEvidence(evidence.path(), evidence.before(), evidence.after(), header + text), null);
					}
				}
			}
			return failure("Source spans do not occur in the same changed block of the named file");
		} catch (Exception e) {
			return failure("Cannot check source evidence: " + e.getClass().getSimpleName());
		}
	}

	/**
	 * Returns whether any independent edit block supplies matching token evidence.
	 * Incomplete lexical context disqualifies only that block, allowing a later match.
	 */
	private static boolean matchesEdit(String hunk, List<Token> before, List<Token> after) throws InvalidInputException {
		StringBuilder removed = new StringBuilder();
		StringBuilder added = new StringBuilder();
		List<Edit> edits = new ArrayList<>();
		int oldStart = -1, newStart = -1;
		for (String line : (hunk + "\n ").split("\n", -1)) {
			if (line.startsWith("\\") || line.startsWith("@@")) {
				continue;
			}
			if ((line.startsWith("-") || line.startsWith("+")) && oldStart < 0) {
				oldStart = removed.length();
				newStart = added.length();
			}
			if (line.startsWith("-")) {
				removed.append(line.substring(1)).append('\n');
			} else if (line.startsWith("+")) {
				added.append(line.substring(1)).append('\n');
			} else {
				if (oldStart >= 0) {
					edits.add(new Edit(oldStart, removed.length(), newStart, added.length()));
					oldStart = -1;
				}
				if (line.startsWith(" ")) {
					removed.append(line.substring(1)).append('\n');
					added.append(line.substring(1)).append('\n');
				}
			}
		}
		// Scan with visible hunk context so changed text inside comments is not Java evidence.
		for (Edit edit : edits) {
			try {
				var oldTokens = new TokenSequence(tokens(removed.toString(), edit.oldStart(), edit.oldEnd()));
				var newTokens = new TokenSequence(tokens(added.toString(), edit.newStart(), edit.newEnd()));
				for (var change : DiffAlgorithm.getAlgorithm(SupportedAlgorithm.HISTOGRAM).diff(COMPARATOR, oldTokens, newTokens)) {
					if (changedOccurrence(oldTokens.values, before, change.getBeginA(), change.getEndA())
							&& changedOccurrence(newTokens.values, after, change.getBeginB(), change.getEndB())) {
						return true;
					}
				}
			} catch (InvalidInputException e) {
				// An edit boundary can fall inside a comment. Reject this block as evidence, then continue:
				// a later block may have enough visible context to yield valid Java tokens.
			}
		}
		return false;
	}

	private record Edit(int oldStart, int oldEnd, int newStart, int newEnd) {}

	private static boolean changedOccurrence(List<Token> tokens, List<Token> quote, int start, int end) {
		for (int i = 0; i + quote.size() <= tokens.size(); i++) {
			int limit = i + quote.size();
			boolean overlaps = start == end ? i <= start && start <= limit : i < end && start < limit;
			if (overlaps && tokens.subList(i, limit).equals(quote)) {
				return true;
			}
		}
		return false;
	}

	private static final class TokenSequence extends Sequence {
		private final List<Token> values;
		private TokenSequence(List<Token> values) { this.values = List.copyOf(values); }
		@Override public int size() { return values.size(); }
	}

	private static final SequenceComparator<TokenSequence> COMPARATOR = new SequenceComparator<>() {
		@Override public boolean equals(TokenSequence a, int ai, TokenSequence b, int bi) {
			return a.values.get(ai).equals(b.values.get(bi));
		}
		@Override public int hash(TokenSequence sequence, int index) {
			return sequence.values.get(index).hashCode();
		}
	};

	private static List<Token> tokens(String source) throws InvalidInputException {
		return tokens(source, 0, source.length());
	}

	private static List<Token> tokens(String source, int start, int end) throws InvalidInputException {
		if (start >= end) {
			return List.of();
		}
		var scanner = ToolFactory.createScanner(false, false, false, JavaCore.VERSION_25);
		scanner.setSource(source.substring(0, end).toCharArray());
		List<Token> result = new ArrayList<>();
		for (int kind = scanner.getNextToken(); kind != ITerminalSymbols.TokenNameEOF; kind = scanner.getNextToken()) {
			if (scanner.getCurrentTokenStartPosition() >= start && scanner.getCurrentTokenEndPosition() < end) {
				result.add(new Token(kind, new String(scanner.getCurrentTokenSource())));
			}
		}
		return result;
	}

	private static boolean shortText(String text) {
		return text != null && !text.isBlank() && text.length() <= 512;
	}

	private static Result failure(String problem) {
		return new Result(null, problem);
	}

	private record Token(int kind, String text) {
		boolean literal() {
			return switch (kind) {
			case ITerminalSymbols.TokenNameStringLiteral, ITerminalSymbols.TokenNameCharacterLiteral,
				ITerminalSymbols.TokenNameTextBlock, ITerminalSymbols.TokenNameIntegerLiteral,
				ITerminalSymbols.TokenNameLongLiteral, ITerminalSymbols.TokenNameFloatingPointLiteral,
				ITerminalSymbols.TokenNameDoubleLiteral, ITerminalSymbols.TokenNametrue,
				ITerminalSymbols.TokenNamefalse, ITerminalSymbols.TokenNamenull -> true;
			default -> false;
			};
		}
	}
}
