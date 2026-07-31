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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.sandbox.jdt.triggerpattern.api.SemanticPlanRelation;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanValue;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKind;

/** Contract tests for typed plan facts, graph relations and stable node keys. */
public class SemanticRewritePlanFactsTest {

	@Test
	public void typedFactsAndRelationsPreserveOrderAndMultiplicity() {
		NodeKey suite= NodeKey.type("Ltest/Suite;"); //$NON-NLS-1$
		NodeKey first= NodeKey.method("Ltest/Suite;.first()V"); //$NON-NLS-1$
		NodeKey second= NodeKey.method("Ltest/Suite;.second()V"); //$NON-NLS-1$
		SemanticRewritePlan plan= SemanticRewritePlan.builder("legacy-tests/v1") //$NON-NLS-1$
				.putString(suite, "runner", "CUSTOM") //$NON-NLS-1$ //$NON-NLS-2$
				.putBoolean(suite, "ordered", true) //$NON-NLS-1$
				.putInteger(suite, "multiplicity", 3) //$NON-NLS-1$
				.putList(suite, "levels", SemanticPlanValue.integer(17), SemanticPlanValue.integer(21)) //$NON-NLS-1$
				.putNode(first, "owner", suite) //$NON-NLS-1$
				.relate(suite, "CONTAINS", first, Map.of("index", SemanticPlanValue.integer(0))) //$NON-NLS-1$ //$NON-NLS-2$
				.relate(suite, "CONTAINS", first, Map.of("index", SemanticPlanValue.integer(1))) //$NON-NLS-1$ //$NON-NLS-2$
				.relate(suite, "CONTAINS", second, Map.of("index", SemanticPlanValue.integer(2))) //$NON-NLS-1$ //$NON-NLS-2$
				.build();

		assertFalse(plan.isEmpty());
		assertEquals(SemanticPlanValue.string("CUSTOM"), plan.value(suite, "runner").orElseThrow()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(SemanticPlanValue.bool(true), plan.value(suite, "ordered").orElseThrow()); //$NON-NLS-1$
		assertEquals(SemanticPlanValue.integer(3), plan.value(suite, "multiplicity").orElseThrow()); //$NON-NLS-1$
		assertEquals(SemanticPlanValue.node(suite), plan.value(first, "owner").orElseThrow()); //$NON-NLS-1$
		assertEquals(3, plan.outgoing(suite, "CONTAINS").size()); //$NON-NLS-1$
		assertEquals(2, plan.incoming(first, "CONTAINS").size()); //$NON-NLS-1$
		assertEquals(SemanticPlanValue.integer(0), relationIndex(plan.outgoing(suite, "CONTAINS").get(0))); //$NON-NLS-1$
		assertEquals(SemanticPlanValue.integer(1), relationIndex(plan.outgoing(suite, "CONTAINS").get(1))); //$NON-NLS-1$
		assertEquals(SemanticPlanValue.integer(2), relationIndex(plan.outgoing(suite, "CONTAINS").get(2))); //$NON-NLS-1$
	}

	@Test
	public void conflictingFactTypesAndHeterogeneousListsFailClosed() {
		NodeKey type= NodeKey.type("Ltest/Sample;"); //$NON-NLS-1$
		NodeKey method= NodeKey.method("Ltest/Sample;.test()V"); //$NON-NLS-1$

		assertThrows(IllegalArgumentException.class, () -> SemanticRewritePlan.builder("contract") //$NON-NLS-1$
				.putString(type, "mode", "CUSTOM") //$NON-NLS-1$ //$NON-NLS-2$
				.putInteger(method, "mode", 1) //$NON-NLS-1$
				.build());
		assertThrows(IllegalArgumentException.class, () -> SemanticPlanValue.list(
				SemanticPlanValue.string("17"), SemanticPlanValue.integer(21))); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class, () -> SemanticRewritePlan.builder()
				.putString(type, "mode", "FIRST") //$NON-NLS-1$ //$NON-NLS-2$
				.putString(type, "mode", "SECOND")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void stableKeysCoverFieldsAndConstructorCallSites() {
		CompilationUnit root= parse("""
				package test;
				public class Sample {
					public Object rule;
					public Sample() {
					}
					public void create() {
						new Sample();
					}
				}
				""");
		TypeDeclaration type= (TypeDeclaration) root.types().get(0);
		VariableDeclarationFragment field= (VariableDeclarationFragment) type.getFields()[0].fragments().get(0);
		ClassInstanceCreation[] creation= new ClassInstanceCreation[1];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				creation[0]= node;
				return false;
			}
		});

		NodeKey typeKey= NodeKey.from(type);
		NodeKey fieldKey= NodeKey.from(field);
		NodeKey invocationKey= NodeKey.from(creation[0]);
		assertNotNull(typeKey);
		assertNotNull(fieldKey);
		assertNotNull(invocationKey);
		assertEquals(NodeKind.TYPE, typeKey.kind());
		assertEquals(NodeKind.FIELD, fieldKey.kind());
		assertEquals(NodeKind.INVOCATION, invocationKey.kind());
		assertTrue(invocationKey.sourceStart() >= 0);
		assertTrue(invocationKey.sourceLength() > 0);
	}

	private static SemanticPlanValue relationIndex(SemanticPlanRelation relation) {
		return relation.attribute("index").orElseThrow(); //$NON-NLS-1$
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
