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
		String details= rows.get(0).details();
		assertTrue(details.contains("ordinal positions")); //$NON-NLS-1$
		assertTrue(details.contains("ORDER: PRESERVED"), details); //$NON-NLS-1$
		assertTrue(details.contains("NULLS: REQUIRES_PROOF"), details); //$NON-NLS-1$
	}

	@Test
	public void synchronizedListIterationsShowSourceEvidenceWithoutMigrationAdvice() {
		String source= """
				import java.util.ArrayList;
				import java.util.Collections;
				import java.util.List;
				class Sample {
					private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
					void inspect(Object monitor) {
						synchronized (this.entries) {
							for (String entry : entries) { }
						}
						synchronized (monitor) {
							for (String entry : this.entries) { }
						}
					}
				}
				""";

		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Test/Handle"); //$NON-NLS-1$

		assertEquals(2, rows.size());
		ContainerAnalysisRow locked= rows.get(0);
		assertEquals("LOCKED_ITERATION", locked.evidenceKind()); //$NON-NLS-1$
		assertEquals("entries", fragment(source, locked)); //$NON-NLS-1$
		assertEquals(source.indexOf("entries) { }"), locked.sourceStart()); //$NON-NLS-1$
		ContainerAnalysisRow unverified= rows.get(1);
		assertEquals("UNRESOLVED_BOUNDARY", unverified.evidenceKind()); //$NON-NLS-1$
		assertEquals("this.entries", fragment(source, unverified)); //$NON-NLS-1$
		assertTrue(unverified.details().contains("monitor could not be verified")); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "=Test/Handle".equals(row.compilationUnitHandle()) //$NON-NLS-1$
				&& "REPORT_ONLY".equals(row.status()) && "No proven target".equals(row.targetContract()))); //$NON-NLS-1$ //$NON-NLS-2$
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

	@Test
	public void repeatedLookupByARecordStringKeyReportsMapSemanticsAtBothLoops() {
		String source= """
				import java.util.List;
				class Sample {
				    record Entry(String key) {}
				    private final List<Entry> entries = List.of();
				    Entry first(String requested) {
				        for (Entry item : entries) if (requested.equals(item.key())) return item;
				        return null;
				    }
				    Entry second(String other) {
				        for (Entry row : this.entries) {
				            if (other.equals(row.key())) { return row; }
				        }
				        return null;
				    }
				}
				""";
		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Sample"); //$NON-NLS-1$
		assertEquals(2, rows.size());
		assertTrue(rows.stream().allMatch(row -> row.targetContract().startsWith("MAP;") //$NON-NLS-1$
				&& row.status().equals("REPORT_ONLY") && fragment(source, row).startsWith("for ("))); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(rows.get(0).semanticSummary().contains("first matching")); //$NON-NLS-1$
		assertTrue(rows.get(0).sourceStart() < rows.get(1).sourceStart());

		String getter= source.replace("Entry second(String other)", //$NON-NLS-1$
				"private List<Entry> all() { return this.entries; } Entry second(String other)") //$NON-NLS-1$
				.replace("row : this.entries", "row : all()"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(2, service.analyze(parse(getter), "=Sample").size()); //$NON-NLS-1$
		for (String changed : List.of(
				getter.replace("private List<Entry> all()", "List<Entry> all()"), //$NON-NLS-1$ //$NON-NLS-2$
				getter.replace("return this.entries;", "return List.copyOf(entries);"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("this.entries", "List.<Entry>of()"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("return row;", "return null;"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("String key) {}", "String key) { public String key() { return key.trim(); } }"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("other.equals(row.key())", "other.equals(row.toString())"))) { //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(service.analyze(parse(changed), "=Sample").stream() //$NON-NLS-1$
					.noneMatch(row -> row.targetContract().startsWith("MAP;"))); //$NON-NLS-1$
		}
	}

	@Test
	public void twoParametersWithTheSameNameInDifferentMethodsRemainDistinct() {
		String method= """
				Entry find(List<Entry> entries, String key) {
				    for (Entry e : entries) if (key.equals(e.id())) return e;
				    return null;
				}
				""";
		String prefix= "import java.util.List; class Sample { record Entry(String id) {} "; //$NON-NLS-1$
		String twoDeclarations= prefix + method + method.replace("find(", "findOther(") + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue(service.analyze(parse(twoDeclarations), "=Sample").isEmpty()); //$NON-NLS-1$
		String oneParameter= prefix + method.replace("return null;", //$NON-NLS-1$
				"for (Entry e : entries) if (e != null) continue; return null;") + "}"; //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(service.analyze(parse(oneParameter), "=Sample").isEmpty()); //$NON-NLS-1$
		String twoQueries= method.replace("String key)", "String key, String fallback)") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("return null;", "for (Entry e : entries) if (fallback.equals(e.id())) return e; return null;"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(2, service.analyze(parse(prefix + twoQueries + "}"), "=Sample").size()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void localSyntaxCleanupsDoNotGenerateCompetingRepresentationProposals() {
		String source= """
				import java.util.*;
				class Sample {
				    void copy(List<String> original, String[] buffer) {
				        List<String> copy = new ArrayList<>(original);
				        ArrayList<String> target = new ArrayList<>();
				        for (String s : original) target.add(s);
				        for (int i = 0; i < buffer.length; i++) buffer[i] = "x";
				        target.addAll(copy);
				    }
				}
				""";
		assertTrue(service.analyze(parse(source), "=Sample").stream() //$NON-NLS-1$
				.allMatch(row -> "No proven target".equals(row.targetContract()))); //$NON-NLS-1$
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
