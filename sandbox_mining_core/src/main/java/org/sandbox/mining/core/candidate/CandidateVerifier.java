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
package org.sandbox.mining.core.candidate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;
import org.sandbox.jdt.triggerpattern.internal.DslValidator;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Deterministically verifies a staged candidate without generating Java source.
 *
 * <p>The verifier parses the DSL and examples, applies exactly one replacement
 * to the positive example by AST match offset/length, compares the complete
 * transformed source with the expected after example, and proves that the
 * negative example does not match.</p>
 */
public final class CandidateVerifier {

	/** Persisted verifier version for reproducibility. */
	public static final String VERSION = "1"; //$NON-NLS-1$

	private final DslValidator dslValidator;
	private final HintFileParser hintFileParser;

	public CandidateVerifier() {
		this(new DslValidator(), new HintFileParser());
	}

	CandidateVerifier(DslValidator dslValidator, HintFileParser hintFileParser) {
		this.dslValidator = dslValidator;
		this.hintFileParser = hintFileParser;
	}

	/** Verifies one candidate and returns structured diagnostics. */
	public CandidateVerification verify(MiningCandidate candidate) {
		String schemaProblem = validateSchema(candidate);
		if (schemaProblem != null) {
			return failure(CandidateVerification.Stage.SCHEMA, schemaProblem, 0, 0);
		}

		var dslValidation = dslValidator.validate(candidate.getDslRule());
		if (!dslValidation.valid()) {
			return failure(CandidateVerification.Stage.DSL_VALIDATION,
					dslValidation.message(), 0, 0);
		}

		HintFile hintFile;
		try {
			hintFile = hintFileParser.parse(candidate.getDslRule());
		} catch (Exception e) {
			return failure(CandidateVerification.Stage.DSL_PARSE,
					"Could not parse DSL: " + safeMessage(e), 0, 0); //$NON-NLS-1$
		}
		if (hintFile.getRules().isEmpty()) {
			return failure(CandidateVerification.Stage.DSL_PARSE,
					"DSL contains no transformation rule", 0, 0); //$NON-NLS-1$
		}

		registerBuiltInGuards();
		ParseResult beforeParse = parse(candidate.getBeforeExample(), candidate.getSourceVersion());
		if (!beforeParse.valid()) {
			return failure(CandidateVerification.Stage.BEFORE_PARSE,
					beforeParse.message(), 0, 0);
		}

		Map<String, String> compilerOptions = compilerOptions(candidate.getSourceVersion());
		List<TransformationResult> positiveResults;
		try {
			positiveResults = new BatchTransformationProcessor(hintFile)
					.process(beforeParse.compilationUnit(), compilerOptions);
		} catch (RuntimeException e) {
			return failure(CandidateVerification.Stage.BEFORE_MATCH,
					"Positive example evaluation failed: " + safeMessage(e), 0, 0); //$NON-NLS-1$
		}

		List<TransformationResult> replacements = positiveResults.stream()
				.filter(TransformationResult::hasReplacement)
				.toList();
		if (replacements.isEmpty()) {
			return failure(CandidateVerification.Stage.BEFORE_MATCH,
					"Positive example produced no replacement", positiveResults.size(), 0); //$NON-NLS-1$
		}
		if (replacements.size() != 1) {
			return failure(CandidateVerification.Stage.BEFORE_MATCH,
					"Positive example is ambiguous: expected one replacement but got " //$NON-NLS-1$
							+ replacements.size(), positiveResults.size(), replacements.size());
		}

		TransformationResult replacement = replacements.get(0);
		String transformed;
		try {
			transformed = applyReplacement(candidate.getBeforeExample(), replacement);
		} catch (IllegalArgumentException e) {
			return failure(CandidateVerification.Stage.AFTER_REWRITE,
					e.getMessage(), positiveResults.size(), replacements.size());
		}
		if (!normalizeSource(candidate.getAfterExample()).equals(normalizeSource(transformed))) {
			return failure(CandidateVerification.Stage.AFTER_REWRITE,
					"Full transformed source does not equal afterExample", //$NON-NLS-1$
					positiveResults.size(), replacements.size());
		}

		ParseResult negativeParse = parse(candidate.getNegativeExample(), candidate.getSourceVersion());
		if (!negativeParse.valid()) {
			return failure(CandidateVerification.Stage.NEGATIVE_PARSE,
					negativeParse.message(), positiveResults.size(), replacements.size());
		}

		List<TransformationResult> negativeResults;
		try {
			negativeResults = new BatchTransformationProcessor(hintFile)
					.process(negativeParse.compilationUnit(), compilerOptions);
		} catch (RuntimeException e) {
			return failure(CandidateVerification.Stage.NEGATIVE_MATCH,
					"Negative example evaluation failed: " + safeMessage(e), //$NON-NLS-1$
					positiveResults.size(), replacements.size());
		}
		if (!negativeResults.isEmpty()) {
			return failure(CandidateVerification.Stage.NEGATIVE_MATCH,
					"Negative example unexpectedly matched " + negativeResults.size() + " time(s)", //$NON-NLS-1$ //$NON-NLS-2$
					positiveResults.size(), replacements.size());
		}

		return CandidateVerification.success(VERSION, positiveResults.size(), replacements.size());
	}

