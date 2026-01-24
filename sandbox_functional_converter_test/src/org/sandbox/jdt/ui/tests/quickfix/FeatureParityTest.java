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
 * Tests zur Sicherstellung der Feature-Parität zwischen V1 und V2 Implementierung.
 * 
 * <p><b>Status Phase 6:</b> V2 hat noch KEINE Feature-Parität mit V1. Diese Tests
 * sind für Phase 7 (Feature-Parität) vorgesehen.</p>
 * 
 * <p>Aktuell sind die meisten Tests disabled, da V2 nur einfache forEach-Konvertierungen
 * unterstützt und eine andere Ausgabe produziert als V1 (stream-basiert vs. direkt forEach).</p>
 * 
 * <p>Jeder Test führt beide Implementierungen aus und vergleicht die Ergebnisse.
 * Diese Tests werden aktiviert, sobald V2 Feature-Parität erreicht hat (Phase 7).</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453 Phase 7</a>
 */
public class FeatureParityTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Helper-Methode um V1 und V2 Ergebnisse zu vergleichen.
	 * 
	 * @param input Der ursprüngliche Java-Code
	 * @param expectedOutput Der erwartete Code nach der Transformation
	 * @throws CoreException bei Fehlern während der Refactoring-Ausführung
	 */
	private void assertParityBetweenV1AndV2(String input, String expectedOutput) 
			throws CoreException {
		IPackageFragment pack = context.getSourceFolder()
			.createPackageFragment("test1", false, null);
		
		// Phase 1: V1 Test
		ICompilationUnit cuV1 = pack.createCompilationUnit("TestV1.java", 
			input.replace("MyTest", "TestV1"), false, null);
		
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2);
		context.assertRefactoringResultAsExpected(
			new ICompilationUnit[] { cuV1 }, 
			new String[] { expectedOutput.replace("MyTest", "TestV1") }, 
			null);
		
		// Phase 2: V2 Test
		ICompilationUnit cuV2 = pack.createCompilationUnit("TestV2.java",
			input.replace("MyTest", "TestV2"), false, null);
		
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2);
		context.assertRefactoringResultAsExpected(
			new ICompilationUnit[] { cuV2 }, 
			new String[] { expectedOutput.replace("MyTest", "TestV2") }, 
			null);
		
		// Both V1 and V2 should produce identical results
		// If we reach this point, both tests passed, so parity is established
	}

	/**
	 * Simple forEach test - DISABLED because V2 produces different output than V1.
	 * 
	 * <p>V1 uses direct {@code list.forEach()} while V2 uses {@code list.stream().forEach()}.</p>
	 * <p>This will be enabled once V2 is configured to produce identical output to V1.</p>
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453 Phase 7</a>
	 */
	@org.junit.jupiter.api.Disabled("V2 produces stream-based output while V1 uses direct forEach - parity in Phase 7")
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

		assertParityBetweenV1AndV2(input, expected);
	}

	/**
	 * Filter pattern test - DISABLED until V2 supports filter operations.
	 * 
	 * <p>V2 currently only supports simple forEach patterns. Filter pattern support
	 * will be added in Phase 7 (Feature-Parität).</p>
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453 Phase 7</a>
	 */
	@org.junit.jupiter.api.Disabled("V2 does not support filter patterns yet - will be added in Phase 7")
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

		// Erwartetes Ergebnis von V1 - wird später mit V2 verglichen
		// V1 generiert Expression-Lambda, nicht Block-Lambda
		String expected = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					list.stream().filter(item -> (item != null)).forEachOrdered(item -> System.out.println(item));
				}
			}""";

		assertParityBetweenV1AndV2(input, expected);
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

		// Sollte NICHT konvertiert werden - Input = Output
		assertParityBetweenV1AndV2(input, input);
	}
}
