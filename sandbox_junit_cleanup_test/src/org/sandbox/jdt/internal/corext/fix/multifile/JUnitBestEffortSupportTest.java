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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
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

import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Analysis;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Gap;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression tests for explicit non-atomic JUnit migration evidence. */
public class JUnitBestEffortSupportTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void reportsParameterizedFieldInjectionWithActionableRemediation() throws CoreException {
		ICompilationUnit unit= compilationUnit("fieldinjection", "FieldInjectionTest", """
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
						return List.<Object[]>of(new Object[] { 1 });
					}

					@Test
					public void verifiesValue() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.PARAMETERIZED), unit);

		assertEquals(1, analysis.gaps().size());
		Gap gap= analysis.gaps().get(0);
		assertEquals("PARAMETERIZED_FIELD_INJECTION", gap.reasonCode()); //$NON-NLS-1$
		assertTrue(gap.explanation().contains("field injection")); //$NON-NLS-1$
		assertTrue(gap.remediation().contains("Jupiter method arguments")); //$NON-NLS-1$
		assertTrue(analysis.toJson("{\"scope\":{}}") //$NON-NLS-1$
				.contains("\"manualCompletionRequired\":true")); //$NON-NLS-1$
	}

	@Test
	public void reportsCustomRunnerWithoutGuessingAnExtension() throws CoreException {
		ICompilationUnit runner= compilationUnit("customrunner", "ProjectRunner", """
				import org.junit.runner.Description;
				import org.junit.runner.Runner;
				import org.junit.runner.notification.RunNotifier;

				public class ProjectRunner extends Runner {
					@Override
					public Description getDescription() {
						return Description.EMPTY;
					}

					@Override
					public void run(RunNotifier notifier) {
					}
				}
				"""); //$NON-NLS-1$
		ICompilationUnit test= compilationUnit("customrunner", "RunnerTest", """
				import org.junit.Test;
				import org.junit.runner.RunWith;

				@RunWith(ProjectRunner.class)
				public class RunnerTest {
					@Test
					public void testSomething() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RUNWITH), runner, test);

