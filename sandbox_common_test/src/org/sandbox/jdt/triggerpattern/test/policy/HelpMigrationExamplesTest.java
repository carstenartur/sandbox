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
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Binds installed Help examples to existing executable input/output fixtures, not a second rewrite oracle. */
@SuppressWarnings("nls")
public class HelpMigrationExamplesTest {

	private static final String INT_TESTS= "sandbox_int_to_enum_test/src/org/eclipse/jdt/ui/tests/quickfix/Java22/";
	private static final String JUNIT_TESTS= "sandbox_junit_cleanup_test/src/org/eclipse/jdt/ui/tests/quickfix/Java8/";

	@Test
	void localIntToEnumIncludesThePrivateSignatureAndCallSites() throws Exception {
		assertFixtureExamples("sandbox_int_to_enum_help", INT_TESTS + "IntToEnumCleanUpTest.java",
				"testBasicIfElseIntStateToEnum", "local-before", "local-after");
	}

	@Test
	void projectWideIntToEnumIncludesBothCompleteFiles() throws Exception {
		assertFixtureExamples("sandbox_int_to_enum_help", INT_TESTS + "IntEnumReferenceQualificationTest.java",
				"supportsGenericOwnerInDefaultPackage", "project-owner-before", "project-client-before",
				"project-owner-after", "project-client-after");
	}

	@Test
	void junit3ExamplePreservesTheTestAndLifecycleMethods() throws Exception {
		assertFixtureExamples("sandbox_junit_cleanup_help", JUNIT_TESTS + "MigrationJUnit3Test.java",
				"removes_extends_TestCase", "junit3-before", "junit3-after");
	}

	@Test
	void junit4ExamplePreservesMessageAndArgumentOrder() throws Exception {
		assertFixtureExamples("sandbox_junit_cleanup_help", JUNIT_TESTS + "MigrationAssertionsTest.java",
				"migrates_assertEquals_with_message_parameter_order", "junit4-before", "junit4-after");
	}

	@Test
	void existingMethodReuseDoesNotInventAnExtractedMethod() throws Exception {
		assertFixtureExamples("sandbox_method_reuse_help",
				"sandbox_method_reuse_test/src/org/sandbox/jdt/ui/tests/quickfix/MethodReuseCleanUpTest.java",
				"SIMPLE_INLINE_SEQUENCE", "existing-method-before", "existing-method-after");
	}

	@Test
	void encodingKeepBehaviorMakesTheRuntimeDefaultExplicit() throws Exception {
		assertEncodingExamples("KeepBehavior", "encoding-keep-after", false);
	}

	@Test
	void encodingPreferUtf8DocumentsItsPolicyChange() throws Exception {
		assertEncodingExamples("PreferUTF8", "encoding-prefer-after", false);
	}

	@Test
	void encodingAggregationIncludesTheFieldAndQualifiedUses() throws Exception {
		assertEncodingExamples("AggregateUTF8", "encoding-aggregate-after", true);
	}

	@Test
	void encodingComparisonRejectsAChangedDefaultAndAnUnchangedCall() throws Exception {
		String source= "class E1 { void example() throws Exception { "
				+ "InputStreamReader is1 = new InputStreamReader(input, Charset.defaultCharset()); } }";
		for (String broken : List.of(source.replace("Charset.defaultCharset()", "StandardCharsets.UTF_8"),
				source.replace(", Charset.defaultCharset()", ""))) {
			Document page= parseHtml("<html><pre id=\"policy\">" + broken + "</pre></html>");
			assertThrows(AssertionError.class, () -> assertExample(source, page, "policy"), broken);
		}
	}

	@Test
	void exampleComparisonRejectsMissingDuplicateBlankInvalidAndStaleCode() throws Exception {
		String expected= "public class Example { void run() { process(Status.PENDING); } }";
		String element= "<pre id=\"sample\">" + expected + "</pre>";
		assertExample(expected, parseHtml("<html>" + element + "</html>"), "sample");
		for (String body : List.of("", element + element, "<pre id=\"sample\"> </pre>",
				element.replace("Status.PENDING", "STATUS_PENDING"), element.replace("void run()", "void ("))) {
			Document document= parseHtml("<html>" + body + "</html>");
			assertThrows(AssertionError.class, () -> assertExample(expected, document, "sample"), body);
		}
	}

	private static void assertEncodingExamples(String strategy, String afterId, boolean aggregate) throws Exception {
		Path root= repositoryRoot();
		String fixture= "sandbox_encoding_quickfix_test/src/org/eclipse/jdt/ui/tests/quickfix/Java10/"
				+ "ExplicitEncodingPatterns" + strategy + ".java";
		List<String> sources= fixtureSources(root, fixture, "INPUTSTREAMREADER");
		assertEquals(2, sources.size(), fixture);
		Document page= parseHtml(Files.readString(root.resolve("sandbox_encoding_quickfix_help/html/usage.html"),
				StandardCharsets.UTF_8));
		assertExample(encodingExcerpt(sources.get(0), false), page, "encoding-before");
		assertExample(encodingExcerpt(sources.get(1), aggregate), page, afterId);
	}

	private static String encodingExcerpt(String source, boolean aggregate) {
		List<String> fields= new ArrayList<>();
		List<String> statements= new ArrayList<>();
		List<String> names= new ArrayList<>();
		parseJava(source).accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				return "method".equals(node.getName().getIdentifier());
			}

