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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseBundleClasspath;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/** Complete-unit contracts used by the pinned JDT UI viewer adapters. */
public class ViewerSorterContractTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();
	private IPackageFragment pack;

	@BeforeEach
	void setup() throws CoreException {
		EclipseBundleClasspath.addBundles(context.getJavaProject(),
				"org.eclipse.swt", "org.eclipse.equinox.common", "org.eclipse.jface", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"org.eclipse.core.commands"); //$NON-NLS-1$
		pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		assertNotNull(context.getJavaProject().findType("org.eclipse.jface.viewers.ViewerSorter")); //$NON-NLS-1$
	}

	static Stream<Arguments> contracts() {
		return Stream.of(
				Arguments.of("inherited collator read", """
						public class Example extends ViewerSorter {
							public Collator value() { return collator; }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("inherited collator write", """
						public class Example extends ViewerSorter {
							public void configure(Collator value) { collator= value; }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("getCollator override", """
						public class Example extends ViewerSorter {
							@Override public Collator getCollator() { return super.getCollator(); }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("sorter-specific member invocation", """
						public class Example {
							public Collator value(ViewerSorter sorter) { return sorter.getCollator(); }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("viewer getter override", """
						public class Example extends TableViewer {
							public Example() { super((Composite) null); }
							@Override public ViewerSorter getSorter() { return super.getSorter(); }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("viewer setter override", """
						public class Example extends TableViewer {
							public Example() { super((Composite) null); }
							@Override public void setSorter(ViewerSorter sorter) { super.setSorter(sorter); }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("interface result contract", """
						public class Example implements Contract {
							@Override public ViewerSorter create() { return new ViewerSorter(); }
						}
						""", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("shared comparator callback remains supported", """
						public class Example extends ViewerSorter {
							@Override public int category(Object element) { return element == null ? 0 : 1; }
						}
						""", true), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("local getter and setter remain supported", """
						public class Example {
							public void configure(TableViewer viewer) {
								ViewerSorter sorter= viewer.getSorter();
								viewer.setSorter(sorter);
							}
						}
						""", true), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("compatible factory result remains supported", """
						public class Example {
							public ViewerComparator create() { return new ViewerSorter(); }
						}
						""", true)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest(name= "{0}") //$NON-NLS-1$
	@MethodSource("contracts") //$NON-NLS-1$
	void preservesInheritedMembersAndOverrideContracts(String name, String declaration, boolean migrate)
			throws CoreException {
		String original= """
				package test;
				import java.text.Collator;
				import org.eclipse.jface.viewers.TableViewer;
				import org.eclipse.jface.viewers.ViewerComparator;
				import org.eclipse.jface.viewers.ViewerSorter;
				import org.eclipse.swt.widgets.Composite;
				""" + declaration; //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		ICompilationUnit contract= pack.createCompilationUnit("Contract.java", """
				package test;
				import org.eclipse.jface.viewers.ViewerSorter;
				public interface Contract { ViewerSorter create(); }
				""", false, null); //$NON-NLS-1$ //$NON-NLS-2$
		var before= CompilerDiagnosticAssertions.capture(unit, contract);
		var root= parse(unit);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(IProblem::isError),
				() -> Arrays.toString(root.getProblems()));
		var operations= operations(root);
		if (!migrate) {
			if (!operations.isEmpty()) {
				var invalidMigration= new CompilationUnitRewriteOperationsFixCore("Migrate viewer sorter", root, //$NON-NLS-1$
						operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
				invalidMigration.createChange(null).perform(new NullProgressMonitor());
			}
			before.assertNoNewDiagnostics();
			assertEquals(original, unit.getSource(), name + ": the existing API contract must stay intact"); //$NON-NLS-1$
			return;
		}
		assertFalse(operations.isEmpty(), name);
		var fix= new CompilationUnitRewriteOperationsFixCore("Migrate viewer sorter", root, //$NON-NLS-1$
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
		var undo= fix.createChange(null).perform(new NullProgressMonitor());
		before.assertNoNewDiagnostics();
		assertFalse(unit.getSource().contains("ViewerSorter"), unit.getSource()); //$NON-NLS-1$
		assertTrue(unit.getSource().contains("ViewerComparator"), unit.getSource()); //$NON-NLS-1$
		assertTrue(operations(parse(unit)).isEmpty(), "A second cleanup must make no change"); //$NON-NLS-1$
		assertNotNull(undo);
		undo.perform(new NullProgressMonitor());
		assertEquals(original, unit.getSource());
		before.assertRestored();
	}

	private static LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange> operations(CompilationUnit root) {
		var operations= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
		JfaceCleanUpFixCore.VIEWER_SORTER.findOperations(root, operations, new HashSet<>(), true);
		return operations;
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
