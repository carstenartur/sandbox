/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;
import org.sandbox.jdt.triggerpattern.eclipse.HintContext;
import org.sandbox.jdt.triggerpattern.string.StringSimplificationHintProvider;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for string simplification cleanup using TriggerPattern hints.
 * 
 * <p>These tests verify that the cleanup correctly transforms code patterns
 * by comparing input (given) code with expected output code.</p>
 * 
 * @since 1.2.5
 */
public class StringSimplificationCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ========== Empty String Prefix Tests ==========

	/**
	 * Tests transformation of {@code "" + value} to {@code String.valueOf(value)}.
	 */
	@Test
	public void testEmptyStringPrefixSimple() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = "" + value;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = String.valueOf(value);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code "" + object} to {@code String.valueOf(object)}.
	 */
	@Test
	public void testEmptyStringPrefixWithObject() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(Object obj) {
			        String result = "" + obj;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(Object obj) {
			        String result = String.valueOf(obj);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code "" + booleanValue} to {@code String.valueOf(booleanValue)}.
	 */
	@Test
	public void testEmptyStringPrefixWithBoolean() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        String result = "" + flag;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        String result = String.valueOf(flag);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== Empty String Suffix Tests ==========

	/**
	 * Tests transformation of {@code value + ""} to {@code String.valueOf(value)}.
	 */
	@Test
	public void testEmptyStringSuffixSimple() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = value + "";
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = String.valueOf(value);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code object + ""} to {@code String.valueOf(object)}.
	 */
	@Test
	public void testEmptyStringSuffixWithObject() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(Object obj) {
			        String result = obj + "";
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(Object obj) {
			        String result = String.valueOf(obj);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== String Length Check Tests ==========

	/**
	 * Tests transformation of {@code str.length() == 0} to {@code str.isEmpty()}.
	 */
	@Test
	public void testStringLengthCheckToIsEmpty() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(String str) {
			        boolean empty = str.length() == 0;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(String str) {
			        boolean empty = str.isEmpty();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code str.equals("")} to {@code str.isEmpty()}.
	 */
	@Test
	public void testStringEqualsEmptyToIsEmpty() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(String str) {
			        boolean empty = str.equals("");
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(String str) {
			        boolean empty = str.isEmpty();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== Boolean Simplification Tests ==========

	/**
	 * Tests transformation of {@code x == true} to {@code x}.
	 */
	@Test
	public void testBooleanEqualsTrue() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = flag == true;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = flag;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code x == false} to {@code !x}.
	 */
	@Test
	public void testBooleanEqualsFalse() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = flag == false;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = !flag;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== Ternary Simplification Tests ==========

	/**
	 * Tests transformation of {@code cond ? true : false} to {@code cond}.
	 */
	@Test
	public void testTernaryTrueFalse() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = flag ? true : false;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = flag;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code cond ? false : true} to {@code !cond}.
	 */
	@Test
	public void testTernaryFalseTrue() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = flag ? false : true;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(boolean flag) {
			        boolean result = !flag;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== Multiple Transformations Test ==========

	/**
	 * Tests multiple transformations in a single file.
	 */
	@Test
	public void testMultipleTransformations() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(int value, String str, boolean flag) {
			        String s1 = "" + value;
			        String s2 = str + "";
			        boolean empty = str.length() == 0;
			        boolean b1 = flag == true;
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(int value, String str, boolean flag) {
			        String s1 = String.valueOf(value);
			        String s2 = String.valueOf(str);
			        boolean empty = str.isEmpty();
			        boolean b1 = flag;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== No Change Tests ==========

	/**
	 * Tests that already clean code is not modified.
	 */
	@Test
	public void testNoChangeWhenAlreadyClean() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(int value, String str) {
			        String s1 = String.valueOf(value);
			        boolean empty = str.isEmpty();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that regular string concatenation is not modified.
	 */
	@Test
	public void testNoChangeForRegularConcatenation() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(String a, String b) {
			        String result = a + b;
			        String message = "Hello " + a;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that cleanup is disabled when option is not enabled.
	 */
	@Test
	public void testNoChangeWhenDisabled() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = "" + value;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		// Note: NOT enabling the cleanup option
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	// ========== Collection Size Check Tests ==========

	/**
	 * Tests transformation of {@code list.size() == 0} to {@code list.isEmpty()}.
	 */
	@Test
	public void testCollectionSizeEqualsZero() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			import java.util.List;

			public class E1 {
			    void method(List<String> list) {
			        boolean empty = list.size() == 0;
			    }
			}
			""";

		String expected = """
			package test1;

			import java.util.List;

			public class E1 {
			    void method(List<String> list) {
			        boolean empty = list.isEmpty();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code list.size() > 0} to {@code !list.isEmpty()}.
	 */
	@Test
	public void testCollectionSizeGreaterThanZero() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			import java.util.List;

			public class E1 {
			    void method(List<String> list) {
			        boolean hasItems = list.size() > 0;
			    }
			}
			""";

		String expected = """
			package test1;

			import java.util.List;

			public class E1 {
			    void method(List<String> list) {
			        boolean hasItems = !list.isEmpty();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	@Test
	public void testMapAndConcreteCollectionSizeChecks() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
			package test1;

			import java.util.ArrayList;
			import java.util.Map;

			class E1 {
			    boolean empty(Map<String, Integer> map) {
			        return map.size() == 0;
			    }

			    boolean nonEmpty(ArrayList<String> list) {
			        return list.size() > 0;
			    }
			}
			""";
		String expected = given.replace("map.size() == 0", "map.isEmpty()")
				.replace("list.size() > 0", "!list.isEmpty()");

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	@Test
	public void testCollectionSizeCheckRejectsUnrelatedSizeMethods() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
			package test1;

			class E1 {
			    static class Dimensions {
			        int size() { return 0; }
			    }

			    static class Buffer {
			        int size() { return 1; }
			        boolean isEmpty() { return true; }
			    }

			    boolean empty(Dimensions dimensions) {
			        return dimensions.size() == 0;
			    }

			    boolean nonEmpty(Buffer buffer) {
			        return buffer.size() > 0;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testCollectionSizeCheckPreservesLambdaCompatibilityAndComments() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
			package test1;

			import java.util.List;
			import java.util.function.Consumer;
			import java.util.function.Predicate;

			class E1 {
			    void use(Consumer<List<String>> action) {}
			    void use(Predicate<List<String>> predicate) {}

			    void test() {
			        use(list -> list.size() == 0);
			    }

			    boolean empty(List<String> list) {
			        return list.size() /* count includes pending entries */ == 0;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testCollectionSizeCheckPreservesCustomIsEmptyImplementation() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
			package test1;

			import java.util.ArrayList;

			class E1 extends ArrayList<String> {
			    private static final long serialVersionUID = 1L;

			    @Override
			    public boolean isEmpty() {
			        return this.size() == 0;
			    }
			}

			class Indirect extends ArrayList<String> {
			    private static final long serialVersionUID = 1L;

			    @Override
			    public boolean isEmpty() {
			        return !hasValues();
			    }

			    private boolean hasValues() {
			        ArrayList<String> values = this;
			        return values.size() > 0;
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testCollectionQuickAssistChecksBindingsBeforeRewriting() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
			package test1;

			import java.util.Map;

			class E1 {
			    static class Dimensions {
			        int size() { return 0; }
			    }

			    boolean empty(Dimensions dimensions) {
			        return dimensions.size() == 0;
			    }

			    boolean empty(Map<?, ?> map) {
			        return map.size() == 0;
			    }
			}
			""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit root = (CompilationUnit) parser.createAST(null);
		var matches = new TriggerPatternEngine().findMatches(root, Pattern.of("$list.size() == 0", PatternKind.EXPRESSION));
		assertNull(StringSimplificationHintProvider.replaceCollectionSizeCheck(
				new HintContext(root, cu, matches.getFirst(), ASTRewrite.create(root.getAST()))));
		assertNotNull(StringSimplificationHintProvider.replaceCollectionSizeCheck(
				new HintContext(root, cu, matches.getLast(), ASTRewrite.create(root.getAST()))));
	}

	// ========== StringBuilder/String.format Tests ==========

	/**
	 * Tests transformation of {@code new StringBuilder().append(x).toString()} to {@code String.valueOf(x)}.
	 */
	@Test
	public void testStringBuilderSingleAppend() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = new StringBuilder().append(value).toString();
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(int value) {
			        String result = String.valueOf(value);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code String.format("%s", x)} to {@code String.valueOf(x)}.
	 */
	@Test
	public void testStringFormatSimplification() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(Object obj) {
			        String result = String.format("%s", obj);
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method(Object obj) {
			        String result = String.valueOf(obj);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ========== Objects.equals/requireNonNullElse Tests ==========

	/**
	 * Tests transformation of {@code x.toString().equals(y)} to {@code Objects.equals(x.toString(), y)}.
	 */
	@Test
	public void testToStringEqualsToObjectsEquals() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(Object obj, String str) {
			        boolean result = obj.toString().equals(str);
			    }
			}
			""";

		String expected = """
			package test1;

			import java.util.Objects;

			public class E1 {
			    void method(Object obj, String str) {
			        boolean result = Objects.equals(obj.toString(), str);
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code x != null ? x : defaultVal} to
	 * {@code Objects.requireNonNullElse(x, defaultVal)}.
	 */
	@Test
	public void testNullCheckTernaryToRequireNonNullElse() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method(String value) {
			        String result = value != null ? value : "default";
			    }
			}
			""";

		String expected = """
			package test1;

			import java.util.Objects;

			public class E1 {
			    void method(String value) {
			        String result = Objects.requireNonNullElse(value, "default");
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
