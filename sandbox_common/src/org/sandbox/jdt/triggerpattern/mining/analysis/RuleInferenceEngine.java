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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Main entry-point for inferring transformation rules from before/after code pairs.
 *
 * <p>Simplest API: two source strings in, an {@link InferredRule} out.</p>
 *
 * <p>The engine parses both strings as compilation units, performs a structural
 * AST diff, generalizes identical sub-trees into placeholders, computes a confidence
 * score, and validates the resulting rule.</p>
 *
 * @since 1.2.6
 */
public class RuleInferenceEngine {

	private final AstDiffAnalyzer diffAnalyzer = new AstDiffAnalyzer();
	private final PlaceholderGeneralizer generalizer = new PlaceholderGeneralizer();
	private final ImportDiffAnalyzer importAnalyzer = new ImportDiffAnalyzer();
	private final InferredRuleValidator validator = new InferredRuleValidator();

	/**
	 * Infers a transformation rule by comparing two code snippets.
	 *
	 * <p>The snippets must be parseable as expressions or statements. The engine
	 * wraps them in a synthetic compilation unit before comparing.</p>
	 *
	 * @param codeBefore the original code snippet
	 * @param codeAfter  the modified code snippet
	 * @param kind       the {@link PatternKind} to treat the snippets as
	 * @return an inferred rule if one could be derived, empty otherwise
	 */
	public Optional<InferredRule> inferRule(String codeBefore, String codeAfter, PatternKind kind) {
		String wrapBefore = wrapForKind(codeBefore, kind);
		String wrapAfter = wrapForKind(codeAfter, kind);

		CompilationUnit cuBefore = parseCompilationUnit(wrapBefore);
		CompilationUnit cuAfter = parseCompilationUnit(wrapAfter);

		if (cuBefore == null || cuAfter == null) {
			return Optional.empty();
		}

		// Extract the relevant AST node for the kind
		ASTNode beforeNode = extractNode(cuBefore, kind);
		ASTNode afterNode = extractNode(cuAfter, kind);

		if (beforeNode == null || afterNode == null) {
			return Optional.empty();
		}

		AstDiff diff = diffAnalyzer.computeDiff(beforeNode, afterNode);

		ImportDirective importDirective = importAnalyzer.analyzeImportChanges(cuBefore, cuAfter);

		InferredRule rule = generalizer.generalize(diff, codeBefore, codeAfter, kind, importDirective);
		if (rule == null) {
			return Optional.empty();
		}

		InferredRuleValidator.ValidationResult validation = validator.validate(rule);
		if (validation.status() != InferredRuleValidator.ValidationStatus.VALID) {
			return Optional.empty();
		}

		return Optional.of(rule);
	}

	/**
	 * Infers a rule from a {@link CodeChangePair}.
	 *
	 * @param change the code change pair
	 * @return an inferred rule if one could be derived
	 */
	public Optional<InferredRule> inferRule(CodeChangePair change) {
		return inferRule(change.beforeSnippet(), change.afterSnippet(), change.inferredKind());
	}

	/**
	 * Infers transformation rules from all Java file changes in a single commit.
	 *
	 * <p>The method retrieves the file diffs from the git provider, refines them
	 * into statement-level change pairs, and infers rules from each pair. Similar
	 * rules are grouped to boost confidence.</p>
	 *
	 * @param git            the git history provider
	 * @param repositoryPath path to the repository working tree
	 * @param commitId       the commit hash to analyze
	 * @return list of inferred rules (may be empty)
	 */
	public List<InferredRule> inferFromCommit(GitHistoryProvider git, Path repositoryPath,
			String commitId) {
		DiffHunkRefiner refiner = new DiffHunkRefiner();
		RuleGrouper grouper = new RuleGrouper();

		List<FileDiff> diffs = git.getDiffs(repositoryPath, commitId);
		List<InferredRule> allRules = new ArrayList<>();

		for (FileDiff diff : diffs) {
			List<CodeChangePair> pairs = refiner.refineToStatements(diff);
			for (CodeChangePair pair : pairs) {
				inferRule(pair).ifPresent(allRules::add);
			}
		}

		if (allRules.size() > 1) {
			List<RuleGroup> groups = grouper.groupSimilar(allRules);
			allRules = new ArrayList<>();
			for (RuleGroup group : groups) {
				allRules.add(group.generalizedRule());
			}
		}

		return allRules;
	}

