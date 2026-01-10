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

/**
 * Constants for stream pipeline construction and functional conversion.
 * 
 * <p>
 * This class centralizes all string literals and magic values used throughout
 * the functional converter implementation. Centralizing these constants:
 * <ul>
 * <li>Reduces code duplication and maintenance burden</li>
 * <li>Makes refactoring easier (change in one place)</li>
 * <li>Improves code clarity by giving meaningful names to literals</li>
 * <li>Prevents typos in repeated string literals</li>
 * </ul>
 * </p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Instead of:
 * methodInvocation.setName(ast.newSimpleName("stream"));
 * 
 * // Use:
 * methodInvocation.setName(ast.newSimpleName(StreamConstants.STREAM_METHOD));
 * }</pre>
 * 
 * @see StreamPipelineBuilder
 * @see ProspectiveOperation
 */
public final class StreamConstants {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private StreamConstants() {
		// Utility class - no instances allowed
	}

	// ========== Stream Methods ==========

	/**
	 * Method name for creating a stream from a collection.
	 * <p>
	 * Used in: {@code collection.stream()}
	 */
	public static final String STREAM_METHOD = "stream";

	/**
	 * Method name for intermediate filter operation.
	 * <p>
	 * Used in: {@code stream.filter(predicate)}
	 */
	public static final String FILTER_METHOD = "filter";

	/**
	 * Method name for intermediate map operation.
	 * <p>
	 * Used in: {@code stream.map(function)}
	 */
	public static final String MAP_METHOD = "map";

	/**
	 * Method name for terminal reduce operation.
	 * <p>
	 * Used in: {@code stream.reduce(identity, accumulator)}
	 */
	public static final String REDUCE_METHOD = "reduce";

	/**
	 * Method name for terminal forEach operation.
	 * <p>
	 * Used in: {@code stream.forEach(consumer)}
	 */
	public static final String FOR_EACH_METHOD = "forEach";

	/**
	 * Method name for terminal forEachOrdered operation.
	 * <p>
	 * Used in: {@code stream.forEachOrdered(consumer)}
	 * <p>
	 * Guarantees processing order for parallel streams.
	 */
	public static final String FOR_EACH_ORDERED_METHOD = "forEachOrdered";

	/**
	 * Method name for terminal anyMatch operation.
	 * <p>
	 * Used in: {@code stream.anyMatch(predicate)}
	 * <p>
	 * Returns true if any element matches the predicate.
	 */
	public static final String ANY_MATCH_METHOD = "anyMatch";

	/**
	 * Method name for terminal noneMatch operation.
	 * <p>
	 * Used in: {@code stream.noneMatch(predicate)}
	 * <p>
	 * Returns true if no elements match the predicate.
	 */
	public static final String NONE_MATCH_METHOD = "noneMatch";

	/**
	 * Method name for terminal allMatch operation.
	 * <p>
	 * Used in: {@code stream.allMatch(predicate)}
	 * <p>
	 * Returns true if all elements match the predicate.
	 */
	public static final String ALL_MATCH_METHOD = "allMatch";

	// ========== Special Values ==========

	/**
	 * Marker variable name used when the loop variable is not directly used in the
	 * lambda body.
	 * <p>
	 * Used in: {@code stream.forEach(_item -> sideEffect())}
	 * <p>
	 * The underscore prefix indicates an intentionally unused parameter.
	 */
	public static final String UNUSED_PARAMETER_NAME = "_item";

	// ========== Math Class Constants ==========

	/**
	 * Simple class name for Math utility class.
	 * <p>
	 * Used in: {@code Math.max(...)}, {@code Math.min(...)}
	 */
	public static final String MATH_CLASS_NAME = "Math";

	/**
	 * Method name for Math.max operation.
	 * <p>
	 * Used in: {@code Math.max(a, b)}
	 */
	public static final String MAX_METHOD_NAME = "max";

	/**
	 * Method name for Math.min operation.
	 * <p>
	 * Used in: {@code Math.min(a, b)}
	 */
	public static final String MIN_METHOD_NAME = "min";

	/**
	 * Fully qualified name of java.lang.Math class.
	 * <p>
	 * Used for type binding comparisons to distinguish Math methods from other
	 * classes with similar method names.
	 */
	public static final String JAVA_LANG_MATH = "java.lang.Math";

	// ========== Type Constants ==========

	/**
	 * Fully qualified name of java.lang.String class.
	 * <p>
	 * Used for type binding comparisons to distinguish String concatenation from
	 * numeric addition when analyzing += operators.
	 */
	public static final String JAVA_LANG_STRING = "java.lang.String";

	// ========== Method Reference Constants ==========

	/**
	 * Method reference for Integer sum reducer.
	 * <p>
	 * Used in: {@code stream.reduce(0, Integer::sum)}
	 */
	public static final String INTEGER_SUM = "Integer::sum";

	/**
	 * Method reference for Long sum reducer.
	 * <p>
	 * Used in: {@code stream.reduce(0L, Long::sum)}
	 */
	public static final String LONG_SUM = "Long::sum";

	/**
	 * Method reference for Double sum reducer.
	 * <p>
	 * Used in: {@code stream.reduce(0.0, Double::sum)}
	 */
	public static final String DOUBLE_SUM = "Double::sum";

	/**
	 * Method reference for Integer max reducer.
	 * <p>
	 * Used in: {@code stream.reduce(Integer.MIN_VALUE, Integer::max)}
	 */
	public static final String INTEGER_MAX = "Integer::max";

	/**
	 * Method reference for Integer min reducer.
	 * <p>
	 * Used in: {@code stream.reduce(Integer.MAX_VALUE, Integer::min)}
	 */
	public static final String INTEGER_MIN = "Integer::min";

	/**
	 * Method reference for Double max reducer.
	 * <p>
	 * Used in: {@code stream.reduce(-Double.MAX_VALUE, Double::max)}
	 */
	public static final String DOUBLE_MAX = "Double::max";

	/**
	 * Method reference for Double min reducer.
	 * <p>
	 * Used in: {@code stream.reduce(Double.MAX_VALUE, Double::min)}
	 */
	public static final String DOUBLE_MIN = "Double::min";

	/**
	 * Method reference for String concat reducer.
	 * <p>
	 * Used in: {@code stream.reduce("", String::concat)}
	 * <p>
	 * Only safe when accumulator variable has @NotNull annotation.
	 */
	public static final String STRING_CONCAT = "String::concat";
}
