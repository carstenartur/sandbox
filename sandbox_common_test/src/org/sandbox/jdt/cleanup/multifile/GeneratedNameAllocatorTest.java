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
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.cleanup.multifile.GeneratedNameAllocator.Allocation;
import org.sandbox.jdt.cleanup.multifile.GeneratedNameAllocator.Namespace;
import org.sandbox.jdt.cleanup.multifile.GeneratedNameAllocator.NestedTypeRequest;

class GeneratedNameAllocatorTest {

	@Test
	void reportsTypeMemberLocalAndImportNamespacesDeterministically() {
		CompilationUnit root= parse("""
				package test;

				import other.Status;

				class OrderProcessor<Status> {
					Object Status;

					void Status() {
					}

					void run(int Status) {
						class Status {
						}
					}
				}
				"""); //$NON-NLS-1$
		NestedTypeRequest request= request("candidate", "Status"); //$NON-NLS-1$ //$NON-NLS-2$

		Allocation allocation= GeneratedNameAllocator.allocateNestedTypes(List.of(root), List.of(request))
				.get(request.requestId());

		assertFalse(allocation.available());
		assertEquals(List.of(Namespace.TYPE, Namespace.TYPE, Namespace.MEMBER, Namespace.MEMBER,
				Namespace.LOCAL, Namespace.IMPORT),
				allocation.collisions().stream().map(GeneratedNameAllocator.Collision::namespace).toList());
		assertTrue(allocation.diagnosticMessage().contains("type parameter")); //$NON-NLS-1$
		assertTrue(allocation.diagnosticMessage().contains("field")); //$NON-NLS-1$
		assertTrue(allocation.diagnosticMessage().contains("parameter or local variable")); //$NON-NLS-1$
		assertTrue(allocation.diagnosticMessage().contains("type import")); //$NON-NLS-1$
	}

	@Test
	void rejectsEveryProspectiveDuplicateIndependentOfInputOrder() {
		CompilationUnit root= parse("""
				package test;

				class OrderProcessor {
				}
				"""); //$NON-NLS-1$
		NestedTypeRequest second= request("b", "Status"); //$NON-NLS-1$ //$NON-NLS-2$
		NestedTypeRequest first= request("a", "Status"); //$NON-NLS-1$ //$NON-NLS-2$

		Map<String, Allocation> allocations= GeneratedNameAllocator.allocateNestedTypes(List.of(root),
				List.of(second, first));

		assertEquals(List.of("a", "b"), allocations.keySet().stream().toList()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(List.of(Namespace.PLANNED), allocations.get("a").collisions().stream() //$NON-NLS-1$
				.map(GeneratedNameAllocator.Collision::namespace).toList());
		assertEquals(List.of(Namespace.PLANNED), allocations.get("b").collisions().stream() //$NON-NLS-1$
				.map(GeneratedNameAllocator.Collision::namespace).toList());
	}

	@Test
	void acceptsAnAvailableNameWithoutInventingASuffix() {
		CompilationUnit root= parse("""
				package test;

				class OrderProcessor {
					void process(int state) {
					}
				}
				"""); //$NON-NLS-1$
		NestedTypeRequest request= request("candidate", "Status"); //$NON-NLS-1$ //$NON-NLS-2$

		Allocation allocation= GeneratedNameAllocator.allocateNestedTypes(List.of(root), List.of(request))
				.get(request.requestId());

		assertTrue(allocation.available());
		assertEquals("Status", allocation.request().requestedName()); //$NON-NLS-1$
		assertTrue(allocation.collisions().isEmpty());
	}

	private static NestedTypeRequest request(String requestId, String requestedName) {
		return new NestedTypeRequest(requestId, "", "", "test.OrderProcessor", requestedName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}