/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.ConstraintVariableType;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;
import org.sandbox.jdt.triggerpattern.eclipse.HintContext;
import org.sandbox.jdt.triggerpattern.internal.HintRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintRegistry.HintDescriptor;

/** The real annotation loader must retain the semantic scope of its provider. */
public class AnnotationHintRegistrySafetyTest {

	@Test
	void registeredTypeConstraintRejectsOtherReceiverTypes() throws Exception {
		Pattern pattern = registered("stringReceiver");
		assertEquals(0, matches(pattern, "class Test { Object run(Integer value) { return value.toString(); } }"));
		assertEquals(1, matches(pattern, "class Test { Object run(String value) { return value.toString(); } }"));
	}

	@Test
	void registeredOverrideConstraintRequiresTheNamedSupertype() throws Exception {
		Pattern pattern = registered("runnableOverride");
		assertEquals(0, matches(pattern, "class Test { public void run() {} }"));
		assertEquals(1, matches(pattern, "class Test implements Runnable { public void run() {} }"));
	}

	@TriggerPattern(value = "$x.toString()", kind = PatternKind.METHOD_CALL,
			constraints = @ConstraintVariableType(variable = "$x", type = "java.lang.String"))
	public static IJavaCompletionProposal stringReceiver(HintContext context) {
		return null;
	}

	@TriggerPattern(value = "public void run()", kind = PatternKind.METHOD_DECLARATION, overrides = "java.lang.Runnable")
	public static IJavaCompletionProposal runnableOverride(HintContext context) {
		return null;
	}

	private Pattern registered(String methodName) throws Exception {
		Method provider = getClass().getDeclaredMethod(methodName, HintContext.class);
		Method loader = HintRegistry.class.getDeclaredMethod("loadTriggerPatternHint", Method.class,
				TriggerPattern.class, Class.class, List.class);
		loader.setAccessible(true);
		List<HintDescriptor> hints = new ArrayList<>();
		loader.invoke(new HintRegistry(), provider, provider.getAnnotation(TriggerPattern.class), getClass(), hints);
		assertEquals(1, hints.size());
		return hints.getFirst().getPattern();
	}

	private int matches(Pattern pattern, String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		parser.setUnitName("Test.java");
		parser.setEnvironment(new String[0], new String[0], null, true);
		return new TriggerPatternEngine().findMatches((CompilationUnit) parser.createAST(null), pattern).size();
	}
}
