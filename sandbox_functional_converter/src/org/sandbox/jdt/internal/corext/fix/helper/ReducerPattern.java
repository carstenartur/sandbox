/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.Statement;

/**
 * Encapsulates the result of detecting a reducer pattern in a statement.
 * 
 * <p>
 * A reducer pattern is an accumulation operation found in loops, such as
 * incrementing counters, summing values, or finding min/max. This record
 * captures all the information needed to convert such patterns into stream
 * reduce operations.
 * </p>
 * 
 * <p><b>Example Patterns:</b></p>
 * <pre>{@code
 * // INCREMENT pattern
 * count++;  // reducerType=INCREMENT, accumulatorVariable="count"
 * 
 * // SUM pattern
 * sum += value;  // reducerType=SUM, accumulatorVariable="sum"
 * 
 * // MAX pattern
 * max = Math.max(max, num);  // reducerType=MAX, accumulatorVariable="max"
 * }</pre>
 * 
 * @param statement            the statement containing the reducer pattern
 * @param accumulatorVariable  the name of the accumulator variable
 * @param reducerType          the type of reduction operation
 * @param accumulatorType      the type of the accumulator variable (e.g., "int",
 *                             "double")
 * 
 * @see ReducerPatternDetector
 * @see ProspectiveOperation.ReducerType
 */
public record ReducerPattern(
		Statement statement,
		String accumulatorVariable,
		ProspectiveOperation.ReducerType reducerType,
		String accumulatorType) {

	/**
	 * Creates a new ReducerPattern with validation.
	 * 
	 * @param statement           the statement containing the reducer pattern (must
	 *                            not be null)
	 * @param accumulatorVariable the name of the accumulator variable (must not be
	 *                            null)
	 * @param reducerType         the type of reduction operation (must not be null)
	 * @param accumulatorType     the type of the accumulator variable (may be null)
	 * @throws IllegalArgumentException if statement, accumulatorVariable, or
	 *                                  reducerType is null
	 */
	public ReducerPattern {
		if (statement == null) {
			throw new IllegalArgumentException("statement cannot be null");
		}
		if (accumulatorVariable == null) {
			throw new IllegalArgumentException("accumulatorVariable cannot be null");
		}
		if (reducerType == null) {
			throw new IllegalArgumentException("reducerType cannot be null");
		}
	}
}
