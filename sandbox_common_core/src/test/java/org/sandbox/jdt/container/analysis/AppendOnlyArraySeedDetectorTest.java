/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class AppendOnlyArraySeedDetectorTest {

	private final AppendOnlyArraySeedDetector detector= new AppendOnlyArraySeedDetector();

	@Test
	void detectsReferenceArrayGrowthFollowedByTailWrite() {
		CompilationUnit compilationUnit= parse("""
			import java.util.Arrays;

			class Sample {
				void append(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""");

		List<ContainerUsageProfile> profiles= detector.findSeeds(compilationUnit);

		assertEquals(1, profiles.size());
		ContainerUsageProfile profile= profiles.get(0);
		assertEquals(ContainerShape.ARRAY, profile.currentShape());
		assertEquals(ElementDomain.REFERENCE, profile.elementDomain());
		assertEquals(AnalysisCompleteness.LOCAL_SEED, profile.completeness());
		assertTrue(profile.access().append());
		assertTrue(profile.access().indexedWrite());
		assertFalse(profile.isFlowComplete());
		assertEquals(List.of(Kind.ARRAY_GROWTH, Kind.APPEND_WRITE, Kind.REFERENCE_COMPONENT),
				profile.evidence().stream().map(evidence -> evidence.kind()).toList());
		assertThrows(UnsupportedOperationException.class,
				() -> profile.evidence().add(profile.evidence().get(0)));
	}

	@Test
	void ignoresPrimitiveArrays() {
		CompilationUnit compilationUnit= parse("""
			import java.util.Arrays;

			class Sample {
				void append(int value) {
					int[] values = new int[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""");

		assertTrue(detector.findSeeds(compilationUnit).isEmpty());
	}

	@Test
	void ignoresWriteToExistingPosition() {
		CompilationUnit compilationUnit= parse("""
			import java.util.Arrays;

			class Sample {
				void append(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[0] = value;
				}
			}
			""");

		assertTrue(detector.findSeeds(compilationUnit).isEmpty());
	}

	@Test
	void ignoresTailWriteToDifferentArray() {
		CompilationUnit compilationUnit= parse("""
			import java.util.Arrays;

			class Sample {
				void append(String value) {
					String[] values = new String[0];
					String[] other = new String[1];
					values = Arrays.copyOf(values, values.length + 1);
					other[other.length - 1] = value;
				}
			}
			""");

		assertTrue(detector.findSeeds(compilationUnit).isEmpty());
	}

	@Test
	void returnsCandidatesInSourceOrder() {
		CompilationUnit compilationUnit= parse("""
			import java.util.Arrays;

			class Sample {
				void appendFirst(String value) {
					String[] first = new String[0];
					first = Arrays.copyOf(first, first.length + 1);
					first[first.length - 1] = value;
				}

				void appendSecond(Object value) {
					Object[] second = new Object[0];
					second = Arrays.copyOf(second, 1 + second.length);
					second[second.length - 1] = value;
				}
			}
			""");

		List<ContainerUsageProfile> profiles= detector.findSeeds(compilationUnit);

		assertEquals(List.of("first", "second"),
				profiles.stream().map(profile -> profile.identity().displayName()).toList());
		assertTrue(profiles.get(0).identity().sourceStart() < profiles.get(1).identity().sourceStart());
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
