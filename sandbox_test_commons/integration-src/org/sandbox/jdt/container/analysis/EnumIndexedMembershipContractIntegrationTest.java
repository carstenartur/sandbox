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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class EnumIndexedMembershipContractIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	private final LocalEnumIndexedMembershipAnalyzer analyzer=
			new LocalEnumIndexedMembershipAnalyzer();
	private final ContainerContractInferrer inferrer= new ContainerContractInferrer();

	@Test
	void infersReportOnlySetFromEnumIndexedBooleanTable() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B, C }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = true;
					boolean present = enabled[flag.ordinal()];
					enabled[flag.ordinal()] = false;
					return present;
				}
			}
			""");

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(ContainerShape.ARRAY, profile.currentShape());
		assertEquals(ElementDomain.PRIMITIVE, profile.elementDomain());
		assertEquals(NullContract.NOT_APPLICABLE, profile.nullContract());
		assertTrue(profile.access().indexedRead());
		assertTrue(profile.access().indexedWrite());
		assertTrue(profile.access().membershipQuery());
		assertTrue(hasEvidence(profile, Kind.ENUM_CARDINALITY));
		assertTrue(hasEvidence(profile, Kind.ENUM_ORDINAL_INDEX));
		assertTrue(hasEvidence(profile, Kind.ENUM_MEMBERSHIP_QUERY));
		assertTrue(hasEvidence(profile, Kind.ENUM_MEMBERSHIP_ENABLE));
		assertTrue(hasEvidence(profile, Kind.ENUM_MEMBERSHIP_DISABLE));
		assertTrue(hasEvidence(profile, Kind.LOCAL_USAGE_COMPLETE));

		ContainerRecommendation recommendation= inferrer.infer(profile).orElseThrow();
		assertEquals(ContainerShape.SET, recommendation.targetContract().shape());
		assertEquals(AutomationLevel.REPORT_ONLY, recommendation.automationLevel());
		assertEquals(Confidence.HIGH, recommendation.confidence());
		assertEquals(ContainerRuleRegistry.ENUM_INDEXED_MEMBERSHIP_SET,
				recommendation.rule().ruleId());
		assertEquals(Preservation.REQUIRES_PROOF,
				recommendation.assessments().stream()
						.filter(assessment -> assessment.property() == ContractProperty.NULLS)
						.findFirst().orElseThrow().preservation());
	}

	@Test
	void numericIndexRejectsOrdinalMembershipInference() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[0] = true;
					return enabled[flag.ordinal()];
				}
			}
			""");

		assertRejected(profile, Kind.REJECTION_BOUNDARY);
	}

	@Test
	void ordinalFromDifferentEnumRejectsMembershipInference() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				enum Other { A, B }
				boolean update(Flag flag, Other other) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[other.ordinal()] = true;
					return enabled[flag.ordinal()];
				}
			}
			""");

		assertRejected(profile, Kind.REJECTION_BOUNDARY);
	}

	@Test
	void ordinalFromDifferentLocalEnumRejectsMembershipInference() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				boolean update() {
					enum Flag { A, B }
					enum Other { A, B }
					Flag flag = Flag.A;
					Other other = Other.A;
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[other.ordinal()] = true;
					return enabled[flag.ordinal()];
				}
			}
			""");

		assertRejected(profile, Kind.REJECTION_BOUNDARY);
	}

	@Test
	void arrayLengthObservationRejectsSetContract() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = true;
					int capacity = enabled.length;
					return enabled[flag.ordinal()] && capacity > 0;
				}
			}
			""");

		assertRejected(profile, Kind.ARRAY_LENGTH_READ);
	}

	@Test
	void localAliasRejectsSetContract() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					boolean[] alias = enabled;
					enabled[flag.ordinal()] = true;
					return alias[flag.ordinal()];
				}
			}
			""");

		assertRejected(profile, Kind.UNSAFE_ESCAPE);
	}

	@Test
	void lambdaCaptureRejectsSetContract() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			import java.util.function.BooleanSupplier;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = true;
					BooleanSupplier reader = () -> enabled[flag.ordinal()];
					return reader.getAsBoolean();
				}
			}
			""");

		assertRejected(profile, Kind.CAPTURED_USAGE);
	}

	@Test
	void arrayIdentityComparisonRejectsSetContract() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = true;
					boolean same = enabled == enabled;
					return same && enabled[flag.ordinal()];
				}
			}
			""");

		assertRejected(profile, Kind.ARRAY_IDENTITY);
	}

	@Test
	void synchronizationOnArrayIdentityRejectsSetContract() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = true;
					synchronized (enabled) {
						return enabled[flag.ordinal()];
					}
				}
			}
			""");

		assertRejected(profile, Kind.ARRAY_IDENTITY);
	}

	@Test
	void writeOnlyTableIsNotPromotedToMembershipContract() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				void update(Flag flag) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = true;
				}
			}
			""");

		assertRejected(profile, Kind.REJECTION_BOUNDARY);
		assertTrue(profile.evidence().stream()
				.anyMatch(evidence -> evidence.summary().contains("No enum membership query"))); //$NON-NLS-1$
	}

	@Test
	void computedMembershipWriteIsRejected() throws CoreException {
		ContainerUsageProfile profile= analyze("""
			package test;
			class Sample {
				enum Flag { A, B }
				boolean update(Flag flag, boolean state) {
					boolean[] enabled = new boolean[Flag.values().length];
					enabled[flag.ordinal()] = state;
					return enabled[flag.ordinal()];
				}
			}
			""");

		assertRejected(profile, Kind.REJECTION_BOUNDARY);
	}

	private ContainerUsageProfile analyze(String source) throws CoreException {
		ICompilationUnit unit= createUnit(source);
		List<ContainerUsageProfile> profiles= analyzer.analyze(parse(unit));
		assertEquals(1, profiles.size());
		return profiles.get(0);
	}

	private void assertRejected(ContainerUsageProfile profile, Kind expectedEvidence) {
		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(hasEvidence(profile, expectedEvidence));
		assertFalse(inferrer.infer(profile).isPresent());
	}

	private static boolean hasEvidence(ContainerUsageProfile profile, Kind kind) {
		return profile.evidence().stream().anyMatch(evidence -> evidence.kind() == kind);
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit("Sample.java", source, true, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