	/**
	 * Infers transformation rules from the most recent commits in a repository.
	 *
	 * <p>Analyzes up to {@code maxCommits} commits from the repository history,
	 * collecting and grouping inferred rules across all commits.</p>
	 *
	 * @param git            the git history provider
	 * @param repositoryPath path to the repository working tree
	 * @param maxCommits     maximum number of commits to analyze
	 * @return list of grouped inferred rules
	 */
	public List<InferredRule> inferFromHistory(GitHistoryProvider git, Path repositoryPath,
			int maxCommits) {
		RuleGrouper grouper = new RuleGrouper();

		List<CommitInfo> commits = git.getHistory(repositoryPath, maxCommits);
		List<InferredRule> allRules = new ArrayList<>();

		for (CommitInfo commit : commits) {
			try {
				List<InferredRule> commitRules = inferFromCommit(git, repositoryPath, commit.id());
				allRules.addAll(commitRules);
			} catch (Exception e) {
				// Skip commits that fail to analyze
			}
		}

		if (allRules.size() > 1) {
			List<RuleGroup> groups = grouper.groupSimilar(allRules);
			allRules = new ArrayList<>();
			for (RuleGroup group : groups) {
				allRules.add(group.generalizedRule());
			}
		}

		return allRules;
	}

	/**
	 * Converts an inferred rule into a {@link TransformationRule} compatible with
	 * the existing trigger-pattern framework.
	 *
	 * @param rule the inferred rule
	 * @return the transformation rule
	 */
	public TransformationRule toTransformationRule(InferredRule rule) {
		Pattern source = new Pattern(rule.sourcePattern(), rule.kind());
		RewriteAlternative alternative = RewriteAlternative.otherwise(rule.replacementPattern());
		return new TransformationRule(
				"Inferred: " + rule.sourcePattern() + " => " + rule.replacementPattern(), //$NON-NLS-1$ //$NON-NLS-2$
				source, null, List.of(alternative),
				rule.importChanges() != null ? rule.importChanges() : new ImportDirective());
	}

	/**
	 * Converts a list of inferred rules into a {@code .sandbox-hint} file string.
	 *
	 * @param rules the inferred rules
	 * @return the hint-file content
	 */
	public String toHintFileString(List<InferredRule> rules) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!id: inferred-rules>\n"); //$NON-NLS-1$
		sb.append("<!description: Rules inferred from code changes>\n"); //$NON-NLS-1$
		sb.append("<!severity: info>\n"); //$NON-NLS-1$
		sb.append("<!tags: inferred, mining>\n\n"); //$NON-NLS-1$

