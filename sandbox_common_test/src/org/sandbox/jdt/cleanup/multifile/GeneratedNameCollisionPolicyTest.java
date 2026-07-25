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

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.cleanup.multifile.GeneratedNameCollisionPolicy.Assessment;
import org.sandbox.jdt.cleanup.multifile.GeneratedNameCollisionPolicy.Namespace;

class GeneratedNameCollisionPolicyTest {

	@Test
	void acceptsUnusedGeneratedName() {
		Assessment assessment= assess("""
				package test;
				class Owner {
					int value;
					void process() {}
				}
				""");

		assertTrue(assessment.available());
		assertTrue(assessment.collisions().isEmpty());
	}

	@Test
	void rejectsTypeMemberImportAndTypeParameterNamespaces() {
		Assessment assessment= assess("""
				package test;
				import other.Status;
				class Owner<Status> {
					int Status;
					void Status() {}
					class Status {}
					void local() {
						class Status {}
					}
				}
				""");

		assertFalse(assessment.available());
		assertTrue(namespaces(assessment).containsAll(List.of(Namespace.IMPORT, Namespace.MEMBER,
				Namespace.TYPE_DECLARATION, Namespace.TYPE_PARAMETER)));
		assertTrue(assessment.explanation().contains("Status")); //$NON-NLS-1$
	}

	@Test
	void rejectsConflictingTypeInAnotherAffectedCompilationUnit() {
		CompilationUnit caller= parse("""
				package test;
				class Caller {
					class Status {}
				}
				""");

		Assessment assessment= GeneratedNameCollisionPolicy.assess(caller, "owner-key", "test.Owner", "Status"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		assertFalse(assessment.available());
		assertEquals(List.of(Namespace.TYPE_DECLARATION), namespaces(assessment).stream().distinct().toList());
	}

	@Test
	void collisionOrderingIsDeterministic() {
		Assessment assessment= assess("""
				package test;
				import z.Status;
				class Owner {
					void Status() {}
					int Status;
				}
				""");

		assertEquals(List.of(Namespace.IMPORT, Namespace.MEMBER, Namespace.MEMBER), namespaces(assessment));
	}

	private static Assessment assess(String source) {
		return GeneratedNameCollisionPolicy.assess(parse(source), "owner-key", "test.Owner", "Status"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static List<Namespace> namespaces(Assessment assessment) {
		return assessment.collisions().stream().map(GeneratedNameCollisionPolicy.Collision::namespace).toList();
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