			@Override
			public boolean visit(FieldDeclaration node) {
				if (node.fragments().size() == 1 && "UTF_8".equals(
						((VariableDeclarationFragment) node.fragments().get(0)).getName().getIdentifier())) {
					fields.add(node.toString());
				}
				return false;
			}

			@Override
			public boolean visit(VariableDeclarationStatement node) {
				if (node.fragments().size() == 1) {
					String name= ((VariableDeclarationFragment) node.fragments().get(0)).getName().getIdentifier();
					if (List.of("is1", "is2").contains(name)) {
						names.add(name);
						statements.add(node.toString());
					}
				}
				return false;
			}
		});
		assertEquals(List.of("is1", "is2"), names, "Missing or ambiguous reader declarations in fixture");
		assertEquals(aggregate ? 1 : 0, fields.size(), "Unexpected aggregation field in fixture");
		// Presentation wrapper only: the original constructor/field ASTs are retained, not recomputed.
		return "class E1 {\n" + String.join("\n", fields) + "\nvoid example() throws Exception {\n"
				+ String.join("\n", statements) + "\n}\n}";
	}

	private static void assertFixtureExamples(String helpBundle, String testPath, String memberName,
			String... exampleIds) throws Exception {
		Path root= repositoryRoot();
		List<String> sources= fixtureSources(root, testPath, memberName);
		assertEquals(exampleIds.length, sources.size(), "Fixture source count: " + testPath + "#" + memberName);
		Document page= parseHtml(Files.readString(root.resolve(helpBundle + "/html/usage.html"), StandardCharsets.UTF_8));
		for (int index= 0; index < exampleIds.length; index++) {
			assertExample(sources.get(index), page, exampleIds[index]);
		}
	}

	private static List<String> fixtureSources(Path root, String testPath, String memberName) throws IOException {
		CompilationUnit test= parseJava(Files.readString(root.resolve(testPath), StandardCharsets.UTF_8));
		List<ASTNode> members= new ArrayList<>();
		test.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (memberName.equals(node.getName().getIdentifier())) {
					members.add(node);
				}
				return false;
			}

			@Override
			public boolean visit(EnumConstantDeclaration node) {
				if (memberName.equals(node.getName().getIdentifier())) {
					members.add(node);
				}
				return false;
			}
		});
		assertEquals(1, members.size(), testPath + "#" + memberName);
		List<String> sources= new ArrayList<>();
		members.getFirst().accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				String value= literalSource(node);
				if (value != null && (value.stripLeading().startsWith("package ")
						|| value.stripLeading().startsWith("public class "))) {
					sources.add(value);
					return false;
				}
				return true;
			}
		});
		return sources;
	}

	private static void assertExample(String expected, Document page, String id) {
		NodeList blocks= page.getElementsByTagName("pre");
		List<String> matches= new ArrayList<>();
		for (int index= 0; index < blocks.getLength(); index++) {
			Element block= (Element) blocks.item(index);
			if (id.equals(block.getAttribute("id"))) {
				matches.add(block.getTextContent());
			}
		}
		assertEquals(1, matches.size(), "Expected one Help example: " + id);
		assertFalse(matches.getFirst().isBlank(), "Empty Help example: " + id);
		// AST rendering ignores layout differences, but retains declarations, imports, calls and literal contents.
		assertEquals(parseJava(expected).toString(), parseJava(matches.getFirst()).toString(), id);
	}

	private static CompilationUnit parseJava(String source) {
		ASTParser parser= ASTParser.newParser(AST.JLS21);
		parser.setCompilerOptions(Map.of(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_21,
				JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_21,
				JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_21));
		parser.setSource(source.toCharArray());
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		assertEquals(List.of(), Arrays.stream(unit.getProblems()).filter(IProblem::isError)
				.map(IProblem::getMessage).toList(), "Invalid Java example or fixture");
		assertFalse(unit.types().isEmpty(), "Expected a complete Java source fixture");
		return unit;
	}

	private static String literalSource(ASTNode node) {
		if (node instanceof TextBlock block) {
			return block.getLiteralValue();
		}
		if (node instanceof StringLiteral literal) {
			return literal.getLiteralValue();
		}
		if (node instanceof InfixExpression expression && expression.getOperator() == InfixExpression.Operator.PLUS) {
			String left= literalSource(expression.getLeftOperand());
			String right= literalSource(expression.getRightOperand());
			if (left == null || right == null) {
				return null;
			}
			StringBuilder result= new StringBuilder(left).append(right);
			for (Object operand : expression.extendedOperands()) {
				String value= literalSource((ASTNode) operand);
				if (value == null) {
					return null;
				}
				result.append(value);
			}
			return result.toString();
		}
		return null;
	}

	private static Document parseHtml(String html) throws Exception {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory.newDocumentBuilder().parse(new InputSource(new StringReader(html)));
	}

	private static Path repositoryRoot() throws IOException {
		Path current= Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml"))
					&& Files.isRegularFile(current.resolve(".github/workflows/maven.yml"))) {
				return current;
			}
			current= current.getParent();
		}
		throw new IOException("Cannot locate the Sandbox repository root");
	}
}
