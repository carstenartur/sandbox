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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.eclipse.HintContext;
import org.sandbox.jdt.triggerpattern.examples.ExampleHintProvider;

class ExampleHintProviderTest {

	@Test
	void primitiveBooleanExpressionIsEvaluatedExactlyOnceInTheReplacement() throws Exception {
		String source= "class Test { boolean read() { return true; } boolean m() { return !!read(); } }"; //$NON-NLS-1$
		HintContext context= context(source, true);
		assertNotNull(ExampleHintProvider.simplifyDoubleNegation(context));
		Document document= new Document(source);
		context.getASTRewrite().rewriteAST(document, null).apply(document);
		assertEquals(source.replace("!!read()", "read()"), document.get()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void directExpressionLambdaRemainsValueOnlyForOverloadResolution() throws Exception {
		String source= """
				class Test {
				  void use(java.util.function.Predicate<String> action) {}
				  void use(java.util.function.Consumer<String> action) {}
				  void m() { use(s -> !!s.isEmpty()); }
				}
				"""; //$NON-NLS-1$
		HintContext context= context(source, true);
		assertCompiles(context.getCompilationUnit());
		assertNotNull(ExampleHintProvider.simplifyDoubleNegation(context));
		Document document= new Document(source);
		context.getASTRewrite().rewriteAST(document, null).apply(document);
		assertCompiles(parse(document.get(), true));
		assertEquals(source.replace("!!s.isEmpty()", "(s.isEmpty())"), document.get()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void aParenthesizedOperandKeepsItsSourceComments() throws Exception {
		String source= "class Test { boolean m(boolean flag) { return !!(flag /* keep */); } }"; //$NON-NLS-1$
		HintContext context= context(source, true);
		assertNotNull(ExampleHintProvider.simplifyDoubleNegation(context));
		Document document= new Document(source);
		context.getASTRewrite().rewriteAST(document, null).apply(document);
		assertEquals(source.replace("!!(flag /* keep */)", "(flag /* keep */)"), document.get()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void boxedBooleanIsExcludedBecauseRemovingUnboxingCanChangeNullBehaviour() {
		String source= "class Test { Object m(Boolean flag) { return !!flag; } }"; //$NON-NLS-1$
		assertNull(ExampleHintProvider.simplifyDoubleNegation(context(source, true)));
	}

	@Test
	void missingTypeBindingsNeverAuthorizeTheExampleRewrite() {
		String source= "class Test { boolean m(boolean flag) { return !!flag; } }"; //$NON-NLS-1$
		assertNull(ExampleHintProvider.simplifyDoubleNegation(context(source, false)));
	}

	@Test
	void commentsBetweenNegationOperatorsAreNeverSilentlyDiscarded() {
		String source= "class Test { boolean m(boolean flag) { return !! /* reason */ flag; } }"; //$NON-NLS-1$
		assertNull(ExampleHintProvider.simplifyDoubleNegation(context(source, true)));
	}

	private static HintContext context(String source, boolean bindings) {
		CompilationUnit cu= parse(source, bindings);
		PrefixExpression[] root= new PrefixExpression[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(PrefixExpression expression) {
				root[0]= expression;
				return false;
			}
		});
		assertNotNull(root[0]);
		Expression operand= ((PrefixExpression) root[0].getOperand()).getOperand();
		Match match= new Match(root[0], Map.of("$x", operand), root[0].getStartPosition(), root[0].getLength()); //$NON-NLS-1$
		ICompilationUnit unit= (ICompilationUnit) Proxy.newProxyInstance(
				ExampleHintProviderTest.class.getClassLoader(), new Class<?>[] { ICompilationUnit.class },
				(proxy, method, arguments) -> switch (method.getName()) {
				case "getElementName", "toString" -> "Test.java"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				case "equals" -> proxy == arguments[0]; //$NON-NLS-1$
				case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
				default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
				});
		return new HintContext(cu, unit, match, ASTRewrite.create(cu.getAST()));
	}

	private static void assertCompiles(CompilationUnit cu) {
		for (var problem : cu.getProblems()) {
			assertFalse(problem.isError(), problem::getMessage);
		}
	}

	private static CompilationUnit parse(String source, boolean bindings) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(bindings);
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		parser.setUnitName("Test.java"); //$NON-NLS-1$
		return (CompilationUnit) parser.createAST(null);
	}
}
