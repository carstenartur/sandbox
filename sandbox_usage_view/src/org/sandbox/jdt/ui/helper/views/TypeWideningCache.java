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
package org.sandbox.jdt.ui.helper.views;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.util.TypeWideningAnalyzer;
import org.sandbox.jdt.internal.corext.util.TypeWideningAnalyzer.TypeWideningResult;

/**
 * Cache for type widening analysis results.
 * Stores results from {@link TypeWideningAnalyzer} so that the widest type
 * column can look up results by variable binding key without re-analyzing.
 */
public final class TypeWideningCache {

	private Map<String, TypeWideningResult> resultsByKey = new HashMap<>();

	/**
	 * Analyzes a compilation unit and caches the type widening results.
	 *
	 * @param compilationUnit the compilation unit to analyze
	 */
	public void analyzeAndCache(CompilationUnit compilationUnit) {
		Map<String, TypeWideningResult> results = TypeWideningAnalyzer.analyzeCompilationUnit(compilationUnit);
		resultsByKey.putAll(results);
	}

	/**
	 * Returns the type widening result for a variable binding key, or null if none.
	 *
	 * @param variableBindingKey the variable binding key
	 * @return the type widening result, or null
	 */
	public TypeWideningResult getResult(String variableBindingKey) {
		return resultsByKey.get(variableBindingKey);
	}

	/**
	 * Returns all cached results.
	 *
	 * @return unmodifiable map of all results
	 */
	public Map<String, TypeWideningResult> getAllResults() {
		return Collections.unmodifiableMap(resultsByKey);
	}

	/**
	 * Clears all cached results.
	 */
	public void clear() {
		resultsByKey.clear();
	}
}
