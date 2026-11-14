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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;

class SynchronizedListAccessProtocolAnalyzerTest {

	private final SynchronizedListAccessProtocolAnalyzer analyzer= new SynchronizedListAccessProtocolAnalyzer();

	@Test
	void groupsBindingResolvedOperationsAndLockedIterationWithoutClaimingClosure() {
		String source= """
				import java.util.*;
				class Sample {
				    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
				    void add(String text) { entries.add(text); }
				    boolean contains(String text) { return entries.contains(text); }
				    void inspect() { synchronized (entries) { for (String item : this.entries) { } } }
				    List<String> live() { return entries; }
				}
				""";
		var reports= analyzer.analyze(parse(source, true));
		assertEquals(1, reports.size());
		var report= reports.getFirst();
		assertFalse(report.permitsSourceRewrite());
		assertEquals(AnalysisCompleteness.LOCAL_SEED, report.protocol().completeness());
		assertEquals(List.of(EvidenceKind.LOCKED_WRITE, EvidenceKind.LOCKED_READ,
				EvidenceKind.LOCKED_ITERATION, EvidenceKind.LIVE_WRAPPER_RETURN),
				report.protocol().evidence().stream().map(e -> e.kind()).toList());
		assertEquals(1, report.protocol().lockIdentities().size());
		for (var evidence : report.protocol().evidence()) {
			String snippet= source.substring(evidence.sourceStart(), evidence.sourceStart() + evidence.sourceLength());
			assertFalse(snippet.isBlank());
		}
	}

	@Test
	void detectsUnprotectedIterationAndUnclassifiedMethodsWithoutRewriting() {
		String source= """
				import java.util.*;
				class Sample {
				    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
				    void inspect() { for (String item : entries) { } }
				    void stream() { entries.stream().count(); }
				}
				""";
		var reports= analyzer.analyze(parse(source, true));
		assertEquals(1, reports.size());
		assertTrue(reports.getFirst().protocol().hasUnresolvedBoundary());
		assertEquals(2, reports.getFirst().protocol().evidence().size());
		assertFalse(reports.getFirst().permitsSourceRewrite());
		assertTrue(analyzer.analyze(parse(source, false)).isEmpty());
	}

	@Test
	void keepsDistinctPrivateFieldsApartAndRejectsFactoryLookalikes() {
		String source= """
				import java.util.*;
				class Sample {
				    static class Collections {
				        static <T> List<T> synchronizedList(List<T> list) { return list; }
				    }
				    private final List<String> first = java.util.Collections.synchronizedList(new ArrayList<>());
				    private final List<String> second = java.util.Collections.synchronizedList(new ArrayList<>());
				    private final List<String> fake = Collections.synchronizedList(new ArrayList<>());
				    void add(String text) { first.add(text); second.add(text); fake.add(text); }
				}
				""";
		var reports= analyzer.analyze(parse(source, true));
		assertEquals(2, reports.size());
		assertEquals(List.of("first", "second"),
				reports.stream().map(r -> r.protocol().identity().displayName()).toList()); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(reports.stream().allMatch(r -> r.protocol().evidence().size() == 1));
	}

	private static CompilationUnit parse(String source, boolean bindings) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(bindings);
		parser.setBindingsRecovery(true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(null, null, null, true);
		var options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		if (bindings) {
			assertTrue(Arrays.stream(unit.getProblems()).noneMatch(IProblem::isError), unit::toString);
		}
		return unit;
	}
}
