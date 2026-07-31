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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;

/** Fail-closed evaluation tests for typed facts and plan relations. */
public class SemanticRewritePlanFactGuardTest {

	@Test
	public void typedFactAndRelationGuardsUseStableBindings() {
		TypeDeclaration type= parseType();
		MethodDeclaration first= type.getMethods()[0];
		MethodDeclaration second= type.getMethods()[1];
		NodeKey typeKey= NodeKey.from(type);
		NodeKey firstKey= NodeKey.from(first);
		NodeKey secondKey= NodeKey.from(second);
		SemanticRewritePlan plan= SemanticRewritePlan.builder("harness/v1") //$NON-NLS-1$
				.putString(typeKey, "runner", "CUSTOM") //$NON-NLS-1$ //$NON-NLS-2$
				.putBoolean(firstKey, "selected", true) //$NON-NLS-1$
				.putList(typeKey, "levels", SemanticPlanValue.integer(17), SemanticPlanValue.integer(21)) //$NON-NLS-1$
				.putNode(firstKey, "owner", typeKey) //$NON-NLS-1$
				.relate(typeKey, "CONTAINS", firstKey, Map.of("index", SemanticPlanValue.integer(0))) //$NON-NLS-1$ //$NON-NLS-2$
				.relate(typeKey, "CONTAINS", firstKey, Map.of("index", SemanticPlanValue.integer(1))) //$NON-NLS-1$ //$NON-NLS-2$
				.relate(typeKey, "CONTAINS", secondKey, Map.of("index", SemanticPlanValue.integer(2))) //$NON-NLS-1$ //$NON-NLS-2$
				.build();
		Map<String, Object> bindings= Map.of(
				"$type", type.getName(), //$NON-NLS-1$
				"$first", first.getName(), //$NON-NLS-1$
				"$second", second.getName()); //$NON-NLS-1$
		Match match= new Match(type, bindings, type.getStartPosition(), type.getLength());
		GuardContext context= GuardContext.fromMatch(match, (CompilationUnit) type.getRoot(), Map.of(), plan);
		Map<String, GuardFunction> guards= guards();

		assertTrue(guards.get("plannedValue").evaluate(context, "$type", "runner", "CUSTOM")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(guards.get("plannedValue").evaluate(context, "$first", "selected", "true")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(guards.get("plannedListContains").evaluate(context, "$type", "levels", "21")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(guards.get("plannedNodeValue").evaluate(context, "$first", "owner", "$type")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(guards.get("plannedRelation").evaluate(context, "$type", "CONTAINS", "$first")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertTrue(guards.get("plannedRelationValue").evaluate(context, "$type", "CONTAINS", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"$first", "index", "1")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(guards.get("plannedOutgoingRelation").evaluate(context, "$type", "CONTAINS")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(guards.get("plannedIncomingRelation").evaluate(context, "$first", "CONTAINS")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(guards.get("plannedRelationCount").evaluate(context, "$type", "CONTAINS", "3")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertFalse(guards.get("plannedValue").evaluate(context, "$type", "runner", "OTHER")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertFalse(guards.get("plannedRelation").evaluate(context, "$first", "CONTAINS", "$type")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertFalse(guards.get("plannedRelationCount").evaluate(context, "$type", "CONTAINS", "2")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertFalse(guards.get("plannedValue").evaluate(context, "$type", "runner", "CUSTOM", "extra")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		assertFalse(guards.get("plannedRelation").evaluate(context, "$type", "CONTAINS", "$first", "extra")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	}

	@Test
	public void typedGuardsRejectMissingPlansAndBindings() {
		TypeDeclaration type= parseType();
		Match match= new Match(type, Map.of(), type.getStartPosition(), type.getLength());
		GuardContext context= GuardContext.fromMatch(match, (CompilationUnit) type.getRoot());
		Map<String, GuardFunction> guards= guards();

		assertFalse(guards.get("plannedValue").evaluate(context, "runner", "CUSTOM")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(guards.get("plannedRelation").evaluate(context, "CONTAINS", "$missing")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(guards.get("plannedNodeValue").evaluate(context, "owner", "$missing")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static Map<String, GuardFunction> guards() {
		Map<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		return guards;
	}

	private static TypeDeclaration parseType() {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource("package test; public class Sample { public void first() {} public void second() {} }" //$NON-NLS-1$
				.toCharArray());
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		return (TypeDeclaration) root.types().get(0);
	}
}
