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
package org.sandbox.jdt.ui.helper.views;

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

import org.sandbox.jdt.ui.helper.views.ContainerAnalysisService.ContainerAnalysisRow;

/** Verifies the read-only projection used by the Container Analysis view. */
public class ContainerAnalysisServiceTest {

	private final ContainerAnalysisService service= new ContainerAnalysisService();

	@Test
	public void appendArrayRecommendationRetainsEvidenceSourceAnchors() {
		String source= """
				import java.util.Arrays;
				class Sample {
					void collect(String value) {
						String[] values = new String[0];
						values = Arrays.copyOf(values, values.length + 1);
						values[values.length - 1] = value;
						for (String current : values) System.out.println(current);
					}
				}
				""";

		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Test/Handle"); //$NON-NLS-1$

		assertFalse(rows.isEmpty());
		assertTrue(rows.stream().allMatch(row -> "values".equals(row.candidate()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "ARRAY".equals(row.currentShape()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> row.targetContract().startsWith("LIST;"))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "HIGH".equals(row.confidence()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "REPORT_ONLY".equals(row.status()))); //$NON-NLS-1$
		ContainerAnalysisRow growth= rows.stream()
				.filter(row -> "ARRAY_GROWTH".equals(row.evidenceKind())) //$NON-NLS-1$
				.findFirst().orElseThrow();
		assertTrue(fragment(source, growth).contains("Arrays.copyOf")); //$NON-NLS-1$
		assertTrue(growth.semanticSummary().contains("dynamically growing mutable sequence")); //$NON-NLS-1$
	}

	@Test
	public void rejectedArrayProfileShowsBoundaryInsteadOfInventingTarget() {
		String source= """
				import java.util.Arrays;
				class Sample {
					String[] collect(String value) {
						String[] values = new String[0];
						values = Arrays.copyOf(values, values.length + 1);
						values[values.length - 1] = value;
						return values;
					}
				}
				""";

		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Test/Handle"); //$NON-NLS-1$

		assertFalse(rows.isEmpty());
		assertTrue(rows.stream().allMatch(row -> "No proven target".equals(row.targetContract()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "N/A".equals(row.confidence()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "REJECTED".equals(row.status()))); //$NON-NLS-1$
		assertTrue(rows.stream().anyMatch(row ->
				row.evidenceKind().equals("UNSAFE_ESCAPE") //$NON-NLS-1$
						|| row.evidenceKind().equals("REJECTION_BOUNDARY"))); //$NON-NLS-1$
	}

	@Test
	public void uniqueSequenceRecommendationUsesTheSameCommonContractInferrer() {
		String source= """
				import java.util.ArrayList;
				import java.util.List;
				class Sample {
					void collect(String name) {
						List<String> names = new ArrayList<>();
						if (!names.contains(name)) names.add(name);
						for (String current : names) System.out.println(current);
					}
				}
				""";

		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Test/Handle"); //$NON-NLS-1$

		assertFalse(rows.isEmpty());
		assertTrue(rows.stream().allMatch(row -> "names".equals(row.candidate()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "LIST".equals(row.currentShape()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> row.targetContract().startsWith("SET;"))); //$NON-NLS-1$
		assertTrue(rows.stream().anyMatch(row -> "DUPLICATE_SUPPRESSION".equals(row.evidenceKind()))); //$NON-NLS-1$
	}

	@Test
	public void dequeRecommendationWinsOverRejectedGenericListProfile() {
		String source= """
				import java.util.ArrayList;
				import java.util.List;
				class Sample {
					void consume(String value) {
						List<String> queue = new ArrayList<>();
						queue.add(value);
						String next = queue.remove(0);
						System.out.println(next + queue.size());
					}
				}
				""";

		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Test/Handle"); //$NON-NLS-1$

		assertFalse(rows.isEmpty());
		assertTrue(rows.stream().allMatch(row -> "queue".equals(row.candidate()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> row.targetContract().startsWith("DEQUE;"))); //$NON-NLS-1$
		assertTrue(rows.stream().anyMatch(row -> "HEAD_REMOVAL".equals(row.evidenceKind()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "REPORT_ONLY".equals(row.status()))); //$NON-NLS-1$
	}

	@Test
	public void enumIndexedMembershipIsVisibleAsReportOnlySetContract() {
		String source= """
				class Sample {
					enum Flag { A, B }
					boolean contains(Flag flag) {
						boolean[] enabled = new boolean[Flag.values().length];
						enabled[flag.ordinal()] = true;
						return enabled[flag.ordinal()];
					}
				}
				""";

		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Test/Handle"); //$NON-NLS-1$

		assertFalse(rows.isEmpty());
		assertTrue(rows.stream().allMatch(row -> "enabled".equals(row.candidate()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "ARRAY".equals(row.currentShape()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> row.targetContract().startsWith("SET;"))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "REPORT_ONLY".equals(row.status()))); //$NON-NLS-1$
		assertTrue(rows.stream().anyMatch(row -> "ENUM_CARDINALITY".equals(row.evidenceKind()))); //$NON-NLS-1$
		assertTrue(rows.stream().anyMatch(row -> "ENUM_MEMBERSHIP_QUERY".equals(row.evidenceKind()))); //$NON-NLS-1$
		assertTrue(rows.stream().anyMatch(row -> row.semanticSummary().contains("ordinal positions"))); //$NON-NLS-1$
	}

	@Test
	public void rowDetailsExposeRecommendationAndEvidenceWithoutHoldingAstNodes() {
		String source= """
				import java.util.Arrays;
				class Sample {
					void collect(String value) {
						String[] values = new String[0];
						values = Arrays.copyOf(values, values.length + 1);
						values[values.length - 1] = value;
					}
				}
				""";
		ContainerAnalysisRow row= service.analyze(parse(source), "=Test/Handle").get(0); //$NON-NLS-1$

		assertEquals("=Test/Handle", row.compilationUnitHandle()); //$NON-NLS-1$
		assertTrue(row.sourceStart() >= 0);
		assertTrue(row.sourceLength() > 0);
		assertTrue(row.details().contains(row.evidenceSummary()));
		assertTrue(row.getClass().getRecordComponents().length > 0);
		assertTrue(java.util.Arrays.stream(row.getClass().getRecordComponents())
				.map(component -> component.getType().getName())
				.noneMatch(name -> name.startsWith("org.eclipse.jdt.core.dom."))); //$NON-NLS-1$
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
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

	private static String fragment(String source, ContainerAnalysisRow row) {
		return source.substring(row.sourceStart(), row.sourceStart() + row.sourceLength());
	}
}
