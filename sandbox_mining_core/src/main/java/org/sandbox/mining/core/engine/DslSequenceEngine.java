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
package org.sandbox.mining.core.engine;

import java.io.IOException;
import java.util.List;

import org.sandbox.mining.core.llm.CommitEvaluation;

/**
 * Abstraction for a configurable engine that generates sandbox DSL sequences
 * from commit diffs.
 *
 * <p>Implementations may use different strategies such as LLM-based analysis,
 * AST-based inference, or hybrid approaches.</p>
 *
 * @since 1.2.6
 */
public interface DslSequenceEngine extends AutoCloseable {

	/**
	 * Evaluates a single commit diff and produces a DSL evaluation.
	 *
	 * @param prompt        the analysis prompt
	 * @param commitHash    the commit hash
	 * @param commitMessage the commit message
	 * @param repoUrl       the repository URL
	 * @return the commit evaluation containing DSL rule proposals
	 * @throws IOException if an I/O error occurs during evaluation
	 */
	CommitEvaluation evaluate(String prompt, String commitHash,
			String commitMessage, String repoUrl) throws IOException;

	/**
	 * Evaluates a batch of commit diffs in a single request.
	 *
	 * @param prompt         the analysis prompt
	 * @param commitHashes   list of commit hashes
	 * @param commitMessages list of commit messages
	 * @param repoUrl        the repository URL
	 * @return list of evaluations, one per commit
	 * @throws IOException if an I/O error occurs during evaluation
	 */
	List<CommitEvaluation> evaluateBatch(String prompt, List<String> commitHashes,
			List<String> commitMessages, String repoUrl) throws IOException;

	/**
	 * Returns the name of the engine type.
	 *
	 * @return the engine type name (e.g. "llm", "ast")
	 */
	String getEngineType();

	/**
	 * Returns the name of the underlying model or strategy.
	 *
	 * @return the model or strategy name
	 */
	String getModelName();

	/**
	 * Checks whether the engine has remaining capacity for evaluation.
	 *
	 * @return {@code true} if the engine can accept more requests
	 */
	boolean hasRemainingCapacity();

	/**
	 * Checks whether the engine is currently unavailable.
	 *
	 * @return {@code true} if the engine is unavailable
	 */
	boolean isUnavailable();

	/**
	 * Returns the number of requests made during the current session.
	 *
	 * @return the request count
	 */
	int getRequestCount();

	/**
	 * Returns whether the last response was truncated.
	 *
	 * @return {@code true} if the last response was truncated
	 */
	boolean wasLastResponseTruncated();

	@Override
	void close();
}
