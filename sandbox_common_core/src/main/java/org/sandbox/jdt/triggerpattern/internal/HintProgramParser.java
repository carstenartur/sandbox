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

import java.util.List;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.HintPredicateDefinition;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Composes high-level declarative language features before delegating ordinary
 * rule parsing to the stable {@link HintFileParser}.
 */
public final class HintProgramParser {

	/**
	 * Immutable local declarations and expanded executable source. The mutable
	 * compatibility {@link HintFile} model is reconstructed on demand rather than
	 * exposed from parser-owned state.
	 */
	public record ParsedProgram(List<HintPredicateDefinition> predicates, String expandedSource) {
		public ParsedProgram {
			predicates= List.copyOf(predicates);
		}

		/** Returns a fresh compatibility rule model for the validated expanded source. */
		public HintFile hintFile() {
			try {
				return new HintFileParser().parse(expandedSource);
			} catch (HintParseException exception) {
				throw new IllegalStateException("Validated expanded hint source can no longer be parsed", exception); //$NON-NLS-1$
			}
		}
	}

	private record PreparedProgram(List<HintPredicateDefinition> predicates, String expandedSource) {
		PreparedProgram {
			predicates= List.copyOf(predicates);
		}
	}

	private final HintFileParser ruleParser;

	public HintProgramParser() {
		this(new HintFileParser());
	}

	HintProgramParser(HintFileParser ruleParser) {
		this.ruleParser= ruleParser;
	}

	public ParsedProgram parse(String source) throws HintParseException {
		PreparedProgram prepared= prepare(source);
		ruleParser.parse(prepared.expandedSource());
		return new ParsedProgram(prepared.predicates(), prepared.expandedSource());
	}

	/** Convenience for consumers that only need the existing rule model. */
	public HintFile parseHintFile(String source) throws HintParseException {
		PreparedProgram prepared= prepare(source);
		return ruleParser.parse(prepared.expandedSource());
	}

	private static PreparedProgram prepare(String source) throws HintParseException {
		HintPredicatePreprocessor.Result preprocessed= HintPredicatePreprocessor.preprocess(source);
		validatePredicateNames(preprocessed.predicates());
		return new PreparedProgram(preprocessed.predicates(), preprocessed.expandedSource());
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
