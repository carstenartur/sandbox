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
import java.util.List;
import java.util.Map;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.HintPredicateDefinition;
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

	/**
	 * Immutable local declarations, parser-compatible source and structured action
	 * attachments. A fresh compatibility model is reconstructed on demand.
	 */
	public record ParsedProgram(List<HintPredicateDefinition> predicates, String expandedSource,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel) {
		public ParsedProgram {
			predicates= List.copyOf(predicates);
			Map<String, List<StructuredRewriteAction>> copy= new LinkedHashMap<>();
			actionsBySentinel.forEach((sentinel, actions) -> copy.put(sentinel, List.copyOf(actions)));
			actionsBySentinel= Map.copyOf(copy);
		}

		/** Returns a fresh validated rule model with structured actions restored. */
		public HintFile hintFile() {
			try {
				return parseWithActions(expandedSource, actionsBySentinel);
			} catch (HintParseException exception) {
				throw new IllegalStateException("Validated expanded hint source can no longer be parsed", exception); //$NON-NLS-1$
			}
		}
	}

	private record PreparedProgram(List<HintPredicateDefinition> predicates, String expandedSource,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel) {
		PreparedProgram {
			predicates= List.copyOf(predicates);
			actionsBySentinel= Map.copyOf(actionsBySentinel);
		}
	}

	private final HintFileParser ruleParser;
	private final RewriteActionCatalog actionCatalog;

	public HintProgramParser() {
		this(new HintFileParser(), RewriteActionCatalog.standard());
	}

	/** Creates a parser with an explicitly composed action catalog. */
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
		parseWithActions(ruleParser, prepared.expandedSource(), prepared.actionsBySentinel());
		return new ParsedProgram(prepared.predicates(), prepared.expandedSource(),
				prepared.actionsBySentinel());
	}

	/** Convenience for consumers that only need the existing rule model. */
	public HintFile parseHintFile(String source) throws HintParseException {
		PreparedProgram prepared= prepare(source, actionCatalog);
		return parseWithActions(ruleParser, prepared.expandedSource(), prepared.actionsBySentinel());
	}

	private static PreparedProgram prepare(String source, RewriteActionCatalog catalog)
			throws HintParseException {
		HintPredicatePreprocessor.Result predicates= HintPredicatePreprocessor.preprocess(source);
		validatePredicateNames(predicates.predicates());
		HintStructuredActionPreprocessor.Result actions=
				HintStructuredActionPreprocessor.preprocess(predicates.expandedSource(), catalog);
		return new PreparedProgram(predicates.predicates(), actions.parserSource(),
				actions.actionsBySentinel());
	}

	private static HintFile parseWithActions(String source,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel)
			throws HintParseException {
		return parseWithActions(new HintFileParser(), source, actionsBySentinel);
	}

	private static HintFile parseWithActions(HintFileParser parser, String source,
			Map<String, List<StructuredRewriteAction>> actionsBySentinel)
			throws HintParseException {
		HintFile parsed= parser.parse(source);
		if (actionsBySentinel.isEmpty()) {
			return parsed;
		}
		HintFile result= copyMetadata(parsed);
		for (TransformationRule rule : parsed.getRules()) {
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
			result.addRule(new TransformationRule(rule.getRuleId(), rule.getDescription(),
					rule.sourcePattern(), rule.sourceGuard(), alternatives,
					rule.getImportDirective(), rule.getSeverity()));
		}
		return result;
	}

	private static HintFile copyMetadata(HintFile source) {
		HintFile copy= new HintFile();
		copy.setId(source.getId());
		copy.setDescription(source.getDescription());
		copy.setSeverity(source.getSeverity());
		copy.setMinJavaVersion(source.getMinJavaVersion());
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
