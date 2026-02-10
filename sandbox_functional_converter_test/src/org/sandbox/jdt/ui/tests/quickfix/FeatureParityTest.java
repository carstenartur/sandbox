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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests to verify that both the original constant ({@code USEFUNCTIONALLOOP_CLEANUP})
 * and the V2 constant ({@code USEFUNCTIONALLOOP_CLEANUP_V2}) produce identical results
 * through the unified implementation.
 * 
 * <p>V1 and V2 have been consolidated into a single implementation. These tests
 * validate backward compatibility: enabling either constant should produce the same
 * transformation output.</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 */
public class FeatureParityTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Helper method to verify both constants produce the same output.
	 * 
	 * @param input the original Java code
	 * @param expectedOutput the expected code after transformation
	 * @throws CoreException on errors during refactoring
	 */
	private void assertParityBetweenConstants(String input, String expectedOutput) 
			throws CoreException {
		IPackageFragment pack = context.getSourceFolder()
			.createPackageFragment("test1", false, null);
		
		// Test with original constant
		ICompilationUnit cuV1 = pack.createCompilationUnit("TestV1.java", 
			input.replace("MyTest", "TestV1"), false, null);
		
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2);
		context.assertRefactoringResultAsExpected(
			new ICompilationUnit[] { cuV1 }, 
			new String[] { expectedOutput.replace("MyTest", "TestV1") }, 
			null);
		
		// Test with V2 constant (backward compatibility)
		ICompilationUnit cuV2 = pack.createCompilationUnit("TestV2.java",
			input.replace("MyTest", "TestV2"), false, null);
		
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2);
		context.assertRefactoringResultAsExpected(
			new ICompilationUnit[] { cuV2 }, 
			new String[] { expectedOutput.replace("MyTest", "TestV2") }, 
			null);
	}

	@Test
	void parity_SimpleForEachConversion() throws CoreException {
		String input = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					for (String item : list) {
						System.out.println(item);
					}
				}
			}""";

		String expected = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					list.forEach(item -> System.out.println(item));
				}
			}""";

		assertParityBetweenConstants(input, expected);
	}

	/**
	 * Filter pattern test - now enabled since V2 supports all V1 patterns
	 * through the Refactorer fallback.
	 */
	@Test
	void parity_FilterPattern() throws CoreException {
		String input = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					for (String item : list) {
						if (item != null) {
							System.out.println(item);
						}
					}
				}
			}""";

		String expected = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					list.stream().filter(item -> (item != null)).forEachOrdered(item -> System.out.println(item));
				}
			}""";

		assertParityBetweenConstants(input, expected);
	}

	@Test
	void parity_BreakShouldNotConvert() throws CoreException {
		String input = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					for (String item : list) {
						if (item.isEmpty()) break;
						System.out.println(item);
					}
				}
			}""";

		// Should NOT be converted - Input = Output
		assertParityBetweenConstants(input, input);
	}
}
