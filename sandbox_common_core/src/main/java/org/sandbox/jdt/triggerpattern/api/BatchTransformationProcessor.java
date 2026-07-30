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
import java.util.regex.Matcher;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

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
		List<TransformationResult> results= new ArrayList<>();
		for (Map.Entry<TransformationRule, List<Match>> entry : allMatches.entrySet()) {
			TransformationRule rule= entry.getKey();
			for (Match match : entry.getValue()) {
				GuardContext guardCtx= GuardContext.fromMatch(match, cu, compilerOptions, effectivePlan);
				if (rule.sourceGuard() != null && !rule.sourceGuard().evaluate(guardCtx)) {
					continue;
				}

				Optional<RewriteAlternative> alternative= rule.findMatchingAlternative(guardCtx);
				String replacement= alternative.isPresent()
						? substituteBindings(alternative.get().replacementPattern(), match)
						: null;
				ImportDirective imports= rule.hasImportDirective() ? rule.getImportDirective() : null;
				results.add(new TransformationResult(rule, match, replacement, imports,
						rule.getDescription(), computeLineNumber(cu, match)));
			}
		}
		return results;
	}

	private String substituteBindings(String pattern, Match match) {
		if (pattern == null || match.getBindings().isEmpty()) {
			return pattern;
		}

		String result= pattern;
		for (Map.Entry<String, Object> binding : match.getBindings().entrySet()) {
			String placeholder= binding.getKey();
			Object value= binding.getValue();
			String replacement;
			if (value instanceof ASTNode astNode) {
				replacement= astNode.toString();
			} else if (value instanceof List<?> list) {
				result= substituteIndexedAccess(result, placeholder, list);
				result= result.replace(placeholder + ".length", String.valueOf(list.size())); //$NON-NLS-1$
				StringBuilder text= new StringBuilder();
				for (int i= 0; i < list.size(); i++) {
					if (i > 0) {
						text.append(", "); //$NON-NLS-1$
					}
					text.append(list.get(i));
				}
				replacement= text.toString();
			} else {
				replacement= String.valueOf(value);
			}
			result= result.replace(placeholder, Matcher.quoteReplacement(replacement));
		}
		return result;
	}

	private static String substituteIndexedAccess(String text, String placeholder, List<?> list) {
		java.util.regex.Pattern indexPattern= java.util.regex.Pattern.compile(
				java.util.regex.Pattern.quote(placeholder) + "\\[(-?\\d+)\\]"); //$NON-NLS-1$
		java.util.regex.Matcher matcher= indexPattern.matcher(text);
		StringBuilder result= new StringBuilder();
		while (matcher.find()) {
			int index= Integer.parseInt(matcher.group(1));
			if (index < 0) {
				index= list.size() + index;
			}
			if (index >= 0 && index < list.size()) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(list.get(index).toString()));
			}
		}
		matcher.appendTail(result);
		return result.toString();
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

	/** Result of applying a transformation rule to one match. */
	public record TransformationResult(TransformationRule rule, Match match, String replacement,
			ImportDirective importDirective, String description, int lineNumber) {

		public TransformationResult {
			importDirective= copyImportDirective(importDirective);
		}

		public ImportDirective importDirective() {
			return copyImportDirective(importDirective);
		}

		public boolean hasReplacement() {
			return replacement != null;
		}

		public boolean hasImportDirective() {
			return importDirective != null && !importDirective.isEmpty();
		}

		public String matchedText() {
			return match.getMatchedNode().toString();
		}
	}
}
