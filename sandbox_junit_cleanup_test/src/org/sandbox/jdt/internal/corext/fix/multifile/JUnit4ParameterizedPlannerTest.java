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

import static org.junit.jupiter.api.Assertions.*;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit4ParameterizedPlan.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner.PlanningOptions;
import org.sandbox.jdt.triggerpattern.api.SemanticPlanRelation;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKind;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Source closure, injection order and stale-source contracts for issue #1367. */
public class JUnit4ParameterizedPlannerTest {

	private static final PlanningOptions OPTIONS= new PlanningOptions(false, false, true);
	private static final String IMPORTS= """
			package planned;
			import org.junit.Test;
			import org.junit.runner.RunWith;
			import org.junit.runners.Parameterized;
			import org.junit.runners.Parameterized.Parameters;
			import org.junit.runners.Parameterized.Parameter;
			""";
	private static final String CONSTRUCTOR= """
			public Sample(int value, String label) { }
			@Test public void first() { }
			@Test public void second() { }
			""";
	private static final String PROVIDER= """
			@Parameters(name="{index}: {0} / {1}")
			public static Object[][] data() { return new Object[][] { {1, "one"}, {2, "two"} }; }
			""";

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();
	private IPackageFragment pack;

	@BeforeEach
	public void setup() throws CoreException {
		pack= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH)
				.createPackageFragment("planned", true, null); //$NON-NLS-1$
	}

	@Test
	public void recordsConstructorParametersAndAllTestsWithoutDuplicatedNamesOrTypes() throws CoreException {
		ICompilationUnit unit= sample(CONSTRUCTOR + PROVIDER);
		Map<String, String> before= sources(unit);
		JUnit4ParameterizedPlan plan= prepared(unit);
		assertEquals(CONTRACT, plan.semanticPlan().contractId());
		NodeKey constructor= targets(plan, plan.testClass(), HAS_CONSTRUCTOR).get(0);
		List<NodeKey> parameters= targets(plan, constructor, HAS_PARAMETER);
		assertEquals(2, parameters.size());
		assertTrue(parameters.stream().allMatch(key -> key.kind() == NodeKind.PARAMETER));
		assertNotEquals(parameters.get(0), parameters.get(1));
		assertEquals(2, targets(plan, plan.testClass(), HAS_TEST).size());
		assertTrue(plan.semanticPlan().relations().stream().allMatch(relation -> relation.attributes().isEmpty()));
		assertTrue(parameters.stream().allMatch(key -> !plan.semanticPlan().valuesByNode().containsKey(key)));
		assertTrue(plan.isCurrent(before));
		assertEquals(before, sources(unit), "Discovery must never execute or rewrite source");
	}

	@Test
	public void discoversInheritedProviderAndEditableExternalDelegateDeterministically() throws CoreException {
		ICompilationUnit data= unit("Data", """
				public class Data {
					public static Object[][] rows() { return new Object[][] { {1, "one"} }; }
				}
				""");
		ICompilationUnit base= unit("Base", """
				public abstract class Base {
					@Parameters public static Object[][] data() { return Data.rows(); }
				}
				""");
		ICompilationUnit leaf= unit("Sample", "@RunWith(Parameterized.class) public class Sample extends Base {"
				+ CONSTRUCTOR + "}");
		JUnit4ParameterizedPlan plan= prepared(data, base, leaf);
		assertEquals(plan, prepared(leaf, base, data));
		NodeKey provider= targets(plan, plan.testClass(), HAS_PROVIDER).get(0);
		NodeKey delegate= targets(plan, provider, DELEGATES_TO).get(0);
		assertEquals(base.getHandleIdentifier(), plan.compilationUnits().get(provider));
		assertEquals(data.getHandleIdentifier(), plan.compilationUnits().get(delegate));
		assertEquals(sources(data, base, leaf).keySet(), plan.sourceFingerprints().keySet());
		assertThrows(UnsupportedOperationException.class, () -> plan.compilationUnits().clear());
	}

	@Test
	public void fieldRelationsFollowInjectionIndicesRatherThanDeclarationOrder() throws CoreException {
		JUnit4ParameterizedPlan plan= prepared(sample("""
				@Parameter(1) public String label;
				@Parameter public int value;
				@Test public void test() { }
				""" + PROVIDER));
		List<NodeKey> fields= targets(plan, plan.testClass(), HAS_FIELD);
		assertEquals(2, fields.size());
		assertTrue(fields.get(0).bindingKey().contains("value"));
		assertTrue(fields.get(1).bindingKey().contains("label"));
		assertTrue(targets(plan, plan.testClass(), HAS_CONSTRUCTOR).isEmpty());
	}

	@Test
	public void incompleteSelectionProducesNoPreparedPlan() throws CoreException {
		ICompilationUnit unit= sample(CONSTRUCTOR + PROVIDER);
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), new ICompilationUnit[] { unit }, OPTIONS, false, null);
		assertRejected(result, "PARAMETERIZED_INCOMPLETE_SCOPE");
	}

	@Test
	public void unselectedInheritedAndDelegatedSourcesFailClosed() throws CoreException {
		unit("Data", "public class Data { public static Object[][] rows() { return new Object[][] {{1, \"one\"}}; } }");
		ICompilationUnit base= unit("Base", "public abstract class Base {" + PROVIDER + "}");
		ICompilationUnit leaf= unit("Sample", "@RunWith(Parameterized.class) public class Sample extends Base {"
				+ CONSTRUCTOR + "}");
		assertRejected(plan(leaf), "PARAMETERIZED_HIERARCHY_OUTSIDE_SCOPE");
		base.getBuffer().setContents(IMPORTS + "public abstract class Base { @Parameters public static Object[][] data() { return Data.rows(); } }");
		assertRejected(plan(leaf, base), "PARAMETERIZED_PROVIDER_OUTSIDE_SCOPE");
	}

	@Test
	public void cyclesAndUnprovenConversionsNeverProducePartialPlans() throws CoreException {
		ICompilationUnit unit= sample(CONSTRUCTOR + """
				@Parameters public static Object[][] data() { return data(); }
				""");
		assertRejected(plan(unit), "PARAMETERIZED_PROVIDER_CYCLE");
		unit.getBuffer().setContents(IMPORTS + "@RunWith(Parameterized.class) public class Sample {"
				+ CONSTRUCTOR + PROVIDER.replace("{1, \"one\"}", "{\"1\", \"one\"}") + "}");
		assertRejected(plan(unit), "PARAMETERIZED_CONVERSION_UNPROVEN");
	}

	@Test
	public void duplicateIndicesGapsAndWrongArityAreRejected() throws CoreException {
		ICompilationUnit unit= sample("@Parameter public int value; @Parameter public String label; @Test public void test() { }"
				+ PROVIDER);
		assertRejected(plan(unit), "PARAMETERIZED_DUPLICATE_FIELD_INDEX");
		unit.getBuffer().setContents(unit.getSource().replace("@Parameter public String", "@Parameter(2) public String"));
		assertRejected(plan(unit), "PARAMETERIZED_FIELD_INDEX_GAP");
		unit.getBuffer().setContents(IMPORTS + "@RunWith(Parameterized.class) public class Sample {"
				+ CONSTRUCTOR + PROVIDER.replace("{1, \"one\"}", "{1}") + "}");
		assertRejected(plan(unit), "PARAMETERIZED_ROW_ARITY");
	}

	@Test
	public void customHooksAndCompilerErrorsAreVisible() throws CoreException {
		ICompilationUnit unit= sample(CONSTRUCTOR + PROVIDER
				+ "@org.junit.Rule public org.junit.rules.TestName name = new org.junit.rules.TestName();");
		assertRejected(plan(unit), "PARAMETERIZED_EXECUTION_HOOK_UNSUPPORTED");
		unit.getBuffer().setContents(IMPORTS + "@RunWith(Parameterized.class) public class Sample {"
				+ CONSTRUCTOR + PROVIDER + "MissingType missing; }");
		assertRejected(plan(unit), "PARAMETERIZED_SOURCE_ERRORS");
	}

	@Test
	public void snapshotsRejectBodyOnlyChangesAndChangedScopeMembership() throws CoreException {
		ICompilationUnit unit= sample(CONSTRUCTOR + PROVIDER);
		JUnit4ParameterizedPlan plan= prepared(unit);
		Map<String, String> snapshot= sources(unit);
		assertTrue(plan.isCurrent(snapshot));
		assertFalse(plan.isCurrent(Map.of()));
		snapshot.put("new-unit", "class Added { }");
		assertFalse(plan.isCurrent(snapshot));
		snapshot= sources(unit);
		snapshot.replaceAll((key, source) -> source.replace("{1, \"one\"}", "{3, \"one\"}"));
		assertFalse(plan.isCurrent(snapshot), "Binding keys alone cannot detect changed provider contents");
		assertThrows(UnsupportedOperationException.class, () -> plan.sourceFingerprints().clear());
	}

	@Test
	public void cancellationPropagatesInsteadOfBecomingARejection() throws CoreException {
		ICompilationUnit unit= sample(CONSTRUCTOR + PROVIDER);
		NullProgressMonitor monitor= new NullProgressMonitor();
		monitor.setCanceled(true);
		assertThrows(OperationCanceledException.class, () -> JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), new ICompilationUnit[] { unit }, OPTIONS, true, monitor));
	}

	private ICompilationUnit sample(String members) throws CoreException {
		return unit("Sample", "@RunWith(Parameterized.class) public class Sample {" + members + "}");
	}

	private ICompilationUnit unit(String name, String declaration) throws CoreException {
		return pack.createCompilationUnit(name + ".java", IMPORTS + declaration, false, null);
	}

	private MultiFileCleanUpPlanResult<JUnitMigrationPlan> plan(ICompilationUnit... units) throws CoreException {
		Map<String, String> before= sources(units);
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), units, OPTIONS, true, null);
		assertEquals(before, sources(units));
		assertFalse(result.status().hasFatalError(), result.status().toString());
		return result;
	}

	private JUnit4ParameterizedPlan prepared(ICompilationUnit... units) throws CoreException {
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= plan(units);
		assertEquals(1, result.plan().preparedParameterizedPlans().size(), result.diagnostics().toString());
		assertEquals(MultiFileCandidateOutcome.FOUND, result.diagnostics().candidates().get(0).outcome());
		assertFalse(result.plan().hasCoordinatedChanges(), "Source discovery cannot enable automatic execution");
		return result.plan().preparedParameterizedPlans().get(0);
	}

	private static void assertRejected(MultiFileCleanUpPlanResult<JUnitMigrationPlan> result, String code) {
		assertTrue(result.plan().preparedParameterizedPlans().isEmpty());
		assertFalse(result.plan().hasCoordinatedChanges());
		assertEquals(List.of(code), result.diagnostics().candidates().stream()
				.map(diagnostic -> diagnostic.reasonCode()).toList());
		assertEquals(MultiFileCandidateOutcome.REJECTED, result.diagnostics().candidates().get(0).outcome());
	}

	private static List<NodeKey> targets(JUnit4ParameterizedPlan plan, NodeKey owner, String relation) {
		return plan.semanticPlan().outgoing(owner, relation).stream().map(SemanticPlanRelation::target).toList();
	}

	private static Map<String, String> sources(ICompilationUnit... units) throws CoreException {
		Map<String, String> sources= new LinkedHashMap<>();
		for (ICompilationUnit unit : units) {
			sources.put(unit.getPrimary().getHandleIdentifier(), unit.getSource());
		}
		return sources;
	}
}
