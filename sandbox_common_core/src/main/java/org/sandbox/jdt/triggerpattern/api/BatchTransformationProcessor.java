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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.internal.BindingSubstitution;

import org.sandbox.jdt.triggerpattern.api.GuardContext.UnknownSemanticRequirement;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.Evaluation;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.TruthValue;

/** Batch processor for applying all transformation rules from a {@link HintFile}. */
public final class BatchTransformationProcessor {

	private final HintFile hintFile;
	private final PatternIndex patternIndex;

	public BatchTransformationProcessor(HintFile hintFile) {
		this.hintFile= hintFile;
		this.patternIndex= new PatternIndex(hintFile.getRules());
		this.patternIndex.setCaseInsensitive(hintFile.isCaseInsensitive());
	}

	public BatchTransformationProcessor(HintFile hintFile, List<TransformationRule> resolvedRules) {
		this.hintFile= hintFile;
		this.patternIndex= new PatternIndex(resolvedRules);
		this.patternIndex.setCaseInsensitive(hintFile.isCaseInsensitive());
	}

	public HintFile getHintFile() {
		return hintFile;
	}

	public PatternIndex getPatternIndex() {
		return patternIndex;
	}

	public List<TransformationResult> process(CompilationUnit cu) {
		return process(cu, SemanticRewritePlanContext.currentCompilerOptions(),
				SemanticRewritePlanContext.current());
	}

	public List<TransformationResult> process(CompilationUnit cu, Map<String, String> compilerOptions) {
		return process(cu, compilerOptions, SemanticRewritePlanContext.current());
	}

	/** Processes a compilation unit with an explicit fail-closed semantic authorization plan. */
	public List<TransformationResult> process(CompilationUnit cu, Map<String, String> compilerOptions,
			SemanticRewritePlan semanticPlan) {
		if (cu == null) {
			return Collections.emptyList();
		}

		Map<TransformationRule, List<Match>> allMatches= patternIndex.findAllMatches(cu);
		if (allMatches.isEmpty()) {
			return Collections.emptyList();
		}

		SemanticRewritePlan effectivePlan= semanticPlan == null ? SemanticRewritePlan.empty() : semanticPlan;
		boolean requiredBindings= hintFile.getBindingPolicy() == HintBindingPolicy.REQUIRED
				|| !effectivePlan.isEmpty();
		List<TransformationResult> results= new ArrayList<>();
		for (Map.Entry<TransformationRule, List<Match>> entry : allMatches.entrySet()) {
			SemanticRewritePlanContext.checkCanceled();
			TransformationRule rule= entry.getKey();
			boolean strictBindings = requiredBindings || rule.alternatives().stream()
					.anyMatch(alternative -> alternative.hasRewrite() || alternative.isEmbeddedFix());
			for (Match match : entry.getValue()) {
				SemanticRewritePlanContext.checkCanceled();
				GuardContext guardCtx= GuardContext.fromMatch(match, cu, compilerOptions, effectivePlan);
				boolean unresolvedSource= guardCtx.hasUnknownSemanticRequirements();
				if (rule.sourceGuard() != null) {
					Evaluation sourceEvaluation= rule.sourceGuard().evaluateDetailed(guardCtx);
					if (strictBindings && sourceEvaluation.truthValue() == TruthValue.UNKNOWN) {
						results.add(unknownResult(rule, match, guardCtx, cu));
						continue;
					}
					if (!sourceEvaluation.compatibilityValue()) {
						continue;
					}
				}
				if (strictBindings && (unresolvedSource || !match.getUnknownSemanticRequirements().isEmpty())) {
					results.add(unknownResult(rule, match, guardCtx, cu));
					continue;
				}

				AlternativeSelection selection= selectAlternative(rule, guardCtx, strictBindings);
				if (selection.unknown()) {
					results.add(unknownResult(rule, match, guardCtx, cu));
					continue;
				}
				Optional<RewriteAlternative> alternative= selection.alternative();
				String replacement= alternative.isPresent() && alternative.get().hasTextReplacement()
						? substituteBindings(alternative.get().replacementPattern(), match)
						: null;
				List<StructuredRewriteAction> actions= alternative
						.map(RewriteAlternative::structuredActions).orElse(List.of());
				ImportDirective imports= rule.hasImportDirective() ? rule.getImportDirective() : null;
				results.add(new TransformationResult(rule, match, replacement, actions, imports,
						rule.getDescription(), computeLineNumber(cu, match), List.of()));
			}
		}
		return results;
	}

