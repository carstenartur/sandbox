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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.StreamConstants;
import org.sandbox.jdt.internal.common.LibStandardNames;

/**
 * Unit tests for {@link StreamConstants}.
 * 
 * <p>These tests verify the stream-related constants and their delegation
 * to {@link LibStandardNames}.</p>
 * 
 * @see StreamConstants
 * @see LibStandardNames
 */
@DisplayName("StreamConstants Tests")
public class StreamConstantsTest {

	@Nested
	@DisplayName("Stream Method Constants")
	class StreamMethodConstantsTests {

		@Test
		@DisplayName("STREAM_METHOD delegates to LibStandardNames")
		void streamMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_STREAM, StreamConstants.STREAM_METHOD);
			assertEquals("stream", StreamConstants.STREAM_METHOD);
		}

		@Test
		@DisplayName("FILTER_METHOD delegates to LibStandardNames")
		void filterMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_FILTER, StreamConstants.FILTER_METHOD);
			assertEquals("filter", StreamConstants.FILTER_METHOD);
		}

		@Test
		@DisplayName("MAP_METHOD delegates to LibStandardNames")
		void mapMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_MAP, StreamConstants.MAP_METHOD);
			assertEquals("map", StreamConstants.MAP_METHOD);
		}

		@Test
		@DisplayName("REDUCE_METHOD delegates to LibStandardNames")
		void reduceMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_REDUCE, StreamConstants.REDUCE_METHOD);
			assertEquals("reduce", StreamConstants.REDUCE_METHOD);
		}

		@Test
		@DisplayName("FOR_EACH_METHOD delegates to LibStandardNames")
		void forEachMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_FOREACH, StreamConstants.FOR_EACH_METHOD);
			assertEquals("forEach", StreamConstants.FOR_EACH_METHOD);
		}

		@Test
		@DisplayName("FOR_EACH_ORDERED_METHOD delegates to LibStandardNames")
		void forEachOrderedMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_FOR_EACH_ORDERED, StreamConstants.FOR_EACH_ORDERED_METHOD);
			assertEquals("forEachOrdered", StreamConstants.FOR_EACH_ORDERED_METHOD);
		}

		@Test
		@DisplayName("ANY_MATCH_METHOD delegates to LibStandardNames")
		void anyMatchMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_ANY_MATCH, StreamConstants.ANY_MATCH_METHOD);
			assertEquals("anyMatch", StreamConstants.ANY_MATCH_METHOD);
		}

		@Test
		@DisplayName("NONE_MATCH_METHOD delegates to LibStandardNames")
		void noneMatchMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_NONE_MATCH, StreamConstants.NONE_MATCH_METHOD);
			assertEquals("noneMatch", StreamConstants.NONE_MATCH_METHOD);
		}

		@Test
		@DisplayName("ALL_MATCH_METHOD delegates to LibStandardNames")
		void allMatchMethodDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_ALL_MATCH, StreamConstants.ALL_MATCH_METHOD);
			assertEquals("allMatch", StreamConstants.ALL_MATCH_METHOD);
		}
	}

	@Nested
	@DisplayName("Math Constants")
	class MathConstantsTests {

		@Test
		@DisplayName("MAX_METHOD_NAME delegates to LibStandardNames")
		void maxMethodNameDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_MAX, StreamConstants.MAX_METHOD_NAME);
			assertEquals("max", StreamConstants.MAX_METHOD_NAME);
		}

		@Test
		@DisplayName("MIN_METHOD_NAME delegates to LibStandardNames")
		void minMethodNameDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.METHOD_MIN, StreamConstants.MIN_METHOD_NAME);
			assertEquals("min", StreamConstants.MIN_METHOD_NAME);
		}

		@Test
		@DisplayName("JAVA_LANG_MATH delegates to LibStandardNames")
		void javaLangMathDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.JAVA_LANG_MATH, StreamConstants.JAVA_LANG_MATH);
			assertEquals("java.lang.Math", StreamConstants.JAVA_LANG_MATH);
		}
	}

	@Nested
	@DisplayName("Type Constants")
	class TypeConstantsTests {

		@Test
		@DisplayName("JAVA_LANG_STRING delegates to LibStandardNames")
		void javaLangStringDelegatesToLibStandardNames() {
			assertEquals(LibStandardNames.JAVA_LANG_STRING, StreamConstants.JAVA_LANG_STRING);
			assertEquals("java.lang.String", StreamConstants.JAVA_LANG_STRING);
		}
	}

	@Nested
	@DisplayName("Converter-Specific Constants")
	class ConverterSpecificConstantsTests {

		@Test
		@DisplayName("UNUSED_PARAMETER_NAME is correct")
		void unusedParameterNameIsCorrect() {
			assertEquals("_item", StreamConstants.UNUSED_PARAMETER_NAME);
		}

		@Test
		@DisplayName("MATH_CLASS_NAME is correct")
		void mathClassNameIsCorrect() {
			assertEquals("Math", StreamConstants.MATH_CLASS_NAME);
		}
	}

	@Nested
	@DisplayName("Method Reference Constants")
	class MethodReferenceConstantsTests {

		@Test
		@DisplayName("INTEGER_SUM is correct")
		void integerSumIsCorrect() {
			assertEquals("Integer::sum", StreamConstants.INTEGER_SUM);
		}

		@Test
		@DisplayName("LONG_SUM is correct")
		void longSumIsCorrect() {
			assertEquals("Long::sum", StreamConstants.LONG_SUM);
		}

		@Test
		@DisplayName("DOUBLE_SUM is correct")
		void doubleSumIsCorrect() {
			assertEquals("Double::sum", StreamConstants.DOUBLE_SUM);
		}

		@Test
		@DisplayName("INTEGER_MAX is correct")
		void integerMaxIsCorrect() {
			assertEquals("Integer::max", StreamConstants.INTEGER_MAX);
		}

		@Test
		@DisplayName("INTEGER_MIN is correct")
		void integerMinIsCorrect() {
			assertEquals("Integer::min", StreamConstants.INTEGER_MIN);
		}

		@Test
		@DisplayName("STRING_CONCAT is correct")
		void stringConcatIsCorrect() {
			assertEquals("String::concat", StreamConstants.STRING_CONCAT);
		}
	}
}
