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
import org.sandbox.jdt.internal.common.LibStandardNames;

/**
 * Unit tests for {@link LibStandardNames}.
 * 
 * <p>These tests verify the standard method and field name constants used
 * across sandbox cleanup plugins.</p>
 * 
 * @see LibStandardNames
 */
@DisplayName("LibStandardNames Tests")
public class LibStandardNamesTest {

	@Nested
	@DisplayName("Stream API Method Constants")
	class StreamApiMethodTests {

		@Test
		@DisplayName("METHOD_STREAM is 'stream'")
		void methodStreamIsStream() {
			assertEquals("stream", LibStandardNames.METHOD_STREAM);
		}

		@Test
		@DisplayName("METHOD_FILTER is 'filter'")
		void methodFilterIsFilter() {
			assertEquals("filter", LibStandardNames.METHOD_FILTER);
		}

		@Test
		@DisplayName("METHOD_MAP is 'map'")
		void methodMapIsMap() {
			assertEquals("map", LibStandardNames.METHOD_MAP);
		}

		@Test
		@DisplayName("METHOD_REDUCE is 'reduce'")
		void methodReduceIsReduce() {
			assertEquals("reduce", LibStandardNames.METHOD_REDUCE);
		}

		@Test
		@DisplayName("METHOD_FOREACH is 'forEach'")
		void methodForeachIsForEach() {
			assertEquals("forEach", LibStandardNames.METHOD_FOREACH);
		}

		@Test
		@DisplayName("METHOD_FOR_EACH_ORDERED is 'forEachOrdered'")
		void methodForEachOrderedIsForEachOrdered() {
			assertEquals("forEachOrdered", LibStandardNames.METHOD_FOR_EACH_ORDERED);
		}

		@Test
		@DisplayName("METHOD_ANY_MATCH is 'anyMatch'")
		void methodAnyMatchIsAnyMatch() {
			assertEquals("anyMatch", LibStandardNames.METHOD_ANY_MATCH);
		}

		@Test
		@DisplayName("METHOD_NONE_MATCH is 'noneMatch'")
		void methodNoneMatchIsNoneMatch() {
			assertEquals("noneMatch", LibStandardNames.METHOD_NONE_MATCH);
		}

		@Test
		@DisplayName("METHOD_ALL_MATCH is 'allMatch'")
		void methodAllMatchIsAllMatch() {
			assertEquals("allMatch", LibStandardNames.METHOD_ALL_MATCH);
		}
	}

	@Nested
	@DisplayName("Math Method Constants")
	class MathMethodTests {

		@Test
		@DisplayName("METHOD_MAX is 'max'")
		void methodMaxIsMax() {
			assertEquals("max", LibStandardNames.METHOD_MAX);
		}

		@Test
		@DisplayName("METHOD_MIN is 'min'")
		void methodMinIsMin() {
			assertEquals("min", LibStandardNames.METHOD_MIN);
		}

		@Test
		@DisplayName("METHOD_SUM is 'sum'")
		void methodSumIsSum() {
			assertEquals("sum", LibStandardNames.METHOD_SUM);
		}

		@Test
		@DisplayName("METHOD_CONCAT is 'concat'")
		void methodConcatIsConcat() {
			assertEquals("concat", LibStandardNames.METHOD_CONCAT);
		}
	}

	@Nested
	@DisplayName("Fully Qualified Type Names")
	class TypeNameTests {

		@Test
		@DisplayName("JAVA_LANG_MATH is 'java.lang.Math'")
		void javaLangMathIsCorrect() {
			assertEquals("java.lang.Math", LibStandardNames.JAVA_LANG_MATH);
		}

		@Test
		@DisplayName("JAVA_LANG_STRING is 'java.lang.String'")
		void javaLangStringIsCorrect() {
			assertEquals("java.lang.String", LibStandardNames.JAVA_LANG_STRING);
		}

		@Test
		@DisplayName("JAVA_LANG_INTEGER is 'java.lang.Integer'")
		void javaLangIntegerIsCorrect() {
			assertEquals("java.lang.Integer", LibStandardNames.JAVA_LANG_INTEGER);
		}

		@Test
		@DisplayName("JAVA_LANG_LONG is 'java.lang.Long'")
		void javaLangLongIsCorrect() {
			assertEquals("java.lang.Long", LibStandardNames.JAVA_LANG_LONG);
		}

		@Test
		@DisplayName("JAVA_LANG_DOUBLE is 'java.lang.Double'")
		void javaLangDoubleIsCorrect() {
			assertEquals("java.lang.Double", LibStandardNames.JAVA_LANG_DOUBLE);
		}
	}

	@Nested
	@DisplayName("Existing Method Constants")
	class ExistingMethodTests {

		@Test
		@DisplayName("METHOD_GET_PROPERTY is 'getProperty'")
		void methodGetPropertyIsGetProperty() {
			assertEquals("getProperty", LibStandardNames.METHOD_GET_PROPERTY);
		}

		@Test
		@DisplayName("METHOD_DEFAULT_CHARSET is 'defaultCharset'")
		void methodDefaultCharsetIsDefaultCharset() {
			assertEquals("defaultCharset", LibStandardNames.METHOD_DEFAULT_CHARSET);
		}

		@Test
		@DisplayName("METHOD_LINE_SEPARATOR is 'lineSeparator'")
		void methodLineSeparatorIsLineSeparator() {
			assertEquals("lineSeparator", LibStandardNames.METHOD_LINE_SEPARATOR);
		}

		@Test
		@DisplayName("METHOD_TOSTRING is 'toString'")
		void methodToStringIsToString() {
			assertEquals("toString", LibStandardNames.METHOD_TOSTRING);
		}

		@Test
		@DisplayName("FIELD_UTF8 is 'UTF_8'")
		void fieldUtf8IsUtf8() {
			assertEquals("UTF_8", LibStandardNames.FIELD_UTF8);
		}
	}
}
