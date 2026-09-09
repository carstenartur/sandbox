/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;

/**
 * Immutable source-discovery evidence for one JUnit 4 Parameterized component.
 * This is a prepared plan, not permission to apply a migration: an executor must
 * still prove its target strategy with the JDT runtime oracle and revalidate the
 * complete source scope. No AST or Java-model elements escape discovery.
 */
public record JUnit4ParameterizedPlan(NodeKey testClass, SemanticRewritePlan semanticPlan,
		Map<NodeKey, String> compilationUnits, Map<String, String> sourceFingerprints) {

	public static final String CONTRACT= "junit4-parameterized"; //$NON-NLS-1$
	public static final String RUNNER_CLASS= "JUNIT4_PARAMETERIZED_CLASS"; //$NON-NLS-1$
	public static final String PROVIDER= "JUNIT4_PARAMETERIZED_PROVIDER"; //$NON-NLS-1$
	public static final String CONSTRUCTOR= "JUNIT4_PARAMETERIZED_CONSTRUCTOR"; //$NON-NLS-1$
	public static final String PARAMETER= "JUNIT4_PARAMETERIZED_PARAMETER"; //$NON-NLS-1$
	public static final String FIELD= "JUNIT4_PARAMETERIZED_FIELD"; //$NON-NLS-1$
	public static final String TEST= "JUNIT4_PARAMETERIZED_TEST"; //$NON-NLS-1$
	public static final String HAS_PROVIDER= "provider"; //$NON-NLS-1$
	public static final String HAS_CONSTRUCTOR= "constructor"; //$NON-NLS-1$
	public static final String HAS_PARAMETER= "constructorParameter"; //$NON-NLS-1$
	public static final String HAS_FIELD= "injectedField"; //$NON-NLS-1$
	public static final String HAS_TEST= "testMethod"; //$NON-NLS-1$
	public static final String DELEGATES_TO= "delegatesTo"; //$NON-NLS-1$

	public JUnit4ParameterizedPlan {
		Objects.requireNonNull(testClass);
		Objects.requireNonNull(semanticPlan);
		if (!semanticPlan.satisfiesContract(CONTRACT)) {
			throw new IllegalArgumentException("Expected the junit4-parameterized contract"); //$NON-NLS-1$
		}
		compilationUnits= Map.copyOf(compilationUnits);
		sourceFingerprints= Map.copyOf(sourceFingerprints);
		if (!semanticPlan.rolesByNode().getOrDefault(testClass, java.util.Set.of()).contains(RUNNER_CLASS)
				|| !compilationUnits.keySet().containsAll(semanticPlan.rolesByNode().keySet())
				|| !sourceFingerprints.keySet().containsAll(compilationUnits.values())) {
			throw new IllegalArgumentException("Incomplete Parameterized source evidence"); //$NON-NLS-1$
		}
	}

	/** Checks exact source text and scope membership, including additions and deletions. */
	public boolean isCurrent(Map<String, String> currentSources) {
		return currentSources != null && currentSources.keySet().equals(sourceFingerprints.keySet())
				&& currentSources.entrySet().stream().allMatch(entry -> entry.getValue() != null
						&& fingerprint(entry.getValue()).equals(sourceFingerprints.get(entry.getKey())));
	}

	static String fingerprint(String source) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
					.digest(source.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Java must provide SHA-256", e); //$NON-NLS-1$
		}
	}
}
