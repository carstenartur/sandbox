/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sandbox.jdt.triggerpattern.api.HintBindingPolicy;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;
import org.sandbox.jdt.triggerpattern.api.HintPredicateDefinition;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Composes high-level declarative language features before delegating ordinary
 * rule parsing to the stable {@link HintFileParser}.
 */
public final class HintProgramParser {

	/** Immutable declarations and parser-compatible expanded source. */
	public record ParsedProgram(List<HintPredicateDefinition> predicates, String expandedSource,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel,
			Map<String, PatternKind> kindsByRuleId) {
		public ParsedProgram {
			predicates= List.copyOf(predicates);
			Map<String, List<StructuredRewriteAction>> actionCopy= new LinkedHashMap<>();
			actionsBySentinel.forEach((sentinel, actions) ->
					actionCopy.put(sentinel, List.copyOf(actions)));
			actionsBySentinel= Map.copyOf(actionCopy);
			kindsByRuleId= Map.copyOf(kindsByRuleId);
		}

		/** Backward-compatible constructor for programs without explicit rule kinds. */
		public ParsedProgram(List<HintPredicateDefinition> predicates, String expandedSource,
				Map<String, List<StructuredRewriteAction>> actionsBySentinel) {
			this(predicates, expandedSource, actionsBySentinel, Map.of());
		}

		/** Returns a fresh validated rule model with high-level features restored. */
		public HintFile hintFile() {
			try {
				return parseComposed(expandedSource, actionsBySentinel, kindsByRuleId);
			} catch (HintParseException exception) {
				throw new IllegalStateException("Validated expanded hint source can no longer be parsed", exception); //$NON-NLS-1$
			}
		}
	}

	private record PreparedProgram(List<HintPredicateDefinition> predicates, String expandedSource,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel,
			Map<String, PatternKind> kindsByRuleId) {
		PreparedProgram {
			predicates= List.copyOf(predicates);
			actionsBySentinel= Map.copyOf(actionsBySentinel);
			kindsByRuleId= Map.copyOf(kindsByRuleId);
		}
	}

	private final HintFileParser ruleParser;
	private final RewriteActionCatalog actionCatalog;

	public HintProgramParser() {
		this(new HintFileParser(), RewriteActionCatalog.standard());
	}

	public HintProgramParser(RewriteActionCatalog actionCatalog) {
		this(new HintFileParser(), actionCatalog);
	}

	HintProgramParser(HintFileParser ruleParser) {
		this(ruleParser, RewriteActionCatalog.standard());
	}

	HintProgramParser(HintFileParser ruleParser, RewriteActionCatalog actionCatalog) {
		this.ruleParser= ruleParser;
		this.actionCatalog= actionCatalog;
	}

	public ParsedProgram parse(String source) throws HintParseException {
		PreparedProgram prepared= prepare(source, actionCatalog);
		parseComposed(ruleParser, prepared.expandedSource(), prepared.actionsBySentinel(),
				prepared.kindsByRuleId());
		return new ParsedProgram(prepared.predicates(), prepared.expandedSource(),
				prepared.actionsBySentinel(), prepared.kindsByRuleId());
	}

	public HintFile parseHintFile(String source) throws HintParseException {
		PreparedProgram prepared= prepare(source, actionCatalog);
		return parseComposed(ruleParser, prepared.expandedSource(), prepared.actionsBySentinel(),
				prepared.kindsByRuleId());
	}

	private static PreparedProgram prepare(String source, RewriteActionCatalog catalog)
			throws HintParseException {
		try {
			HintPlanRequirement.fromContent(source);
		} catch (IllegalArgumentException exception) {
			throw new HintParseException(exception.getMessage(), 0);
		}
		HintPredicatePreprocessor.Result predicates= HintPredicatePreprocessor.preprocess(source);
		validatePredicateNames(predicates.predicates());
		HintRuleKindPreprocessor.Result kinds=
				HintRuleKindPreprocessor.preprocess(predicates.expandedSource());
		String normalizedTypeSources= HintTypeDeclarationSourcePreprocessor.preprocess(
				kinds.parserSource(), kinds.kindsByRuleId());
		HintStructuredActionPreprocessor.Result actions=
				HintStructuredActionPreprocessor.preprocess(normalizedTypeSources, catalog);
		return new PreparedProgram(predicates.predicates(), actions.parserSource(),
				actions.actionsBySentinel(), kinds.kindsByRuleId());
	}

