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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.api.RewriteActionSchema;
import org.sandbox.jdt.triggerpattern.api.RewriteActionValue;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

class StructuredRewriteActionParserTest {

	@Test
	void parsesOrderedPlanAwareActionsAndTypedValueExpressions() throws HintParseException {
		String source= """
				<!id: structured.demo>
				<!requires-plan: structured/demo>
				void $name()
				=>! addAnnotation(target=$name,
				        annotation="org.junit.jupiter.api.Order",
				        value=planValue($name, "order"));
				    addModifier(target=$name, modifier=public)
				;;
				""";

		HintProgramParser.ParsedProgram program= new HintProgramParser().parse(source);
		HintFile hintFile= program.hintFile();
		RewriteAlternative alternative= hintFile.getRules().get(0).alternatives().get(0);

		assertNull(alternative.replacementPattern());
		assertTrue(alternative.hasStructuredActions());
		assertEquals(2, alternative.structuredActions().size());
		StructuredRewriteAction annotation= alternative.structuredActions().get(0);
		assertEquals("addAnnotation", annotation.name()); //$NON-NLS-1$
		assertEquals(new RewriteActionValue.Binding("$name"), annotation.arguments().get("target")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(new RewriteActionValue.Literal(
				SemanticPlanValue.string("org.junit.jupiter.api.Order")), //$NON-NLS-1$
				annotation.arguments().get("annotation")); //$NON-NLS-1$
		assertEquals(new RewriteActionValue.PlanValue("$name", "order"), //$NON-NLS-1$ //$NON-NLS-2$
				annotation.arguments().get("value")); //$NON-NLS-1$
		assertEquals("addModifier", alternative.structuredActions().get(1).name()); //$NON-NLS-1$
		assertFalse(program.expandedSource().contains("=>!")); //$NON-NLS-1$
		assertTrue(program.expandedSource().contains("__sandbox_structured_action_0__")); //$NON-NLS-1$
	}

	@Test
	void parsesClassLiteralNameAndListExpressions() throws HintParseException {
		HintFile hintFile= new HintProgramParser().parseHintFile("""
				Object $field;
				=>! addAnnotation(target=$field, annotation="example.Values",
				        value=list(classLiteral("example.One"), name("example.Mode.TWO"), 3, true))
				;;
				""");
		RewriteActionValue value= hintFile.getRules().get(0).alternatives().get(0)
				.structuredActions().get(0).arguments().get("value"); //$NON-NLS-1$
		RewriteActionValue.ListValue list= assertInstanceOf(RewriteActionValue.ListValue.class, value);
		assertEquals(4, list.values().size());
		assertInstanceOf(RewriteActionValue.ClassLiteral.class, list.values().get(0));
		assertInstanceOf(RewriteActionValue.Name.class, list.values().get(1));
		assertEquals(RewriteActionValue.literal(SemanticPlanValue.integer(3)), list.values().get(2));
		assertEquals(RewriteActionValue.literal(SemanticPlanValue.bool(true)), list.values().get(3));
	}

	@Test
	void catalogCanBeExtendedWithoutChangingTheCompatibilityParser() throws HintParseException {
		RewriteActionCatalog catalog= RewriteActionCatalog.standard().toBuilder()
				.register(new RewriteActionSchema("generateAdapter", Set.of("target"), //$NON-NLS-1$ //$NON-NLS-2$
						Set.of("name"), "Generate one adapter")) //$NON-NLS-1$ //$NON-NLS-2$
				.build();
		HintFile hintFile= new HintProgramParser(catalog).parseHintFile("""
				class $type {}
				=>! generateAdapter(target=$type, name="Generated")
				;;
				""");
		assertEquals("generateAdapter", hintFile.getRules().get(0).alternatives().get(0) //$NON-NLS-1$
				.structuredActions().get(0).name());
	}

	@Test
	void rejectsUnknownMissingDuplicateAndMalformedActions() {
		List<String> invalid= List.of(
				"void $name()\n=>! unknown(target=$name)\n;;", //$NON-NLS-1$
				"void $name()\n=>! addAnnotation(target=$name)\n;;", //$NON-NLS-1$
				"void $name()\n=>! removeDeclaration(target=$name, target=$name)\n;;", //$NON-NLS-1$
				"void $name()\n=>! addModifier(target=$name, modifier=public);\n;;", //$NON-NLS-1$
				"void $name()\n=>! addAnnotation(target=$name, annotation=planValue($name))\n;;"); //$NON-NLS-1$

		for (String source : invalid) {
			assertThrows(HintParseException.class, () -> new HintProgramParser().parse(source), source);
		}
	}
}
