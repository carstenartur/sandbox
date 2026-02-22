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
package org.sandbox.mining.core.llm;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Abstraction over an LLM provider for commit evaluation.
 */
public interface LlmClient extends AutoCloseable {

CommitEvaluation evaluate(String prompt, String commitHash,
String commitMessage, String repoUrl) throws IOException;

List<CommitEvaluation> evaluateBatch(String prompt, List<String> commitHashes,
List<String> commitMessages, String repoUrl) throws IOException;

boolean hasRemainingQuota();

boolean isApiUnavailable();

int getDailyRequestCount();

Duration getMaxFailureDuration();

void setMaxFailureDuration(Duration duration);

/**
 * Returns true if the last API response was truncated (e.g. finishReason=MAX_TOKENS).
 *
 * @return true if truncation was detected
 */
boolean wasLastResponseTruncated();

/**
 * Returns the model name being used by this client.
 *
 * @return the model name
 */
String getModel();

@Override
void close();
}
