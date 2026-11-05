/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.tools.ToolProvider;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.HintFile;

/** Regression cases for behavior-preserving miscellaneous hint rewrites. */
class MiscHintSafetyTest extends HintRuleTestSupport {

	private HintFile hints;

	@BeforeEach
	void setUp() throws Exception {
		registerBuiltInGuards();
		hints= loadBundledHint("misc.sandbox-hint"); //$NON-NLS-1$
	}

	@Test
	void doesNotReplaceNullableOrArrayToString() {
		assertNoMatch(hints, "class Test { String m(String value) { return value.toString(); } }"); //$NON-NLS-1$
		assertNoMatch(hints, "class Test { String m(char[] value) { return value.toString(); } }"); //$NON-NLS-1$
	}

	@Test
	void preservesUnboxingOfNullableBooleanConditions() {
		assertNoMatch(hints, "class Test { Boolean m(Boolean value) { return value ? true : false; } }"); //$NON-NLS-1$
		assertNoMatch(hints, "class Test { boolean m(Boolean value) { return value ? true : false; } }"); //$NON-NLS-1$
	}

	@Test
	void doesNotChangeSwitchExpressionTargetTyping() {
		assertNoMatch(hints, """
				class Test {
				  Boolean m(int x, Boolean value) {
				    return (switch (x) { case 0 -> { yield true; } default -> { yield value; } }) ? true : false;
				  }
				}
				"""); //$NON-NLS-1$
		assertNoMatch(hints, """
				class Test {
				  static void use(boolean value) {}
				  static void use(Boolean value) {}
				  static void m(int i) {
				    use((switch (i) { case 0 -> { yield true; } default -> { yield Boolean.FALSE; } }) ? true : false);
				  }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void avoidsRenderingNestedSwitchConstructs() {
		assertNoMatch(hints, """
				class Test {
				  boolean m(int i, boolean b) {
				    return !((switch (i) { case 0 -> true; default -> false; }) && b);
				  }
				}
				"""); //$NON-NLS-1$
		assertNoMatch(hints, """
				class Test {
				  boolean m(int i, boolean b) {
				    return (b && (switch (i) { case 0 -> true; default -> false; })) ? true : false;
				  }
				}
				"""); //$NON-NLS-1$
		assertNoMatch(hints, """
				class Test {
				  boolean m(int i) {
				    return !((switch (i) { case 0 -> "a"; default -> "b"; }).length() == 0);
				  }
				}
				"""); //$NON-NLS-1$
		assertNoMatch(hints, """
				class Test {
				  boolean test(java.util.function.IntConsumer c) { return true; }
				  boolean m(boolean b) {
				    return !(test(n -> { switch (n) { case 0 -> System.out.println(0); default -> System.out.println(1); } }) && b);
				  }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void simplifiesPrimitiveBooleanCondition() {
		assertFullReplacement(hints,
				"class Test { boolean m(boolean value) { return value ? true : false; } }", //$NON-NLS-1$
				"class Test { boolean m(boolean value) { return value; } }"); //$NON-NLS-1$
		assertFullReplacement(hints,
				"class Test { boolean check() { return true; } boolean m() { return check() ? true : false; } }", //$NON-NLS-1$
				"class Test { boolean check() { return true; } boolean m() { return check(); } }"); //$NON-NLS-1$
	}

	@Test
	void preservesLambdaOverloadSelection() {
		assertNoMatch(hints, """
				class Test {
				  void use(java.util.function.Predicate<String> p) {}
				  void use(java.util.function.Consumer<String> c) {}
				  boolean check(String value) { return true; }
				  void m() { use(value -> check(value) ? true : false); }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void requiresResolvedPrimitiveBooleanCondition() {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource("class Test { boolean m(boolean b) { return b ? true : false; } }".toCharArray()); //$NON-NLS-1$
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		assertTrue(new BatchTransformationProcessor(hints).process(unit, Map.of()).stream()
				.noneMatch(BatchTransformationProcessor.TransformationResult::hasReplacement));
	}

	@Test
	void keepsPrecedenceInDeMorganReplacement() {
		assertFullReplacement(hints,
				"class Test { boolean m(int a, int b, boolean c) { return !(a == b || c); } }", //$NON-NLS-1$
				"class Test { boolean m(int a, int b, boolean c) { return (!(a == b) && !c); } }"); //$NON-NLS-1$
	}

	@Test
	void doesNotDropAdditionalBooleanOperands() {
		assertNoMatch(hints, "class Test { boolean m(boolean a, boolean b, boolean c) { return !(a || b || c); } }"); //$NON-NLS-1$
	}

	@Test
	void nestedDeMorganRewritePreservesRuntimeBehavior(@TempDir Path temporary) throws Exception {
		String original= "public class Probe { public static boolean run(boolean c, boolean a, boolean b) { return c && !(a && b); } }"; //$NON-NLS-1$
		String rewritten= applyJdtRewrite(original);
		try (var before= compile(original, temporary.resolve("before")); //$NON-NLS-1$
				var after= compile(rewritten, temporary.resolve("after"))) { //$NON-NLS-1$
			var originalMethod= before.loadClass("Probe").getMethod("run", boolean.class, boolean.class, boolean.class); //$NON-NLS-1$ //$NON-NLS-2$
			var rewrittenMethod= after.loadClass("Probe").getMethod("run", boolean.class, boolean.class, boolean.class); //$NON-NLS-1$ //$NON-NLS-2$
			for (boolean c : new boolean[] { false, true }) {
				for (boolean a : new boolean[] { false, true }) {
					for (boolean b : new boolean[] { false, true }) {
						assertEquals(originalMethod.invoke(null, c, a, b), rewrittenMethod.invoke(null, c, a, b), rewritten);
					}
				}
			}
		}
	}

	@Test
	void nestedLengthComparisonStillCompiles(@TempDir Path temporary) throws Exception {
		String original= "public class Probe { public static boolean run(boolean b, String s) { return b == !(s.length() == 0); } }"; //$NON-NLS-1$
		String rewritten= applyJdtRewrite(original);
		try (var before= compile(original, temporary.resolve("before")); //$NON-NLS-1$
				var after= compile(rewritten, temporary.resolve("after"))) { //$NON-NLS-1$
			var originalMethod= before.loadClass("Probe").getMethod("run", boolean.class, String.class); //$NON-NLS-1$ //$NON-NLS-2$
			var rewrittenMethod= after.loadClass("Probe").getMethod("run", boolean.class, String.class); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(originalMethod.invoke(null, true, "x"), rewrittenMethod.invoke(null, true, "x")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(originalMethod.invoke(null, false, ""), rewrittenMethod.invoke(null, false, "")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Test
	void doesNotReplaceMutableSystemProperties() {
		assertNoMatch(hints, "class Test { String m() { return System.getProperty(\"line.separator\"); } }"); //$NON-NLS-1$
		assertNoMatch(hints, "class Test { String m() { return System.getProperty(\"file.encoding\"); } }"); //$NON-NLS-1$
	}

	@Test
	void doesNotNormalizeLiteralNewlines() {
		assertNoMatch(hints, "class Test { String m(String a, String b) { return a + \"\\n\\n\" + b; } }"); //$NON-NLS-1$
	}

	@Test
	void retainsCommentsAroundBooleanRewrites() {
		assertNoMatch(hints, "class Test { boolean m(boolean a) { return /* keep */ a ? true : false; } }"); //$NON-NLS-1$
		assertNoMatch(hints, "class Test { boolean m(boolean a, boolean b) { return /* keep */ !(a || b); } }"); //$NON-NLS-1$
		assertNoMatch(hints, "class Test { boolean m(boolean a, boolean b) { return /* keep */ !(a && b); } }"); //$NON-NLS-1$
		assertNoMatch(hints, "class Test { boolean m(String a) { return /* keep */ !(a.length() == 0); } }"); //$NON-NLS-1$
	}

	private String applyJdtRewrite(String source) throws Exception {
		Map<String, String> options= new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions("21", options); //$NON-NLS-1$
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setCompilerOptions(options);
		parser.setUnitName("Probe.java"); //$NON-NLS-1$
		parser.setResolveBindings(true);
		parser.setEnvironment(new String[0], null, null, true);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		var match= new BatchTransformationProcessor(hints).process(unit, options).stream()
				.filter(BatchTransformationProcessor.TransformationResult::hasReplacement).findFirst().orElseThrow();
		ASTParser replacementParser= ASTParser.newParser(AST.getJLSLatest());
		replacementParser.setKind(ASTParser.K_EXPRESSION);
		replacementParser.setSource(match.replacement().toCharArray());
		replacementParser.setCompilerOptions(options);
		ASTNode replacement= replacementParser.createAST(null);
		ASTRewrite rewrite= ASTRewrite.create(unit.getAST());
		rewrite.replace(match.match().getMatchedNode(), ASTNode.copySubtree(unit.getAST(), replacement), null);
		Document document= new Document(source);
		rewrite.rewriteAST(document, options).apply(document);
		return document.get();
	}

	private static URLClassLoader compile(String source, Path directory) throws Exception {
		Files.createDirectories(directory);
		Path file= directory.resolve("Probe.java"); //$NON-NLS-1$
		Files.writeString(file, source);
		int exit= ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", directory.toString(), file.toString()); //$NON-NLS-1$
		assertEquals(0, exit, source);
		return new URLClassLoader(new java.net.URL[] { directory.toUri().toURL() }, null);
	}
}
