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
 * <p>Jeder Test führt beide Implementierungen aus und vergleicht die Ergebnisse.
 * Diese Tests werden entfernt, sobald V2 vollständige Parität erreicht hat
 * und V1 deprecated/entfernt wird.</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
 */
public class FeatureParityTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Helper-Methode um V1 und V2 Ergebnisse zu vergleichen.
	 * 
	 * <p>TODO: Aktivieren sobald LOOP_V2 implementiert ist</p>
	 * 
	 * @param input Der ursprüngliche Java-Code
	 * @param expectedOutput Der erwartete Code nach der Transformation
	 * @throws CoreException bei Fehlern während der Refactoring-Ausführung
	 */
	private void assertParityBetweenV1AndV2(String input, String expectedOutput) 
			throws CoreException {
		// Phase 1: Nur V1 testen (V2 noch nicht implementiert)
		IPackageFragment pack = context.getSourceFolder()
			.createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(
			new ICompilationUnit[] { cu }, 
			new String[] { expectedOutput }, 
			null);
		
		// TODO Phase 2: Wenn V2 implementiert ist:
		// - V2 aktivieren, V1 deaktivieren
		// - Gleiches Refactoring ausführen
		// - Ergebnisse vergleichen
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

		assertParityBetweenV1AndV2(input, expected);
	}

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
		String expected = """
			package test1;
			import java.util.List;
			class MyTest {
				public void test(List<String> list) {
					list.stream().filter(item -> (item != null)).forEachOrdered(item -> {
						System.out.println(item);
					});
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
