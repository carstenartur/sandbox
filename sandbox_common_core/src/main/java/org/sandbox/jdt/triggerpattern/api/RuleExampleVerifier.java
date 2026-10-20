/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite.Example;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintProgramParser;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;

/**
 * Replays examples with the shared matcher, guards and binding substitution.
 * This bounded verifier supports ordinary text replacements. Import edits,
 * structured actions, embedded Java and plan-aware rules require the native
 * project preview and are reported as unsupported, never as qualified here.
 * The supplied parser must use the caller's actual project/build classpath.
 */
public final class RuleExampleVerifier {

	@FunctionalInterface
	public interface SourceParser {
		CompilationUnit parse(String unitName, String source);
	}

	public enum Outcome { PASS, FAIL, UNKNOWN }

	public record Result(String name, Outcome outcome, String message, String actualSource) { }

	public record Report(String ruleSha256, String suiteSha256, List<Result> results, String diagnostic) {
		public Report {
			results = List.copyOf(results);
		}

		/** Passing examples are evidence for this exact input, not a semantic proof. */
		public boolean passed() {
			return diagnostic.isEmpty() && !results.isEmpty()
					&& results.stream().allMatch(result -> result.outcome() == Outcome.PASS);
		}
	}

	private final Map<String, String> compilerOptions;
	private final SourceParser parser;

	public RuleExampleVerifier(Map<String, String> compilerOptions, SourceParser parser) {
		this.compilerOptions = Map.copyOf(compilerOptions);
		this.parser = Objects.requireNonNull(parser, "parser");
	}

	public Report verify(String dsl, RuleExampleSuite suite) {
		Objects.requireNonNull(dsl, "dsl");
		Objects.requireNonNull(suite, "suite");
		checkCanceled();
		HintFile hint;
		try {
			hint = new HintProgramParser().parseHintFile(dsl);
			if (hint.getRules().isEmpty() || new PatternIndex(hint.getRules()).size() != hint.getRules().size()) {
				return unsupported(dsl, suite, "The complete rule document must contain valid source patterns");
			}
			if (!hint.getIncludes().isEmpty() || !hint.getEmbeddedJavaBlocks().isEmpty()
					|| !hint.getSuppressWarnings().isEmpty() || !hint.getTreeKindNodeTypes().isEmpty()
					|| HintPlanRequirement.fromContent(dsl).isPresent()
					|| RuleAuthoringDocument.containsJavaTextBlock(dsl)
					|| hint.getRules().stream().anyMatch(rule -> rule.hasImportDirective()
							|| rule.alternatives().stream().anyMatch(alternative -> alternative.isEmbeddedFix()
									|| alternative.hasStructuredActions() || !alternative.hasTextReplacement()))) {
				return unsupported(dsl, suite,
						"Example replay supports self-contained plain text rules; includes, imports, host filters and other rule kinds require native project verification");
			}
			if (hint.getMinJavaVersion() > 0) {
				String level = compilerOptions.getOrDefault(JavaCore.COMPILER_SOURCE, "");
				int sourceVersion = Integer.parseInt(level.startsWith("1.") ? level.substring(2) : level);
				if (sourceVersion < hint.getMinJavaVersion()) {
					return unsupported(dsl, suite, "The current source version is below this rule's minJavaVersion");
				}
			}
		} catch (HintParseException | IllegalArgumentException exception) {
			return unsupported(dsl, suite, "Rule language check failed: " + exception.getMessage());
		}
		String diagnostic = suite.examples().stream().anyMatch(Example::expectChange)
				&& suite.examples().stream().anyMatch(example -> !example.expectChange()) ? ""
				: "Add at least one positive example and one counterexample before qualifying a rule";
		List<Result> results = new ArrayList<>();
		for (Example example : suite.examples()) {
			checkCanceled();
			try {
				results.add(verifyExample(hint, example));
			} catch (OperationCanceledException exception) {
				throw exception;
			} catch (RuntimeException exception) {
				results.add(new Result(example.name(), Outcome.UNKNOWN,
						"Example could not be checked: " + exception.getMessage(), example.before()));
			}
		}
		return new Report(hash(dsl), hash(suite.toJson()), results, diagnostic);
	}

