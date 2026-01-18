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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.eclipse.jdt.core.dom.*;

/**
 * Fast unit tests for StreamPipelineBuilder analysis logic.
 * These tests run without Tycho/Eclipse runtime.
 */
public class StreamPipelineBuilderTest {
	
	@Test
	void testSimpleForEachIsAnalyzable() {
		String code = """
			import java.util.List;
			class Test {
				void test(List<String> list) {
					for (String s : list) {
						System.out.println(s);
					}
				}
			}
			""";
		CompilationUnit cu = TestASTHelper.parse(code);
		EnhancedForStatement loop = TestASTHelper.findFirstEnhancedFor(cu);
		
		assertNotNull(loop, "Should find an enhanced for loop");
		
		PreconditionsChecker pc = new PreconditionsChecker(loop, cu);
		assertTrue(pc.isSafeToRefactor(), "Simple forEach loop should be safe to refactor");
		
		StreamPipelineBuilder builder = new StreamPipelineBuilder(loop, pc);
		assertTrue(builder.analyze(), "Simple forEach loop should be analyzable");
	}
	
	@Test
	void testLoopWithBreakIsNotSafe() {
		String code = """
			import java.util.List;
			class Test {
				void test(List<String> list) {
					for (String s : list) {
						if (s.isEmpty()) break;
						System.out.println(s);
					}
				}
			}
			""";
		CompilationUnit cu = TestASTHelper.parse(code);
		EnhancedForStatement loop = TestASTHelper.findFirstEnhancedFor(cu);
		
		assertNotNull(loop, "Should find an enhanced for loop");
		
		PreconditionsChecker pc = new PreconditionsChecker(loop, cu);
		assertFalse(pc.isSafeToRefactor(), "Loop with break should not be safe to refactor");
	}
	
	@Test
	void testLoopWithContinueIsSafe() {
		String code = """
			import java.util.List;
			class Test {
				void test(List<String> list) {
					for (String s : list) {
						if (s.isEmpty()) continue;
						System.out.println(s);
					}
				}
			}
			""";
		CompilationUnit cu = TestASTHelper.parse(code);
		EnhancedForStatement loop = TestASTHelper.findFirstEnhancedFor(cu);
		
		assertNotNull(loop, "Should find an enhanced for loop");
		
		PreconditionsChecker pc = new PreconditionsChecker(loop, cu);
		assertTrue(pc.isSafeToRefactor(), "Loop with continue should be safe to refactor (converts to filter)");
	}
	
	@Test
	void testLoopWithReducerIsDetected() {
		String code = """
			import java.util.List;
			class Test {
				void test(List<Integer> list) {
					int sum = 0;
					for (Integer i : list) {
						sum += i;
					}
				}
			}
			""";
		CompilationUnit cu = TestASTHelper.parse(code);
		EnhancedForStatement loop = TestASTHelper.findFirstEnhancedFor(cu);
		
		assertNotNull(loop, "Should find an enhanced for loop");
		
		PreconditionsChecker pc = new PreconditionsChecker(loop, cu);
		assertTrue(pc.isSafeToRefactor(), "Loop with reducer should be safe to refactor");
		assertTrue(pc.isReducer(), "Should detect reducer pattern");
	}
	
	@Test
	void testNestedLoopIsNotSafe() {
		String code = """
			import java.util.List;
			class Test {
				void test(List<List<String>> lists) {
					for (List<String> list : lists) {
						for (String s : list) {
							System.out.println(s);
						}
					}
				}
			}
			""";
		CompilationUnit cu = TestASTHelper.parse(code);
		EnhancedForStatement loop = TestASTHelper.findFirstEnhancedFor(cu);
		
		assertNotNull(loop, "Should find an enhanced for loop");
		
		PreconditionsChecker pc = new PreconditionsChecker(loop, cu);
		assertFalse(pc.isSafeToRefactor(), "Nested loop should not be safe to refactor");
	}
}
