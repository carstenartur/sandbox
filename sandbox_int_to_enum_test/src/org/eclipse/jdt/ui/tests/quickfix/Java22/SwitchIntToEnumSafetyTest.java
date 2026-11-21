/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Complete switch contracts, including the JDT DefaultBytecodeVisitor failure. */
@SuppressWarnings("nls")
class SwitchIntToEnumSafetyTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	static Stream<Arguments> sources() {
		return Stream.of(
				Arguments.of("interface-contract", candidate(
						"interface Processor { void process(int status); }", "implements Processor", "public", "", ""), false),
				Arguments.of("inherited-contract-without-annotation", candidate(
						"abstract class Parent { protected abstract void process(int status); }", "extends Parent", "protected", "", ""), false),
				Arguments.of("public-api", candidate("", "", "public", "", ""), false),
				Arguments.of("numeric-use-inside-case", candidate("", "", "private",
						"System.out.println(Integer.toString(status));", ""), false),
				Arguments.of("numeric-case-label", candidate("", "", "private", "", "")
						.replace("case STATUS_DONE:", "case 99: break;\ncase STATUS_DONE:"), false),
				Arguments.of("caller-with-unknown-integer", candidate("", "", "private", "",
						"public void run() { process(99); }"), false),
				Arguments.of("constant-retained-as-an-integer-field", candidate("", "", "private", "",
						"private final int original= STATUS_PENDING;\npublic int original() { return this.original; }"), false),
				Arguments.of("closed-private-domain-and-callers", candidate("", "", "private", "",
						"public void run() { process(STATUS_PENDING); process(STATUS_DONE); }"), true));
	}

	private static String candidate(String contract, String inheritance, String visibility, String extraUse, String members) {
		return """
				package test1;
				CONTRACT
				public class Sample INHERITANCE {
					private static final int STATUS_PENDING= 4;
					private static final int STATUS_DONE= 8;
					VISIBILITY void process(int status) {
						switch (status) {
						case STATUS_PENDING:
							EXTRA_USE
							System.out.println("pending");
							break;
						case STATUS_DONE:
							System.out.println("done");
							break;
						}
					}
					MEMBERS
				}
				""".replace("CONTRACT", contract).replace("INHERITANCE", inheritance)
				.replace("VISIBILITY", visibility).replace("EXTRA_USE", extraUse).replace("MEMBERS", members);
	}

	@ParameterizedTest(name= "{0}")
	@MethodSource("sources")
	void preservesTheCompleteIntegerContract(String name, String source, boolean mustMigrate) throws Exception {
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test1", false, null)
				.createCompilationUnit("Sample.java", source, false, null);
		var preferences= context.getJavaProject().getOptions(false);
		var cleanup= new IntToEnumCleanUpCore(Map.of(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, "true"));
		assertFalse(cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null).hasError());
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var root= (CompilationUnit) parser.createAST(null);
		assertFalse(Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError()),
				Arrays.toString(root.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		var fix= cleanup.createFix(new CleanUpContext(unit, root));
		if (mustMigrate) {
			assertNotNull(fix, "The complete private domain and its callers are supported");
			var change= fix.createChange(null);
			var undo= change.perform(null);
			try {
				diagnostics.assertNoNewDiagnostics();
				String result= unit.getSource();
				assertTrue(result.contains("private enum Status"), result);
				assertTrue(result.contains("process(Status.PENDING)"), result);
				assertTrue(result.contains("process(Status.DONE)"), result);
			} finally {
				assertNotNull(undo);
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
				change.dispose();
			}
		} else {
			assertNull(fix, "A switch cannot change an unproven integer contract");
		}
		assertEquals(source, unit.getSource());
		assertEquals(preferences, context.getJavaProject().getOptions(false));
		diagnostics.assertRestored();
	}
}
