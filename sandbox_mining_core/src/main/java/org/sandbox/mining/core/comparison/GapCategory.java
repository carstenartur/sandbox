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
	MISSING_TYPE_CONTEXT
}
