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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite.Example;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintProgramParser;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument;

/**
 * Replays examples with the shared matcher, guards and binding substitution.
 * This bounded verifier uses JDT AST rewrites for ordinary expressions and
 * single statements. Comments, structural rules, imports and native cleanup
 * adaptations require the project preview and are reported as unsupported.
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
			if (hint.getRules().stream().anyMatch(rule -> !switch (rule.sourcePattern().getKind()) {
				case EXPRESSION, METHOD_CALL, CONSTRUCTOR, STATEMENT, DECLARATION -> true;
				default -> false;
			})) {
				return unsupported(dsl, suite, "Structural, method, field and type patterns require native project verification");
			}
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
		ASTRewrite rewrite = ASTRewrite.create(unit.getAST());
		int previousStart = source.length();
		for (TransformationResult edit : edits) {
			int start = edit.match().getOffset();
			int end = start + edit.match().getLength();
			if (start < 0 || end > previousStart || end <= start) {
				return new Rewrite(source, "Overlapping or invalid edits require the native project preview");
			}
			if (unit.getCommentList().stream().anyMatch(value -> {
				ASTNode comment = (ASTNode) value;
				return comment.getStartPosition() < end && comment.getStartPosition() + comment.getLength() > start;
			})) {
				return new Rewrite(source, "Comments inside a match require native project verification");
			}
			ASTNode original = edit.match().getMatchedNode();
			if (original instanceof VariableDeclarationStatement && edit.replacement().contains("$widestType")) {
				return new Rewrite(source, "Type-widening replacements require native project verification");
			}
			ASTNode replacement = parseReplacement(original, edit.replacement());
			if (replacement == null) {
				return new Rewrite(source, "Only complete comment-free expressions and single-statement replacements can be replayed");
			}
			if (original.getParent() instanceof Expression && !(original.getParent() instanceof ParenthesizedExpression)
					&& replacement instanceof Expression && !isPrimaryExpression(replacement)) {
				return new Rewrite(source, "Compound replacements inside expressions need explicit parentheses and native project verification");
			}
			if (requiresNativeAdaptation(original, replacement)) {
				return new Rewrite(source, "String-literal removal and charset cleanup adaptations require native project verification");
			}
			rewrite.replace(original, ASTNode.copySubtree(unit.getAST(), replacement), null);
			previousStart = start;
		}
		Document output = new Document(source);
		try {
			rewrite.rewriteAST(output, compilerOptions).apply(output);
			return new Rewrite(output.get(), "");
		} catch (BadLocationException exception) {
			return new Rewrite(source, "AST edits could not be applied: " + exception.getMessage());
		}
	}

	/** Mirrors the native backend's strict syntax boundary without importing its host lifecycle. */
	private ASTNode parseReplacement(ASTNode original, String replacement) {
		ASTParser syntax = ASTParser.newParser(AST.getJLSLatest());
		syntax.setKind(ASTParser.K_COMPILATION_UNIT);
		String body = original instanceof Expression ? "return " + replacement + ";" : replacement;
		syntax.setSource(("class __Hint { void __hint() {\n" + body + "\n} }").toCharArray());
		syntax.setCompilerOptions(compilerOptions);
		CompilationUnit parsed = (CompilationUnit) syntax.createAST(null);
		if (!syntaxProblem(parsed).isEmpty() || !parsed.getCommentList().isEmpty() || parsed.types().size() != 1
				|| !(parsed.types().getFirst() instanceof TypeDeclaration type)
				|| type.bodyDeclarations().size() != 1 || type.getMethods().length != 1
				|| type.getMethods()[0].getBody().statements().size() != 1) {
			return null;
		}
		Statement statement = (Statement) type.getMethods()[0].getBody().statements().getFirst();
		if (original instanceof Expression) {
			return statement instanceof ReturnStatement returned ? returned.getExpression() : null;
		}
		return original instanceof Statement ? statement : null;
	}

	private static boolean isPrimaryExpression(ASTNode node) {
		return switch (node.getNodeType()) {
			case ASTNode.NUMBER_LITERAL, ASTNode.STRING_LITERAL, ASTNode.BOOLEAN_LITERAL, ASTNode.CHARACTER_LITERAL,
					ASTNode.NULL_LITERAL, ASTNode.SIMPLE_NAME, ASTNode.QUALIFIED_NAME, ASTNode.FIELD_ACCESS,
					ASTNode.SUPER_FIELD_ACCESS, ASTNode.METHOD_INVOCATION, ASTNode.SUPER_METHOD_INVOCATION,
					ASTNode.CLASS_INSTANCE_CREATION, ASTNode.ARRAY_ACCESS, ASTNode.THIS_EXPRESSION,
					ASTNode.TYPE_LITERAL, ASTNode.PARENTHESIZED_EXPRESSION -> true;
			default -> false;
		};
	}

	private static boolean requiresNativeAdaptation(ASTNode original, ASTNode replacement) {
		if (stringLiteralCount(original) > stringLiteralCount(replacement)) {
			return true;
		}
		if (original instanceof ClassInstanceCreation creation && creation.resolveConstructorBinding() != null) {
			String owner = creation.resolveConstructorBinding().getDeclaringClass().getQualifiedName();
			if (Set.of("java.util.Scanner", "java.util.Formatter").contains(owner)) {
				return true;
			}
		}
		List<?> arguments = original instanceof MethodInvocation call ? call.arguments()
				: original instanceof SuperMethodInvocation call ? call.arguments()
				: original instanceof ClassInstanceCreation creation ? creation.arguments() : List.of();
		for (Object value : arguments) {
			ASTNode argument = (ASTNode) value;
			while (argument instanceof ParenthesizedExpression parentheses) {
				argument = parentheses.getExpression();
			}
			if (argument instanceof StringLiteral literal
					&& Set.of("UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "US-ASCII", "ISO-8859-1")
							.contains(literal.getLiteralValue().toUpperCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private static int stringLiteralCount(ASTNode node) {
		int[] count = { 0 };
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(StringLiteral literal) { count[0]++; return false; }
		});
		return count[0];
	}

	private static String compilationProblem(CompilationUnit unit) {
		if (unit == null || !unit.getAST().hasResolvedBindings()) {
			return "Resolved project or build bindings are required";
		}
		return syntaxProblem(unit);
	}

	private static String syntaxProblem(CompilationUnit unit) {
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