	private static String validateSchema(MiningCandidate candidate) {
		if (candidate == null) {
			return "Candidate is null"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getSourceRepo())) {
			return "sourceRepo is required"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getSourceCommit())) {
			return "sourceCommit is required"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getDslRule())) {
			return "dslRule is required"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getBeforeExample())) {
			return "beforeExample is required"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getAfterExample())) {
			return "afterExample is required"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getNegativeExample())) {
			return "negativeExample is required"; //$NON-NLS-1$
		}
		if (isBlank(candidate.getSourceVersion())) {
			return "sourceVersion is required"; //$NON-NLS-1$
		}
		return null;
	}

	private static ParseResult parse(String source, String sourceVersion) {
		try {
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(source.toCharArray());
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setCompilerOptions(compilerOptions(sourceVersion));
			CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
			for (IProblem problem : compilationUnit.getProblems()) {
				if (problem.isError()) {
					return new ParseResult(null, false,
							"Java parse error at line " + problem.getSourceLineNumber() //$NON-NLS-1$
									+ ": " + problem.getMessage()); //$NON-NLS-1$
				}
			}
			return new ParseResult(compilationUnit, true, "valid"); //$NON-NLS-1$
		} catch (RuntimeException e) {
			return new ParseResult(null, false, "Java parse failed: " + safeMessage(e)); //$NON-NLS-1$
		}
	}

	private static Map<String, String> compilerOptions(String sourceVersion) {
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions(sourceVersion, options);
		options.put(JavaCore.COMPILER_SOURCE, sourceVersion);
		return options;
	}

	private static void registerBuiltInGuards() {
		Map<String, GuardFunction> guards = new HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	private static String applyReplacement(String source, TransformationResult result) {
		int offset = result.match().getOffset();
		int length = result.match().getLength();
		if (offset < 0 || length < 0 || offset + length > source.length()) {
			throw new IllegalArgumentException(
					"Invalid match range: offset=" + offset + ", length=" + length); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return source.substring(0, offset) + result.replacement()
				+ source.substring(offset + length);
	}

	private static String normalizeSource(String source) {
		return source == null ? "" : source.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String safeMessage(Throwable throwable) {
		String message = throwable.getMessage();
		return message == null || message.isBlank()
				? throwable.getClass().getSimpleName() : message;
	}

	private static CandidateVerification failure(CandidateVerification.Stage stage,
			String message, int matches, int replacements) {
		return CandidateVerification.failure(stage, message, VERSION, matches, replacements);
	}

	private record ParseResult(CompilationUnit compilationUnit, boolean valid, String message) {
	}
}
