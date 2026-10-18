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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.ConstraintVariableType;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

class AnnotationConstraintSemanticsTest {

	@Test
	void explicitlyConstrainedPlaceholderRequiresResolvedType() throws Exception {
		assertCount("class Example { String show(String value) { return value.toString(); } }", false, "constrained", 0);
	}

	@Test
	void missingConstrainedPlaceholderDoesNotAuthorizeAMatch() throws Exception {
		assertCount("class Example { String show(String value) { return value.toString(); } }", true, "missing", 0);
	}

	@Test
	void resolvedCompatibleAndIncompatibleTypesHaveIdenticalSingleAndBatchResults() throws Exception {
		assertCount("class Example { String show(String value) { return value.toString(); } }", true, "constrained", 1);
		assertCount("class Example { String show(Integer value) { return value.toString(); } }", true, "constrained", 0);
	}

	@Test
	void resolvedParameterizedTypeSatisfiesErasedInterfaceConstraint() throws Exception {
		assertCount("class Example { int size(java.util.ArrayList<String> value) { return value.size(); } }",
				true, "collection", 1);
	}

	@TriggerPattern(value = "$x.toString()", kind = PatternKind.METHOD_CALL,
			constraints = @ConstraintVariableType(variable = "$x", type = "java.lang.String"))
	private void constrained() {
	}

	@TriggerPattern(value = "$x.toString()", kind = PatternKind.METHOD_CALL,
			constraints = @ConstraintVariableType(variable = "$absent", type = "java.lang.String"))
	private void missing() {
	}

	@TriggerPattern(value = "$x.size()", kind = PatternKind.METHOD_CALL,
			constraints = @ConstraintVariableType(variable = "$x", type = "java.util.List"))
	private void collection() {
	}

	private void assertCount(String source, boolean bindings, String method, int expected) throws Exception {
		var annotation = getClass().getDeclaredMethod(method)
				.getAnnotation(TriggerPattern.class);
		Pattern pattern = new Pattern(annotation.value(), annotation.kind(), null, null, null, null, annotation.constraints());
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		if (bindings) {
			parser.setUnitName("Example.java");
			parser.setResolveBindings(true);
			parser.setEnvironment(new String[0], new String[0], null, true);
		}
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		assertEquals(expected, new TriggerPatternEngine().findMatches(cu, pattern).size());
		TransformationRule rule = TransformationRule.of(pattern, List.of());
		assertEquals(expected, new PatternIndex(List.of(rule)).findAllMatches(cu).getOrDefault(rule, List.of()).size());
	}
}
