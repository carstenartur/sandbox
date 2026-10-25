/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;

/** Sandbox's commit-message vocabulary for the released Search projection. */
public final class SandboxHistoryAnalysisConfigurer implements LuceneAnalysisConfigurer {

	@Override
	public void configure(LuceneAnalysisConfigurationContext context) {
		context.analyzer(GitTextAnalysis.NATURAL_LANGUAGE_ANALYZER).custom()
				.tokenizer("standard")
				.tokenFilter("wordDelimiterGraph")
				.param("splitOnCaseChange", "1")
				.param("preserveOriginal", "1")
				.tokenFilter("lowercase")
				.tokenFilter("stop");
	}
}
