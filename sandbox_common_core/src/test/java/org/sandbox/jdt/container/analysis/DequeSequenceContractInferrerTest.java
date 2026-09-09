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
package org.sandbox.jdt.container.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class DequeSequenceContractInferrerTest {

	private final LocalDequeSequenceAnalyzer analyzer= new LocalDequeSequenceAnalyzer();
	private final ContainerContractInferrer inferrer= new ContainerContractInferrer();

	@Test
	void infersFifoDequeFromTailAppendAndHeadRemoval() {
		ContainerUsageProfile profile= profile("""
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				String next(String first, String second) {
					List<String> work = new ArrayList<>();
					work.add(first);
					work.add(second);
					return work.remove(0);
				}
			}
			""");
		ContainerRecommendation recommendation= inferrer.infer(profile).orElseThrow();

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(ContainerShape.DEQUE, recommendation.targetContract().shape());
		assertEquals(ContainerRuleRegistry.FIFO_SEQUENCE_DEQUE, recommendation.rule().ruleId());
		assertEquals(AutomationLevel.REPORT_ONLY, recommendation.automationLevel());
		assertTrue(profile.evidence().stream().anyMatch(evidence -> evidence.kind() == Kind.HEAD_REMOVAL));
		assertFalse(profile.evidence().stream().anyMatch(evidence -> evidence.kind() == Kind.TAIL_REMOVAL));
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.ORDER));
		assertEquals(Preservation.REQUIRES_PROOF,
				preservation(recommendation, ContractProperty.NULLS));
	}

	@Test
	void infersLifoDequeFromTailAppendAndTailRemoval() {
		ContainerUsageProfile profile= profile("""
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				String pop(String first, String second) {
					List<String> stack = new ArrayList<>();
					stack.add(first);
					stack.add(second);
					return stack.remove(stack.size() - 1);
				}
			}
			""");
		ContainerRecommendation recommendation= inferrer.infer(profile).orElseThrow();

		assertEquals(ContainerShape.DEQUE, recommendation.targetContract().shape());
		assertEquals(ContainerRuleRegistry.LIFO_SEQUENCE_DEQUE, recommendation.rule().ruleId());
		assertTrue(profile.evidence().stream().anyMatch(evidence -> evidence.kind() == Kind.TAIL_REMOVAL));
		assertFalse(profile.evidence().stream().anyMatch(evidence -> evidence.kind() == Kind.HEAD_REMOVAL));
	}

	@Test
	void explicitNullInsertionIsRetainedInSemanticTargetContract() {
		ContainerUsageProfile profile= profile("""
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				String next() {
					List<String> work = new ArrayList<>();
					work.add(null);
					return work.remove(0);
				}
			}
			""");
		ContainerRecommendation recommendation= inferrer.infer(profile).orElseThrow();

		assertEquals(NullContract.ALLOWED, profile.nullContract());
		assertEquals(NullContract.ALLOWED, recommendation.targetContract().nullContract());
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.NULLS));
	}

	@Test
	void mixedHeadAndTailRemovalIsRejected() {
		ContainerUsageProfile profile= profile("""
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				void drain(boolean fromFront, String value) {
					List<String> work = new ArrayList<>();
					work.add(value);
					if (fromFront) work.remove(0);
					else work.remove(work.size() - 1);
				}
			}
			""");

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(profile.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.REJECTION_BOUNDARY
						&& evidence.summary().contains("Mixed head and tail"))); //$NON-NLS-1$
		assertTrue(inferrer.infer(profile).isEmpty());
	}

	@Test
	void indexedReadKeepsListSemanticsAndRejectsDequeRecommendation() {
		ContainerUsageProfile profile= profile("""
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				String next(String value) {
					List<String> work = new ArrayList<>();
					work.add(value);
					String observed = work.get(0);
					work.remove(0);
					return observed;
				}
			}
			""");

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(profile.evidence().stream().anyMatch(evidence ->
				evidence.kind() == Kind.UNCLASSIFIED_USAGE));
		assertTrue(inferrer.infer(profile).isEmpty());
	}

	@Test
	void capturedListIsRejectedInsteadOfAssumingThreadConfinement() {
		ContainerUsageProfile profile= profile("""
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				void schedule(String value) {
					List<String> work = new ArrayList<>();
					work.add(value);
					Runnable read = () -> System.out.println(work.size());
					work.remove(0);
					read.run();
				}
			}
			""");

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(profile.evidence().stream().anyMatch(evidence -> evidence.kind() == Kind.CAPTURED_USAGE));
		assertTrue(inferrer.infer(profile).isEmpty());
	}

	@Test
	void registryKeepsDistinctFifoAndLifoSemanticRules() {
		assertEquals(ContainerShape.DEQUE, ContainerRuleRegistry.fifoSequenceDeque().targetShape());
		assertEquals(ContainerShape.DEQUE, ContainerRuleRegistry.lifoSequenceDeque().targetShape());
		assertFalse(ContainerRuleRegistry.fifoSequenceDeque().ruleId()
				.equals(ContainerRuleRegistry.lifoSequenceDeque().ruleId()));
	}

	private ContainerUsageProfile profile(String source) {
		List<ContainerUsageProfile> profiles= analyzer.analyze(parse(source));
		assertEquals(1, profiles.size());
		return profiles.get(0);
	}

	private static Preservation preservation(ContainerRecommendation recommendation,
			ContractProperty property) {
		return recommendation.assessments().stream()
				.filter(assessment -> assessment.property() == property)
				.findFirst().orElseThrow().preservation();
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(null, null, null, true);
		return (CompilationUnit) parser.createAST(null);
	}
}
