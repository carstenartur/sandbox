/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintKind;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.eclipse.HintContext;
import org.sandbox.jdt.triggerpattern.internal.HintRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintRegistry.HintDescriptor;

public class TriggerPatternQuickAssistProcessorTest {

	private static int handlerInvocations;
	private static String boundType;

	@BeforeEach
	void resetCounter() {
		handlerInvocations = 0;
		boundType = null;
	}

	@Test
	void unresolvedApiCandidateNeverInvokesTheAutomaticHandler() throws Exception {
		String source = "import java.util.Collections; class Test { Object run() { return Collections.emptyList(); } }";
		var processor = processor("java.util.Collections.emptyList()", TriggerPatternQuickAssistProcessorTest.class, "record");
		assertNull(processor.collectAssists(parse(source, false), unit(), source.indexOf("emptyList")));
		assertEquals(0, handlerInvocations);
	}

	@Test
	void resolvedCompatibleCandidateInvokesTheHandler() throws Exception {
		String source = "import java.util.Collections; class Test { Object run() { return Collections.emptyList(); } }";
		var processor = processor("java.util.Collections.emptyList()", TriggerPatternQuickAssistProcessorTest.class, "record");
		processor.collectAssists(parse(source, true), unit(), source.indexOf("emptyList"));
		assertEquals(1, handlerInvocations);
	}

	@Test
	void reusesTheInvocationAstSoTheHandlerReceivesBindings() throws Exception {
		String source = "class Test { boolean run(boolean flag) { return !!flag; } }";
		var processor = processor("!!$x", TriggerPatternQuickAssistProcessorTest.class, "recordBoundType");
		processor.getAssists(
				invocation(parse(source, true), source.indexOf("!!flag") + 1), new IProblemLocation[0]);
		assertEquals(1, handlerInvocations);
		assertEquals("boolean", boundType);
	}

	public static IJavaCompletionProposal recordBoundType(HintContext context) {
		Expression expression = assertInstanceOf(Expression.class, context.getMatch().getBindings().get("$x"));
		var binding = expression.resolveTypeBinding();
		assertNotNull(binding);
		boundType = binding.getQualifiedName();
		handlerInvocations++;
		return null;
	}

	public static IJavaCompletionProposal record(HintContext context) {
		handlerInvocations++;
		return null;
	}

	private TriggerPatternQuickAssistProcessor processor(String value, Class<?> provider, String method) throws Exception {
		HintDescriptor descriptor = new HintDescriptor(Pattern.of(value, PatternKind.EXPRESSION),
				"test", "test", true, Severity.INFO, "test", "test", new String[0], HintKind.INSPECTION,
				"", null, provider, provider.getMethod(method, HintContext.class));
		return new TriggerPatternQuickAssistProcessor(new HintRegistry() {
			@Override
			public synchronized List<HintDescriptor> getHints() {
				return List.of(descriptor);
			}
		});
	}

	private IInvocationContext invocation(CompilationUnit root, int offset) {
		return (IInvocationContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IInvocationContext.class },
				(proxy, method, arguments) -> switch (method.getName()) {
				case "getCompilationUnit" -> unit();
				case "getASTRoot" -> root;
				case "getSelectionOffset" -> offset;
				case "getSelectionLength" -> 0;
				default -> null;
				});
	}

	private ICompilationUnit unit() {
		return (ICompilationUnit) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ICompilationUnit.class },
				(proxy, method, arguments) -> switch (method.getName()) {
				case "getElementName", "toString" -> "Test.java";
				case "equals" -> proxy == arguments[0];
				case "hashCode" -> System.identityHashCode(proxy);
				case "getJavaProject" -> throw new AssertionError("The supplied invocation AST must be reused");
				default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
				});
	}

	private CompilationUnit parse(String source, boolean bindings) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		if (bindings) {
			parser.setUnitName("Test.java");
			parser.setEnvironment(new String[0], new String[0], null, true);
			parser.setResolveBindings(true);
		}
		return (CompilationUnit) parser.createAST(null);
	}
}
