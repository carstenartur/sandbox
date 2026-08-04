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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class LocalArrayUsageAnalyzerTest {

	private final AppendOnlyArraySeedDetector seedDetector= new AppendOnlyArraySeedDetector();
	private final LocalArrayUsageAnalyzer analyzer= new LocalArrayUsageAnalyzer();

	@Test
	void completesLocalAppendAndEncounterIteration() {
		ContainerUsageProfile profile= analyze("""
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

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(OrderRequirement.ENCOUNTER, profile.orderRequirement());
		assertEquals(EscapeLevel.LOCAL, profile.escapeLevel());
		assertEquals(AliasingContract.NO_OBSERVED_ALIAS, profile.aliasingContract());
		assertTrue(hasEvidence(profile, Kind.ENCOUNTER_ITERATION));
		assertTrue(hasEvidence(profile, Kind.LOCAL_USAGE_COMPLETE));
		assertFalse(hasEvidence(profile, Kind.REJECTION_BOUNDARY));
	}

	@Test
	void recordsPositionalContractForIndexedRead() {
		ContainerUsageProfile profile= analyze("""
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

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(OrderRequirement.POSITIONAL, profile.orderRequirement());
		assertTrue(profile.access().indexedRead());
		assertTrue(hasEvidence(profile, Kind.INDEXED_READ));
	}

	@Test
	void rejectsMethodArgumentEscape() {
		ContainerUsageProfile profile= analyze("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					consume(values);
				}
				void consume(String[] values) { }
			}
			""");

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(hasEvidence(profile, Kind.UNSAFE_ESCAPE));
	}

	@Test
	void rejectsReturnEscape() {
		ContainerUsageProfile profile= analyze("""
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

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(hasEvidence(profile, Kind.UNSAFE_ESCAPE));
	}

	@Test
	void rejectsAliasCreation() {
		ContainerUsageProfile profile= analyze("""
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					String[] alias = values;
					System.out.println(alias.length);
				}
			}
			""");

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(hasEvidence(profile, Kind.UNSAFE_ESCAPE)
				|| hasEvidence(profile, Kind.UNCLASSIFIED_USAGE));
	}

	@Test
	void rejectsArrayIdentityComparison() {
		ContainerUsageProfile profile= analyze("""
			import java.util.Arrays;
			class Sample {
				boolean collect(String value, String[] other) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return values == other;
				}
			}
			""");

		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
		assertTrue(hasEvidence(profile, Kind.ARRAY_IDENTITY));
	}

	@Test
	void reportsFieldBoundaryWithoutRejectingClosedLocalUses() {
		ContainerUsageProfile profile= analyze("""
			import java.util.Arrays;
			class Sample {
				private String[] values = new String[0];
				void collect(String value) {
					this.values = Arrays.copyOf(this.values, this.values.length + 1);
					this.values[this.values.length - 1] = value;
					for (String current : this.values) {
						System.out.println(current);
					}
				}
			}
			""");

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(EscapeLevel.FIELD, profile.escapeLevel());
		assertEquals(OrderRequirement.ENCOUNTER, profile.orderRequirement());
	}

	private ContainerUsageProfile analyze(String source) {
		CompilationUnit unit= parse(source);
		ContainerUsageProfile seed= seedDetector.findSeeds(unit).get(0);
		return analyzer.analyze(unit, seed);
	}

	private static boolean hasEvidence(ContainerUsageProfile profile, Kind kind) {
		return profile.evidence().stream().anyMatch(evidence -> evidence.kind() == kind);
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
