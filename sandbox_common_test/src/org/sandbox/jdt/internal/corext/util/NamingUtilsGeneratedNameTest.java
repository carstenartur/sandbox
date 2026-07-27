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
package org.sandbox.jdt.internal.corext.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

/** Tests shared collision validation for generated JUnit helper classes. */
class NamingUtilsGeneratedNameTest {

	@Test
	void retainsDeterministicFieldAndChecksumNameWhenAvailable() {
		AnonymousClassDeclaration anonymous= parseAnonymous(sourceWithoutCollision());

		String generated= NamingUtils.generateUniqueNestedClassName(anonymous, "resource"); //$NON-NLS-1$

		assertTrue(generated.matches("Resource_[0-9a-f]{5}")); //$NON-NLS-1$
		assertEquals(generated, NamingUtils.generateUniqueNestedClassName(anonymous, "resource")); //$NON-NLS-1$
	}

	@Test
	void rejectsExistingNestedTypeWithGeneratedName() {
		AnonymousClassDeclaration initial= parseAnonymous(sourceWithoutCollision());
		String generated= NamingUtils.generateUniqueNestedClassName(initial, "resource"); //$NON-NLS-1$
		String source= sourceWithoutCollision().replace("class Owner {", //$NON-NLS-1$
				"class Owner {\n\tstatic class " + generated + " {} "); //$NON-NLS-1$ //$NON-NLS-2$
		AnonymousClassDeclaration conflicting= parseAnonymous(source);

		IllegalStateException exception= assertThrows(IllegalStateException.class,
				() -> NamingUtils.generateUniqueNestedClassName(conflicting, "resource")); //$NON-NLS-1$

		assertTrue(exception.getMessage().contains(generated));
		assertTrue(exception.getMessage().contains("type declaration")); //$NON-NLS-1$
	}

	private static AnonymousClassDeclaration parseAnonymous(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		AnonymousClassDeclaration[] result= new AnonymousClassDeclaration[1];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				result[0]= node;
				return false;
			}
		});
		return result[0];
	}

	private static String sourceWithoutCollision() {
		return """
				package test;

				class Owner {
					Object resource = new Object() {
						void before() {
						}
					};
				}
				""";
	}
}
