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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.RewriteActionValue;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

class HintDeclarationDiffPreprocessorTest {

	@Test
	void lowersOneTargetDeclarationToTypedActions() throws Exception {
		String source= """
				<!requires-plan: signature-demo>
				@id: signature.demo
				public void $method(String $value, int $obsolete)
				=> protected void parameterized(Integer $value, long count)
				;;
				""";

		HintProgramParser.ParsedProgram program= new HintProgramParser().parse(source);
		HintFile hint= program.hintFile();
		List<StructuredRewriteAction> actions= hint.getRules().get(0)
				.alternatives().get(0).structuredActions();

		assertEquals(List.of("renameDeclaration", "removeModifier", "addModifier", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"replaceParameterType", "removeParameter", "addParameter"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				actions.stream().map(StructuredRewriteAction::name).toList());
		assertTrue(actions.stream().noneMatch(action -> action.arguments().containsKey("target"))); //$NON-NLS-1$
		assertEquals(new RewriteActionValue.Literal(SemanticPlanValue.string("parameterized")), //$NON-NLS-1$
				actions.get(0).arguments().get("name")); //$NON-NLS-1$
		assertEquals(new RewriteActionValue.Binding("$value"), //$NON-NLS-1$
				actions.get(3).arguments().get("name")); //$NON-NLS-1$
		assertEquals(new RewriteActionValue.Binding("$obsolete"), //$NON-NLS-1$
				actions.get(4).arguments().get("name")); //$NON-NLS-1$
		assertEquals(new RewriteActionValue.Literal(SemanticPlanValue.string("count")), //$NON-NLS-1$
				actions.get(5).arguments().get("name")); //$NON-NLS-1$
		assertFalse(program.expandedSource().contains("protected void parameterized")); //$NON-NLS-1$
	}

	@Test
	void ordinaryNonPlanDeclarationReplacementIsNotLowered() throws Exception {
		String source= """
				void $method()
				=> void renamed()
				;;
				""";

		String processed= HintDeclarationDiffPreprocessor.preprocess(source);

		assertEquals(source, processed);
	}

	@Test
	void annotationOnlyMethodReplacementKeepsTheEstablishedTextPath() throws Exception {
		String source= """
				<!requires-plan: annotation-demo>
				void $method()
				=> @Deprecated void $method()
				;;
				""";

		HintFile hint= new HintProgramParser().parseHintFile(source);

		assertTrue(hint.getRules().get(0).alternatives().get(0).hasTextReplacement());
		assertFalse(hint.getRules().get(0).alternatives().get(0).hasStructuredActions());
	}

	@Test
	void rejectsReturnTypeChangesAndParameterReordering() {
		assertThrows(HintParseException.class, () -> new HintProgramParser().parse("""
				<!requires-plan: signature-demo>
				void $method(String $value)
				=> int $method(String $value)
				;;
				"""));
		assertThrows(HintParseException.class, () -> new HintProgramParser().parse("""
				<!requires-plan: signature-demo>
				void $method(String $first, int $second)
				=> void $method(int $second, String $first)
				;;
				"""));
	}
}