		for (InferredRule rule : rules) {
			sb.append(rule.sourcePattern()).append('\n');
			sb.append("=> ").append(rule.replacementPattern()).append('\n'); //$NON-NLS-1$
			if (rule.importChanges() != null && !rule.importChanges().isEmpty()) {
				for (String imp : rule.importChanges().getAddImports()) {
					sb.append("   addImport ").append(imp).append('\n'); //$NON-NLS-1$
				}
				for (String imp : rule.importChanges().getRemoveImports()) {
					sb.append("   removeImport ").append(imp).append('\n'); //$NON-NLS-1$
				}
			}
			sb.append(";;\n\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	/**
	 * Converts a list of inferred rules into a {@link HintFile}.
	 *
	 * @param rules the inferred rules
	 * @return the hint file
	 */
	public HintFile toHintFile(List<InferredRule> rules) {
		HintFile hintFile = new HintFile();
		hintFile.setId("inferred-rules"); //$NON-NLS-1$
		hintFile.setDescription("Rules inferred from code changes"); //$NON-NLS-1$
		hintFile.setSeverity("info"); //$NON-NLS-1$
		for (InferredRule rule : rules) {
			hintFile.addRule(toTransformationRule(rule));
		}
		return hintFile;
	}

	// ---- helpers ----

	private static CompilationUnit parseCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		options.put(JavaCore.COMPILER_COMPLIANCE, "21"); //$NON-NLS-1$
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21"); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		ASTNode node = parser.createAST(null);
		if (node instanceof CompilationUnit cu) {
			return cu;
		}
		return null;
	}

	private static String wrapForKind(String snippet, PatternKind kind) {
		return switch (kind) {
		case EXPRESSION -> "class _Infer { void _m() { Object _r = " + snippet + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		case STATEMENT -> "class _Infer { void _m() { " + snippet + " } }"; //$NON-NLS-1$ //$NON-NLS-2$
		case METHOD_CALL -> "class _Infer { void _m() { " + snippet + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		case CONSTRUCTOR -> "class _Infer { void _m() { Object _r = " + snippet + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		case ANNOTATION -> snippet + " class _Infer {}"; //$NON-NLS-1$
		case IMPORT -> snippet + " class _Infer {}"; //$NON-NLS-1$
		case FIELD -> "class _Infer { " + snippet + " }"; //$NON-NLS-1$ //$NON-NLS-2$
		case METHOD_DECLARATION -> "class _Infer { " + snippet + " }"; //$NON-NLS-1$ //$NON-NLS-2$
		case BLOCK -> "class _Infer { void _m() " + snippet + " }"; //$NON-NLS-1$ //$NON-NLS-2$
		case STATEMENT_SEQUENCE -> "class _Infer { void _m() { " + snippet + " } }"; //$NON-NLS-1$ //$NON-NLS-2$
		};
	}

	@SuppressWarnings("unchecked")
	private static ASTNode extractNode(CompilationUnit cu, PatternKind kind) {
		if (cu.types().isEmpty()) {
			return null;
		}
		org.eclipse.jdt.core.dom.TypeDeclaration type =
				(org.eclipse.jdt.core.dom.TypeDeclaration) cu.types().get(0);

		return switch (kind) {
		case EXPRESSION, CONSTRUCTOR -> {
			if (type.getMethods().length == 0) {
				yield null;
			}
			org.eclipse.jdt.core.dom.Block body = type.getMethods()[0].getBody();
			if (body == null || body.statements().isEmpty()) {
				yield null;
			}
			Object firstStmt = body.statements().get(0);
			if (firstStmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement vds) {
				var fragments = vds.fragments();
				if (!fragments.isEmpty()) {
					yield ((org.eclipse.jdt.core.dom.VariableDeclarationFragment) fragments.get(0))
							.getInitializer();
				}
			}
			yield null;
		}
		case STATEMENT, STATEMENT_SEQUENCE -> {
			if (type.getMethods().length == 0) {
				yield null;
			}
			org.eclipse.jdt.core.dom.Block body = type.getMethods()[0].getBody();
			if (body == null || body.statements().isEmpty()) {
				yield null;
			}
			yield (ASTNode) body.statements().get(0);
		}
		case METHOD_CALL -> {
			if (type.getMethods().length == 0) {
				yield null;
			}
			org.eclipse.jdt.core.dom.Block body = type.getMethods()[0].getBody();
			if (body == null || body.statements().isEmpty()) {
				yield null;
			}
			Object stmt = body.statements().get(0);
			if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement es) {
				yield es.getExpression();
			}
			yield null;
		}
		case ANNOTATION -> {
			if (type.modifiers().isEmpty()) {
				yield null;
			}
			Object first = type.modifiers().get(0);
			if (first instanceof ASTNode ann) {
				yield ann;
			}
			yield null;
		}
		case IMPORT -> {
			if (cu.imports().isEmpty()) {
				yield null;
			}
			yield (ASTNode) cu.imports().get(0);
		}
		case FIELD -> {
			if (type.getFields().length == 0) {
				yield null;
			}
			yield type.getFields()[0];
		}
		case METHOD_DECLARATION -> {
			if (type.getMethods().length == 0) {
				yield null;
			}
			yield type.getMethods()[0];
		}
		case BLOCK -> {
			if (type.getMethods().length == 0) {
				yield null;
			}
			yield type.getMethods()[0].getBody();
		}
		};
	}
}