	private static HintFile parseComposed(String source,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel,
			Map<String, PatternKind> kindsByRuleId) throws HintParseException {
		return parseComposed(new HintFileParser(), source, actionsBySentinel, kindsByRuleId);
	}

	private static HintFile parseComposed(HintFileParser parser, String source,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel,
			Map<String, PatternKind> kindsByRuleId) throws HintParseException {
		HintFile parsed= parser.parse(source);
		try {
			HintBindingPolicy.fromContent(source).ifPresent(parsed::setBindingPolicy);
		} catch (IllegalArgumentException exception) {
			throw new HintParseException(exception.getMessage(), 0);
		}
		if (actionsBySentinel.isEmpty() && kindsByRuleId.isEmpty()) {
			return parsed;
		}
		HintFile result= copyMetadata(parsed);
		Set<String> appliedKinds= new LinkedHashSet<>();
		for (TransformationRule rule : parsed.getRules()) {
			List<RewriteAlternative> alternatives= restoreActions(rule, actionsBySentinel);
			Pattern sourcePattern= rule.sourcePattern();
			PatternKind explicitKind= rule.getRuleId() == null ? null : kindsByRuleId.get(rule.getRuleId());
			if (explicitKind != null) {
				appliedKinds.add(rule.getRuleId());
				sourcePattern= withKind(sourcePattern, explicitKind, rule.getRuleId());
			}
			result.addRule(new TransformationRule(rule.getRuleId(), rule.getDescription(),
					sourcePattern, rule.sourceGuard(), alternatives,
					rule.getImportDirective(), rule.getSeverity()));
		}
		if (!appliedKinds.equals(kindsByRuleId.keySet())) {
			Set<String> missing= new LinkedHashSet<>(kindsByRuleId.keySet());
			missing.removeAll(appliedKinds);
			throw new HintParseException("Explicit pattern kinds reference missing rule ids " + missing, 0); //$NON-NLS-1$
		}
		return result;
	}

	private static List<RewriteAlternative> restoreActions(TransformationRule rule,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel) {
		List<RewriteAlternative> alternatives= new ArrayList<>();
		for (RewriteAlternative alternative : rule.alternatives()) {
			List<StructuredRewriteAction> actions=
					actionsBySentinel.get(alternative.replacementPattern());
			if (actions == null) {
				alternatives.add(alternative);
			} else {
				alternatives.add(RewriteAlternative.structured(actions, alternative.condition()));
			}
		}
		return alternatives;
	}

	private static Pattern withKind(Pattern source, PatternKind kind, String ruleId)
			throws HintParseException {
		if (kind == PatternKind.TYPE_DECLARATION
				&& new TypeDeclarationPatternParser().parse(source.getValue()) == null) {
			throw new HintParseException("Rule " + ruleId //$NON-NLS-1$
					+ " must contain one syntactically valid type header with an empty body", 0); //$NON-NLS-1$
		}
		return new Pattern(source.getValue(), kind, source.getId(), source.getDisplayName(),
				source.getQualifiedType(), source.getOverridesType(), source.getConstraints());
	}

	private static HintFile copyMetadata(HintFile source) {
		HintFile copy= new HintFile();
		copy.setId(source.getId());
		copy.setDescription(source.getDescription());
		copy.setSeverity(source.getSeverity());
		copy.setMinJavaVersion(source.getMinJavaVersion());
		copy.setBindingPolicy(source.getBindingPolicy());
		copy.setTags(source.getTags());
		copy.setCaseInsensitive(source.isCaseInsensitive());
		copy.setSuppressWarnings(source.getSuppressWarnings());
		copy.setTreeKindNodeTypes(source.getTreeKindNodeTypes());
		source.getIncludes().forEach(copy::addInclude);
		source.getEmbeddedJavaBlocks().forEach(copy::addEmbeddedJavaBlock);
		return copy;
	}

	private static void validatePredicateNames(Iterable<HintPredicateDefinition> predicates)
			throws HintParseException {
		for (HintPredicateDefinition predicate : predicates) {
			if (HintLanguageVocabulary.builtInGuardNames().contains(predicate.name())) {
				throw new HintParseException("Predicate name shadows built-in guard " //$NON-NLS-1$
						+ predicate.name(), predicate.lineNumber());
			}
		}
	}
}
