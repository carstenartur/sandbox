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

import org.sandbox.jdt.internal.common.LibStandardNames;

/**
 * Constants for stream pipeline construction and functional conversion.
 * 
 * <p>
 * This class centralizes all string literals and magic values used throughout
 * the functional converter implementation. Common method names are imported from
 * {@link LibStandardNames} in sandbox_common, while converter-specific constants
 * are defined here.
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
 * @see LibStandardNames
 */
public final class StreamConstants {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private StreamConstants() {
		// Utility class - no instances allowed
	}

	// ========== Stream Methods (delegating to LibStandardNames) ==========

	/**
	 * Method name for creating a stream from a collection.
	 * @see LibStandardNames#METHOD_STREAM
	 */
	public static final String STREAM_METHOD = LibStandardNames.METHOD_STREAM;

	/**
	 * Method name for intermediate filter operation.
	 * @see LibStandardNames#METHOD_FILTER
	 */
	public static final String FILTER_METHOD = LibStandardNames.METHOD_FILTER;

	/**
	 * Method name for intermediate map operation.
	 * @see LibStandardNames#METHOD_MAP
	 */
	public static final String MAP_METHOD = LibStandardNames.METHOD_MAP;

	/**
	 * Method name for terminal reduce operation.
	 * @see LibStandardNames#METHOD_REDUCE
	 */
	public static final String REDUCE_METHOD = LibStandardNames.METHOD_REDUCE;

	/**
	 * Method name for terminal forEach operation.
	 * @see LibStandardNames#METHOD_FOREACH
	 */
	public static final String FOR_EACH_METHOD = LibStandardNames.METHOD_FOREACH;

	/**
	 * Method name for terminal forEachOrdered operation.
	 * @see LibStandardNames#METHOD_FOR_EACH_ORDERED
	 */
	public static final String FOR_EACH_ORDERED_METHOD = LibStandardNames.METHOD_FOR_EACH_ORDERED;

	/**
	 * Method name for terminal anyMatch operation.
	 * @see LibStandardNames#METHOD_ANY_MATCH
	 */
	public static final String ANY_MATCH_METHOD = LibStandardNames.METHOD_ANY_MATCH;

	/**
	 * Method name for terminal noneMatch operation.
	 * @see LibStandardNames#METHOD_NONE_MATCH
	 */
	public static final String NONE_MATCH_METHOD = LibStandardNames.METHOD_NONE_MATCH;

	/**
	 * Method name for terminal allMatch operation.
	 * @see LibStandardNames#METHOD_ALL_MATCH
	 */
	public static final String ALL_MATCH_METHOD = LibStandardNames.METHOD_ALL_MATCH;

	// ========== Converter-Specific Constants ==========

	/**
	 * Marker variable name used when the loop variable is not directly used in the
	 * lambda body.
	 * <p>
	 * Used in: {@code stream.forEach(_item -> sideEffect())}
	 * <p>
	 * The underscore prefix indicates an intentionally unused parameter.
	 */
	public static final String UNUSED_PARAMETER_NAME = "_item"; //$NON-NLS-1$

	/**
	 * Simple class name for Arrays utility class.
	 * <p>
	 * Used for creating streams from arrays: {@code Arrays.stream(array)}
	 */
	public static final String ARRAYS_CLASS_NAME = "Arrays"; //$NON-NLS-1$

	// ========== Math Class Constants (delegating to LibStandardNames) ==========

	/**
	 * Simple class name for Math utility class.
	 */
	public static final String MATH_CLASS_NAME = "Math"; //$NON-NLS-1$

	/**
	 * Method name for Math.max operation.
	 * @see LibStandardNames#METHOD_MAX
	 */
	public static final String MAX_METHOD_NAME = LibStandardNames.METHOD_MAX;

	/**
	 * Method name for Math.min operation.
	 * @see LibStandardNames#METHOD_MIN
	 */
	public static final String MIN_METHOD_NAME = LibStandardNames.METHOD_MIN;

	/**
	 * Fully qualified name of java.lang.Math class.
	 * @see LibStandardNames#JAVA_LANG_MATH
	 */
	public static final String JAVA_LANG_MATH = LibStandardNames.JAVA_LANG_MATH;

	// ========== Type Constants (delegating to LibStandardNames) ==========

	/**
	 * Fully qualified name of java.lang.String class.
	 * @see LibStandardNames#JAVA_LANG_STRING
	 */
	public static final String JAVA_LANG_STRING = LibStandardNames.JAVA_LANG_STRING;

	// ========== Method Reference Constants ==========

	/**
	 * Method reference for Integer sum reducer.
	 * <p>
	 * Used in: {@code stream.reduce(0, Integer::sum)}
	 */
	public static final String INTEGER_SUM = "Integer::sum"; //$NON-NLS-1$

	/**
	 * Method reference for Long sum reducer.
	 * <p>
	 * Used in: {@code stream.reduce(0L, Long::sum)}
	 */
	public static final String LONG_SUM = "Long::sum"; //$NON-NLS-1$

	/**
	 * Method reference for Double sum reducer.
	 * <p>
	 * Used in: {@code stream.reduce(0.0, Double::sum)}
	 */
	public static final String DOUBLE_SUM = "Double::sum"; //$NON-NLS-1$

	/**
	 * Method reference for Integer max reducer.
	 * <p>
	 * Used in: {@code stream.reduce(Integer.MIN_VALUE, Integer::max)}
	 */
	public static final String INTEGER_MAX = "Integer::max"; //$NON-NLS-1$

	/**
	 * Method reference for Integer min reducer.
	 * <p>
	 * Used in: {@code stream.reduce(Integer.MAX_VALUE, Integer::min)}
	 */
	public static final String INTEGER_MIN = "Integer::min"; //$NON-NLS-1$

	/**
	 * Method reference for Double max reducer.
	 * <p>
	 * Used in: {@code stream.reduce(-Double.MAX_VALUE, Double::max)}
	 */
	public static final String DOUBLE_MAX = "Double::max"; //$NON-NLS-1$

	/**
	 * Method reference for Double min reducer.
	 * <p>
	 * Used in: {@code stream.reduce(Double.MAX_VALUE, Double::min)}
	 */
	public static final String DOUBLE_MIN = "Double::min"; //$NON-NLS-1$

	/**
	 * Method reference for String concat reducer.
	 * <p>
	 * Used in: {@code stream.reduce("", String::concat)}
	 * <p>
	 * Only safe when accumulator variable has @NotNull annotation.
	 */
	public static final String STRING_CONCAT = "String::concat"; //$NON-NLS-1$
}
