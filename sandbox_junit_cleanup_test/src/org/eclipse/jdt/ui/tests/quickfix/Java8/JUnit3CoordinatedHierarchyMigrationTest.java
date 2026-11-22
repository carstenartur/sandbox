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
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import org.sandbox.jdt.internal.corext.fix.multifile.JUnitTestTypeInventory;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeComparator;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeComparator.Comparison;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeComparator.Policy;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** End-to-end rewrite contract for ordinary closed JUnit 3 hierarchies. */
public class JUnit3CoordinatedHierarchyMigrationTest {

	@RegisterExtension
	Context context= new Context();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		// Keep JUnit 3, Vintage and Jupiter on one classpath. The baseline is launched
		// by JDT's JUnit 3 loader and the migrated code by JDT's JUnit 5 loader.
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void migratesAbstractBaseAndConcreteLeafAtomically() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					@Override
					protected void setUp() {
					}

					public void testInherited() {
						assertEquals("value", 1, 1);
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ConcreteTest extends BaseTest {
					public void helper() {
					}

					public void testLocal() {
					}
				}
				""", false, null);

		IType launchTarget= concrete.findPrimaryType();
		assertNotNull(launchTarget);
		JUnitTestTypeInventory before= JUnitTestTypeInventory.capture(context.getJavaProject(), null);
		ExecutionTreeSnapshot runtimeBefore= JUnitRuntimeTestTree.capture(launchTarget,
				JUnitRuntimeTestTree.TestKind.JUNIT3);
		assertTrue(runtimeBefore.successful(), () -> "JUnit 3 baseline failed: " + runtimeBefore.roots()); //$NON-NLS-1$
		enableMigration();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { base, concrete }, new String[] {
						"""
						package test;
						import org.junit.jupiter.api.Assertions;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.TestMethodOrder;

						@TestMethodOrder(OrderAnnotation.class)
						public abstract class BaseTest {
							@BeforeEach
							protected void setUp() {
							}

							@Test
							@Order(3)
							public void testInherited() {
								Assertions.assertEquals(1, 1, "value");
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;

						public class ConcreteTest extends BaseTest {
							public void helper() {
							}

							@Test
							@Order(1)
							public void testLocal() {
							}
						}
						"""
				}, null);
		assertFinderInventoryUnchanged(before);
		assertRuntimeTreePreservedAcrossLoaders(runtimeBefore, launchTarget);
	}

	@Test
	public void migratesThreeLevelHierarchyAndPreservesFinderInventory() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("deep", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package deep;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					public void testFromBase() {
					}
				}
				""", false, null);
		ICompilationUnit middle= pack.createCompilationUnit("IntermediateTest.java", //$NON-NLS-1$
				"""
				package deep;

				public abstract class IntermediateTest extends BaseTest {
					public void testFromMiddle() {
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package deep;

				public class ConcreteTest extends IntermediateTest {
					public void testFromLeaf() {
					}
				}
				""", false, null);

		IType launchTarget= concrete.findPrimaryType();
		assertNotNull(launchTarget);
		JUnitTestTypeInventory before= JUnitTestTypeInventory.capture(context.getJavaProject(), null);
		ExecutionTreeSnapshot runtimeBefore= JUnitRuntimeTestTree.capture(launchTarget,
				JUnitRuntimeTestTree.TestKind.JUNIT3);
		assertTrue(runtimeBefore.successful(), () -> "JUnit 3 baseline failed: " + runtimeBefore.roots()); //$NON-NLS-1$
		enableMigration();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { base, middle, concrete }, new String[] {
						"""
						package deep;
						import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.TestMethodOrder;

						@TestMethodOrder(OrderAnnotation.class)
						public abstract class BaseTest {
							@Test
							@Order(5)
							public void testFromBase() {
							}
						}
						""",
						"""
						package deep;

						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;

						public abstract class IntermediateTest extends BaseTest {
							@Test
							@Order(3)
							public void testFromMiddle() {
							}
						}
						""",
						"""
						package deep;

						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;

						public class ConcreteTest extends IntermediateTest {
							@Test
							@Order(1)
							public void testFromLeaf() {
							}
						}
						"""
				}, null);
		assertFinderInventoryUnchanged(before);
		assertRuntimeTreePreservedAcrossLoaders(runtimeBefore, launchTarget);
	}

