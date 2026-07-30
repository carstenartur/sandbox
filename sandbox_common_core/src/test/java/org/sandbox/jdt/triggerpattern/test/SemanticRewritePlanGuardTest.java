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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;

/** Fail-closed tests for semantic-plan guards. */
public class SemanticRewritePlanGuardTest {

	@Test
	public void plannedRoleRequiresExactMethodBindingAndRole() {
		MethodDeclaration method= parseMethod();
		IMethodBinding binding= method.resolveBinding();
		assertNotNull(binding);
		NodeKey key= NodeKey.method(binding.getMethodDeclaration().getKey());
		SemanticRewritePlan plan= SemanticRewritePlan.builder().add(key, "TEST_ROLE").build(); //$NON-NLS-1$

		Map<String, Object> bindings= Map.of("$name", method.getName()); //$NON-NLS-1$
		Match match= new Match(method, bindings, method.getStartPosition(), method.getLength());
		GuardContext context= GuardContext.fromMatch(match, (CompilationUnit) method.getRoot(), Map.of(), plan);
		GuardFunction plannedRole= guards().get("plannedRole"); //$NON-NLS-1$

		assertTrue(plannedRole.evaluate(context, "$name", "TEST_ROLE")); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(plannedRole.evaluate(context, "$name", "OTHER_ROLE")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void plannedRoleRejectsEmptyPlanAndMissingBinding() {
		MethodDeclaration method= parseMethod();
		Match match= new Match(method, Map.of(), method.getStartPosition(), method.getLength());
		GuardContext context= GuardContext.fromMatch(match, (CompilationUnit) method.getRoot());
		GuardFunction plannedRole= guards().get("plannedRole"); //$NON-NLS-1$

		assertFalse(plannedRole.evaluate(context, "TEST_ROLE")); //$NON-NLS-1$
		assertFalse(plannedRole.evaluate(context, "$name", "TEST_ROLE")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void semanticPlanContractMustMatchExactly() {
		SemanticRewritePlan planned= SemanticRewritePlan.builder(" junit3-hierarchy ") //$NON-NLS-1$
				.add(NodeKey.type("Ltest/Sample;"), "TYPE_ROLE").build(); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(planned.satisfiesContract("junit3-hierarchy")); //$NON-NLS-1$
		assertFalse(planned.satisfiesContract("other-hierarchy")); //$NON-NLS-1$
		assertFalse(SemanticRewritePlan.builder().add(NodeKey.type("Ltest/Sample;"), "TYPE_ROLE") //$NON-NLS-1$ //$NON-NLS-2$
				.build().satisfiesContract("junit3-hierarchy")); //$NON-NLS-1$
	}

	private static Map<String, GuardFunction> guards() {
		Map<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		return guards;
	}

	private static MethodDeclaration parseMethod() {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource("package test; public class Sample { public void testOne() {} }".toCharArray()); //$NON-NLS-1$
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		TypeDeclaration type= (TypeDeclaration) root.types().get(0);
		return type.getMethods()[0];
	}
}