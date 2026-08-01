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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Stable reason-code contract for unsupported JUnit 3 harness semantics. */
class JUnit3HarnessSemanticsTest {

	@Test
	void distinguishesNamedAndCustomConstructors() {
		assertReason("Sample(String name) {}", //$NON-NLS-1$
				"NAMED_JUNIT3_TEST_CONSTRUCTION"); //$NON-NLS-1$
		assertReason("Sample(int value) {}", //$NON-NLS-1$
				"CUSTOM_JUNIT3_CONSTRUCTOR"); //$NON-NLS-1$
	}

	@Test
	void distinguishesSuiteSelectionAndLifecycleContracts() {
		assertReason("public static junit.framework.Test suite() { return null; }", //$NON-NLS-1$
				"CUSTOM_JUNIT3_SUITE_BUILDER"); //$NON-NLS-1$
		assertReason("protected void runTest() {}", //$NON-NLS-1$
				"CUSTOM_JUNIT3_TEST_SELECTION"); //$NON-NLS-1$
		assertReason("public void runBare() {}", //$NON-NLS-1$
				"CUSTOM_JUNIT3_LIFECYCLE_WRAPPER"); //$NON-NLS-1$
	}

	@Test
	void distinguishesResultNameAndRunnerContracts() {
		assertReason("protected junit.framework.TestResult createResult() { return null; }", //$NON-NLS-1$
				"CUSTOM_JUNIT3_RESULT_MODEL"); //$NON-NLS-1$
		assertReason("public int countTestCases() { return 1; }", //$NON-NLS-1$
				"CUSTOM_JUNIT3_RESULT_MODEL"); //$NON-NLS-1$
		assertReason("public String getName() { return null; }", //$NON-NLS-1$
				"NAMED_JUNIT3_TEST_CONTRACT"); //$NON-NLS-1$
		assertReason("public void setName(String name) {}", //$NON-NLS-1$
				"NAMED_JUNIT3_TEST_CONTRACT"); //$NON-NLS-1$
		assertReason("public void run(junit.framework.TestResult result) {}", //$NON-NLS-1$
				"CUSTOM_JUNIT3_RUNNER_INTEGRATION"); //$NON-NLS-1$
	}

	@Test
	void ignoresSameNamedMethodsThatAreNotJUnit3Hooks() {
		assertUnclassified("public void suite(int value) {}"); //$NON-NLS-1$
		assertUnclassified("public static Object suite() { return null; }"); //$NON-NLS-1$
		assertUnclassified("public void run() {}"); //$NON-NLS-1$
		assertUnclassified("public void run(Object result) {}"); //$NON-NLS-1$
		assertUnclassified("public String getName(int index) { return null; }"); //$NON-NLS-1$
		assertUnclassified("public void setName(Object name) {}"); //$NON-NLS-1$
		assertUnclassified("private void runBare() {}"); //$NON-NLS-1$
	}

	@Test
	void leavesOrdinaryTestsAndHelpersUnclassified() {
		assertUnclassified("public void testOne() {}"); //$NON-NLS-1$
		assertUnclassified("private int helper() { return 1; }"); //$NON-NLS-1$
	}

	private static void assertReason(String member, String reasonCode) {
		JUnit3HarnessSemantics.Rejection rejection= JUnit3HarnessSemantics
				.rejection(parseMethod(member)).orElseThrow();
		assertEquals(reasonCode, rejection.reasonCode());
		assertFalse(rejection.explanation().isBlank());
	}

	private static void assertUnclassified(String member) {
		assertTrue(JUnit3HarnessSemantics.rejection(parseMethod(member)).isEmpty());
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
