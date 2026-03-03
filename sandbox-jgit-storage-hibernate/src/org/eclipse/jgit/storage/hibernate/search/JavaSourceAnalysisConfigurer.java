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
package org.eclipse.jgit.storage.hibernate.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

/**
 * Configures Lucene analyzers tailored for Java source code search.
 * <p>
 * This configurer defines named analyzers for CamelCase-aware identifier
 * search, file path and package search, commit message analysis,
 * dot-qualified fully qualified name search, and ECJ-based Java source
 * tokenization. It is registered with Hibernate Search via the
 * {@code hibernate.search.backend.analysis.configurer} property.
 * </p>
 */
public class JavaSourceAnalysisConfigurer implements LuceneAnalysisConfigurer {

	@Override
	public void configure(LuceneAnalysisConfigurationContext context) {
		// generateWordParts + preserveOriginal: indexes both the original
		// identifier and its CamelCase parts (e.g. "StringBuilder" →
		// "StringBuilder", "String", "Builder")
		context.analyzer("javaIdentifier").custom() //$NON-NLS-1$
				.tokenizer("standard") //$NON-NLS-1$
				.tokenFilter("wordDelimiterGraph") //$NON-NLS-1$
				.param("splitOnCaseChange", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.param("generateWordParts", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.param("preserveOriginal", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.tokenFilter("lowercase"); //$NON-NLS-1$

		context.analyzer("javaPath").custom() //$NON-NLS-1$
				.tokenizer("standard") //$NON-NLS-1$
				.tokenFilter("wordDelimiterGraph") //$NON-NLS-1$
				.param("splitOnCaseChange", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.tokenFilter("lowercase"); //$NON-NLS-1$

		context.analyzer("commitMessage").custom() //$NON-NLS-1$
				.tokenizer("standard") //$NON-NLS-1$
				.tokenFilter("wordDelimiterGraph") //$NON-NLS-1$
				.param("splitOnCaseChange", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.param("preserveOriginal", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.tokenFilter("lowercase") //$NON-NLS-1$
				.tokenFilter("stop"); //$NON-NLS-1$

		context.analyzer("dotQualifiedName").custom() //$NON-NLS-1$
				.tokenizer("keyword") //$NON-NLS-1$
				.tokenFilter("patternReplace") //$NON-NLS-1$
				.param("pattern", "\\.") //$NON-NLS-1$ //$NON-NLS-2$
				.param("replacement", " ") //$NON-NLS-1$ //$NON-NLS-2$
				.tokenFilter("lowercase"); //$NON-NLS-1$

		// ECJ Scanner-based analyzer for Java source code
		context.analyzer("javaSourceEcj") //$NON-NLS-1$
				.instance(new Analyzer() {
					@Override
					protected TokenStreamComponents createComponents(
							String fieldName) {
						EcjTokenizer tokenizer = new EcjTokenizer();
						TokenStream filter = new EcjTokenFilter(tokenizer);
						filter = new LowerCaseFilter(filter);
						return new TokenStreamComponents(tokenizer, filter);
					}
				});

		// Generic analyzer for non-Java text content
		context.analyzer("genericContent").custom() //$NON-NLS-1$
				.tokenizer("standard") //$NON-NLS-1$
				.tokenFilter("wordDelimiterGraph") //$NON-NLS-1$
				.param("splitOnCaseChange", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.param("preserveOriginal", "1") //$NON-NLS-1$ //$NON-NLS-2$
				.tokenFilter("lowercase") //$NON-NLS-1$
				.tokenFilter("stop"); //$NON-NLS-1$
	}
}
