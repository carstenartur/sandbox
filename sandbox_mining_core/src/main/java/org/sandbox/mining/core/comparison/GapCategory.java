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
package org.sandbox.mining.core.comparison;

/**
 * Categories of gaps identified when comparing Gemini mining results
 * against a reference evaluation (e.g. Copilot).
 *
 * <p>Includes both coarse-grained categories (used in programmatic comparison)
 * and fine-grained categories (used in manual gap analysis from comparison runs).</p>
 */
public enum GapCategory {

	/** Gemini missed a relevant commit that the reference found */
	MISSED_RELEVANT,

	/** Gemini assigned wrong traffic light color */
	WRONG_TRAFFIC_LIGHT,

	/** Reference produced a valid DSL rule where Gemini did not */
	MISSING_DSL_RULE,

	/** Gemini produced an invalid DSL rule where reference was valid */
	INVALID_DSL_RULE,

	/** Gemini and reference disagree on the category */
	CATEGORY_MISMATCH,

	/** Reference found a pattern Gemini lacks context for (e.g. Eclipse API) */
	MISSING_API_CONTEXT,

	/** Reference found a pattern requiring type hierarchy info Gemini lacks */
	MISSING_TYPE_CONTEXT,

	// --- Fine-grained categories for iterative improvement (Step 5 from issue #884) ---

	/** Missing type context — add type information to eclipse-api-context.md */
	TYP_KONTEXT,

	/** Missing API version context — add Java version info to eclipse-api-context.md */
	API_VERSION,

	/** Missing guard knowledge — add guard examples to dsl-explanation.md */
	GUARD_WISSEN,

	/** DSL syntax errors — add negative examples to dsl-explanation.md */
	DSL_SYNTAX,

	/** Insufficient generalization — add generalization examples to mining-examples.md */
	GENERALISIERUNG,

	/** Missed duplicate — improve existing .sandbox-hint descriptions */
	DUPLIKAT_ERKENNUNG,

	/** Insufficient context usage — extend PromptBuilder context sections */
	KONTEXT_NUTZUNG;

	/**
	 * Returns the suggested action for this gap category.
	 *
	 * @return human-readable action description
	 */
	public String suggestedAction() {
		return switch (this) {
			case MISSED_RELEVANT -> "Review prompt relevance criteria"; //$NON-NLS-1$
			case WRONG_TRAFFIC_LIGHT -> "Calibrate traffic light scoring in prompt examples"; //$NON-NLS-1$
			case MISSING_DSL_RULE -> "Add DSL rule examples to mining-examples.md"; //$NON-NLS-1$
			case INVALID_DSL_RULE -> "Add negative DSL examples to dsl-explanation.md"; //$NON-NLS-1$
			case CATEGORY_MISMATCH -> "Improve category definitions in prompt"; //$NON-NLS-1$
			case MISSING_API_CONTEXT -> "Add API migration patterns to eclipse-api-context.md"; //$NON-NLS-1$
			case MISSING_TYPE_CONTEXT -> "Add type hierarchy info to eclipse-api-context.md"; //$NON-NLS-1$
			case TYP_KONTEXT -> "Add type information to eclipse-api-context.md"; //$NON-NLS-1$
			case API_VERSION -> "Add Java version context to eclipse-api-context.md"; //$NON-NLS-1$
			case GUARD_WISSEN -> "Add guard examples to dsl-explanation.md"; //$NON-NLS-1$
			case DSL_SYNTAX -> "Add negative examples to dsl-explanation.md"; //$NON-NLS-1$
			case GENERALISIERUNG -> "Add generalization examples to mining-examples.md"; //$NON-NLS-1$
			case DUPLIKAT_ERKENNUNG -> "Improve existing .sandbox-hint descriptions for better matching"; //$NON-NLS-1$
			case KONTEXT_NUTZUNG -> "Extend PromptBuilder context sections"; //$NON-NLS-1$
		};
	}
}
