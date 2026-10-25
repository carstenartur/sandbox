/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.history;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;
import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;

/** The Sandbox commit-message vocabulary for the optional remote search backend. */
public final class SandboxHistoryElasticsearchAnalysisConfigurer implements ElasticsearchAnalysisConfigurer {
  @Override public void configure(ElasticsearchAnalysisConfigurationContext context) {
    context.tokenFilter("sandbox_commit_words").type("word_delimiter_graph")
        .param("split_on_case_change", true).param("preserve_original", true);
    context.analyzer(GitTextAnalysis.NATURAL_LANGUAGE_ANALYZER).custom().tokenizer("standard")
        .tokenFilters("sandbox_commit_words", "lowercase", "stop");
  }
}