	private Result verifyExample(HintFile hint, Example example) {
		CompilationUnit before = parser.parse(example.unitName(), example.before());
		String problem = compilationProblem(before);
		if (!problem.isEmpty()) {
			return new Result(example.name(), Outcome.UNKNOWN, "Before source: " + problem, example.before());
		}
		Rewrite first = rewrite(hint, before, example.before());
		if (!first.diagnostic().isEmpty()) {
			return new Result(example.name(), Outcome.UNKNOWN, first.diagnostic(), first.source());
		}
		if (!first.source().equals(example.after())) {
			return new Result(example.name(), Outcome.FAIL,
					example.expectChange() ? "Actual output differs from the complete expected source"
						: "Counterexample changed", first.source());
		}
		CompilationUnit after = parser.parse(example.unitName(), first.source());
		problem = compilationProblem(after);
		if (!problem.isEmpty()) {
			return new Result(example.name(), Outcome.FAIL, "After source: " + problem, first.source());
		}
		Rewrite second = rewrite(hint, after, first.source());
		if (!second.diagnostic().isEmpty()) {
			return new Result(example.name(), Outcome.UNKNOWN, second.diagnostic(), first.source());
		}
		if (!second.source().equals(first.source())) {
			return new Result(example.name(), Outcome.FAIL, "A second application changes the output again", first.source());
		}
		return new Result(example.name(), Outcome.PASS,
				example.expectChange() ? "Exact expected change; source compiles; second application unchanged"
						: "Counterexample compiles and stays unchanged", first.source());
	}

	private record Rewrite(String source, String diagnostic) { }

	private Rewrite rewrite(HintFile hint, CompilationUnit unit, String source) {
		checkCanceled();
		List<TransformationResult> results = new BatchTransformationProcessor(hint)
				.process(unit, compilerOptions, SemanticRewritePlan.empty());
		if (results.stream().anyMatch(TransformationResult::isSemanticUnknown)) {
			return new Rewrite(source, "A match has unresolved semantic requirements");
		}
		List<TransformationResult> edits = results.stream().filter(TransformationResult::hasReplacement)
				.sorted(Comparator.comparingInt((TransformationResult result) -> result.match().getOffset()).reversed())
				.toList();
		StringBuilder output = new StringBuilder(source);
		int previousStart = source.length();
		for (TransformationResult edit : edits) {
			int start = edit.match().getOffset();
			int end = start + edit.match().getLength();
			if (start < 0 || end > previousStart || end <= start) {
				return new Rewrite(source, "Overlapping or invalid edits require the native project preview");
			}
			output.replace(start, end, edit.replacement());
			previousStart = start;
		}
		return new Rewrite(output.toString(), "");
	}

	private static String compilationProblem(CompilationUnit unit) {
		if (unit == null || !unit.getAST().hasResolvedBindings()) {
			return "Resolved project or build bindings are required";
		}
		var error = Arrays.stream(unit.getProblems()).filter(problem -> problem.isError()).findFirst();
		if (error.isPresent()) {
			return error.get().getMessage();
		}
		boolean[] recovered = { false };
		unit.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				recovered[0] |= (node.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0;
			}
		});
		return recovered[0] ? "Recovered or malformed Java syntax cannot qualify an example" : "";
	}

	private static Report unsupported(String dsl, RuleExampleSuite suite, String diagnostic) {
		return new Report(hash(dsl), hash(suite.toJson()), suite.examples().stream()
				.map(example -> new Result(example.name(), Outcome.UNKNOWN, diagnostic, example.before())).toList(), diagnostic);
	}

	private static String hash(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	static void checkCanceled() {
		if (Thread.currentThread().isInterrupted()) {
			throw new OperationCanceledException();
		}
		SemanticRewritePlanContext.checkCanceled();
	}
}
