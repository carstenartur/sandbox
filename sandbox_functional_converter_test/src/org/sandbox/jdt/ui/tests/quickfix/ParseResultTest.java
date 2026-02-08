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
import org.sandbox.jdt.internal.corext.fix.helper.LoopBodyParser;

/**
 * Unit tests for {@link LoopBodyParser.ParseResult}.
 * 
 * <p>These tests verify the ParseResult inner class used to communicate
 * parsing results and state changes during loop body analysis.</p>
 * 
 * @see LoopBodyParser
 * @see LoopBodyParser.ParseResult
 */
@DisplayName("LoopBodyParser.ParseResult Tests")
public class ParseResultTest {

	@Nested
	@DisplayName("Constructor with variable name")
	class VariableNameConstructorTests {

		@Test
		@DisplayName("Creates result with current variable name")
		void createsResultWithCurrentVariableName() {
			LoopBodyParser.ParseResult result = new LoopBodyParser.ParseResult("item");
			
			assertEquals("item", result.getCurrentVarName());
			assertFalse(result.shouldAbort());
			assertEquals(-1, result.getSkipToIndex());
		}

		@Test
		@DisplayName("Allows null variable name")
		void allowsNullVariableName() {
			LoopBodyParser.ParseResult result = new LoopBodyParser.ParseResult(null);
			
			assertNull(result.getCurrentVarName());
			assertFalse(result.shouldAbort());
		}
	}

	@Nested
	@DisplayName("Constructor with skip index")
	class SkipIndexConstructorTests {

		@Test
		@DisplayName("Creates result with skip index")
		void createsResultWithSkipIndex() {
			LoopBodyParser.ParseResult result = new LoopBodyParser.ParseResult("item", 5);
			
			assertEquals("item", result.getCurrentVarName());
			assertFalse(result.shouldAbort());
			assertEquals(5, result.getSkipToIndex());
		}

		@Test
		@DisplayName("Skip index 0 is valid")
		void skipIndexZeroIsValid() {
			LoopBodyParser.ParseResult result = new LoopBodyParser.ParseResult("item", 0);
			
			assertEquals(0, result.getSkipToIndex());
		}
	}

	@Nested
	@DisplayName("abort() factory method")
	class AbortFactoryMethodTests {

		@Test
		@DisplayName("Creates abort result")
		void createsAbortResult() {
			LoopBodyParser.ParseResult result = LoopBodyParser.ParseResult.abort();
			
			assertTrue(result.shouldAbort());
			assertNull(result.getCurrentVarName());
			assertEquals(-1, result.getSkipToIndex());
		}

		@Test
		@DisplayName("Multiple abort calls return equivalent results")
		void multipleAbortCallsReturnEquivalentResults() {
			LoopBodyParser.ParseResult result1 = LoopBodyParser.ParseResult.abort();
			LoopBodyParser.ParseResult result2 = LoopBodyParser.ParseResult.abort();
			
			assertTrue(result1.shouldAbort() == result2.shouldAbort());
			assertEquals(result1.getCurrentVarName(), result2.getCurrentVarName());
			assertEquals(result1.getSkipToIndex(), result2.getSkipToIndex());
		}
	}

	@Nested
	@DisplayName("State checks")
	class StateCheckTests {

		@Test
		@DisplayName("Non-abort result has shouldAbort() == false")
		void nonAbortResultHasShouldAbortFalse() {
			LoopBodyParser.ParseResult result = new LoopBodyParser.ParseResult("item");
			
			assertFalse(result.shouldAbort());
		}

		@Test
		@DisplayName("Abort result has shouldAbort() == true")
		void abortResultHasShouldAbortTrue() {
			LoopBodyParser.ParseResult result = LoopBodyParser.ParseResult.abort();
			
			assertTrue(result.shouldAbort());
		}

		@Test
		@DisplayName("Default skip index is -1")
		void defaultSkipIndexIsNegativeOne() {
			LoopBodyParser.ParseResult result = new LoopBodyParser.ParseResult("item");
			
			assertEquals(-1, result.getSkipToIndex());
		}
	}
}
