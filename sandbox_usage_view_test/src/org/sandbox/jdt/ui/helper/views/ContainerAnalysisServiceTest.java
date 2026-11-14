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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.analysis.RecordKeyLookupAnalyzer;
import org.sandbox.jdt.ui.helper.views.ContainerAnalysisService.ContainerAnalysisRow;

/** Verifies the read-only projection used by the Container Analysis view. */
public class ContainerAnalysisServiceTest {

	private final ContainerAnalysisService service= new ContainerAnalysisService();

	@Test
	public void canceledReportStopsBeforePublishingAnyRows() {
		var monitor= new NullProgressMonitor();
		monitor.setCanceled(true);
		CompilationUnit unit= parse("class Sample {}"); //$NON-NLS-1$
		assertThrows(OperationCanceledException.class, () -> service.analyze(unit, "=Sample", monitor)); //$NON-NLS-1$
	}

	@Test
	public void recordLookupTraversalCanBeCanceledInsideOneLargeCompilationUnit() {
		StringBuilder source= new StringBuilder("class Sample {"); //$NON-NLS-1$
		for (int index= 0; index < 500; index++) {
			source.append("void method").append(index).append("() { int n = 1; }"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		CompilationUnit unit= parse(source.append('}').toString());
		int[] checks= { 0 };
		assertThrows(OperationCanceledException.class, () -> RecordKeyLookupAnalyzer.analyze(unit, () -> {
			if (++checks[0] == 3) {
				throw new OperationCanceledException();
			}
		}));
		assertEquals(3, checks[0]);
	}

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
	public void typedArrayReturnShowsListBuilderAndRetainedPublication() {
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
		assertTrue(rows.stream().allMatch(row -> "ARRAY".equals(row.currentShape()) //$NON-NLS-1$
				&& row.targetContract().startsWith("LIST;") && "REPORT_ONLY".equals(row.status()))); //$NON-NLS-1$ //$NON-NLS-2$
		ContainerAnalysisRow publication= rows.stream()
				.filter(row -> "ARRAY_RETURN_PUBLICATION".equals(row.evidenceKind())) //$NON-NLS-1$
				.findFirst().orElseThrow();
		assertEquals(source.lastIndexOf("return values") + "return ".length(), publication.sourceStart()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("values", fragment(source, publication)); //$NON-NLS-1$
		assertTrue(publication.evidenceSummary().contains("original component type")); //$NON-NLS-1$
	}

	@Test
	public void rejectedArrayProfileShowsBoundaryInsteadOfInventingTarget() {
		String source= """
				import java.util.Arrays;
				class Sample {
					Object collect(String value) {
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
	public void atomicDrainReportsWhyTheWrapperMonitorMustBeRetained() {
		String source= """
				import java.util.*;
				final class Sample {
				    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
				    void offer(String value) { entries.add(value); }
				    List<String> drain() {
				        synchronized (entries) {
				            List<String> batch = new ArrayList<>(entries);
				            entries.clear();
				            return batch;
				        }
				    }
				}
				""";
		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Sample"); //$NON-NLS-1$
		assertEquals(2, rows.size(), rows::toString);
		assertTrue(rows.stream().allMatch(row -> "NO_MIGRATION_RECOMMENDED".equals(row.status()))); //$NON-NLS-1$
		assertTrue(rows.stream().allMatch(row -> "Retain existing locking".equals(row.targetContract()))); //$NON-NLS-1$
		assertEquals("LOCKED_WRITE", rows.get(0).evidenceKind()); //$NON-NLS-1$
		assertEquals("entries.add(value)", fragment(source, rows.get(0))); //$NON-NLS-1$
		assertEquals("DRAIN", rows.get(1).evidenceKind()); //$NON-NLS-1$
		assertEquals("new ArrayList<>(entries)", fragment(source, rows.get(1))); //$NON-NLS-1$
		assertTrue(rows.get(1).semanticSummary().contains("between the snapshot and clear")); //$NON-NLS-1$
		for (String changed : List.of(source.replace("synchronized (entries)", "synchronized (this)"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("void offer", "List<String> expose() { return entries; } void offer"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("entries.clear();", "entries.clear(); offer(null);"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("entries.add(value);", "new Thread(() -> entries.add(value)).start();"))) { //$NON-NLS-1$ //$NON-NLS-2$
			var ambiguous= service.analyze(parse(changed), "=Sample"); //$NON-NLS-1$
			assertTrue(ambiguous.stream().noneMatch(row -> "NO_MIGRATION_RECOMMENDED".equals(row.status())), changed); //$NON-NLS-1$
			assertTrue(ambiguous.stream().anyMatch(row -> "UNRESOLVED_BOUNDARY".equals(row.evidenceKind()) //$NON-NLS-1$
					&& "REPORT_ONLY".equals(row.status())), changed); //$NON-NLS-1$
		}
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

	@Test
	public void parallelArraysReportARecordSequenceWithNavigablePairedEvidence() {
		String source= """
				import java.util.Arrays;
				class Sample {
				    static void consume(String label, Integer value) {}
				    void run(String[] input) {
				        String[] labels = new String[0];
				        Integer[] values = new Integer[0];
				        int count = 0;
				        for (String label : input) {
				            labels = Arrays.copyOf(labels, count + 1);
				            values = Arrays.copyOf(values, count + 1);
				            labels[count] = label;
				            values[count] = label.length();
				            count++;
				        }
				        for (int i = 0; i < count; i++) consume(labels[i], values[i]);
				    }
				}
				""";
		List<ContainerAnalysisRow> rows= service.analyze(parse(source), "=Sample"); //$NON-NLS-1$
		var records= rows.stream().filter(row -> row.targetContract().startsWith("RECORD_SEQUENCE;")).toList(); //$NON-NLS-1$
		assertEquals(List.of("PAIRED_ARRAY_WRITE", "PAIRED_ARRAY_WRITE", "PAIRED_ARRAY_READ", "PAIRED_ARRAY_READ"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				records.stream().map(ContainerAnalysisRow::evidenceKind).toList());
		assertEquals(List.of("labels[count]", "values[count]", "labels[i]", "values[i]"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				records.stream().map(row -> fragment(source, row)).toList());
		assertTrue(records.stream().allMatch(row -> "REPORT_ONLY".equals(row.status()) //$NON-NLS-1$
				&& row.semanticSummary().contains("CHANGED") && row.semanticSummary().contains("PRESERVED"))); //$NON-NLS-1$ //$NON-NLS-2$
		for (String changed : List.of(
				source.replace("values[count]", "values[0]"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("copyOf(values, count + 1)", "copyOf(values, count + 2)"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("values[i]", "values[count - i - 1]"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("count++;", "count++; consume(labels[0], values[count - 1]);"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("int count = 0;", "Object alias = labels; int count = 0;"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("count++;", "count++; java.util.Arrays.sort(values);"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("count++;", "count++; count = 0;"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("label.length()", "missingValue()"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("new String[0]", "new String[1]"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("Integer[] values = new Integer", "int[] values = new int"))) { //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(service.analyze(parse(changed), "=Sample").stream() //$NON-NLS-1$
					.noneMatch(row -> row.targetContract().startsWith("RECORD_SEQUENCE;")), changed); //$NON-NLS-1$
		}
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