	@Test
	public void preservesAssertionEvaluationInACoordinatedHierarchy() throws Exception {
		IPackageFragment pack= root.createPackageFragment("evaluation", true, null); //$NON-NLS-1$
		String originalBase= """
				package evaluation;
				import junit.framework.Assert;
				import junit.framework.TestCase;
				public abstract class BaseTest extends TestCase {
					private String events= "";
					public void testInherited() {
						this.receiver().assertTrue(this.message(), this.actual());
						Assert.assertTrue(this.message(), this.actual());
						assertEquals("message", "value", "value"); //$NON-NLS-1$
						assertEquals("receiver;message;actual;message;actual;", this.events);
					}
					private TestCase receiver() { this.events += "receiver;"; return null; }
					private String message() { this.events += "message;"; return this.events; }
					private boolean actual() { this.events += "actual;"; return true; }
				}
				""";
		String originalLeaf= "package evaluation; public class ConcreteTest extends BaseTest { }"; //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", originalBase, false, null); //$NON-NLS-1$
		ICompilationUnit leaf= pack.createCompilationUnit("ConcreteTest.java", originalLeaf, false, null); //$NON-NLS-1$
		ICompilationUnit[] units= { base, leaf };
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		var options= context.getJavaProject().getOptions(false);
		var classpath= List.of(context.getJavaProject().getRawClasspath());
		ExecutionTreeSnapshot before= JUnitRuntimeTestTree.capture(leaf.findPrimaryType(),
				JUnitRuntimeTestTree.TestKind.JUNIT3, true);
		assertTrue(before.successful(), before.toString());
		enableMigration();
		context.apply(units);
		assertFalse(base.getSource().contains("extends TestCase"), base.getSource()); //$NON-NLS-1$
		assertTrue(base.getSource().contains("Assertions.assertTrue"), base.getSource()); //$NON-NLS-1$
		assertEquals(JUnit3BooleanAssertionTest.taggedLiterals(originalBase), JUnit3BooleanAssertionTest.taggedLiterals(base.getSource()));
		diagnostics.assertNoNewDiagnostics();
		ExecutionTreeSnapshot after= JUnitRuntimeTestTree.capture(leaf.findPrimaryType(),
				JUnitRuntimeTestTree.TestKind.JUNIT5, true);
		assertTrue(after.successful(), after.toString());
		Comparison comparison= ExecutionTreeComparator.compare(before, after, Policy.sameShape().withAttributes(false));
		assertTrue(comparison.equivalent(), comparison::difference);
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(originalBase, base.getSource());
		assertEquals(originalLeaf, leaf.getSource());
		diagnostics.assertRestored();
		assertEquals(options, context.getJavaProject().getOptions(false));
		assertEquals(classpath, List.of(context.getJavaProject().getRawClasspath()));
	}

	@Test
	public void rejectsAnEqualLengthEditThatChangesTheAssertionEvaluationPlan() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("stale", true, null); //$NON-NLS-1$
		String original= """
				package stale;
				public abstract class BaseTest extends junit.framework.TestCase {
					private String value= "message";
					public void testInherited() { assertTrue("abc", actual()); }
					public String msg() { return this.value; }
					private boolean actual() { return this.value != null; }
				}
				""";
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", original, false, null); //$NON-NLS-1$
		ICompilationUnit leaf= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"package stale; public class ConcreteTest extends BaseTest { }", false, null); //$NON-NLS-1$
		var plan= JUnitMultiFilePlanner.createCoordinated(context.getJavaProject(),
				new ICompilationUnit[] { base, leaf }, false, true, true, null).plan();
		assertTrue(plan.hasCoordinatedChanges());
		String edited= original.replace("assertTrue(\"abc\",", "assertTrue(msg(),"); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(original.length(), edited.length());
		base.getBuffer().setContents(edited);
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(base);
		parser.setResolveBindings(true);
		CompilationUnit changed= (CompilationUnit) parser.createAST(null);
		assertThrows(CoreException.class, () -> plan.addOperationsFor(base, changed,
				new java.util.LinkedHashSet<>(), new java.util.LinkedHashSet<>()));
		assertEquals(edited, base.getSource());
	}

	private void enableMigration() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
	}

	private void assertFinderInventoryUnchanged(JUnitTestTypeInventory before) throws CoreException {
		JUnitTestTypeInventory after= JUnitTestTypeInventory.capture(context.getJavaProject(), null);
		assertEquals(before.typeHandles(), after.typeHandles(),
				"The configured JDT JUnit finder must expose the same test types after migration."); //$NON-NLS-1$
	}

	private void assertRuntimeTreePreservedAcrossLoaders(ExecutionTreeSnapshot before, IType launchTarget)
			throws CoreException {
		ExecutionTreeSnapshot after= JUnitRuntimeTestTree.capture(launchTarget,
				JUnitRuntimeTestTree.TestKind.JUNIT5);
		Policy crossLoaderPolicy= Policy.sameShape().withAttributes(false);
		Comparison comparison= ExecutionTreeComparator.compare(before, after, crossLoaderPolicy);
		assertTrue(comparison.equivalent(), comparison::difference);
	}

	static final class Context extends AbstractEclipseJava {
		Context() {
			super("testresources/rtstubs_17.jar", JavaCore.VERSION_17); //$NON-NLS-1$
		}

		void apply(ICompilationUnit[] units) throws CoreException {
			var status= performRefactoring(units, null);
			assertFalse(status.hasError(), status.toString());
		}
	}
}
