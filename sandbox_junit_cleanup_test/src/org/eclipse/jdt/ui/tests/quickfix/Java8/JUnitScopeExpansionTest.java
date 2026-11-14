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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.MultiFileCleanUpLifecycleAssertions;

/** Tests candidate-gated scope expansion for coordinated JUnit migration. */
public class JUnitScopeExpansionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void localOnlyOptionDoesNotExpandScope() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= createUnit(pack, "SelectedTest.java"); //$NON-NLS-1$
		createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		JUnitCleanUpCore cleanup= assertionCleanup();

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);

		assertTrue(expanded.isEmpty(), "A local assertion migration must retain the user's target scope");
	}

	@Test
	public void assertionHelperAddsItsDeclarationAndCompleteSourceCallerClosure() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit helper= pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
				package test;

				final class Checks {
					static void equal(String message, int expected, int actual) {
						org.junit.Assert.assertEquals(message, expected, actual);
					}
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				public class SelectedTest {
					void selected() { Checks.equal("selected", 1, 1); }
				}
				""", false, null);
		ICompilationUnit secondCaller= pack.createCompilationUnit("SecondTest.java", //$NON-NLS-1$
				"""
				package test;

				public class SecondTest {
					void second() { Checks.equal("second", 2, 2); }
				}
				""", false, null);
		ICompilationUnit unrelated= createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		JUnitCleanUpCore cleanup= assertionCleanup();

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);
		Set<String> expandedHandles= expanded.stream().map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());

		assertEquals(Set.of(selected.getHandleIdentifier(), helper.getHandleIdentifier(),
				secondCaller.getHandleIdentifier()), expandedHandles,
				"A selected helper caller must expand to the helper declaration and every editable source caller");
		assertTrue(!expanded.contains(unrelated));
		assertTrue(cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected, helper, secondCaller), null).isEmpty(),
				"The second expansion pass must recognize the complete helper closure");
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	public void selectedCallerReachesBooleanAssertionThroughStaticForwarders(boolean methodReference)
			throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		String leafSource= """
				package test;
				final class Leaf {
					static void assertFlagAtTerminal(boolean ready) { org.junit.Assert.assertTrue("expected", ready); }
				}
				""";
		ICompilationUnit leaf= pack.createCompilationUnit("Leaf.java", leafSource, false, null); //$NON-NLS-1$
		ICompilationUnit bridge= pack.createCompilationUnit("Bridge.java", """
				package test;
				final class Bridge {
					static void verifyDelegatedFlag(boolean ready) { forwardFlagInternally(ready); }
					private static void forwardFlagInternally(boolean ready) { Leaf.assertFlagAtTerminal(ready); }
				}
				""", false, null);
		String selectedBody= methodReference ? "java.util.function.Consumer<Boolean> check = Bridge::verifyDelegatedFlag;" //$NON-NLS-1$
				: "void run() { Bridge.verifyDelegatedFlag(true); }"; //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("Selected.java", //$NON-NLS-1$
				"package test; public class Selected { " + selectedBody + " }", false, null); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit other= pack.createCompilationUnit("Other.java", """
				package test;
				final class Other { void run() { Leaf.assertFlagAtTerminal(false); } }
				""", false, null);
		createUnit(pack, "Unrelated.java"); //$NON-NLS-1$

		JUnitCleanUpCore cleanup= assertionCleanup();
		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);
		assertEquals(Set.of(selected, bridge, leaf, other), Set.copyOf(expanded));
		assertTrue(cleanup.expandCleanUpScope(selected.getJavaProject(), expanded, null).isEmpty());
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(expanded.toArray(ICompilationUnit[]::new),
				new ICompilationUnit[] { leaf, bridge, selected, other }, new String[] {
						leafSource.replace("org.junit.Assert.assertTrue(\"expected\", ready)", //$NON-NLS-1$
								"org.junit.jupiter.api.Assertions.assertTrue(ready, \"expected\")"), //$NON-NLS-1$
						bridge.getSource(), selected.getSource(), other.getSource() });
	}

	@ParameterizedTest
	@ValueSource(strings = { "private", "" })
	public void onlyPrivateInstanceDispatchCanExtendAnAssertionChain(String access) throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		String leafSource= """
				package test;
				final class Leaf {
					static void checkPrivateForwarderResult(boolean ready) { org.junit.Assert.assertFalse(ready); }
				}
				""";
		ICompilationUnit leaf= pack.createCompilationUnit("Leaf.java", leafSource, false, null); //$NON-NLS-1$
		ICompilationUnit bridge= pack.createCompilationUnit("Bridge.java", """
				package test;
				class Bridge {
					static void verifyViaBoundReceiver(Bridge receiver, boolean ready) { receiver.delegatePrivateFixtureFlag(ready); }
					ACCESS void delegatePrivateFixtureFlag(boolean ready) { Leaf.checkPrivateForwarderResult(ready); }
				}
				""".replace("ACCESS", access), false, null); //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("Selected.java", """
				package test;
				final class Selected { void run() { Bridge.verifyViaBoundReceiver(new Bridge(), false); } }
				""", false, null);

		Collection<ICompilationUnit> expanded= assertionCleanup().expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);
		if (access.isEmpty()) {
			assertTrue(expanded.isEmpty(), "A package-private instance method can dispatch to an override");
			return;
		}
		assertEquals(Set.of(selected, bridge, leaf), Set.copyOf(expanded));
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(expanded.toArray(ICompilationUnit[]::new),
				new ICompilationUnit[] { leaf, bridge, selected }, new String[] {
						leafSource.replace("org.junit.Assert", "org.junit.jupiter.api.Assertions"), //$NON-NLS-1$ //$NON-NLS-2$
						bridge.getSource(), selected.getSource() });
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bridge.verify(ready)", "Missing.unknown(ready)", "Leaf.check(ready, 1)" })
	public void unprovenForwarderDoesNotAdmitAnySource(String delegate) throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Leaf.java", """
				package test;
				final class Leaf {
					static void check(boolean ready) { org.junit.Assert.assertTrue(ready); }
					public static void check(boolean ready, int n) { org.junit.Assert.assertTrue(ready); }
				}
				""", false, null);
		pack.createCompilationUnit("Bridge.java", """
				package test;
				final class Bridge { static void verify(boolean ready) { CALL; } }
				""".replace("CALL", delegate), false, null);
		ICompilationUnit selected= pack.createCompilationUnit("Selected.java", """
				package test;
				final class Selected { void run() { Bridge.verify(true); } }
				""", false, null);

		assertTrue(assertionCleanup().expandCleanUpScope(selected.getJavaProject(), List.of(selected), null).isEmpty());
	}

	@Test
	public void unsafeCallerOfIntermediateForwarderRejectsTheWholePath() throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Leaf.java", """
				package test;
				final class Leaf {
					static void assertFlagAtTerminal(boolean ready) { org.junit.Assert.assertFalse(ready); }
				}
				""", false, null);
		pack.createCompilationUnit("Bridge.java", """
				package test;
				final class Bridge {
					static void verifyDelegatedFlag(boolean ready) { Leaf.assertFlagAtTerminal(ready); }
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("Selected.java", """
				package test;
				final class Selected { void run() { Bridge.verifyDelegatedFlag(true); } }
				""", false, null);
		pack.createCompilationUnit("Other.java", """
				package test;
				final class Other {
					int count;
					void run() {
						Bridge.verifyDelegatedFlag(false);
						org.junit.Assert.assertTrue("count " + count++, count == 1);
					}
				}
				""", false, null);

		assertTrue(assertionCleanup().expandCleanUpScope(selected.getJavaProject(), List.of(selected), null).isEmpty());
	}

	@Test
	public void assertionHelperMethodReferenceAddsItsDeclarationAndOtherCallers() throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit helper= pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
				package test;

				final class Checks {
					static void verifyConditionWithMessage(String message, boolean condition) {
						org.junit.Assert.assertTrue(message, condition);
					}
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.function.BiConsumer;

				public class SelectedTest {
					BiConsumer<String, Boolean> check = Checks::verifyConditionWithMessage;
				}
				""", false, null);
		ICompilationUnit secondCaller= pack.createCompilationUnit("SecondTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.function.BiConsumer;

				public class SecondTest {
					BiConsumer<String, Boolean> check = Checks::verifyConditionWithMessage;
				}
				""", false, null);
		createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		JUnitCleanUpCore cleanup= assertionCleanup();

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);

		assertEquals(Set.of(selected, helper, secondCaller), Set.copyOf(expanded));
		assertTrue(cleanup.expandCleanUpScope(selected.getJavaProject(), expanded, null).isEmpty());
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				expanded.toArray(ICompilationUnit[]::new), new ICompilationUnit[] { helper, selected, secondCaller },
				new String[] {
						"""
						package test;

						final class Checks {
							static void verifyConditionWithMessage(String message, boolean condition) {
								org.junit.jupiter.api.Assertions.assertTrue(condition, message);
							}
						}
						""", selected.getSource(), secondCaller.getSource() });
	}

	@Test
	public void assertionHelperMethodReferenceRejectsUnsafeAdditionalCaller() throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
				package test;

				final class Checks {
					static void verifyClosureFixture(boolean ready) { org.junit.Assert.assertTrue(ready); }
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.function.Consumer;

				public class SelectedTest {
					Consumer<Boolean> verify = Checks::verifyClosureFixture;
				}
				""", false, null);
		pack.createCompilationUnit("SecondTest.java", //$NON-NLS-1$
				"""
				package test;

				public class SecondTest {
					private int calls;

					void verify() {
						Checks.verifyClosureFixture(true);
						org.junit.Assert.assertTrue("before " + calls++, calls == 1);
					}
				}
				""", false, null);

		assertTrue(assertionCleanup().expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null).isEmpty());
	}

	@Test
	public void methodReferenceToNonJUnitOverloadDoesNotAddTheHelper() throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
				package test;

				final class Checks {
					static void verifyExpectedValue(String value) { org.junit.Assert.assertNotNull(value); }
					static void verifyExpectedValue(int value) { if (value < 0) throw new IllegalArgumentException(); }
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.function.IntConsumer;

				public class SelectedTest {
					IntConsumer check = Checks::verifyExpectedValue;
				}
				""", false, null);

		assertTrue(assertionCleanup().expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null).isEmpty());
	}

	@Test
	public void methodReferenceDoesNotExpandScopeWhenAssumptionMigrationIsEnabled() throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
				package test;

				final class Checks {
					static void requireAvailableFixture(boolean ready) { org.junit.Assume.assumeTrue(ready); }
					static void verifyAvailableFixture(boolean ready) { org.junit.Assert.assertTrue(ready); }
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.function.Consumer;
				public class SelectedTest {
					Consumer<Boolean> require = Checks::requireAvailableFixture;
					Consumer<Boolean> verify = Checks::verifyAvailableFixture;
				}
				""", false, null);
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, CleanUpOptions.TRUE));

		assertTrue(cleanup.expandCleanUpScope(selected.getJavaProject(), List.of(selected), null).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings= { "nextMessage()", "message", "(String) value", "\"\" + ++calls" })
	public void methodReferenceDoesNotAddAssertionWithAnUnprovenMessageRead(String expression)
			throws CoreException {
		addJupiter();
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
				package test;

				final class Checks {
					private static int calls;
					private static String message = "fixture";
					private static String nextMessage() { calls++; return message; }
					static void verifyFixtureWithMessage(Object value, boolean ready) {
						org.junit.Assert.assertTrue(MESSAGE_EXPRESSION, ready);
					}
				}
				""".replace("MESSAGE_EXPRESSION", expression), false, null); //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.function.BiConsumer;
				public class SelectedTest {
					BiConsumer<Object, Boolean> verify = Checks::verifyFixtureWithMessage;
				}
				""", false, null);

		assertTrue(assertionCleanup().expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null).isEmpty());
	}

	@Test
	public void assertionHelperMethodReferenceDoesNotExpandScopeWithoutJupiter() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		pack.createCompilationUnit("Checks.java", //$NON-NLS-1$
				"""
			package test;

			final class Checks {
					static void verifyLegacyFixture(boolean ready) { org.junit.Assert.assertTrue(ready); }
				}
				""", false, null);
		ICompilationUnit selected= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
			package test;

			import java.util.function.Consumer;

			public class SelectedTest {
					Consumer<Boolean> verify = Checks::verifyLegacyFixture;
				}
				""", false, null);

		assertNull(selected.getJavaProject().findType("org.junit.jupiter.api.Assertions")); //$NON-NLS-1$
		assertTrue(assertionCleanup().expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null).isEmpty());
	}

	@Test
	public void externalResourceOptionWithoutCandidateDoesNotExpandScope() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= createUnit(pack, "SelectedTest.java"); //$NON-NLS-1$
		createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$

		Collection<ICompilationUnit> expanded= externalResourceCleanup().expandCleanUpScope(
				selected.getJavaProject(), List.of(selected), null);

		assertTrue(expanded.isEmpty(),
				"The coordinated option must not scan the project without a resource or rule candidate in scope");
	}

	@Test
	public void selectedExternalResourceFindsOnlyRequiredRuleUser() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() {
					}
				}
				""", false, null);
		ICompilationUnit related= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Rule;

				public class SelectedTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);
		ICompilationUnit unrelated= createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$

		Collection<ICompilationUnit> expanded= externalResourceCleanup().expandCleanUpScope(
				selected.getJavaProject(), List.of(selected), null);
		Set<String> expandedHandles= expanded.stream().map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());

		assertEquals(Set.of(selected.getHandleIdentifier(), related.getHandleIdentifier()), expandedHandles,
				"The exact closure must add the Rule user without broadening to unrelated source");
		assertTrue(!expanded.contains(unrelated));
	}

	@ParameterizedTest
	@ValueSource(strings = { "TestName", "TemporaryFolder" })
	public void localDedicatedRuleDoesNotBlockRequiredSourceClosure(String ruleType) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null);
		ICompilationUnit resource= ruleUnit(pack, "SharedResource",
				"extends org.junit.rules.ExternalResource", "");
		ICompilationUnit user= ruleUnit(pack, "RuleUser", "",
				"@org.junit.Rule public SharedResource resource = new SharedResource();");
		ICompilationUnit local= ruleUnit(pack, "LocalRule", "",
				"@org.junit.Rule public org.junit.rules." + ruleType + " rule = new org.junit.rules." + ruleType + "();");
		ICompilationUnit unrelated= createUnit(pack, "Unrelated.java");
		ReportedCleanup cleanup= new ReportedCleanup();

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(user.getJavaProject(), List.of(user, local), null);
		assertEquals(Set.of(resource, user, local), Set.copyOf(expanded),
				"A local Rule must not prevent admission of the required source fixture");
		assertFalse(expanded.contains(unrelated));
		cleanup.checkPreConditions(user.getJavaProject(), expanded.toArray(ICompilationUnit[]::new), null);
		MultiFileCleanUpDiagnostics diagnostics= cleanup.diagnostics(user);
		assertTrue(diagnostics.scope().complete(), diagnostics.toJson());
		assertTrue(diagnostics.candidates().stream().anyMatch(candidate ->
				candidate.outcome() == MultiFileCandidateOutcome.TRANSFORMED
						&& candidate.candidateId().equals("external-resource:test.SharedResource")), diagnostics.toJson());
	}

	@ParameterizedTest
	@ValueSource(strings = { "factory", "anonymous", "different-subtype", "mixed-fragments" })
	public void unprovedDedicatedRuleInitializerRetainsConservativeScope(String variant) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null);
		ICompilationUnit resource= ruleUnit(pack, "SharedResource", "extends org.junit.rules.ExternalResource", "");
		ICompilationUnit user= ruleUnit(pack, "RuleUser", "",
				"@org.junit.Rule public SharedResource resource = new SharedResource();");
		String body= switch (variant) {
			case "factory" -> "@org.junit.Rule public org.junit.rules.TestName rule = make(); "
					+ "static org.junit.rules.TestName make() { return new org.junit.rules.TestName(); }";
			case "anonymous" -> "@org.junit.Rule public org.junit.rules.TestName rule = new org.junit.rules.TestName() {};";
			case "different-subtype" -> "@org.junit.Rule public org.junit.rules.TemporaryFolder rule = new CustomFolder(); "
					+ "static class CustomFolder extends org.junit.rules.TemporaryFolder {}";
			default -> "@org.junit.Rule public org.junit.rules.TestName first = new org.junit.rules.TestName(), second = make(); "
					+ "static org.junit.rules.TestName make() { return new org.junit.rules.TestName(); }";
		};
		ICompilationUnit local= ruleUnit(pack, "UnprovedRule", "", body);
		ReportedCleanup cleanup= new ReportedCleanup();
		List<ICompilationUnit> units= List.of(resource, user, local);

		assertTrue(cleanup.expandCleanUpScope(user.getJavaProject(), units, null).isEmpty());
		cleanup.checkPreConditions(user.getJavaProject(), units.toArray(ICompilationUnit[]::new), null);
		assertFalse(cleanup.diagnostics(user).scope().complete(), cleanup.diagnostics(user).toJson());
		assertFalse(cleanup.diagnostics(user).candidates().stream().anyMatch(candidate ->
				candidate.outcome() == MultiFileCandidateOutcome.TRANSFORMED));
	}

	@Test
	public void binaryIntermediateExternalResourceRemainsOutsideEditableClosure() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null);
		ICompilationUnit resource= ruleUnit(pack, "CustomFolder", "extends org.junit.rules.TemporaryFolder", "");
		ICompilationUnit user= ruleUnit(pack, "RuleUser", "",
				"@org.junit.Rule public CustomFolder resource = new CustomFolder();");
		ReportedCleanup cleanup= new ReportedCleanup();
		ICompilationUnit[] units= { resource, user };

		assertTrue(cleanup.expandCleanUpScope(user.getJavaProject(), List.of(units), null).isEmpty());
		cleanup.checkPreConditions(user.getJavaProject(), units, null);
		assertFalse(cleanup.diagnostics(user).scope().complete(), cleanup.diagnostics(user).toJson());
	}

	private static ICompilationUnit ruleUnit(IPackageFragment pack, String name, String superclass, String body)
			throws CoreException {
		return pack.createCompilationUnit(name + ".java", "package test; public class " + name + " "
				+ superclass + " { " + body + " }", false, null);
	}

	private static final class ReportedCleanup extends JUnitCleanUpCore {
		ReportedCleanup() {
			super(Map.of(MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
					MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE));
		}

		MultiFileCleanUpDiagnostics diagnostics(ICompilationUnit unit) {
			return getPlanningDiagnostics(unit.getJavaProject());
		}
	}

	private static JUnitCleanUpCore assertionCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.TRUE));
	}

	private void addJupiter() throws CoreException {
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
	}

	private static JUnitCleanUpCore externalResourceCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE));
	}

	private static ICompilationUnit createUnit(IPackageFragment pack, String name) throws CoreException {
		String source= "package test;%n%npublic class %s {%n}%n" //$NON-NLS-1$
				.formatted(name.substring(0, name.length() - ".java".length())); //$NON-NLS-1$
		return pack.createCompilationUnit(name, source, false, null);
	}
}
