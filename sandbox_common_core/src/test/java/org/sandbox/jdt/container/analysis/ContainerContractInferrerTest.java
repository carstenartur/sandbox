/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;

class ContainerContractInferrerTest {

	private final AppendOnlyArraySeedDetector seedDetector= new AppendOnlyArraySeedDetector();
	private final LocalArrayUsageAnalyzer usageAnalyzer= new LocalArrayUsageAnalyzer();
	private final ContainerContractInferrer inferrer= new ContainerContractInferrer();

	@Test
	void infersHighConfidenceReportOnlyListContractForClosedEncounterSequence() {
		ContainerRecommendation recommendation= infer("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					for (String current : values) {
						System.out.println(current);
					}
				}
			}
			""");

		assertEquals(ContainerShape.LIST, recommendation.targetContract().shape());
		assertEquals(OrderRequirement.ENCOUNTER,
				recommendation.targetContract().orderRequirement());
		assertEquals(Confidence.HIGH, recommendation.confidence());
		assertEquals(AutomationLevel.REPORT_ONLY, recommendation.automationLevel());
		assertEquals(RuleOwnership.NOVEL, recommendation.rule().ownership());
		assertFalse(recommendation.isExecutable());
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.ORDER));
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.SIGNATURES));
	}

	@Test
	void infersListContractWhenOnlySizeIsObserved() {
		ContainerRecommendation recommendation= infer("""
			import java.util.Arrays;
			class Sample {
				int collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return values.length;
				}
			}
			""");

		assertEquals(ContainerShape.LIST, recommendation.targetContract().shape());
		assertEquals(OrderRequirement.NONE,
				recommendation.targetContract().orderRequirement());
		assertEquals(Confidence.HIGH, recommendation.confidence());
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.ORDER));
	}

	@Test
	void preservesPositionalRequirementInTargetContract() {
		ContainerRecommendation recommendation= infer("""
			import java.util.Arrays;
			class Sample {
				String collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return values[0];
				}
			}
			""");

		assertEquals(OrderRequirement.POSITIONAL,
				recommendation.targetContract().orderRequirement());
		assertEquals(Preservation.PRESERVED,
				preservation(recommendation, ContractProperty.ORDER));
	}

	@Test
	void doesNotRecommendFromRejectedLocalProfile() {
		CompilationUnit unit= parse("""
			import java.util.Arrays;
			class Sample {
				String[] collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return values;
				}
			}
			""");
		ContainerUsageProfile seed= seedDetector.findSeeds(unit).get(0);
		ContainerUsageProfile rejected= usageAnalyzer.analyze(unit, seed);

		assertEquals(AnalysisCompleteness.REJECTED, rejected.completeness());
		assertTrue(inferrer.infer(rejected).isEmpty());
	}

	@Test
	void doesNotRecommendFromUnvalidatedLocalSeed() {
		CompilationUnit unit= parse("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""");
		ContainerUsageProfile seed= seedDetector.findSeeds(unit).get(0);

		assertEquals(AnalysisCompleteness.LOCAL_SEED, seed.completeness());
		assertTrue(inferrer.infer(seed).isEmpty());
	}

	@Test
	void registryPreventsKnownLocalCleanupOwnershipFromBecomingRecommendations() {
		assertEquals(RuleOwnership.DUPLICATE,
				ContainerRuleRegistry.find(ContainerRuleRegistry.COLLECTION_BULK_ADD)
						.orElseThrow().ownership());
		assertFalse(ContainerRuleRegistry.find(ContainerRuleRegistry.ARRAY_FILL)
				.orElseThrow().mayRecommend());
		assertTrue(ContainerRuleRegistry.arrayAppendSequence().mayRecommend());
	}

	@Test
	void registryHasStableUniqueIdentifiers() {
		List<String> ids= ContainerRuleRegistry.all().stream()
				.map(rule -> rule.ruleId())
				.toList();

		assertEquals(ids.size(), ids.stream().distinct().count());
		assertEquals(ContainerRuleRegistry.COLLECTION_BULK_ADD, ids.get(0));
		assertEquals(ContainerRuleRegistry.ARRAY_APPEND_SEQUENCE, ids.get(ids.size() - 1));
	}

	private ContainerRecommendation infer(String source) {
		CompilationUnit unit= parse(source);
		ContainerUsageProfile seed= seedDetector.findSeeds(unit).get(0);
		ContainerUsageProfile profile= usageAnalyzer.analyze(unit, seed);
		Optional<ContainerRecommendation> recommendation= inferrer.infer(profile);
		return recommendation.orElseThrow();
	}

	private static Preservation preservation(
			ContainerRecommendation recommendation,
			ContractProperty property) {
		return recommendation.assessments().stream()
				.filter(assessment -> assessment.property() == property)
				.findFirst()
				.orElseThrow()
				.preservation();
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		return (CompilationUnit) parser.createAST(null);
	}
}
