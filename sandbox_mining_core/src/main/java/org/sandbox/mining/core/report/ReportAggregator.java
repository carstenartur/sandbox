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
package org.sandbox.mining.core.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;

/**
 * Aggregates {@link CommitEvaluation} results.
 *
 * <p>Groups evaluations by repository, category, and traffic light
 * for reporting purposes.</p>
 */
public class ReportAggregator {

	private final List<CommitEvaluation> evaluations = new ArrayList<>();

	/**
	 * Adds an evaluation to the aggregator.
	 *
	 * @param evaluation the evaluation to add
	 */
	public void add(CommitEvaluation evaluation) {
		if (evaluation != null) {
			evaluations.add(evaluation);
		}
	}

	/**
	 * Returns all evaluations.
	 *
	 * @return unmodifiable list of all evaluations
	 */
	public List<CommitEvaluation> getAllEvaluations() {
		return Collections.unmodifiableList(evaluations);
	}

	/**
	 * Groups evaluations by repository URL.
	 *
	 * @return map of repo URL to list of evaluations
	 */
	public Map<String, List<CommitEvaluation>> groupByRepo() {
		return evaluations.stream()
				.collect(Collectors.groupingBy(
						CommitEvaluation::repoUrl,
						LinkedHashMap::new,
						Collectors.toList()));
	}

	/**
	 * Groups evaluations by category.
	 *
	 * @return map of category to list of evaluations
	 */
	public Map<String, List<CommitEvaluation>> groupByCategory() {
		return evaluations.stream()
				.filter(e -> e.category() != null)
				.collect(Collectors.groupingBy(
						CommitEvaluation::category,
						LinkedHashMap::new,
						Collectors.toList()));
	}

	/**
	 * Groups evaluations by traffic light.
	 *
	 * @return map of traffic light to list of evaluations
	 */
	public Map<TrafficLight, List<CommitEvaluation>> groupByTrafficLight() {
		return evaluations.stream()
				.collect(Collectors.groupingBy(
						CommitEvaluation::trafficLight,
						LinkedHashMap::new,
						Collectors.toList()));
	}

	/**
	 * Returns only evaluations marked as relevant.
	 *
	 * @return list of relevant evaluations
	 */
	public List<CommitEvaluation> getRelevantEvaluations() {
		return evaluations.stream()
				.filter(CommitEvaluation::relevant)
				.toList();
	}
}
