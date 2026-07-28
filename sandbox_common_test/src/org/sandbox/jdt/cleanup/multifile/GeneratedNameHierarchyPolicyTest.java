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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Binding-level tests for inherited generated-name collisions. */
class GeneratedNameHierarchyPolicyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void reportsInterfaceMemberTypesDeterministically() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		pack.createCompilationUnit("First.java", //$NON-NLS-1$
				"package test; interface First { class Status {} }", false, null); //$NON-NLS-1$
		pack.createCompilationUnit("Second.java", //$NON-NLS-1$
				"package test; interface Second { class Status {} }", false, null); //$NON-NLS-1$
		ICompilationUnit owner= pack.createCompilationUnit("Owner.java", //$NON-NLS-1$
				"package test; class Owner implements Second, First {}", false, null); //$NON-NLS-1$
		ITypeBinding ownerBinding= ownerBinding(owner);

		assertEquals(List.of(
				"test.First.Status inherited from test.First", //$NON-NLS-1$
				"test.Second.Status inherited from test.Second"), //$NON-NLS-1$
				GeneratedNameHierarchyPolicy.inheritedMemberTypeCollisions(ownerBinding, "Status")); //$NON-NLS-1$
	}

	private static ITypeBinding ownerBinding(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		return ((TypeDeclaration) root.types().get(0)).resolveBinding();
	}
}
