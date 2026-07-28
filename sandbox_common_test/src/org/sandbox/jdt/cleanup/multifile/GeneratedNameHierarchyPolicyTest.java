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
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/** Binding-level tests for inherited generated-name collisions. */
class GeneratedNameHierarchyPolicyTest {

	@Test
	void reportsInterfaceMemberTypesDeterministically() throws IOException {
		ITypeBinding ownerBinding= ownerBinding(Map.of(
				"First.java", "package test; interface First { class Status {} }", //$NON-NLS-1$ //$NON-NLS-2$
				"Second.java", "package test; interface Second { class Status {} }", //$NON-NLS-1$ //$NON-NLS-2$
				"Owner.java", "package test; class Owner implements Second, First {}")); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(ownerBinding);
		assertEquals(List.of(
				"test.First.Status inherited from test.First", //$NON-NLS-1$
				"test.Second.Status inherited from test.Second"), //$NON-NLS-1$
				GeneratedNameHierarchyPolicy.inheritedMemberTypeCollisions(ownerBinding, "Status")); //$NON-NLS-1$
	}

	private static ITypeBinding ownerBinding(Map<String, String> sources) throws IOException {
		Path sourceRoot= Files.createTempDirectory("generated-hierarchy-bindings"); //$NON-NLS-1$
		Path packageDirectory= Files.createDirectories(sourceRoot.resolve("test")); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : sources.entrySet()) {
			Files.writeString(packageDirectory.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
		}
		String ownerSource= sources.get("Owner.java"); //$NON-NLS-1$
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setUnitName("test/Owner.java"); //$NON-NLS-1$
		parser.setEnvironment(null, new String[] { sourceRoot.toString() },
				new String[] { StandardCharsets.UTF_8.name() }, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(ownerSource.toCharArray());
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		return ((TypeDeclaration) root.types().get(0)).resolveBinding();
	}
}
