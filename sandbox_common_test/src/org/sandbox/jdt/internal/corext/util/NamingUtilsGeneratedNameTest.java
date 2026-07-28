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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.JavaCore;
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

	@Test
	void rejectsAccessibleInheritedMemberTypeWithGeneratedName() throws IOException {
		String generated= NamingUtils.generateUniqueNestedClassName(parseAnonymous(sourceWithoutCollision()),
				"resource"); //$NON-NLS-1$
		AnonymousClassDeclaration anonymous= parseAnonymousWithSibling(
				"package test; class Base { protected static class " + generated + " {} }", //$NON-NLS-1$ //$NON-NLS-2$
				ownerSource("extends Base")); //$NON-NLS-1$

		IllegalStateException exception= assertThrows(IllegalStateException.class,
				() -> NamingUtils.generateUniqueNestedClassName(anonymous, "resource")); //$NON-NLS-1$

		assertTrue(exception.getMessage().contains("inherited member type")); //$NON-NLS-1$
		assertTrue(exception.getMessage().contains("test.Base." + generated)); //$NON-NLS-1$
	}

	@Test
	void ignoresPrivateMemberTypeInSuperclass() throws IOException {
		String generated= NamingUtils.generateUniqueNestedClassName(parseAnonymous(sourceWithoutCollision()),
				"resource"); //$NON-NLS-1$
		AnonymousClassDeclaration anonymous= parseAnonymousWithSibling(
				"package test; class Base { private static class " + generated + " {} }", //$NON-NLS-1$ //$NON-NLS-2$
				ownerSource("extends Base")); //$NON-NLS-1$

		assertEquals(generated, NamingUtils.generateUniqueNestedClassName(anonymous, "resource")); //$NON-NLS-1$
	}

	private static AnonymousClassDeclaration parseAnonymous(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		return findAnonymous((CompilationUnit) parser.createAST(null));
	}

	private static AnonymousClassDeclaration parseAnonymousWithSibling(String baseSource, String ownerSource)
			throws IOException {
		Path sourceRoot= Files.createTempDirectory("generated-name-bindings"); //$NON-NLS-1$
		Path packageDirectory= Files.createDirectories(sourceRoot.resolve("test")); //$NON-NLS-1$
		Files.writeString(packageDirectory.resolve("Base.java"), baseSource, StandardCharsets.UTF_8); //$NON-NLS-1$
		Files.writeString(packageDirectory.resolve("Owner.java"), ownerSource, StandardCharsets.UTF_8); //$NON-NLS-1$

		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("test/Owner.java"); //$NON-NLS-1$
		parser.setEnvironment(null, new String[] { sourceRoot.toString() },
				new String[] { StandardCharsets.UTF_8.name() }, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(ownerSource.toCharArray());
		return findAnonymous((CompilationUnit) parser.createAST(null));
	}

	private static AnonymousClassDeclaration findAnonymous(CompilationUnit root) {
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

	private static String ownerSource(String inheritance) {
		return """
				package test;

				class Owner __INHERITANCE__ {
					Object resource = new Object() {
						void before() {
						}
					};
				}
				""".replace("__INHERITANCE__", inheritance); //$NON-NLS-1$
	}

	private static String sourceWithoutCollision() {
		return ownerSource(""); //$NON-NLS-1$
	}
}