		assertEquals(1, analysis.gaps().size());
		Gap gap= analysis.gaps().get(0);
		assertEquals("CUSTOM_JUNIT4_RUNNER", gap.reasonCode()); //$NON-NLS-1$
		assertTrue(gap.explanation().contains("customrunner.ProjectRunner")); //$NON-NLS-1$
		assertTrue(gap.remediation().contains("Jupiter extensions")); //$NON-NLS-1$
	}

	@Test
	public void mixedRuleLifecycleDefersOnlyCoordinatedExternalResourcePlanning() throws CoreException {
		ICompilationUnit resource= compilationUnit("mixedlifecycle", "SharedResource", """
				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() {
					}
				}
				"""); //$NON-NLS-1$
		ICompilationUnit instanceUse= compilationUnit("mixedlifecycle", "InstanceUseTest", """
				import org.junit.Rule;
				import org.junit.Test;

				public class InstanceUseTest {
					@Rule
					public SharedResource resource = new SharedResource();

					@Test
					public void instanceTest() {
					}
				}
				"""); //$NON-NLS-1$
		ICompilationUnit classUse= compilationUnit("mixedlifecycle", "ClassUseTest", """
				import org.junit.ClassRule;
				import org.junit.Test;

				public class ClassUseTest {
					@ClassRule
					public static SharedResource resource = new SharedResource();

					@Test
					public void classTest() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE,
				JUnitCleanUpFixCore.EXTERNALRESOURCE), resource, instanceUse, classUse);

		assertTrue(analysis.disableCoordinatedExternalResource());
		assertMixedGaps(analysis, resource, instanceUse, classUse);
	}

	@Test
	public void mixedSiblingFixturesQuarantineTheirSourceHierarchyAndInheritedRuleConsumers() throws CoreException {
		ICompilationUnit[] units= inheritedMixedRuleSources(root);
		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE,
				JUnitCleanUpFixCore.EXTERNALRESOURCE), units);

		assertTrue(analysis.disableCoordinatedExternalResource(), analysis::toString);
		assertMixedGaps(analysis, units[0], units[1], units[2], units[3], units[4], units[5], units[6]);
		assertTrue(analysis.gapsFor(units[7]).isEmpty(), analysis::toString);
	}

	@ParameterizedTest
	@ValueSource(strings= { "(new OtherFixture())", "OtherFixture.create()" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void mixedRuleRetainsSecondFixtureIdentifiedByInitializerType(String initializer) throws CoreException {
		inheritedMixedRuleSources(root);
		IPackageFragment pack= root.getPackageFragment("inheritedrules"); //$NON-NLS-1$
		ICompilationUnit otherFixture= otherFixture(pack);
		addOtherFixtureRule(pack, initializer);

		EnumSet<JUnitCleanUpFixCore> fixes= EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE,
				JUnitCleanUpFixCore.EXTERNALRESOURCE);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Analysis analysis= analyze(fixes, units);

		assertTrue(analysis.gapsFor(otherFixture).stream()
				.anyMatch(gap -> "MIXED_RULE_LIFECYCLE".equals(gap.reasonCode())), analysis::toString); //$NON-NLS-1$
		Analysis strict= JUnitBestEffortSupport.analyze(context.getJavaProject(), units, fixes, false, null);
		assertTrue(strict.gapsFor(otherFixture).isEmpty(), strict::toString);
	}

	@ParameterizedTest
	@ValueSource(strings= { "(new TemporaryFolder())", "newFolder()" }) //$NON-NLS-1$ //$NON-NLS-2$
	public void binaryResourceInitializerKeepsUnsupportedInterfaceRuleGap(String initializer) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("interfacerule", true, null); //$NON-NLS-1$
		ICompilationUnit owner= interfaceRuleTest(pack, initializer);

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE,
				JUnitCleanUpFixCore.TEST), owner);

		assertEquals(1, analysis.gapsFor(owner).size(), analysis::toString);
		assertEquals("UNSUPPORTED_JUNIT4_RULE", analysis.gapsFor(owner).getFirst().reasonCode()); //$NON-NLS-1$
		assertFalse(analysis.disableCoordinatedExternalResource(), analysis::toString);
	}

	@Test
	public void factoryClassRuleContributesLifecycleEvidenceForItsSourceFixture() throws CoreException {
		IPackageFragment pack= factoryMixedFixtureSources(root);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE, JUnitCleanUpFixCore.TEST), units);

		assertMixedGaps(analysis, units);
		assertTrue(analysis.gapsFor(pack.getCompilationUnit("ClassUseTest.java")).stream() //$NON-NLS-1$
				.anyMatch(gap -> "UNSUPPORTED_JUNIT4_RULE".equals(gap.reasonCode())), analysis::toString); //$NON-NLS-1$
	}

	@Test
	public void binaryDeclarationsDoNotMixUnrelatedSourceInitializerFixtures() throws CoreException {
		IPackageFragment pack= independentInitializerSources(root);
		ICompilationUnit[] units= pack.getCompilationUnits();
		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE, JUnitCleanUpFixCore.TEST), units);

		assertTrue(analysis.gaps().stream().noneMatch(gap -> "MIXED_RULE_LIFECYCLE".equals(gap.reasonCode())), //$NON-NLS-1$
				analysis::toString);
		for (ICompilationUnit unit : units) {
			assertTrue(analysis.gapsFor(unit).stream()
					.anyMatch(gap -> "EXTERNAL_RESOURCE_INITIALIZER_UNSUPPORTED".equals(gap.reasonCode())), analysis::toString); //$NON-NLS-1$
		}
	}

	static IPackageFragment factoryMixedFixtureSources(IPackageFragmentRoot root) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("mixedfactory", true, null); //$NON-NLS-1$
		initializerFixture(pack, "Fixture"); //$NON-NLS-1$
		compilationUnit(pack, "InstanceUseTest", """
				public class InstanceUseTest {
					@org.junit.Rule public Fixture rule = new Fixture();
					@org.junit.Test public void instanceTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		compilationUnit(pack, "ClassUseTest", """
				public class ClassUseTest {
					@org.junit.ClassRule public static org.junit.rules.TestRule rule = Fixture.create();
					@org.junit.Test public void classTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		return pack;
	}

	static IPackageFragment independentInitializerSources(IPackageFragmentRoot root) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("separateinitializers", true, null); //$NON-NLS-1$
		initializerFixture(pack, "OneFixture"); //$NON-NLS-1$
		initializerFixture(pack, "TwoFixture"); //$NON-NLS-1$
		compilationUnit(pack, "InstanceUseTest", """
				public class InstanceUseTest {
					@org.junit.Rule public org.junit.rules.ExternalResource rule = (new OneFixture());
					@org.junit.Test public void instanceTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		compilationUnit(pack, "ClassUseTest", """
				public class ClassUseTest {
					@org.junit.ClassRule public static org.junit.rules.ExternalResource rule = TwoFixture.create();
					@org.junit.Test public void classTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		compilationUnit(pack, "OtherInstanceUseTest", """
				public class OtherInstanceUseTest {
					@org.junit.Rule public OneFixture rule = new OneFixture();
					@org.junit.Test public void otherInstanceTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
		return pack;
	}

	private static void initializerFixture(IPackageFragment pack, String name) throws CoreException {
		compilationUnit(pack, name, """
				public class FixtureName extends org.junit.rules.ExternalResource {
					public static FixtureName create() {
						return new FixtureName();
					}

					@Override
					protected void before() {}
				}
				""".replace("FixtureName", name)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	static ICompilationUnit interfaceRuleTest(IPackageFragment pack, String initializer) throws CoreException {
		return compilationUnit(pack, "InterfaceRuleTest", """
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				import org.junit.rules.TestRule;

				public class InterfaceRuleTest {
					@Rule public TestRule folder = $initializer;

					public static TemporaryFolder newFolder() {
						return new TemporaryFolder();
					}

					@Test public void ruleTest() {}
				}
				""".replace("$initializer", initializer)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	static ICompilationUnit otherFixture(IPackageFragment pack) throws CoreException {
		return compilationUnit(pack, "OtherFixture", """
				import org.junit.rules.ExternalResource;

				public class OtherFixture extends ExternalResource {
					public static OtherFixture create() {
						return new OtherFixture();
					}

					@Override
					protected void before() throws Throwable {
						super.before();
					}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	static void addOtherFixtureRule(IPackageFragment pack, String initializer) throws CoreException {
		ICompilationUnit owner= pack.getCompilationUnit("InstanceUseTest.java"); //$NON-NLS-1$
		String field= "@Rule public org.junit.rules.ExternalResource other = " + initializer + ";\n\t"; //$NON-NLS-1$ //$NON-NLS-2$
		owner.getBuffer().setContents(owner.getSource().replace("@Before public", field + "@Before public")); //$NON-NLS-1$ //$NON-NLS-2$
		owner.save(null, true);
	}

	@Test
	public void binaryExternalResourceDoesNotLinkIndependentSourceHierarchies() throws CoreException {
		ICompilationUnit instanceFixture= compilationUnit("separate", "InstanceFixture", """
				public class InstanceFixture extends org.junit.rules.ExternalResource {
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ICompilationUnit classFixture= compilationUnit("separate", "ClassFixture", """
				public class ClassFixture extends org.junit.rules.ExternalResource {
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ICompilationUnit instanceUse= compilationUnit("separate", "InstanceUseTest", """
				public class InstanceUseTest {
					@org.junit.Rule public InstanceFixture fixture = new InstanceFixture();
					@org.junit.Test public void instanceTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ICompilationUnit classUse= compilationUnit("separate", "ClassUseTest", """
				public class ClassUseTest {
					@org.junit.ClassRule public static ClassFixture fixture = new ClassFixture();
					@org.junit.Test public void classTest() {}
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE,
				JUnitCleanUpFixCore.EXTERNALRESOURCE), instanceFixture, classFixture, instanceUse, classUse);

		assertFalse(analysis.disableCoordinatedExternalResource(), analysis::toString);
		assertTrue(analysis.gaps().isEmpty(), analysis::toString);
	}

	@Test
	public void sameBinaryFixtureKeepsItsEstablishedMixedLifecycleProtection() throws CoreException {
		ICompilationUnit instanceUse= compilationUnit("binarylifecycle", "InstanceUseTest", """
				import org.junit.Rule;
				import org.junit.rules.TemporaryFolder;

				public class InstanceUseTest {
					@Rule public TemporaryFolder folder = new TemporaryFolder();
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ICompilationUnit classUse= compilationUnit("binarylifecycle", "ClassUseTest", """
				import org.junit.ClassRule;
				import org.junit.rules.TemporaryFolder;

				public class ClassUseTest {
					@ClassRule public static TemporaryFolder folder = new TemporaryFolder();
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE), instanceUse, classUse);

		assertTrue(analysis.disableCoordinatedExternalResource(), analysis::toString);
		assertMixedGaps(analysis, instanceUse, classUse);
	}

	private static void assertMixedGaps(Analysis analysis, ICompilationUnit... affectedUnits) {
		var mixed= analysis.gaps().stream().filter(gap -> "MIXED_RULE_LIFECYCLE".equals(gap.reasonCode())).toList(); //$NON-NLS-1$
		assertEquals(affectedUnits.length, mixed.size(), analysis::toString);
		Set<String> markedTypes= mixed.stream().map(Gap::targetTypeName).collect(Collectors.toSet());
		for (ICompilationUnit unit : affectedUnits) {
			assertTrue(markedTypes.contains(unit.findPrimaryType().getFullyQualifiedName()), unit::getElementName);
			assertTrue(analysis.gapsFor(unit).stream().anyMatch(mixed::contains), unit::getElementName);
		}
		assertTrue(mixed.stream().allMatch(gap -> gap.remediation().contains("separate instance and class extensions"))); //$NON-NLS-1$
	}

	static ICompilationUnit[] inheritedMixedRuleSources(IPackageFragmentRoot root) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("inheritedrules", true, null); //$NON-NLS-1$
		return new ICompilationUnit[] {
				compilationUnit(pack, "SharedResource", """
						import org.junit.rules.ExternalResource;

						public class SharedResource extends ExternalResource {
							@Override
							protected void before() throws Throwable {
								super.before();
							}

							@Override
							protected void after() {
								super.after();
							}
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "InstanceFixture", """
						public class InstanceFixture extends SharedResource {
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "ClassFixture", """
						public class ClassFixture extends SharedResource {
							@Override
							protected void after() {
								super.after();
							}
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "SpecializedFixture", """
						public class SpecializedFixture extends InstanceFixture {
							@Override
							protected void before() throws Throwable {
								super.before();
							}
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "InstanceUseTest", """
						import org.junit.Before;
						import org.junit.Rule;
						import org.junit.Test;

						public class InstanceUseTest {
							@Rule public InstanceFixture fixture = new InstanceFixture();
							@Before public void prepare() {}
							@Test public void instanceTest() {}
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "ClassUseTest", """
						import org.junit.AfterClass;
						import org.junit.ClassRule;
						import org.junit.Test;

						public class ClassUseTest {
							@ClassRule public static ClassFixture fixture = new ClassFixture();
							@AfterClass public static void finish() {}
							@Test public void classTest() {}
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "InheritedRuleTest", """
						import static org.junit.Assert.assertNotNull;

						import org.junit.After;
						import org.junit.Test;

						public class InheritedRuleTest extends InstanceUseTest {
							@After public void release() {}
							@Test public void inheritedRuleTest() { assertNotNull(fixture); }
						}
						"""), //$NON-NLS-1$ //$NON-NLS-2$
				compilationUnit(pack, "IndependentTest", """
						import org.junit.Test;

						public class IndependentTest {
							@Test public void independentTest() {}
						}
						""") //$NON-NLS-1$ //$NON-NLS-2$
		};
	}

	@Test
	public void supportedParameterizedContractProducesNoManualGap() throws CoreException {
		ICompilationUnit unit= compilationUnit("supported", "SupportedTest", """
				import java.util.Arrays;
				import java.util.List;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class SupportedTest {
					private final int value;

					public SupportedTest(int value) {
						this.value = value;
					}

					@Parameters
					public static List<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 } });
					}

					@Test
					public void testValue() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.PARAMETERIZED), unit);

		assertTrue(analysis.gaps().isEmpty());
		assertFalse(analysis.disableCoordinatedExternalResource());
		assertTrue(analysis.toJson("").contains("\"manualCompletionRequired\":false")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private Analysis analyze(EnumSet<JUnitCleanUpFixCore> fixes, ICompilationUnit... units) throws CoreException {
		return JUnitBestEffortSupport.analyze(context.getJavaProject(), units, fixes, null);
	}

	private ICompilationUnit compilationUnit(String packageName, String typeName, String body)
			throws CoreException {
		return compilationUnit(root.createPackageFragment(packageName, true, null), typeName, body);
	}

	static ICompilationUnit compilationUnit(IPackageFragment pack, String typeName, String body)
			throws CoreException {
		return pack.createCompilationUnit(typeName + ".java", //$NON-NLS-1$
				"package " + pack.getElementName() + ";\n\n" + body, false, null); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
