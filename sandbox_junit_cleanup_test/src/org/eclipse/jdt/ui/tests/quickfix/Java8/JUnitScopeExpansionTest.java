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
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

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
