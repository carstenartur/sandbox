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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupportTest.addOtherFixtureRule;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupportTest.compilationUnit;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupportTest.factoryMixedFixtureSources;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupportTest.independentInitializerSources;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupportTest.interfaceRuleTest;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupportTest.otherFixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** End-to-end cleanup policy tests for explicit non-atomic JUnit migration. */
public class JUnitBestEffortMigrationCleanUpTest {

	@RegisterExtension
	ApplyContext context= new ApplyContext();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);
	}

	@Test
	public void strictModeQuarantinesTheCompleteUnsupportedCompilationUnit() throws CoreException {
		ICompilationUnit unit= createSource();

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void bestEffortModeKeepsSafeChangesAndAddsActionableTodoScaffold() throws CoreException {
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		ICompilationUnit unit= createSource();

		String expected= """
				package test;

				import static org.junit.jupiter.api.Assertions.assertEquals;

				import java.util.Arrays;
				import java.util.List;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameter;
				import org.junit.runners.Parameterized.Parameters;

				/**
				 * @todo Sandbox JUnit migration gap parameterized:test.FieldInjectionTest (PARAMETERIZED_FIELD_INJECTION): @Parameterized.Parameter field injection is not represented by the constructor-based local rewrite. Manual completion: Replace field injection or the custom provider with explicit Jupiter method arguments/Arguments sources, then remove the Parameterized runner and constructor coupling.  <pre>
				 * private static void sandboxJUnitMigrationTodoParameterizedFieldInjection(){
				 *   throw new UnsupportedOperationException("Manual JUnit migration required: PARAMETERIZED_FIELD_INJECTION");
				 * }
				 *
				 * </pre>
				 */
				@RunWith(Parameterized.class)
				public class FieldInjectionTest {
					@Parameter
					public int value;

					@Parameters
					public static List<Object[]> data() {
						return Arrays.asList(new Object[][] { { Integer.getInteger("value", 1) } });
					}

					@Test
					public void verifiesValue() {
						assertEquals(1, value);
					}
				}
				"""; //$NON-NLS-1$

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}

	@ParameterizedTest
	@ValueSource(strings= { "(new TemporaryFolder())", "newFolder()" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void bestEffortKeepsInterfaceTypedRuleAndItsJUnit4Test(String initializer) throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		context.disable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= root.createPackageFragment("interfacerule", true, null); //$NON-NLS-1$
		ICompilationUnit owner= interfaceRuleTest(pack, initializer);
		ICompilationUnit independent= compilationUnit(pack, "IndependentTest", """
				import org.junit.Test;

				public class IndependentTest {
					@Test public void independentTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		String retained= owner.getSource();
		assertEquals(before.get(owner.getElementName()), withoutMarker(retained));
		assertTrue(retained.contains("(UNSUPPORTED_JUNIT4_RULE)"), retained); //$NON-NLS-1$
		assertEquals(before.get(independent.getElementName()).replace("org.junit.Test", "org.junit.jupiter.api.Test"), //$NON-NLS-1$ //$NON-NLS-2$
				independent.getSource());
		diagnostics.assertNoNewDiagnostics();

		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@Test
	public void strictAssertionsMigrateInAConsumerInheritingAMixedSiblingRule() throws CoreException {
		context.disable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.disable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.disable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);
		IPackageFragment pack= mixedRulePackage();
		ICompilationUnit independent= pack.getCompilationUnit("IndependentTest.java"); //$NON-NLS-1$
		replaceSource(independent, "import org.junit.Test;", "import static org.junit.Assert.assertNotNull;\n\nimport org.junit.Test;"); //$NON-NLS-1$ //$NON-NLS-2$
		replaceSource(independent, "independentTest() {}", "independentTest() { assertNotNull(this); }"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		for (ICompilationUnit unit : units) {
			assertEquals(before.get(unit.getElementName()).replace("org.junit.Assert.assertNotNull", //$NON-NLS-1$
					"org.junit.jupiter.api.Assertions.assertNotNull"), unit.getSource(), unit.getElementName()); //$NON-NLS-1$
		}
		diagnostics.assertNoNewDiagnostics();
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@ParameterizedTest
	@ValueSource(strings= { "OtherFixture", "SecondFixture" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void bestEffortRetainsEachFixtureInAMultipleRuleDeclaration(String secondFixture) throws CoreException {
		IPackageFragment pack= mixedRulePackage();
		List<ICompilationUnit> retained= new ArrayList<>();
		for (String name : List.of("OtherFixture", secondFixture).stream().distinct().toList()) { //$NON-NLS-1$
			retained.add(compilationUnit(pack, name, """
					public class FixtureName extends org.junit.rules.ExternalResource {
						@Override protected void before() {}
					}
					""".replace("FixtureName", name))); //$NON-NLS-1$ //$NON-NLS-2$
			String consumer= name + "UseTest"; //$NON-NLS-1$
			retained.add(compilationUnit(pack, consumer, """
					public class FixtureNameUseTest {
						@org.junit.Rule public FixtureName fixture = new FixtureName();
						@org.junit.Test public void fixtureTest() {}
					}
					""".replace("FixtureName", name))); //$NON-NLS-1$ //$NON-NLS-2$
		}
		addOtherFixtureRule(pack, "new OtherFixture(), second = new " + secondFixture + "()"); //$NON-NLS-1$ //$NON-NLS-2$
		retained.add(pack.getCompilationUnit("InstanceUseTest.java")); //$NON-NLS-1$
		assertComponentRetainedThroughApplyAndUndo(pack, "(MIXED_RULE_LIFECYCLE)", retained); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(strings= { "Child.create()", "(new Child())" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void bestEffortRetainsOverridingFixtureOutsideThePlannedClassRuleChain(String initializer) throws CoreException {
		IPackageFragment pack= classRuleSubtypePackage(initializer, "@Override protected void before() { super.before(); }"); //$NON-NLS-1$
		assertComponentRetainedThroughApplyAndUndo(pack, "(EXTERNAL_RESOURCE_INITIALIZER_UNSUPPORTED)", //$NON-NLS-1$
				List.of(pack.getCompilationUnits()));
	}

	@Test
	public void bestEffortMigratesAFactoryClassRuleWhenTheSubtypeInheritsAllCallbacks() throws CoreException {
		enableResourceLifecycleMigration();
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= classRuleSubtypePackage("Child.create()", ""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		String owner= pack.getCompilationUnit("ClassUseTest.java").getSource(); //$NON-NLS-1$
		assertTrue(owner.contains("@RegisterExtension"), owner); //$NON-NLS-1$
		assertTrue(owner.contains("Child.create()"), owner); //$NON-NLS-1$
		String base= pack.getCompilationUnit("Base.java").getSource(); //$NON-NLS-1$
		assertTrue(base.contains("implements BeforeAllCallback"), base); //$NON-NLS-1$
		assertEquals(before.get("Child.java"), pack.getCompilationUnit("Child.java").getSource()); //$NON-NLS-1$ //$NON-NLS-2$
		diagnostics.assertNoNewDiagnostics();
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	private IPackageFragment classRuleSubtypePackage(String initializer, String childHook) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("classfactory", true, null); //$NON-NLS-1$
		compilationUnit(pack, "Base", """
				public class Base extends org.junit.rules.ExternalResource {
					@Override protected void before() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		compilationUnit(pack, "Child", """
				public class Child extends Base {
					public static Child create() { return new Child(); }
					CHILD_HOOK
				}
				""".replace("CHILD_HOOK", childHook)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		compilationUnit(pack, "ClassUseTest", """
				public class ClassUseTest {
					@org.junit.ClassRule public static Base fixture = INITIALIZER;
					@org.junit.Test public void classTest() {}
				}
				""".replace("INITIALIZER", initializer)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return pack;
	}

	private void assertComponentRetainedThroughApplyAndUndo(IPackageFragment pack, String reason,
			List<ICompilationUnit> retained) throws CoreException {
		enableResourceLifecycleMigration();
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		ICompilationUnit independent= createIndependentRuleTest(pack);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		for (ICompilationUnit unit : retained) {
			String source= unit.getSource();
			assertTrue(source.contains(reason), source);
			assertEquals(before.get(unit.getElementName()), withoutMarker(source), unit.getElementName());
		}
		assertIndependentRuleMigrated(pack, independent);
		diagnostics.assertNoNewDiagnostics();
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@Test
	public void bestEffortRetainsMixedFixtureUsedByFactoryClassRule() throws CoreException {
		enableResourceLifecycleMigration();
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= factoryMixedFixtureSources(root);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		for (ICompilationUnit unit : units) {
			String source= unit.getSource();
			assertTrue(source.contains("(MIXED_RULE_LIFECYCLE)"), source); //$NON-NLS-1$
			assertEquals(before.get(unit.getElementName()), withoutMarker(source), unit.getElementName());
		}
		String classOwner= pack.getCompilationUnit("ClassUseTest.java").getSource(); //$NON-NLS-1$
		assertTrue(classOwner.contains("(UNSUPPORTED_JUNIT4_RULE)"), classOwner); //$NON-NLS-1$
		diagnostics.assertNoNewDiagnostics();

		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@Test
	public void bestEffortRetainsUnrelatedInitializersWithoutReportingMixedLifecycle() throws CoreException {
		enableResourceLifecycleMigration();
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= independentInitializerSources(root);
		ICompilationUnit[] retained= pack.getCompilationUnits();
		ICompilationUnit independent= createIndependentRuleTest(pack);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		for (ICompilationUnit unit : retained) {
			String source= unit.getSource();
			assertFalse(source.contains("(MIXED_RULE_LIFECYCLE)"), source); //$NON-NLS-1$
			assertTrue(source.contains("(EXTERNAL_RESOURCE_INITIALIZER_UNSUPPORTED)"), source); //$NON-NLS-1$
			assertEquals(before.get(unit.getElementName()), withoutMarker(source), unit.getElementName());
		}
		assertIndependentRuleMigrated(pack, independent);
		diagnostics.assertNoNewDiagnostics();

		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@ParameterizedTest
	@ValueSource(strings= { "(new OtherFixture())", "OtherFixture.create()" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void strictModeRejectsSiblingFixtureLifecyclesWithoutEditingSources(String initializer) throws CoreException {
		enableResourceLifecycleMigration();
		IPackageFragment pack= mixedRulePackage();
		otherFixture(pack);
		addOtherFixtureRule(pack, initializer);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		CoreException failure= assertThrows(CoreException.class, () -> context.apply(units));
		assertEquals(RefactoringStatus.FATAL, failure.getStatus().getSeverity());
		assertTrue(failure.getMessage().contains("both @Rule and @ClassRule"), failure::getMessage); //$NON-NLS-1$
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@Test
	public void bestEffortPreservesMixedFixtureAndTestLifecyclesThroughApplyAndUndo() throws CoreException {
		enableResourceLifecycleMigration();
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		ICompilationUnit[] units= JUnitBestEffortSupportTest.inheritedMixedRuleSources(root);
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		Map<String, String> applied= sources(units);
		for (var entry : applied.entrySet()) {
			String name= entry.getKey();
			String source= entry.getValue();
			String expected= before.get(name);
			if ("IndependentTest.java".equals(name)) { //$NON-NLS-1$
				assertEquals(expected.replace("org.junit.Test", "org.junit.jupiter.api.Test"), source, name); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				assertTrue(source.contains("(MIXED_RULE_LIFECYCLE)"), source); //$NON-NLS-1$
				assertTrue(source.contains("separate instance and class extensions"), source); //$NON-NLS-1$
				if ("InheritedRuleTest.java".equals(name)) { //$NON-NLS-1$
					expected= expected.replace("org.junit.Assert.assertNotNull", //$NON-NLS-1$
							"org.junit.jupiter.api.Assertions.assertNotNull"); //$NON-NLS-1$
				}
				assertEquals(expected, withoutMarker(source), name);
			}
		}
		diagnostics.assertNoNewDiagnostics();

		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units), "One Undo must restore every fixture, test and dependent compilation unit"); //$NON-NLS-1$
		diagnostics.assertRestored();

		context.apply(units);
		assertEquals(applied, sources(units));
		context.apply(units);
		assertEquals(applied, sources(units), "A second best-effort Apply must be idempotent"); //$NON-NLS-1$
		diagnostics.assertNoNewDiagnostics();
	}

	@Test
	public void bestEffortRetainsInheritedTestAndLifecycleDeclarations() throws CoreException {
		enableResourceLifecycleMigration();
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= mixedRulePackage();
		ICompilationUnit testBase= compilationUnit(pack, "TestBase", """
				import org.junit.After;
				import org.junit.Before;
				import org.junit.Test;

				public abstract class TestBase {
					@Before public void prepareBase() {}
					@After public void finishBase() {}
					@Test public void inheritedTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit sibling= compilationUnit(pack, "SiblingTest", """
				import org.junit.Test;

				public class SiblingTest extends TestBase {
					@Test public void siblingTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit instanceOwner= pack.getCompilationUnit("InstanceUseTest.java"); //$NON-NLS-1$
		replaceSource(instanceOwner, "class InstanceUseTest {", "class InstanceUseTest extends TestBase {"); //$NON-NLS-1$ //$NON-NLS-2$

		assertRetainedSourcesAfterApply(pack, testBase, sibling);
	}

	@ParameterizedTest
	@CsvSource({
		"true, new OtherFixture(), true", //$NON-NLS-1$
		"true, (new OtherFixture()), false", //$NON-NLS-1$
		"true, OtherFixture.create(), false", //$NON-NLS-1$
		"false, (new OtherFixture()), false", //$NON-NLS-1$
		"false, OtherFixture.create(), false" //$NON-NLS-1$
	})
	public void bestEffortRetainsOtherFixtureAndConsumersSharingABlockedRuleOwner(
			boolean migrateStandaloneFixtures, String initializer, boolean includeAnonymousConsumer) throws CoreException {
		enableResourceLifecycleMigration(migrateStandaloneFixtures);
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= mixedRulePackage();
		ICompilationUnit otherFixture= otherFixture(pack);
		ICompilationUnit otherTest= compilationUnit(pack, "OtherUseTest", """
				import org.junit.Rule;
				import org.junit.Test;

				public class OtherUseTest {
					@Rule public OtherFixture other = new OtherFixture();
					@Test public void otherTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit inheritor= compilationUnit(pack, "OtherInheritedTest", """
				import org.junit.Test;

				public class OtherInheritedTest extends OtherUseTest {
					@Test public void inheritedOtherRuleTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		addOtherFixtureRule(pack, initializer);
		ICompilationUnit owner= pack.getCompilationUnit("InstanceUseTest.java"); //$NON-NLS-1$
		List<ICompilationUnit> retained= new ArrayList<>(List.of(owner, otherFixture, otherTest, inheritor));
		if (includeAnonymousConsumer) {
			retained.addAll(anonymousConsumers(pack));
		}
		assertRetainedSourcesAfterApply(pack, retained.toArray(ICompilationUnit[]::new));
	}

	private static List<ICompilationUnit> anonymousConsumers(IPackageFragment pack) throws CoreException {
		ICompilationUnit anonymousOwner= compilationUnit(pack, "OtherAnonymousUseTest", """
				import org.junit.Rule;
				import org.junit.Test;

				public class OtherAnonymousUseTest {
					@Rule public OtherFixture other = new OtherFixture() {};
					@Test public void anonymousRuleTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit anonymousInheritor= compilationUnit(pack, "OtherAnonymousInheritedTest", """
				import org.junit.Test;

				public class OtherAnonymousInheritedTest extends OtherAnonymousUseTest {
					@Test public void inheritedAnonymousRuleTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		return List.of(anonymousInheritor, anonymousOwner);
	}

	@ParameterizedTest
	@ValueSource(booleans= { true, false })
	public void bestEffortMigratesIndependentRuleAndFixtureInSeparateFiles(boolean migrateStandaloneFixtures) throws CoreException {
		enableResourceLifecycleMigration(migrateStandaloneFixtures);
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		IPackageFragment pack= mixedRulePackage();
		ICompilationUnit independent= createIndependentRuleTest(pack);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Map<String, String> before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);

		context.apply(units);
		assertIndependentRuleMigrated(pack, independent);

		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	private static ICompilationUnit createIndependentRuleTest(IPackageFragment pack) throws CoreException {
		compilationUnit(pack, "IndependentFixture", """
				import org.junit.rules.ExternalResource;

				public class IndependentFixture extends ExternalResource {
					@Override
					protected void before() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		return compilationUnit(pack, "IndependentRuleTest", """
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExternalResource;

				public class IndependentRuleTest {
					@Rule public ExternalResource independent = new IndependentFixture();
					@Test public void independentRuleTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void assertIndependentRuleMigrated(IPackageFragment pack, ICompilationUnit independent) throws CoreException {
		ICompilationUnit fixture= pack.getCompilationUnit("IndependentFixture.java"); //$NON-NLS-1$
		String independentSource= independent.getSource();
		assertTrue(independentSource.contains("import org.junit.jupiter.api.Test;"), independentSource); //$NON-NLS-1$
		assertTrue(independentSource.contains("@RegisterExtension"), independentSource); //$NON-NLS-1$
		assertTrue(independentSource.contains("public IndependentFixture independent"), independentSource); //$NON-NLS-1$
		assertTrue(fixture.getSource().contains("implements BeforeEachCallback"), fixture::getElementName); //$NON-NLS-1$
		assertFalse(independentSource.contains("MIXED_RULE_LIFECYCLE"), independentSource); //$NON-NLS-1$
	}

	private IPackageFragment mixedRulePackage() throws CoreException {
		JUnitBestEffortSupportTest.inheritedMixedRuleSources(root);
		return root.getPackageFragment("inheritedrules"); //$NON-NLS-1$
	}

	private void assertRetainedSourcesAfterApply(IPackageFragment pack, ICompilationUnit... retained) throws CoreException {
		Map<String, String> before= sources(retained);
		context.apply(pack.getCompilationUnits());
		for (ICompilationUnit unit : retained) {
			String source= unit.getSource();
			assertTrue(source.contains("(MIXED_RULE_LIFECYCLE)"), source); //$NON-NLS-1$
			assertEquals(before.get(unit.getElementName()), withoutMarker(source), unit.getElementName());
		}
	}

	private static void replaceSource(ICompilationUnit unit, String original, String replacement) throws CoreException {
		unit.getBuffer().setContents(unit.getSource().replace(original, replacement));
		unit.save(null, true);
	}

	private static String withoutMarker(String source) {
		return source.replaceFirst("(?s)/\\*\\*.*?\\*/\\s*", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void enableResourceLifecycleMigration() throws CoreException {
		enableResourceLifecycleMigration(true);
	}

	private void enableResourceLifecycleMigration(boolean migrateStandaloneFixtures) throws CoreException {
		if (migrateStandaloneFixtures) {
			context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		} else {
			context.disable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		}
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
	}

	private static Map<String, String> sources(ICompilationUnit[] units) throws CoreException {
		Map<String, String> sources= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			sources.put(unit.getElementName(), unit.getSource());
		}
		return sources;
	}

	private static final class ApplyContext extends AbstractEclipseJava {

		ApplyContext() {
			super("testresources/rtstubs_17.jar", JavaCore.VERSION_17); //$NON-NLS-1$
		}

		void apply(ICompilationUnit[] units) throws CoreException {
			var status= performRefactoring(units, null);
			assertFalse(status.hasError(), status::toString);
		}
	}

	private ICompilationUnit createSource() throws CoreException {
		// Dynamic rows remain outside the coordinated constant-row contract even
		// when the target runtime supports ParameterizedClass field injection.
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		return pack.createCompilationUnit("FieldInjectionTest.java", """
				package test;

				import static org.junit.Assert.assertEquals;

				import java.util.Arrays;
				import java.util.List;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameter;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class FieldInjectionTest {
					@Parameter
					public int value;

					@Parameters
					public static List<Object[]> data() {
						return Arrays.asList(new Object[][] { { Integer.getInteger("value", 1) } });
					}

					@Test
					public void verifiesValue() {
						assertEquals(1, value);
					}
				}
				""", false, null); //$NON-NLS-1$
	}
}
