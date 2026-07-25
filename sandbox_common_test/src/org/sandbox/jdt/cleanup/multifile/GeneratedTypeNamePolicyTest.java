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
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

class GeneratedTypeNamePolicyTest {

	@Test
	void acceptsAvailableNameAndProducesDeterministicFingerprint() {
		CompilationUnit root= parse("package test; class Owner { void run() {} }"); //$NON-NLS-1$
		AbstractTypeDeclaration owner= owner(root);

		GeneratedTypeNamePolicy.Assessment first= GeneratedTypeNamePolicy.assessNestedType(root, owner,
				List.of(root), "Status"); //$NON-NLS-1$
		GeneratedTypeNamePolicy.Assessment second= GeneratedTypeNamePolicy.assessNestedType(root, owner,
				List.of(root), "Status"); //$NON-NLS-1$

		assertTrue(first.available());
		assertEquals("AVAILABLE", first.reasonCode()); //$NON-NLS-1$
		assertEquals(first.fingerprint(), second.fingerprint());
	}

	@Test
	void rejectsTypeImportMemberAndTypeParameterCollisions() {
		assertCollision("package test; class Owner { class Status {} }"); //$NON-NLS-1$
		assertCollision("package test; import other.Status; class Owner {}"); //$NON-NLS-1$
		assertCollision("package test; class Owner { int Status; }"); //$NON-NLS-1$
		assertCollision("package test; class Owner { void Status() {} }"); //$NON-NLS-1$
		assertCollision("package test; class Owner<Status> {}"); //$NON-NLS-1$
		assertCollision("package test; class Owner { void run() { class Status {} } }"); //$NON-NLS-1$
	}

	@Test
	void detectsSamePackageTopLevelTypeCollisionAcrossAffectedRoots() {
		CompilationUnit ownerRoot= parse("package test; class Owner {}"); //$NON-NLS-1$
		CompilationUnit otherRoot= parse("package test; class Status {}"); //$NON-NLS-1$

		GeneratedTypeNamePolicy.Assessment assessment= GeneratedTypeNamePolicy.assessNestedType(ownerRoot,
				owner(ownerRoot), List.of(ownerRoot, otherRoot), "Status"); //$NON-NLS-1$

		assertFalse(assessment.available());
		assertEquals("GENERATED_NAME_COLLISION", assessment.reasonCode()); //$NON-NLS-1$
		assertTrue(assessment.explanation().contains("PACKAGE_TYPE:Status")); //$NON-NLS-1$
	}

	@Test
	void usesOrdinarySamePackageReferenceWithoutConflict() {
		CompilationUnit caller= parse("package test; class Client {}"); //$NON-NLS-1$

		GeneratedTypeNamePolicy.ReferenceResolution resolution= GeneratedTypeNamePolicy.resolveReference(caller,
				"test.OrderProcessor", "Status", false); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(resolution.accessible());
		assertEquals("OrderProcessor.Status", resolution.qualifier()); //$NON-NLS-1$
	}

	@Test
	void qualifiesOwnerWhenCallerContainsConflictingTypeName() {
		CompilationUnit caller= parse("package test; class Client { class OrderProcessor {} }"); //$NON-NLS-1$

		GeneratedTypeNamePolicy.ReferenceResolution resolution= GeneratedTypeNamePolicy.resolveReference(caller,
				"test.OrderProcessor", "Status", false); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(resolution.accessible());
		assertEquals("test.OrderProcessor.Status", resolution.qualifier()); //$NON-NLS-1$
		assertEquals("QUALIFIED_TO_AVOID_COLLISION", resolution.reasonCode()); //$NON-NLS-1$
	}

	@Test
	void rejectsCrossPackageReferenceToPackagePrivateGeneratedType() {
		CompilationUnit caller= parse("package client; class Client {}"); //$NON-NLS-1$

		GeneratedTypeNamePolicy.ReferenceResolution resolution= GeneratedTypeNamePolicy.resolveReference(caller,
				"test.OrderProcessor", "Status", false); //$NON-NLS-1$ //$NON-NLS-2$

		assertFalse(resolution.accessible());
		assertEquals("INACCESSIBLE_GENERATED_TYPE", resolution.reasonCode()); //$NON-NLS-1$
	}

	private static void assertCollision(String source) {
		CompilationUnit root= parse(source);
		GeneratedTypeNamePolicy.Assessment assessment= GeneratedTypeNamePolicy.assessNestedType(root, owner(root),
				List.of(root), "Status"); //$NON-NLS-1$
		assertFalse(assessment.available(), source);
		assertEquals("GENERATED_NAME_COLLISION", assessment.reasonCode(), source); //$NON-NLS-1$
	}

	private static AbstractTypeDeclaration owner(CompilationUnit root) {
		return (AbstractTypeDeclaration) root.types().get(0);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
