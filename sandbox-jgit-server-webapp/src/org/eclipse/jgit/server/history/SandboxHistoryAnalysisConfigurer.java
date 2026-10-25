/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import org.eclipse.jgit.storage.hibernate.search.JavaSourceAnalysisConfigurer;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;

/** Sandbox's message vocabulary plus the transitional Java projection analyzers. */
public final class SandboxHistoryAnalysisConfigurer implements LuceneAnalysisConfigurer {

	@Override
	public void configure(LuceneAnalysisConfigurationContext context) {
		new JavaSourceAnalysisConfigurer().configure(context);
		context.analyzer(GitTextAnalysis.NATURAL_LANGUAGE_ANALYZER).custom()
				.tokenizer("standard")
				.tokenFilter("wordDelimiterGraph")
				.param("splitOnCaseChange", "1")
				.param("preserveOriginal", "1")
				.tokenFilter("lowercase")
				.tokenFilter("stop");
	}
}
