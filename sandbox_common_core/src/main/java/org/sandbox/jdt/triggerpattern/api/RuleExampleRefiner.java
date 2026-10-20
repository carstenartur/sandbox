/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.sandbox.jdt.triggerpattern.internal.ChangeVariableGeneralizer;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument.ChangeExample;

/** Bounded, deterministic proposals; names are generalized only after replay. */
public final class RuleExampleRefiner {

	public static final int MAX_VARIABLES = 6;

	private RuleExampleRefiner() { }

	public record Proposal(Set<String> generalizedVariables, ChangeExample example, RuleExampleVerifier.Report report) {
		public Proposal {
			generalizedVariables = Set.copyOf(generalizedVariables);
		}
	}

	/**
	 * Tests most-general candidates first (at most 64). The caller builds the
	 * complete document, preserving guards and metadata. Nothing is applied to
	 * the rule or source files; the returned proposal still needs review.
	 */
	public static Optional<Proposal> refine(ChangeExample seed, RuleExampleSuite suite,
			RuleExampleVerifier verifier, Function<ChangeExample, String> documentFactory) {
		var names = ChangeVariableGeneralizer.candidates(seed);
		if (names.size() > MAX_VARIABLES) {
			throw new IllegalArgumentException("At most " + MAX_VARIABLES
					+ " variable names can be refined together; narrow the change first");
		}
		var masks = IntStream.range(0, 1 << names.size()).boxed()
				.sorted(Comparator.<Integer>comparingInt(Integer::bitCount).reversed().thenComparingInt(Integer::intValue))
				.toList();
		for (int mask : masks) {
			RuleExampleVerifier.checkCanceled();
			Set<String> selected = new LinkedHashSet<>();
			for (int index = 0; index < names.size(); index++) {
				if ((mask & (1 << index)) != 0) {
					selected.add(names.get(index));
				}
			}
			var candidate = ChangeVariableGeneralizer.generalize(seed, selected);
			var report = verifier.verify(documentFactory.apply(candidate), suite);
			if (report.passed()) {
				return Optional.of(new Proposal(selected, candidate, report));
			}
		}
		return Optional.empty();
	}
}
