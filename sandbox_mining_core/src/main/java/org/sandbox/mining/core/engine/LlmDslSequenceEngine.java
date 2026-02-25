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
import java.time.Duration;
import java.util.List;

import org.sandbox.mining.core.llm.CommitEvaluation;
import org.sandbox.mining.core.llm.LlmClient;

/**
 * {@link DslSequenceEngine} implementation that delegates to a configurable
 * {@link LlmClient} for DSL sequence generation.
 *
 * <p>This engine wraps an existing LLM client and exposes it through the
 * unified engine interface, allowing the mining pipeline to work with
 * any supported LLM provider.</p>
 *
 * @since 1.2.6
 */
public class LlmDslSequenceEngine implements DslSequenceEngine {

	private final LlmClient delegate;

	/**
	 * Creates a new engine backed by the given LLM client.
	 *
	 * @param delegate the LLM client to delegate to
	 */
	public LlmDslSequenceEngine(LlmClient delegate) {
		if (delegate == null) {
			throw new IllegalArgumentException("LlmClient must not be null"); //$NON-NLS-1$
		}
		this.delegate = delegate;
	}

	@Override
	public CommitEvaluation evaluate(String prompt, String commitHash,
			String commitMessage, String repoUrl) throws IOException {
		return delegate.evaluate(prompt, commitHash, commitMessage, repoUrl);
	}

	@Override
	public List<CommitEvaluation> evaluateBatch(String prompt, List<String> commitHashes,
			List<String> commitMessages, String repoUrl) throws IOException {
		return delegate.evaluateBatch(prompt, commitHashes, commitMessages, repoUrl);
	}

	@Override
	public String getEngineType() {
		return EngineType.LLM.name();
	}

	@Override
	public String getModelName() {
		return delegate.getModel();
	}

	@Override
	public boolean hasRemainingCapacity() {
		return delegate.hasRemainingQuota();
	}

	@Override
	public boolean isUnavailable() {
		return delegate.isApiUnavailable();
	}

	@Override
	public int getRequestCount() {
		return delegate.getDailyRequestCount();
	}

	@Override
	public boolean wasLastResponseTruncated() {
		return delegate.wasLastResponseTruncated();
	}

	/**
	 * Returns the maximum duration the engine will tolerate consecutive failures.
	 *
	 * @return the max failure duration
	 */
	public Duration getMaxFailureDuration() {
		return delegate.getMaxFailureDuration();
	}

	/**
	 * Sets the maximum duration the engine will tolerate consecutive failures.
	 *
	 * @param duration the max failure duration
	 */
	public void setMaxFailureDuration(Duration duration) {
		delegate.setMaxFailureDuration(duration);
	}

	/**
	 * Returns the underlying LLM client class name for logging purposes.
	 *
	 * @return the LLM client class simple name
	 */
	public String getClientClassName() {
		return delegate.getClass().getSimpleName();
	}

	@Override
	public void close() {
		delegate.close();
	}
}
