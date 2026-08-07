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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;

class SynchronizedWrapperProtocolAnalyzerTest {

	private final SynchronizedWrapperProtocolAnalyzer analyzer= new SynchronizedWrapperProtocolAnalyzer();

	@Test
	void reportsIterationLockedOnTheWrapperItself() {
		CompilationUnit compilationUnit= parse("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			class Sample {
				private final List<String> listeners = Collections.synchronizedList(new ArrayList<>());

				void notifyListeners() {
					synchronized (listeners) {
						for (String listener : listeners) {
							System.out.println(listener);
						}
					}
				}
			}
			""");

		List<ConcurrencyProtocol> protocols= analyzer.analyze(compilationUnit);

		assertEquals(1, protocols.size());
		ConcurrencyProtocol protocol= protocols.get(0);
		assertEquals("listeners", protocol.identity().displayName()); //$NON-NLS-1$
		assertEquals(AnalysisCompleteness.LOCAL_SEED, protocol.completeness());
		assertEquals(SynchronizationKind.SYNCHRONIZED_WRAPPER, protocol.summary().synchronization());
		assertEquals(IterationSemantics.EXTERNALLY_LOCKED, protocol.summary().iteration());
		assertEquals(List.of(EvidenceKind.WRAPPER_CREATION, EvidenceKind.LOCKED_ITERATION),
				protocol.evidence().stream().map(item -> item.kind()).toList());
		assertTrue(protocol.hasSingleProtectingLock());
		assertFalse(protocol.isFlowComplete());
	}

	@Test
	void reportsUnprotectedEnhancedForIteration() {
		CompilationUnit compilationUnit= parse("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			class Sample {
				private final List<String> listeners = Collections.synchronizedList(new ArrayList<>());

				void notifyListeners() {
					for (String listener : listeners) {
						System.out.println(listener);
					}
				}
			}
			""");

		ConcurrencyProtocol protocol= analyzer.analyze(compilationUnit).get(0);

		assertEquals(IterationSemantics.UNKNOWN, protocol.summary().iteration());
		assertEquals(List.of(EvidenceKind.WRAPPER_CREATION, EvidenceKind.UNPROTECTED_ACCESS),
				protocol.evidence().stream().map(item -> item.kind()).toList());
		assertFalse(protocol.hasSingleProtectingLock());
	}

	@Test
	void synchronizationOnAnotherObjectDoesNotProtectWrapperIteration() {
		CompilationUnit compilationUnit= parse("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			class Sample {
				private final Object lock = new Object();
				private final List<String> listeners = Collections.synchronizedList(new ArrayList<>());

				void notifyListeners() {
					synchronized (lock) {
						for (String listener : listeners) {
							System.out.println(listener);
						}
					}
				}
			}
			""");

		ConcurrencyProtocol protocol= analyzer.analyze(compilationUnit).get(0);

		assertEquals(EvidenceKind.UNPROTECTED_ACCESS, protocol.evidence().get(1).kind());
		assertTrue(protocol.lockIdentities().isEmpty());
	}

	@Test
	void ignoresLocalSynchronizedWrappers() {
		CompilationUnit compilationUnit= parse("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			class Sample {
				void run() {
					List<String> listeners = Collections.synchronizedList(new ArrayList<>());
					for (String listener : listeners) {
						System.out.println(listener);
					}
				}
			}
			""");

		assertTrue(analyzer.analyze(compilationUnit).isEmpty());
	}

	@Test
	void ignoresSameNamedFactoryFromAnotherType() {
		CompilationUnit compilationUnit= parse("""
			import java.util.ArrayList;
			import java.util.List;

			class Collections {
				static <T> List<T> synchronizedList(List<T> source) {
					return source;
				}
			}

			class Sample {
				private final List<String> listeners = Collections.synchronizedList(new ArrayList<>());
			}
			""");

		assertTrue(analyzer.analyze(compilationUnit).isEmpty());
	}

	@Test
	void returnsWrapperCandidatesInSourceOrder() {
		CompilationUnit compilationUnit= parse("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.HashSet;
			import java.util.List;
			import java.util.Set;

			class Sample {
				private final List<String> first = Collections.synchronizedList(new ArrayList<>());
				private final Set<String> second = Collections.synchronizedSet(new HashSet<>());
			}
			""");

		assertEquals(List.of("first", "second"), //$NON-NLS-1$ //$NON-NLS-2$
				analyzer.analyze(compilationUnit).stream()
						.map(protocol -> protocol.identity().displayName())
						.toList());
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
