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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.Match;

/** Regression tests for strict unknown tracking with historical guard fallbacks. */
class GenericTypeIsCompatibilityTest {

	@Test
	void rawGenericTypeIsUnknownButKeepsHistoricalFalseFallback() {
		CompilationUnit unit= parse("""
				class Sample {
					void use(java.util.List values) {
						values.size();
					}
				}
				""");
		SingleVariableDeclaration parameter= firstParameter(unit);
		assertNotNull(parameter.resolveBinding());
		assertTrue(parameter.resolveBinding().getType().isRawType(),
				"The fixture must expose an actually resolved raw generic type"); //$NON-NLS-1$

		GuardFunction genericTypeIs= guards().get("genericTypeIs"); //$NON-NLS-1$
		assertNotNull(genericTypeIs);
		GuardContext context= context(parameter, unit);

		assertFalse(genericTypeIs.evaluate(
				context, "$values", "0", "java.lang.String"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"Optional hints must retain the historical false result for raw generic types"); //$NON-NLS-1$
		assertUnknown(context, "genericTypeIs"); //$NON-NLS-1$
	}

	@Test
	void recoveredInstanceOfKeepsHistoricalNameComparisonWhileMarkingUnknown() {
		CompilationUnit unit= parse("""
				class Sample {
					void use(MissingType value) {
					}
				}
				""");
		SingleVariableDeclaration parameter= firstParameter(unit);
		ITypeBinding binding= parameter.resolveBinding() == null
				? parameter.getType().resolveBinding() : parameter.resolveBinding().getType();
		assertNotNull(binding);
		assertTrue(binding.isRecovered(),
				"The fixture must expose a recovered type binding"); //$NON-NLS-1$

		GuardFunction instanceOf= guards().get("instanceof"); //$NON-NLS-1$
		assertNotNull(instanceOf);
		GuardContext mismatch= context(parameter, unit);
		assertFalse(instanceOf.evaluate(mismatch, "$values", "java.lang.String"), //$NON-NLS-1$ //$NON-NLS-2$
				"A recovered binding must not become an unconditional optional match"); //$NON-NLS-1$
		assertUnknown(mismatch, "instanceof"); //$NON-NLS-1$

		GuardContext matchingName= context(parameter, unit);
		assertTrue(instanceOf.evaluate(matchingName, "$values", "MissingType"), //$NON-NLS-1$ //$NON-NLS-2$
				"Optional mode must retain the historical recovered-name comparison"); //$NON-NLS-1$
		assertUnknown(matchingName, "instanceof"); //$NON-NLS-1$
	}

	private static Map<String, GuardFunction> guards() {
		Map<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		return guards;
	}

	private static GuardContext context(SingleVariableDeclaration parameter, CompilationUnit unit) {
		Map<String, Object> bindings= new HashMap<>();
		bindings.put("$values", parameter); //$NON-NLS-1$
		return GuardContext.fromMatch(
				new Match(parameter, bindings, parameter.getStartPosition(), parameter.getLength()), unit);
	}

	private static void assertUnknown(GuardContext context, String guardName) {
		assertTrue(context.hasUnknownSemanticRequirements(),
				"Required hints must still fail closed on incomplete semantic information"); //$NON-NLS-1$
		assertEquals(guardName, context.getUnknownSemanticRequirements().get(0).guardName());
	}

	private static SingleVariableDeclaration firstParameter(CompilationUnit unit) {
		TypeDeclaration type= (TypeDeclaration) unit.types().get(0);
		MethodDeclaration method= type.getMethods()[0];
		return (SingleVariableDeclaration) method.parameters().get(0);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		return (CompilationUnit) parser.createAST(null);
	}
}
