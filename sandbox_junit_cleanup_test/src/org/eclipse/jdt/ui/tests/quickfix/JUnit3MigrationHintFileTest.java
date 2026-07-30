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
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.cleanup.PlanAwareHintFileFixCore;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/** Tests for the planner-authorized JUnit 3 hierarchy hint program. */
public class JUnit3MigrationHintFileTest {

	private final HintFileParser parser= new HintFileParser();

	@Test
	public void loadsPlannedJUnit3MigrationLibrary() throws Exception {
		String content= loadHintContent();
		HintFile hintFile= parser.parse(content);
		assertEquals("junit3-hierarchy-to-jupiter", hintFile.getId()); //$NON-NLS-1$
		assertEquals(Severity.WARNING, hintFile.getSeverity());
		assertTrue(hintFile.getTags().contains("planned")); //$NON-NLS-1$
		assertEquals("junit3-hierarchy", HintPlanRequirement.fromContent(content).orElseThrow()); //$NON-NLS-1$
	}

	@Test
	public void allRulesAreGuardedAndLocallyRewritable() throws Exception {
		HintFile hintFile= loadHintFile();
		assertEquals(39, hintFile.getRules().size());
		for (TransformationRule rule : hintFile.getRules()) {
			assertNotNull(rule.sourceGuard(), "Every JUnit 3 rule must require a semantic plan role"); //$NON-NLS-1$
			assertTrue(rule.sourcePattern().getKind() == PatternKind.METHOD_DECLARATION
					|| rule.sourcePattern().getKind() == PatternKind.METHOD_CALL);
			assertFalse(rule.alternatives().stream()
					.anyMatch(alternative -> alternative.replacementPattern().contains("BeforeAll") //$NON-NLS-1$
							|| alternative.replacementPattern().contains("AfterAll"))); //$NON-NLS-1$
		}
	}

	@Test
	public void programIsInertWithoutSemanticPlan() throws Exception {
		Function<String, GuardFunction> previous= GuardFunctionResolverHolder.getResolver();
		try {
			Map<String, GuardFunction> guards= new HashMap<>();
			BuiltInGuardRegistration.registerAll(guards);
			GuardFunctionResolverHolder.setResolver(guards::get);
			ASTParser astParser= ASTParser.newParser(AST.getJLSLatest());
			astParser.setSource("class Sample { public void testOne() {} }".toCharArray()); //$NON-NLS-1$
			CompilationUnit compilationUnit= (CompilationUnit) astParser.createAST(null);
			assertTrue(new BatchTransformationProcessor(loadHintFile()).process(compilationUnit).isEmpty());
		} finally {
			GuardFunctionResolverHolder.setResolver(previous);
		}
	}

	@Test
	public void rejectsMismatchedSemanticPlanContractBeforeRewrite() throws Exception {
		ASTParser astParser= ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource("class Sample { public void testOne() {} }".toCharArray()); //$NON-NLS-1$
		CompilationUnit compilationUnit= (CompilationUnit) astParser.createAST(null);
		SemanticRewritePlan wrongPlan= SemanticRewritePlan.builder("other-migration") //$NON-NLS-1$
				.add(NodeKey.type("Lsample/Sample;"), "JUNIT3_HIERARCHY_TYPE").build(); //$NON-NLS-1$ //$NON-NLS-2$

		CoreException failure= assertThrows(CoreException.class,
				() -> PlanAwareHintFileFixCore.findOperationsFromContent(compilationUnit, loadHintContent(), wrongPlan,
						Map.of(), new LinkedHashSet<>(), new LinkedHashSet<>()));
		assertTrue(failure.getStatus().getMessage().contains("junit3-hierarchy")); //$NON-NLS-1$
	}

	@Test
	public void rejectsAnalysisDependentWidestTypeBeforeRewrite() {
		ASTParser astParser= ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource("class Sample { Object value; }".toCharArray()); //$NON-NLS-1$
		CompilationUnit compilationUnit= (CompilationUnit) astParser.createAST(null);
		SemanticRewritePlan plan= SemanticRewritePlan.builder("junit3-hierarchy") //$NON-NLS-1$
				.add(NodeKey.type("Lsample/Sample;"), "JUNIT3_HIERARCHY_TYPE").build(); //$NON-NLS-1$ //$NON-NLS-2$
		String content= """
				<!requires-plan: junit3-hierarchy>
				$x
				=> $widestType
				;;
				"""; //$NON-NLS-1$

		CoreException failure= assertThrows(CoreException.class,
				() -> PlanAwareHintFileFixCore.findOperationsFromContent(compilationUnit, content, plan,
						Map.of(), new LinkedHashSet<>(), new LinkedHashSet<>()));
		assertTrue(failure.getStatus().getMessage().contains("$widestType")); //$NON-NLS-1$
	}

	private HintFile loadHintFile() throws Exception {
		return parser.parse(loadHintContent());
	}

	private String loadHintContent() throws Exception {
		String resourcePath=
				"org/sandbox/jdt/internal/corext/fix/hints/junit3-hierarchy-to-jupiter.sandbox-hint"; //$NON-NLS-1$
		InputStream stream= getClass().getClassLoader().getResourceAsStream(resourcePath);
		assertNotNull(stream, "planned JUnit 3 hint resource should be found"); //$NON-NLS-1$
		try (InputStreamReader reader= new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			StringBuilder content= new StringBuilder();
			char[] buffer= new char[1024];
			int count;
			while ((count= reader.read(buffer)) > 0) {
				content.append(buffer, 0, count);
			}
			return content.toString();
		}
	}
}
