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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

import java.util.List;

/**
 * Tests for {@link HintFileSerializer}.
 */
class HintFileSerializerTest {

	private final HintFileSerializer serializer = new HintFileSerializer();
	private final HintFileParser parser = new HintFileParser();

	@Test
	void testSerializeEmptyHintFile() {
		HintFile hintFile = new HintFile();
		String result = serializer.serialize(hintFile);
		assertNotNull(result);
		assertTrue(result.isEmpty() || result.isBlank());
	}

	@Test
	void testSerializeWithMetadata() {
		HintFile hintFile = new HintFile();
		hintFile.setId("test.rule");
		hintFile.setDescription("A test rule");
		hintFile.setSeverity(Severity.WARNING);
		hintFile.setMinJavaVersion(11);
		hintFile.setTags(List.of("performance", "modernization"));

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("<!id: test.rule>"));
		assertTrue(result.contains("<!description: A test rule>"));
		assertTrue(result.contains("<!severity: warning>"));
		assertTrue(result.contains("<!minJavaVersion: 11>"));
		assertTrue(result.contains("<!tags: performance, modernization>"));
	}

	@Test
	void testSerializeWithDefaultSeverityOmitted() {
		HintFile hintFile = new HintFile();
		hintFile.setId("test.rule");
		// INFO is default — should not appear in output
		hintFile.setSeverity(Severity.INFO);

		String result = serializer.serialize(hintFile);

		assertFalse(result.contains("<!severity:"));
	}

	@Test
	void testSerializeSimpleRule() {
		HintFile hintFile = new HintFile();
		hintFile.setId("boolean.constructor");

		Pattern source = new Pattern("new Boolean(true)", PatternKind.CONSTRUCTOR, null, null, null, null, null);
		RewriteAlternative alt = RewriteAlternative.otherwise("Boolean.TRUE");
		TransformationRule rule = new TransformationRule(null, null, source, null, List.of(alt), null, null);
		hintFile.addRule(rule);

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("new Boolean(true)"));
		assertTrue(result.contains("=> Boolean.TRUE"));
		assertTrue(result.contains(";;"));
	}

	@Test
	void testSerializeRuleWithGuard() {
		HintFile hintFile = new HintFile();
		hintFile.setId("guarded.rule");

		Pattern source = new Pattern("$s.getBytes(\"UTF-8\")", PatternKind.METHOD_CALL, null, null, null, null, null);
		GuardExpression guard = new GuardExpression.FunctionCall("sourceVersionGE", List.of("7"));
		RewriteAlternative alt = RewriteAlternative.otherwise(
				"$s.getBytes(java.nio.charset.StandardCharsets.UTF_8)");
		TransformationRule rule = new TransformationRule(null, null, source, guard, List.of(alt), null, null);
		hintFile.addRule(rule);

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("$s.getBytes(\"UTF-8\") :: sourceVersionGE(7)"));
		assertTrue(result.contains("=> $s.getBytes(java.nio.charset.StandardCharsets.UTF_8)"));
	}

	@Test
	void testSerializeRuleWithDescription() {
		HintFile hintFile = new HintFile();

		Pattern source = new Pattern("old()", PatternKind.METHOD_CALL, null, null, null, null, null);
		TransformationRule rule = new TransformationRule(null, "Use new API", source, null, List.of(), null, null);
		hintFile.addRule(rule);

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("\"Use new API\":"));
	}

	@Test
	void testSerializeRoundTrip() throws HintParseException {
		String original = """
				<!id: roundtrip.test>
				<!description: Round trip test>
				<!severity: warning>
				<!minJavaVersion: 11>
				<!tags: test, roundtrip>

				new Boolean(true)
				=> Boolean.TRUE
				;;
				""";

		HintFile parsed = parser.parse(original);
		String serialized = serializer.serialize(parsed);
		HintFile reparsed = parser.parse(serialized);

		assertEquals(parsed.getId(), reparsed.getId());
		assertEquals(parsed.getDescription(), reparsed.getDescription());
		assertEquals(parsed.getSeverityAsString(), reparsed.getSeverityAsString());
		assertEquals(parsed.getMinJavaVersion(), reparsed.getMinJavaVersion());
		assertEquals(parsed.getRules().size(), reparsed.getRules().size());
	}

	@Test
	void testSerializeWithIncludes() {
		HintFile hintFile = new HintFile();
		hintFile.setId("parent");
		hintFile.addInclude("child1");
		hintFile.addInclude("child2");

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("<!include: child1>"));
		assertTrue(result.contains("<!include: child2>"));
	}

	@Test
	void testSerializeCaseInsensitive() {
		HintFile hintFile = new HintFile();
		hintFile.setCaseInsensitive(true);

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("<!caseInsensitive>"));
	}

	@Test
	void testSerializeNullThrows() {
		assertThrows(NullPointerException.class, () -> serializer.serialize(null));
	}

	@Test
	void testSerializeSuppressWarnings() {
		HintFile hintFile = new HintFile();
		hintFile.addSuppressWarnings("unchecked");
		hintFile.addSuppressWarnings("deprecation");

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("<!suppressWarnings: unchecked, deprecation>"));
	}

	@Test
	void testSerializeTreeKind() {
		HintFile hintFile = new HintFile();
		hintFile.setTreeKindNodeTypes(List.of(
				org.eclipse.jdt.core.dom.ASTNode.METHOD_DECLARATION));

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("<!treeKind: METHOD_DECLARATION>"));
	}

	@Test
	void testSerializePerRuleId() {
		HintFile hintFile = new HintFile();
		Pattern pattern = new Pattern("$x.foo()", PatternKind.METHOD_CALL, null, null, null, null, null);
		RewriteAlternative alt = new RewriteAlternative("$x.bar()", null);
		TransformationRule rule = new TransformationRule(
				"my.rule.id", null, pattern, null, List.of(alt), null, null);
		hintFile.addRule(rule);

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("@id: my.rule.id"), "Serialized DSL should contain @id annotation");
	}

	@Test
	void testSerializePerRuleSeverity() {
		HintFile hintFile = new HintFile();
		Pattern pattern = new Pattern("$x.foo()", PatternKind.METHOD_CALL, null, null, null, null, null);
		RewriteAlternative alt = new RewriteAlternative("$x.bar()", null);
		TransformationRule rule = new TransformationRule(
				null, null, pattern, null, List.of(alt), null, Severity.ERROR);
		hintFile.addRule(rule);

		String result = serializer.serialize(hintFile);

		assertTrue(result.contains("@severity: error"), "Serialized DSL should contain @severity annotation");
	}

	@Test
	void testSerializerRoundTripWithPerRuleMetadata() throws HintParseException {
		String dsl = """
			<!id: test-hint>
			
			@id: rule.one
			@severity: warning
			$x.foo()
			=> $x.bar()
			;;
			""";

		HintFile hintFile = parser.parse(dsl);
		String serialized = serializer.serialize(hintFile);
		HintFile reparsed = parser.parse(serialized);

		assertEquals(1, reparsed.getRules().size());
		TransformationRule rule = reparsed.getRules().get(0);
		assertEquals("rule.one", rule.getRuleId());
		assertEquals(Severity.WARNING, rule.getSeverity());
	}
}
