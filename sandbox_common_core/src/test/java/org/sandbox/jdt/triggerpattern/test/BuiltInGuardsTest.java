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
		assertTrue(guards.containsKey("isNullable")); //$NON-NLS-1$
		assertTrue(guards.containsKey("isNonNull")); //$NON-NLS-1$
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
}
