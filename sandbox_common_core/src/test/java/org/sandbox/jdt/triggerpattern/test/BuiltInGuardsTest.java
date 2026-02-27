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
		assertTrue(guards.containsKey("subtypeOf")); //$NON-NLS-1$
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
		assertTrue(guards.containsKey("isNullable")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isNonNull")); //$NON-NLS-1$
		assertTrue(guards.containsKey("methodNameMatches")); //$NON-NLS-1$
		assertTrue(guards.containsKey("hasSuppressWarnings")); //$NON-NLS-1$
	}

	@Test
	public void testMethodNameMatchesWithTestPrefix() {
		GuardFunction methodNameMatches = guards.get("methodNameMatches"); //$NON-NLS-1$
		String code = "class Test { void testSomething() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$name", method.getName()); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(methodNameMatches.evaluate(ctx, "$name", "test.*"), //$NON-NLS-1$ //$NON-NLS-2$
				"testSomething should match test.*"); //$NON-NLS-1$
	}

	@Test
	public void testMethodNameMatchesNoMatch() {
		GuardFunction methodNameMatches = guards.get("methodNameMatches"); //$NON-NLS-1$
		String code = "class Test { void doSomething() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$name", method.getName()); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(methodNameMatches.evaluate(ctx, "$name", "test.*"), //$NON-NLS-1$ //$NON-NLS-2$
				"doSomething should not match test.*"); //$NON-NLS-1$
	}

	@Test
	public void testMethodNameMatchesExactName() {
		GuardFunction methodNameMatches = guards.get("methodNameMatches"); //$NON-NLS-1$
		String code = "class Test { void setUp() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$name", method.getName()); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(methodNameMatches.evaluate(ctx, "$name", "setUp"), //$NON-NLS-1$ //$NON-NLS-2$
				"setUp should match exact name 'setUp'"); //$NON-NLS-1$
		assertFalse(methodNameMatches.evaluate(ctx, "$name", "tearDown"), //$NON-NLS-1$ //$NON-NLS-2$
				"setUp should not match 'tearDown'"); //$NON-NLS-1$
	}

	@Test
	public void testMethodNameMatchesInsufficientArgs() {
		GuardFunction methodNameMatches = guards.get("methodNameMatches"); //$NON-NLS-1$
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(methodNameMatches.evaluate(ctx, "$name"), //$NON-NLS-1$
				"Should return false with only one arg"); //$NON-NLS-1$
		assertFalse(methodNameMatches.evaluate(ctx),
				"Should return false with no args"); //$NON-NLS-1$
	}

	@Test
	public void testIsStaticOnMethodNameWithBindings() {
		GuardFunction isStatic = guards.get("isStatic"); //$NON-NLS-1$
		String code = "class Test { public static void suiteSetUp() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$name", method.getName()); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isStatic.evaluate(ctx, "$name"), //$NON-NLS-1$
				"Static method name should report isStatic=true"); //$NON-NLS-1$
	}

	@Test
	public void testIsStaticOnNonStaticMethodName() {
		GuardFunction isStatic = guards.get("isStatic"); //$NON-NLS-1$
		String code = "class Test { public void testFoo() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$name", method.getName()); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(isStatic.evaluate(ctx, "$name"), //$NON-NLS-1$
				"Non-static method name should report isStatic=false"); //$NON-NLS-1$
	}

	@Test
	public void testIsStaticFallbackWithoutBindings() {
		// When bindings are NOT resolved, resolveModifiers should fall back to
		// navigating from SimpleName to parent MethodDeclaration
		GuardFunction isStatic = guards.get("isStatic"); //$NON-NLS-1$
		String code = "class Test { public static void suite() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithoutBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$name", method.getName()); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isStatic.evaluate(ctx, "$name"), //$NON-NLS-1$
				"isStatic should work via parent fallback even without binding resolution"); //$NON-NLS-1$
	}

	@Test
	public void testIsNullableWithStringBuilderInitialized() {
		// StringBuilder initialized with 'new' should be NON_NULL
		GuardFunction isNullable = guards.get("isNullable"); //$NON-NLS-1$
		String code = """
			class Test {
				void m() {
					StringBuilder sb = new StringBuilder();
					sb.toString();
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		
		// Extract the 'new StringBuilder()' expression directly
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);
		// Get the initializer (ClassInstanceCreation)
		ASTNode initializer = findClassInstanceCreationInNode(varDeclStmt);
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", initializer); //$NON-NLS-1$
		Match match = new Match(initializer, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);
		
		assertFalse(isNullable.evaluate(ctx, "$x"), //$NON-NLS-1$
				"ClassInstanceCreation (new) should not be nullable"); //$NON-NLS-1$
	}

	@Test
	public void testIsNullableWithUnguardedParameter() {
		// For expressions on Object type, the NullabilityGuard will return UNKNOWN (not in whitelist),
		// so isNullable should return true (not provably NON_NULL)
		GuardFunction isNullable = guards.get("isNullable"); //$NON-NLS-1$
		String code = """
			class Test {
				void m(Object obj) {
					obj.toString();
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		
		// Extract the SimpleName 'obj' (the receiver, not the method invocation result)
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode callStmt = (ASTNode) method.getBody().statements().get(0);
		ASTNode receiver = findSimpleNameInNode(callStmt, "obj"); //$NON-NLS-1$
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", receiver); //$NON-NLS-1$
		Match match = new Match(receiver, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);
		
		// SimpleName 'obj' of type Object is not in whitelist → UNKNOWN
		// isNullable returns true for UNKNOWN
		assertTrue(isNullable.evaluate(ctx, "$x"), //$NON-NLS-1$
				"Unknown nullability should be treated as nullable"); //$NON-NLS-1$
	}

	@Test
	public void testIsNullableWithMinScore10() {
		// Test with high-risk threshold (only NULLABLE=10 should match)
		// For this test, we use a simple ClassInstanceCreation which is NON_NULL (score=0)
		GuardFunction isNullable = guards.get("isNullable"); //$NON-NLS-1$
		String code = """
			class Test {
				void m() {
					StringBuilder sb = new StringBuilder();
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		
		// Extract the 'new StringBuilder()' which is NON_NULL
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);
		ASTNode initializer = findClassInstanceCreationInNode(varDeclStmt);
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", initializer); //$NON-NLS-1$
		Match match = new Match(initializer, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);
		
		// With minScore=10, NON_NULL (score=0) should NOT match
		assertFalse(isNullable.evaluate(ctx, "$x", "10"), //$NON-NLS-1$ //$NON-NLS-2$
				"NON_NULL should not match high-risk threshold"); //$NON-NLS-1$
	}

	@Test
	public void testIsNullableWithMinScore5() {
		// Test with moderate threshold (UNKNOWN=5 and above should match)
		GuardFunction isNullable = guards.get("isNullable"); //$NON-NLS-1$
		String code = """
			class Test {
				void m(Object obj) {
					obj.toString();
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		
		// Extract the SimpleName 'obj' (not the method invocation)
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode callStmt = (ASTNode) method.getBody().statements().get(0);
		ASTNode receiver = findSimpleNameInNode(callStmt, "obj"); //$NON-NLS-1$
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", receiver); //$NON-NLS-1$
		Match match = new Match(receiver, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);
		
		// SimpleName 'obj' of type Object is not in whitelist → UNKNOWN (score=5)
		assertTrue(isNullable.evaluate(ctx, "$x", "5"), //$NON-NLS-1$ //$NON-NLS-2$
				"Unknown nullability (score 5) should match minScore=5"); //$NON-NLS-1$
	}

	@Test
	public void testIsNonNullWithStringBuilder() {
		// ClassInstanceCreation (new StringBuilder()) should be NON_NULL
		GuardFunction isNonNull = guards.get("isNonNull"); //$NON-NLS-1$
		String code = """
			class Test {
				void m() {
					StringBuilder sb = new StringBuilder();
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		
		// Extract the 'new StringBuilder()' expression
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);
		ASTNode initializer = findClassInstanceCreationInNode(varDeclStmt);
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", initializer); //$NON-NLS-1$
		Match match = new Match(initializer, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);
		
		assertTrue(isNonNull.evaluate(ctx, "$x"), //$NON-NLS-1$
				"ClassInstanceCreation should be non-null"); //$NON-NLS-1$
	}

	@Test
	public void testIsNonNullWithUnguardedParameter() {
		// SimpleName of Object type is UNKNOWN, not NON_NULL
		GuardFunction isNonNull = guards.get("isNonNull"); //$NON-NLS-1$
		String code = """
			class Test {
				void m(Object obj) {
					obj.toString();
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		
		// Extract the SimpleName 'obj' (not the method invocation)
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode callStmt = (ASTNode) method.getBody().statements().get(0);
		ASTNode receiver = findSimpleNameInNode(callStmt, "obj"); //$NON-NLS-1$
		
		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", receiver); //$NON-NLS-1$
		Match match = new Match(receiver, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);
		
		assertFalse(isNonNull.evaluate(ctx, "$x"), //$NON-NLS-1$
				"Unknown nullability should not be treated as non-null"); //$NON-NLS-1$
	}

	@Test
	public void testIsNullableWithNullBinding() {
		// When the placeholder binding is null, isNullable should return false
		GuardFunction isNullable = guards.get("isNullable"); //$NON-NLS-1$
		Map<String, Object> bindings = new HashMap<>();
		// No binding for $x
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(isNullable.evaluate(ctx, "$x")); //$NON-NLS-1$
	}

	@Test
	public void testIsNonNullWithNullBinding() {
		// When the placeholder binding is null, isNonNull should return false
		GuardFunction isNonNull = guards.get("isNonNull"); //$NON-NLS-1$
		Map<String, Object> bindings = new HashMap<>();
		// No binding for $x
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(isNonNull.evaluate(ctx, "$x")); //$NON-NLS-1$
	}

	// ---- New NetBeans-compatible guard tests ----

	@Test
	public void testOtherwiseGuardAlwaysTrue() {
		GuardFunction otherwise = guards.get("otherwise"); //$NON-NLS-1$
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertTrue(otherwise.evaluate(ctx), "otherwise guard should always return true"); //$NON-NLS-1$
	}

	@Test
	public void testNewGuardsRegistered() {
		// Verify all new guards are registered
		assertTrue(guards.containsKey("otherwise")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isLiteral")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isNullLiteral")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isCharsetString")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isSingleCharacter")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isRegexp")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isInTryWithResourceBlock")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isPassedToMethod")); //$NON-NLS-1$
		assertTrue(guards.containsKey("inSerializableClass")); //$NON-NLS-1$
		assertTrue(guards.containsKey("containsAnnotation")); //$NON-NLS-1$
		assertTrue(guards.containsKey("parentMatches")); //$NON-NLS-1$
		assertTrue(guards.containsKey("inClass")); //$NON-NLS-1$
		assertTrue(guards.containsKey("inPackage")); //$NON-NLS-1$
		assertTrue(guards.containsKey("hasModifier")); //$NON-NLS-1$
		assertTrue(guards.containsKey("enclosingClassExtends")); //$NON-NLS-1$
	}

	@Test
	public void testIsLiteralWithStringLiteral() {
		GuardFunction isLiteral = guards.get("isLiteral"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"hello\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		// Find the StringLiteral "hello"
		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isLiteral.evaluate(ctx, "$x"), "StringLiteral should be a literal"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsLiteralWithNonLiteral() {
		GuardFunction isLiteral = guards.get("isLiteral"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"hello\"; s.toString(); } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode callStmt = (ASTNode) method.getBody().statements().get(1);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", callStmt); //$NON-NLS-1$
		Match match = new Match(callStmt, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(isLiteral.evaluate(ctx, "$x"), "MethodInvocation should not be a literal"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsNullLiteralWithNullExpression() {
		GuardFunction isNullLiteral = guards.get("isNullLiteral"); //$NON-NLS-1$
		String code = "class Test { void m() { Object o = null; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode nullLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.NullLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", nullLit); //$NON-NLS-1$
		Match match = new Match(nullLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isNullLiteral.evaluate(ctx, "$x"), "NullLiteral should match isNullLiteral"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsCharsetStringWithUTF8() {
		GuardFunction isCharsetString = guards.get("isCharsetString"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"UTF-8\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isCharsetString.evaluate(ctx, "$x"), "UTF-8 should be a charset string"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsCharsetStringWithNonStandardCharset() {
		GuardFunction isCharsetString = guards.get("isCharsetString"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"NOT-A-CHARSET\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(isCharsetString.evaluate(ctx, "$x"), "NOT-A-CHARSET should not be a standard charset string"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsSingleCharacterWithOneChar() {
		GuardFunction isSingleChar = guards.get("isSingleCharacter"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"x\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isSingleChar.evaluate(ctx, "$x"), "Single char string should match"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsSingleCharacterWithMultipleChars() {
		GuardFunction isSingleChar = guards.get("isSingleCharacter"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"hello\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(isSingleChar.evaluate(ctx, "$x"), "Multi-char string should not match"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsRegexpWithRegexPattern() {
		GuardFunction isRegexp = guards.get("isRegexp"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"a.*b\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(isRegexp.evaluate(ctx, "$x"), "String with regex metacharacters should be regex"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testIsRegexpWithPlainString() {
		GuardFunction isRegexp = guards.get("isRegexp"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"hello world\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		ASTNode stringLit = findNodeOfType(varDeclStmt, org.eclipse.jdt.core.dom.StringLiteral.class);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stringLit); //$NON-NLS-1$
		Match match = new Match(stringLit, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(isRegexp.evaluate(ctx, "$x"), "Plain string should not be regex"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testInPackageGuard() {
		GuardFunction inPackage = guards.get("inPackage"); //$NON-NLS-1$
		String code = "package com.example; class Test { void m() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(inPackage.evaluate(ctx, "\"com.example\""), "Should match correct package"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(inPackage.evaluate(ctx, "\"com.other\""), "Should not match wrong package"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testHasModifierGuard() {
		GuardFunction hasModifier = guards.get("hasModifier"); //$NON-NLS-1$
		String code = "class Test { public static void m() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode methodName = method.getName();

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$m", methodName); //$NON-NLS-1$
		Match match = new Match(methodName, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(hasModifier.evaluate(ctx, "$m", "PUBLIC"), "Should detect PUBLIC modifier"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(hasModifier.evaluate(ctx, "$m", "STATIC"), "Should detect STATIC modifier"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(hasModifier.evaluate(ctx, "$m", "PRIVATE"), "Should not detect PRIVATE modifier"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testIsInTryWithResourceBlockNegative() {
		GuardFunction isInTryWithResourceBlock = guards.get("isInTryWithResourceBlock"); //$NON-NLS-1$
		String code = "class Test { void m() { int x = 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode varDeclStmt = (ASTNode) method.getBody().statements().get(0);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", varDeclStmt); //$NON-NLS-1$
		Match match = new Match(varDeclStmt, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(isInTryWithResourceBlock.evaluate(ctx, "$x"), "Variable not in try-with-resources should be false"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testContainsAnnotationGuard() {
		GuardFunction containsAnnotation = guards.get("containsAnnotation"); //$NON-NLS-1$
		String code = "class Test { @Deprecated void m() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$m", method); //$NON-NLS-1$
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(containsAnnotation.evaluate(ctx, "$m", "\"Deprecated\""), "Should detect @Deprecated annotation"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(containsAnnotation.evaluate(ctx, "$m", "\"Override\""), "Should not detect missing @Override annotation"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testParentMatchesGuard() {
		GuardFunction parentMatches = guards.get("parentMatches"); //$NON-NLS-1$
		String code = "class Test { void m() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode methodName = method.getName();

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$n", methodName); //$NON-NLS-1$
		Match match = new Match(methodName, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(parentMatches.evaluate(ctx, "$n", "\"void m\""), "Parent should contain 'void m'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(parentMatches.evaluate(ctx, "$n", "\"nonexistent\""), "Parent should not contain 'nonexistent'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testInClassGuard() {
		GuardFunction inClass = guards.get("inClass"); //$NON-NLS-1$
		String code = "class Test { void m() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(inClass.evaluate(ctx, "\"Test\""), "Should match enclosing class Test"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(inClass.evaluate(ctx, "\"Other\""), "Should not match different class name"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testEnclosingClassExtendsDirectSuperclass() {
		GuardFunction guard = guards.get("enclosingClassExtends"); //$NON-NLS-1$
		// Note: without classpath, bindings won't resolve for BaseClass, so this tests the fallback path
		String code = "class MyTest extends BaseClass { void testSomething() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		// Fallback: simple name of "com.example.BaseClass" matches "BaseClass" in extends clause
		assertTrue(guard.evaluate(ctx, "\"com.example.BaseClass\""), "Should match via simple name fallback"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(guard.evaluate(ctx, "\"BaseClass\""), "Should match exact simple name"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(guard.evaluate(ctx, "\"OtherClass\""), "Should not match wrong class name"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testEnclosingClassExtendsNoExtendsClause() {
		GuardFunction guard = guards.get("enclosingClassExtends"); //$NON-NLS-1$
		String code = "class PlainClass { void testSomething() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(guard.evaluate(ctx, "\"junit.framework.TestCase\""), //$NON-NLS-1$
				"Class without extends clause should not match"); //$NON-NLS-1$
	}

	@Test
	public void testEnclosingClassExtendsNoArgs() {
		GuardFunction guard = guards.get("enclosingClassExtends"); //$NON-NLS-1$
		String code = "class MyTest extends BaseClass { void m() { } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];

		Map<String, Object> bindings = new HashMap<>();
		Match match = new Match(method, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		// No arguments — should return false
		assertFalse(guard.evaluate(ctx), "Should return false with no arguments"); //$NON-NLS-1$
	}

	// --- subtypeOf guard tests ---

	@Test
	public void testSubtypeOfGuardGracefulDegradation() {
		GuardFunction subtypeOf = guards.get("subtypeOf"); //$NON-NLS-1$
		String code = "class Test { void m() { String s = \"hello\"; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithoutBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode stmt = (ASTNode) method.getBody().statements().get(0);

		Map<String, Object> bindings = new HashMap<>();
		bindings.put("$x", stmt); //$NON-NLS-1$
		Match match = new Match(stmt, bindings, 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		// Without binding resolution, subtypeOf should gracefully degrade to true
		assertTrue(subtypeOf.evaluate(ctx, "$x", "java.lang.Object"), //$NON-NLS-1$ //$NON-NLS-2$
				"subtypeOf should return true without bindings (graceful degradation)"); //$NON-NLS-1$
	}

	@Test
	public void testSubtypeOfGuardNullBinding() {
		GuardFunction subtypeOf = guards.get("subtypeOf"); //$NON-NLS-1$
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(subtypeOf.evaluate(ctx, "$x", "java.lang.String"), //$NON-NLS-1$ //$NON-NLS-2$
				"subtypeOf should return false when binding is missing"); //$NON-NLS-1$
	}

	@Test
	public void testSubtypeOfGuardInsufficientArgs() {
		GuardFunction subtypeOf = guards.get("subtypeOf"); //$NON-NLS-1$
		ASTNode dummyNode = createDummyNode();
		Match match = new Match(dummyNode, new HashMap<>(), 0, 0);
		GuardContext ctx = GuardContext.fromMatch(match, null);

		assertFalse(subtypeOf.evaluate(ctx), "Should return false with no args"); //$NON-NLS-1$
		assertFalse(subtypeOf.evaluate(ctx, "$x"), "Should return false with only one arg"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	// --- hasSuppressWarnings guard tests ---

	@Test
	public void testHasSuppressWarningsWithSingleValue() {
		GuardFunction guard = guards.get("hasSuppressWarnings"); //$NON-NLS-1$
		String code = """
			class Test {
				@SuppressWarnings("unchecked")
				void m() {
					int x = 1;
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode stmt = (ASTNode) method.getBody().statements().get(0);

		Match match = new Match(stmt, new HashMap<>(), stmt.getStartPosition(), stmt.getLength());
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(guard.evaluate(ctx, "unchecked"), //$NON-NLS-1$
				"Should detect @SuppressWarnings(\"unchecked\") on method"); //$NON-NLS-1$
		assertFalse(guard.evaluate(ctx, "deprecation"), //$NON-NLS-1$
				"Should not match a different key"); //$NON-NLS-1$
	}

	@Test
	public void testHasSuppressWarningsWithArrayValue() {
		GuardFunction guard = guards.get("hasSuppressWarnings"); //$NON-NLS-1$
		String code = """
			class Test {
				@SuppressWarnings({"unchecked", "deprecation"})
				void m() {
					int x = 1;
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode stmt = (ASTNode) method.getBody().statements().get(0);

		Match match = new Match(stmt, new HashMap<>(), stmt.getStartPosition(), stmt.getLength());
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(guard.evaluate(ctx, "deprecation"), //$NON-NLS-1$
				"Should detect 'deprecation' in array @SuppressWarnings"); //$NON-NLS-1$
		assertTrue(guard.evaluate(ctx, "unchecked"), //$NON-NLS-1$
				"Should detect 'unchecked' in array @SuppressWarnings"); //$NON-NLS-1$
		assertFalse(guard.evaluate(ctx, "rawtypes"), //$NON-NLS-1$
				"Should not match absent key"); //$NON-NLS-1$
	}

	@Test
	public void testHasSuppressWarningsOnClass() {
		GuardFunction guard = guards.get("hasSuppressWarnings"); //$NON-NLS-1$
		String code = """
			@SuppressWarnings("all")
			class Test {
				void m() {
					int x = 1;
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode stmt = (ASTNode) method.getBody().statements().get(0);

		Match match = new Match(stmt, new HashMap<>(), stmt.getStartPosition(), stmt.getLength());
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertTrue(guard.evaluate(ctx, "all"), //$NON-NLS-1$
				"Should detect @SuppressWarnings on enclosing class"); //$NON-NLS-1$
	}

	@Test
	public void testHasSuppressWarningsAbsent() {
		GuardFunction guard = guards.get("hasSuppressWarnings"); //$NON-NLS-1$
		String code = """
			class Test {
				void m() {
					int x = 1;
				}
			}
			"""; //$NON-NLS-1$
		CompilationUnit cu = parseCodeWithBindings(code);
		TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = typeDecl.getMethods()[0];
		ASTNode stmt = (ASTNode) method.getBody().statements().get(0);

		Match match = new Match(stmt, new HashMap<>(), stmt.getStartPosition(), stmt.getLength());
		GuardContext ctx = GuardContext.fromMatch(match, cu);

		assertFalse(guard.evaluate(ctx, "unchecked"), //$NON-NLS-1$
				"Should return false when no @SuppressWarnings present"); //$NON-NLS-1$
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

	private CompilationUnit parseCodeWithBindings(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		astParser.setResolveBindings(true);
		astParser.setEnvironment(new String[0], new String[0], new String[0], true);
		astParser.setUnitName("Test.java"); //$NON-NLS-1$
		return (CompilationUnit) astParser.createAST(null);
	}

	private ASTNode findSimpleNameInNode(ASTNode node, String name) {
		ASTNode[] result = new ASTNode[1];
		node.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.SimpleName simpleName) {
				if (simpleName.getIdentifier().equals(name)) {
					result[0] = simpleName;
					return false;
				}
				return true;
			}
		});
		return result[0];
	}

	private ASTNode findClassInstanceCreationInNode(ASTNode node) {
		ASTNode[] result = new ASTNode[1];
		node.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation cic) {
				result[0] = cic;
				return false;
			}
		});
		return result[0];
	}

	private ASTNode findNodeOfType(ASTNode root, Class<? extends ASTNode> nodeType) {
		ASTNode[] result = new ASTNode[1];
		root.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (result[0] == null && nodeType.isInstance(node)) {
					result[0] = node;
				}
			}
		});
		return result[0];
	}
}