	private static AlternativeSelection selectAlternative(TransformationRule rule,
			GuardContext context, boolean strictBindings) {
		for (RewriteAlternative alternative : rule.alternatives()) {
			if (alternative.isOtherwise()) {
				return new AlternativeSelection(Optional.of(alternative), false);
			}
			if (alternative.condition() == null) {
				continue;
			}
			Evaluation evaluation= alternative.condition().evaluateDetailed(context);
			if (strictBindings && evaluation.truthValue() == TruthValue.UNKNOWN) {
				return new AlternativeSelection(Optional.empty(), true);
			}
			if (evaluation.compatibilityValue()) {
				return new AlternativeSelection(Optional.of(alternative), false);
			}
		}
		return new AlternativeSelection(Optional.empty(), false);
	}

	private static TransformationResult unknownResult(TransformationRule rule, Match match,
			GuardContext context, CompilationUnit cu) {
		List<UnknownSemanticRequirement> unknowns= context.getUnknownSemanticRequirements();
		String ruleName= rule.getRuleId() == null ? rule.sourcePattern().getValue() : rule.getRuleId();
		String details= unknowns.stream()
				.map(requirement -> requirement.guardName() + ": " + requirement.detail()) //$NON-NLS-1$
				.distinct().collect(java.util.stream.Collectors.joining("; ")); //$NON-NLS-1$
		String description= "Cannot apply binding-required rule " + ruleName //$NON-NLS-1$
				+ " because semantic information is unresolved: " + details; //$NON-NLS-1$
		return new TransformationResult(rule, match, null, List.of(), null, description,
				cu.getLineNumber(match.getOffset()), unknowns);
	}

	private String substituteBindings(String pattern, Match match) {
		return BindingSubstitution.substitute(pattern, match);
	}

	private int computeLineNumber(CompilationUnit cu, Match match) {
		return cu.getLineNumber(match.getOffset());
	}

	private static ImportDirective copyImportDirective(ImportDirective source) {
		if (source == null) {
			return null;
		}
		ImportDirective copy= new ImportDirective(source.getAddImports(), source.getRemoveImports(),
				source.getAddStaticImports(), source.getRemoveStaticImports());
		source.getReplaceStaticImports().forEach(copy::replaceStaticImport);
		return copy;
	}

	private record AlternativeSelection(Optional<RewriteAlternative> alternative, boolean unknown) {
	}

	/** Result of applying one selected rewrite alternative to one match. */
	public record TransformationResult(TransformationRule rule, Match match, String replacement,
			List<StructuredRewriteAction> structuredActions, ImportDirective importDirective,
			String description, int lineNumber,
			List<UnknownSemanticRequirement> unknownSemanticRequirements) {

		public TransformationResult {
			structuredActions= List.copyOf(structuredActions == null ? List.of() : structuredActions);
			importDirective= copyImportDirective(importDirective);
			unknownSemanticRequirements= List.copyOf(
					unknownSemanticRequirements == null ? List.of() : unknownSemanticRequirements);
		}

		/** Backward-compatible constructor for text-only transformation results. */
		public TransformationResult(TransformationRule rule, Match match, String replacement,
				ImportDirective importDirective, String description, int lineNumber) {
			this(rule, match, replacement, List.of(), importDirective, description, lineNumber, List.of());
		}

		/** Backward-compatible constructor for text and structured transformation results. */
		public TransformationResult(TransformationRule rule, Match match, String replacement,
				List<StructuredRewriteAction> structuredActions, ImportDirective importDirective,
				String description, int lineNumber) {
			this(rule, match, replacement, structuredActions, importDirective, description,
					lineNumber, List.of());
		}

		public ImportDirective importDirective() {
			return copyImportDirective(importDirective);
		}

		public boolean hasReplacement() {
			return replacement != null;
		}

		public boolean hasStructuredActions() {
			return !structuredActions.isEmpty();
		}

		public boolean hasRewrite() {
			return hasReplacement() || hasStructuredActions();
		}

		public boolean hasImportDirective() {
			return importDirective != null && !importDirective.isEmpty();
		}

		public boolean isSemanticUnknown() {
			return !unknownSemanticRequirements.isEmpty();
		}

		public String matchedText() {
			return match.getMatchedNode().toString();
		}
	}
}
