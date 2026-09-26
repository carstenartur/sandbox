/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Real iterator conversions must remove only imports whose final use disappears. */
class IteratorImportCleanupTest {
	@RegisterExtension
	final EclipseJava22 context= new EclipseJava22();

	@TempDir
	Path temporary;

	record Scenario(String name, String imports, String expression, String body, String extra,
			String doc, boolean keepWildcard) {
	}

	static Stream<Arguments> scenarios() {
		return Stream.of(
				new Scenario("wildcard", "import java.util.*;\n", "list", "result.append(item);", "", "", false),
				new Scenario("explicit", "import java.util.Iterator;\n", "list", "result.append(item);", "", "", false),
				new Scenario("retained-body", "import java.util.*;\n", "list", "result.append(List.of(item));", "", "", true),
				new Scenario("retained-factory", "import java.util.*;\n", "Arrays.asList(\"a\", \"bb\")",
						"result.append(item);", "result.append(list.size());", "", true),
				new Scenario("retained-iterator", "import java.util.*;\n", "list", "result.append(item);",
						"Iterator<String> other = list.iterator(); result.append(other.hasNext());", "", true),
				new Scenario("explicit-body", "import java.util.*;\nimport java.util.List;\n", "list",
						"result.append(List.of(item));", "", "", false),
				new Scenario("unrelated-wildcard", "import java.util.*;\nimport java.time.*;\n", "list",
						"result.append(item);", "", "", false),
				new Scenario("javadoc", "import java.util.*;\n", "list", "result.append(item);", "", "/** @see List */\n", true),
				new Scenario("static-wildcard", "import java.util.*;\nimport static java.util.Collections.*;\n", "list",
						"result.append(item).append(emptyList().size());", "", "", false))
				.flatMap(scenario -> Stream.of(false, true)
						.map(classic -> Arguments.of(scenario.name(), classic, scenario)));
	}

	@ParameterizedTest(name= "{0}, classic-for={1}")
	@MethodSource("scenarios")
	void preservesRequiredImportsAndRuntime(String name, boolean classic, Scenario scenario) throws Exception {
		String given= source(scenario, classic);
		String expected= given.replace(loop(scenario, classic), scenario.expression() + ".stream().forEach(item -> "
				+ scenario.body().substring(0, scenario.body().length() - 1) + ");\n");
		if (!scenario.keepWildcard()) {
			expected= expected.replace("import java.util.*;\n", "");
		}
		if ("explicit".equals(name)) {
			expected= expected.replace("import java.util.Iterator;\n", "");
		}
		var pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit unit= pack.createCompilationUnit("Example.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		// The shared assertion also enforces the baseline-relative diagnostic invariant.
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { expected }, null);
		assertEquals(StreamChainToLoopTest.executeSource(given, temporary.resolve("before")),
				StreamChainToLoopTest.executeSource(unit.getSource(), temporary.resolve("after")));
	}

	private static String source(Scenario scenario, boolean classic) {
		return "package test1;\n" + scenario.imports()
				+ "import java.util.concurrent.CopyOnWriteArrayList;\npublic class Example {\n" + scenario.doc()
				+ "public static String run() {\n"
				+ "CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>(new String[] {\"a\", \"bb\"});\n"
				+ "StringBuilder result = new StringBuilder();\n" + loop(scenario, classic)
				+ scenario.extra() + "\nreturn result.toString();\n}\n}\n";
	}

	private static String loop(Scenario scenario, boolean classic) {
		String header= classic
				? "for (Iterator<String> it = " + scenario.expression() + ".iterator(); it.hasNext();) {\n"
				: "Iterator<String> it = " + scenario.expression() + ".iterator();\nwhile (it.hasNext()) {\n";
		return header + "String item = it.next();\n" + scenario.body() + "\n}\n";
	}
}
