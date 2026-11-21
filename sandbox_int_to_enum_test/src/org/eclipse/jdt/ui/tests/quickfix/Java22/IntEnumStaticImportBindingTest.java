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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMultiFilePlanner;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Static import bindings from source dependencies must survive batch parsing. */
public class IntEnumStaticImportBindingTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();
	@RegisterExtension
	AbstractEclipseJava dependency= new EclipseJava22();
	private IPackageFragment pack;

	@BeforeEach
	void setup() throws CoreException {
		IPackageFragment library= dependency.getSourceFolder().createPackageFragment("library", false, null); //$NON-NLS-1$
		library.createCompilationUnit("Library.java", """
				package library;
				public class Library {
					public static void unrelated() {}
					public static void accept() {}
					public static void STATE_OPEN() {}
				}
				""", false, null); //$NON-NLS-1$ //$NON-NLS-2$
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newProjectEntry(dependency.getJavaProject().getPath()));
		pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
	}

	@Test
	void sourceDependencyImportDoesNotCrashAnEmptyPlan() throws CoreException {
		ICompilationUnit client= client("library.Library.unrelated", "unrelated();"); //$NON-NLS-1$ //$NON-NLS-2$
		assertCandidates(new ICompilationUnit[] { client }, 0);
	}

	@ParameterizedTest
	@ValueSource(strings= { "unrelated", "accept", "STATE_OPEN" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void sourceDependencyImportDoesNotBlockAValidCandidate(String importedMethod) throws CoreException {
		ICompilationUnit owner= owner(""); //$NON-NLS-1$
		ICompilationUnit client= client("library.Library." + importedMethod, //$NON-NLS-1$
				importedMethod + "(); new Owner().accept(Owner.STATE_OPEN);"); //$NON-NLS-1$
		assertCandidates(new ICompilationUnit[] { owner, client }, 1);
	}

	@Test
	void modeledConstantImportStillRejectsTheCandidate() throws CoreException {
		ICompilationUnit owner= owner(""); //$NON-NLS-1$
		ICompilationUnit client= client("test.Owner.STATE_OPEN", "new Owner().accept(STATE_OPEN);"); //$NON-NLS-1$ //$NON-NLS-2$
		assertRejectedImport(owner, client, "UNSUPPORTED_CONSTANT_REFERENCE"); //$NON-NLS-1$
	}

	@Test
	void modeledMethodImportStillRejectsTheCandidate() throws CoreException {
		ICompilationUnit owner= owner("static "); //$NON-NLS-1$
		ICompilationUnit client= client("test.Owner.accept", "accept(Owner.STATE_OPEN);"); //$NON-NLS-1$ //$NON-NLS-2$
		assertRejectedImport(owner, client, "UNSUPPORTED_METHOD_REFERENCE"); //$NON-NLS-1$
	}

	private ICompilationUnit owner(String modifier) throws CoreException {
		return pack.createCompilationUnit("Owner.java", """
				package test;
				class Owner {
					static final int STATE_OPEN= 1;
					static final int STATE_CLOSED= 2;
					$modifier void accept(int state) {
						if (state == STATE_OPEN) System.out.println(1);
						else if (state == STATE_CLOSED) System.out.println(2);
					}
				}
				""".replace("$modifier ", modifier), false, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private ICompilationUnit client(String importedMember, String statements) throws CoreException {
		return pack.createCompilationUnit("Client.java", """
				package test;
				import static $member;
				class Client { void run() { $statements } }
				""".replace("$member", importedMember).replace("$statements", statements), false, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private void assertCandidates(ICompilationUnit[] units, int expected) throws CoreException {
		assertCompiles(units);
		var result= IntEnumMultiFilePlanner.create(context.getJavaProject(), units, true, null);
		assertFalse(result.status().hasFatalError(), () -> result.status().toString());
		assertEquals(expected, result.plan().candidates().size());
	}

	private void assertRejectedImport(ICompilationUnit owner, ICompilationUnit client, String reason) throws CoreException {
		ICompilationUnit[] units= { owner, client };
		assertCompiles(units);
		var result= IntEnumMultiFilePlanner.create(context.getJavaProject(), units, true, null);
		assertEquals(0, result.plan().candidates().size());
		assertEquals(reason, result.diagnostics().candidates().getFirst().reasonCode());
	}

	private static void assertCompiles(ICompilationUnit[] units) {
		for (ICompilationUnit unit : units) {
			ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(unit);
			parser.setResolveBindings(true);
			CompilationUnit root= (CompilationUnit) parser.createAST(null);
			assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
					() -> Arrays.toString(root.getProblems()));
		}
	}
}
