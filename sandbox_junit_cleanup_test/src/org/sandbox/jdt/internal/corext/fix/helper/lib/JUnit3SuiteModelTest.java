/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Contract of the fail-closed JUnit 3 {@code suite()} aggregator model. */
class JUnit3SuiteModelTest {

	@Test
	void modelsDirectlyConstructedSuites() {
		assertSelected("public static Test suite() { return new TestSuite(A.class, B.class); }", //$NON-NLS-1$
				List.of("A", "B")); //$NON-NLS-1$ //$NON-NLS-2$
		assertSelected("public static Test suite() { return new TestSuite(\"all\", A.class); }", //$NON-NLS-1$
				List.of("A")); //$NON-NLS-1$
	}

	@Test
	void modelsAccumulatingSuites() {
		assertSelected("public static Test suite() {" //$NON-NLS-1$
				+ " TestSuite suite= new TestSuite(\"all\");" //$NON-NLS-1$
				+ " suite.addTestSuite(A.class);" //$NON-NLS-1$
				+ " suite.addTest(new TestSuite(B.class));" //$NON-NLS-1$
				+ " suite.addTest(C.suite());" //$NON-NLS-1$
				+ " return suite; }", //$NON-NLS-1$
				List.of("A", "B", "C")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void rejectsUnprovableSuites() {
		assertRejected("public static Test suite() { return null; }", //$NON-NLS-1$
				"DYNAMIC_JUNIT3_SUITE"); //$NON-NLS-1$
		assertRejected("public static Test suite() { return new TestSuite(); }", //$NON-NLS-1$
				"EMPTY_JUNIT3_SUITE"); //$NON-NLS-1$
		assertRejected("public static Test suite() { return new TestSuite(A.class, A.class); }", //$NON-NLS-1$
				"DUPLICATED_JUNIT3_SUITE_ENTRY"); //$NON-NLS-1$
		assertRejected("public static Test suite() {" //$NON-NLS-1$
				+ " TestSuite suite= new TestSuite(A.class);" //$NON-NLS-1$
				+ " suite.addTestSuite(names[0]);" //$NON-NLS-1$
				+ " return suite; }", //$NON-NLS-1$
				"DYNAMIC_JUNIT3_SUITE"); //$NON-NLS-1$
		assertRejected("public static Test suite() {" //$NON-NLS-1$
				+ " TestSuite suite= new TestSuite(A.class);" //$NON-NLS-1$
				+ " suite.addTest(new ProjectTestSetup(new TestSuite(B.class)));" //$NON-NLS-1$
				+ " return suite; }", //$NON-NLS-1$
				"CUSTOM_JUNIT3_SUITE_DECORATOR"); //$NON-NLS-1$
		assertRejected("public static Test suite() {" //$NON-NLS-1$
				+ " TestSuite suite= new TestSuite(A.class);" //$NON-NLS-1$
				+ " suite.setName(\"x\");" //$NON-NLS-1$
				+ " return suite; }", //$NON-NLS-1$
				"CUSTOM_JUNIT3_SUITE_COMPOSITION"); //$NON-NLS-1$
		assertRejected("public static Test suite() {" //$NON-NLS-1$
				+ " TestSuite suite= new TestSuite(A.class);" //$NON-NLS-1$
				+ " if (enabled) { suite.addTestSuite(B.class); }" //$NON-NLS-1$
				+ " return suite; }", //$NON-NLS-1$
				"ORDER_DEPENDENT_JUNIT3_SUITE"); //$NON-NLS-1$
	}

	@Test
	void rejectsMethodsThatAreNoSuiteBuilders() {
		assertFalse(JUnit3SuiteModel.isSuiteBuilder(parseMethod("public static Object suite() { return null; }"))); //$NON-NLS-1$
		assertFalse(JUnit3SuiteModel.isSuiteBuilder(parseMethod("public Test suite() { return null; }"))); //$NON-NLS-1$
		assertFalse(JUnit3SuiteModel.isSuiteBuilder(parseMethod("public static Test suite(int i) { return null; }"))); //$NON-NLS-1$
		assertRejected("public static Object suite() { return null; }", //$NON-NLS-1$
				"NOT_A_JUNIT3_SUITE_BUILDER"); //$NON-NLS-1$
	}

	private static void assertSelected(String member, List<String> expected) {
		JUnit3SuiteModel.Result result= JUnit3SuiteModel.analyze(parseMethod(member));
		assertTrue(result.supported(), () -> "unexpected rejection: " + result.rejection()); //$NON-NLS-1$
		assertEquals(expected, result.selectedTypes());
	}

	private static void assertRejected(String member, String reasonCode) {
		JUnit3SuiteModel.Result result= JUnit3SuiteModel.analyze(parseMethod(member));
		assertFalse(result.supported());
		assertEquals(reasonCode, result.rejection().reasonCode());
		assertFalse(result.rejection().explanation().isBlank());
	}

	private static MethodDeclaration parseMethod(String member) {
		ASTParser parser= ASTParser.newParser(AST.JLS21);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(("class Sample { " + member + " }").toCharArray()); //$NON-NLS-1$ //$NON-NLS-2$
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		TypeDeclaration type= (TypeDeclaration) root.types().get(0);
		return type.getMethods()[0];
	}
}
