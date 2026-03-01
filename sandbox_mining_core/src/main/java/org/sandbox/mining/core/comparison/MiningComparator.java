/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core.comparison;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;

/**
 * Compares Gemini mining results against a reference evaluation
 * (e.g. from Copilot) and produces a {@link DeltaReport} identifying
 * gaps and improvement opportunities.
 */
public class MiningComparator {

	/**
	 * Compares mining results against reference evaluations and
	 * produces a delta report.
	 *
	 * @param miningResults    evaluations from the Gemini mining pipeline
	 * @param referenceResults evaluations from the reference tool (e.g. Copilot)
	 * @return a delta report identifying gaps
	 */
	public DeltaReport compare(List<CommitEvaluation> miningResults,
			List<CommitEvaluation> referenceResults) {
		DeltaReport report = new DeltaReport();

		Map<String, CommitEvaluation> miningByHash = miningResults.stream()
				.collect(Collectors.toMap(CommitEvaluation::commitHash, e -> e, (a, b) -> b));

		Map<String, CommitEvaluation> refByHash = referenceResults.stream()
				.collect(Collectors.toMap(CommitEvaluation::commitHash, e -> e, (a, b) -> b));

		// Check reference results that mining missed or got wrong
		for (CommitEvaluation ref : referenceResults) {
			CommitEvaluation mining = miningByHash.get(ref.commitHash());

			if (mining == null) {
				if (ref.relevant()) {
					report.addGap(new GapEntry(ref.commitHash(),
							GapCategory.MISSED_RELEVANT, null,
							ref.trafficLight().name(),
							"Commit was not evaluated by mining pipeline")); //$NON-NLS-1$
				}
				continue;
			}

			// Compare relevance
			if (ref.relevant() && !mining.relevant()) {
				report.addGap(new GapEntry(ref.commitHash(),
						GapCategory.MISSED_RELEVANT,
						"irrelevant", "relevant", //$NON-NLS-1$ //$NON-NLS-2$
						"Mining marked as irrelevant but reference found it relevant")); //$NON-NLS-1$
				continue;
			}

			// Compare traffic light
			if (ref.trafficLight() != null && mining.trafficLight() != null
					&& ref.trafficLight() != mining.trafficLight()) {
				report.addGap(new GapEntry(ref.commitHash(),
						GapCategory.WRONG_TRAFFIC_LIGHT,
						mining.trafficLight().name(),
						ref.trafficLight().name(),
						"Traffic light mismatch")); //$NON-NLS-1$
			}

			// Compare DSL rules
			compareDslRules(report, ref, mining);

			// Compare categories
			if (ref.category() != null && mining.category() != null
					&& !ref.category().equals(mining.category())) {
				report.addGap(new GapEntry(ref.commitHash(),
						GapCategory.CATEGORY_MISMATCH,
						mining.category(), ref.category(),
						"Category mismatch")); //$NON-NLS-1$
			}
		}

		return report;
	}

	private static void compareDslRules(DeltaReport report, CommitEvaluation ref,
			CommitEvaluation mining) {
		boolean refHasRule = ref.dslRule() != null && !ref.dslRule().isBlank();
		boolean miningHasRule = mining.dslRule() != null && !mining.dslRule().isBlank();

		if (refHasRule && !miningHasRule) {
			report.addGap(new GapEntry(ref.commitHash(),
					GapCategory.MISSING_DSL_RULE,
					null, ref.dslRule(),
					"Reference has DSL rule but mining does not")); //$NON-NLS-1$
		} else if (miningHasRule && mining.dslValidationResult() != null
				&& !"VALID".equals(mining.dslValidationResult()) //$NON-NLS-1$
				&& refHasRule) {
			report.addGap(new GapEntry(ref.commitHash(),
					GapCategory.INVALID_DSL_RULE,
					mining.dslValidationResult(), ref.dslRule(),
					"Mining produced invalid DSL rule")); //$NON-NLS-1$
		}
	}
}
