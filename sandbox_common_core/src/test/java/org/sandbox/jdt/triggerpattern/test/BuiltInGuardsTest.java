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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;

/**
 * Tests for {@link BuiltInGuards}, specifically the instanceof guard.
 *
 * @since 1.3.6
 */
public class BuiltInGuardsTest {

	private static Map<String, GuardFunction> guards;

	@BeforeAll
	static void setUp() {
		guards = new HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	@Test
	public void testInstanceOfGuardWithNullBinding() {
		// When the placeholder binding is null, instanceof should return false
		GuardFunction instanceOfGuard = guards.get("instanceof"); //$NON-NLS-1$
		Map<String, Object> bindings = new HashMap<>();
		// No binding for $x
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(instanceOfGuard.evaluate(ctx, "$x", "java.lang.String")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testInstanceOfGuardGracefulDegradationWithoutBindings() {
		// When binding exists but type binding is null (no resolve), should return true
		GuardFunction instanceOfGuard = guards.get("instanceof"); //$NON-NLS-1$

		// Parse code without bindings (no setResolveBindings, no project)
		String code = "class Test { void m() { String s = \"hello\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithoutBindings(code);

		// Get a SimpleName node from the CU
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode body = (ASTNode) method.getBody().statements().get(0);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", body); //$NON-NLS-1$
		Match match = new Match(body, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		// Without binding resolution, instanceof guard should gracefully degrade to true
		assertTrue(instanceOfGuard.evaluate(ctx, "$x", "java.lang.String"), //$NON-NLS-1$ //$NON-NLS-2$
				"instanceof should return true when bindings are not resolved (graceful degradation)"); //$NON-NLS-1$
	}

	@Test
	public void testInstanceOfGuardInsufficientArgs() {
		// When insufficient arguments are provided, should return false
		GuardFunction instanceOfGuard = guards.get("instanceof"); //$NON-NLS-1$
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(instanceOfGuard.evaluate(ctx), "Should return false with no args"); //$NON-NLS-1$
		assertFalse(instanceOfGuard.evaluate(ctx, "$x"), "Should return false with only one arg"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testAllBuiltInGuardsRegistered() {
		// Verify all expected guards are registered
		assertTrue(guards.containsKey("instanceof")); //$NON-NLS-1$
		assertTrue(guards.containsKey("matchesAny")); //$NON-NLS-1$
		assertTrue(guards.containsKey("matchesNone")); //$NON-NLS-1$
		assertTrue(guards.containsKey("sourceVersionGE")); //$NON-NLS-1$
		assertTrue(guards.containsKey("sourceVersionLE")); //$NON-NLS-1$
		assertTrue(guards.containsKey("sourceVersionBetween")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isStatic")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isFinal")); //$NON-NLS-1$
		assertTrue(guards.containsKey("hasAnnotation")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isDeprecated")); //$NON-NLS-1$
		assertTrue(guards.containsKey("contains")); //$NON-NLS-1$
		assertTrue(guards.containsKey("notContains")); //$NON-NLS-1$
	}

	// --- Helper methods ---

	private ASTNode createDummyNode() {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource("class Dummy { }".toCharArray()); //$NON-NLS-1$
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		astParser.setCompilerOptions(JavaCore.getOptions());
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		return cu.types().isEmpty() ? cu : (ASTNode) cu.types().get(0);
	}

	private CompilationUnit parseCodeWithoutBindings(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		// NOT calling setResolveBindings(true) - this tests graceful degradation
		return (CompilationUnit) astParser.createAST(null);
	}
}
