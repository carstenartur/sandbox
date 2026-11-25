/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.ReportingJUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Native cleanup planning must retain contracts consumed by unselected projects. */
@SuppressWarnings("nls")
class ExternalResourceCallerScopeTest {

	@RegisterExtension
	final EclipseJava17 provider= new EclipseJava17();
	@RegisterExtension
	final EclipseJava17 caller= new EclipseJava17();
	@TempDir
	Path temporary;
	private IPackageFragment providers;
	private IPackageFragment callers;

	@BeforeEach
	void separateProjectsWithRealJUnitApis() throws Exception {
		providers= junitPackage(provider);
		callers= junitPackage(caller);
		AbstractEclipseJava.addToClasspath(caller.getJavaProject(),
				JavaCore.newProjectEntry(provider.getJavaProject().getPath()));
		assertTrue(Arrays.asList(provider.getJavaProject().getProject().getReferencingProjects())
				.contains(caller.getJavaProject().getProject()));
	}

	private static IPackageFragment junitPackage(EclipseJava17 context) throws Exception {
		var root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		return root.createPackageFragment("test", false, null);
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void preservesProviderForUnselectedAnonymousRule(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit consumer= inheritedCaller("Resource");
		List<String> changed= applyAndUndo(bestEffort, List.of(resource), List.of(resource, consumer));
		assertLegacyResource(changed.getFirst());
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void preservesRealBinaryFieldCallerWithSameNamedLambdaParameter(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit factory= providers.createCompilationUnit("Factory.java", """
				package test;
				public class Factory {
					public static final Resource resource = new Resource();
				}
				""", false, null);
		String binarySource= """
				package test;
				public class BinaryCaller {
					public static org.junit.rules.TestRule read() {
						java.util.function.Consumer<Object> consume = resource -> System.out.println(resource);
						consume.accept(Factory.resource);
						return Factory.resource;
					}
				}
				""";
		Path classes= Files.createDirectory(temporary.resolve("classes"));
		var arguments= new ArrayList<>(List.of("--release", "17", "-cp",
				Arrays.stream(provider.getJavaProject().getResolvedClasspath(true))
						.filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY)
						.map(entry -> entry.getPath().toFile()).filter(File::isFile)
						.map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator)),
				"-d", classes.toString()));
		for (ICompilationUnit unit : List.of(resource, factory)) {
			arguments.add(Files.writeString(temporary.resolve(unit.getElementName()), unit.getSource(),
					StandardCharsets.UTF_8).toString());
		}
		arguments.add(Files.writeString(temporary.resolve("BinaryCaller.java"), binarySource, StandardCharsets.UTF_8).toString());
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
		Path binaryJar= temporary.resolve("caller.jar");
		writeJar(binaryJar, "test/BinaryCaller.class", Files.readAllBytes(classes.resolve("test/BinaryCaller.class")));
		Path sourceJar= temporary.resolve("caller-sources.jar");
		writeJar(sourceJar, "test/BinaryCaller.java", binarySource.getBytes(StandardCharsets.UTF_8));
		AbstractEclipseJava.addToClasspath(caller.getJavaProject(), JavaCore.newLibraryEntry(
				org.eclipse.core.runtime.Path.fromOSString(binaryJar.toString()),
				org.eclipse.core.runtime.Path.fromOSString(sourceJar.toString()), null));
		var binaryType= caller.getJavaProject().findType("test.BinaryCaller");
		assertNotNull(binaryType);
		assertTrue(binaryType.isBinary());
		assertNotNull(binaryType.getClassFile().getBuffer(), "The actual binary must have its complete source attachment");
		ICompilationUnit consumer= callers.createCompilationUnit("UsesBinary.java", """
				package test;
				public class UsesBinary {
					public org.junit.rules.TestRule read() { return BinaryCaller.read(); }
				}
				""", false, null);
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, factory), List.of(resource, factory, consumer));
		assertLegacyResource(changed.getFirst());
	}

	private static void writeJar(Path path, String entry, byte[] bytes) throws Exception {
		try (var output= new JarOutputStream(Files.newOutputStream(path))) {
			output.putNextEntry(new JarEntry(entry));
			output.write(bytes);
			output.closeEntry();
		}
	}

	static Stream<Arguments> factoryCallbackCalls() {
		return Stream.of(false, true).flatMap(bestEffort -> Stream.of(
				Arguments.of(bestEffort, "Factory.resource().before();"),
				Arguments.of(bestEffort, "Throwing action = Factory.resource()::before; action.run();"),
				Arguments.of(bestEffort, "org.junit.rules.TestRule rule = Factory.resource(); System.out.println(rule);"),
				Arguments.of(bestEffort, "org.junit.rules.TestRule rule = Factory.RESOURCE; System.out.println(rule);"),
				Arguments.of(bestEffort, "org.junit.rules.TestRule rule = Factory.resourceArray()[0]; System.out.println(rule);"),
				Arguments.of(bestEffort, "org.junit.rules.TestRule rule = Factory.resourceList().get(0); System.out.println(rule);")));
	}

	@ParameterizedTest
	@MethodSource("factoryCallbackCalls")
	void preservesCallbacksReferencedWithoutProviderTypeName(boolean bestEffort, String invocation) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit factory= providers.createCompilationUnit("Factory.java", """
				package test;
				public class Factory {
					public static final Resource RESOURCE = new Resource();
					public static Resource resource() { return new Resource(); }
					public static Resource[] resourceArray() { return new Resource[]{new Resource()}; }
					public static java.util.List<Resource> resourceList() { return java.util.List.of(new Resource()); }
				}
				""", false, null);
		ICompilationUnit consumer= callers.createCompilationUnit("Consumer.java", """
				package test;
				public class Consumer {
					interface Throwing { void run() throws Throwable; }
					public void example() throws Throwable { $invocation }
				}
				""".replace("$invocation", invocation), false, null);
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, factory), List.of(resource, factory, consumer));
		assertLegacyResource(changed.getFirst());
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void preservesNewCallerBeforeItsNextAutomaticBuild(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit factory= providers.createCompilationUnit("Factory.java", """
				package test;
				public class Factory {
					public static final Resource RESOURCE = new Resource();
				}
				""", false, null);
		ICompilationUnit seed= callers.createCompilationUnit("Seed.java", """
				package test;
				public class Seed {}
				""", false, null);
		var workspace= ResourcesPlugin.getWorkspace();
		var description= workspace.getDescription();
		boolean originalAutoBuilding= description.isAutoBuilding();
		try {
			if (!originalAutoBuilding) {
				description.setAutoBuilding(true);
				workspace.setDescription(description);
			}
			var buildRule= workspace.getRuleFactory().buildRule();
			var monitor= new NullProgressMonitor();
			Job.getJobManager().beginRule(buildRule, monitor);
			try {
				provider.getJavaProject().getProject().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
				caller.getJavaProject().getProject().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
				// The completed caller build contains only Seed. Indexing may proceed,
				// but an automatic build cannot publish the new Consumer's references yet.
				ICompilationUnit consumer= callers.createCompilationUnit("Consumer.java", """
						package test;
						public class Consumer {
							public void example() {
								org.junit.rules.TestRule rule = Factory.RESOURCE;
								System.out.println(rule);
							}
						}
						""", false, null);
				List<String> changed= applyAndUndo(bestEffort, List.of(resource, factory),
						List.of(resource, factory, seed, consumer));
				assertLegacyResource(changed.getFirst());
			} finally {
				Job.getJobManager().endRule(buildRule);
			}
		} finally {
			if (!originalAutoBuilding) {
				description= workspace.getDescription();
				description.setAutoBuilding(false);
				workspace.setDescription(description);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void preservesSelectedRuleConsumerButMigratesIndependentTest(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit local= localRule();
		ICompilationUnit consumer= inheritedCaller("Resource");
		ICompilationUnit independent= providers.createCompilationUnit("Independent.java", """
				package test;
				import org.junit.Test;
				public class Independent {
					@Test public void example() { System.out.println(1); }
				}
				""", false, null);
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, local, independent),
				List.of(resource, local, independent, consumer));
		assertLegacyResource(changed.get(0));
		assertTrue(changed.get(1).contains("@Rule"), changed.toString());
		assertTrue(changed.get(1).contains("import org.junit.Test;"), changed.toString());
		assertFalse(changed.get(1).contains("@RegisterExtension"), changed.toString());
		assertTrue(changed.get(2).contains("import org.junit.jupiter.api.Test;"), changed.toString());
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void preservesResourceExposedByAnUnselectedRecordAccessor(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit holder= recordHolder();
		ICompilationUnit consumer= callers.createCompilationUnit("Consumer.java", """
				package test;
				public class Consumer {
					public void example(Holder holder) {
						org.junit.rules.TestRule rule = holder.resource();
						System.out.println(rule);
					}
				}
				""", false, null);
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, holder), List.of(resource, holder, consumer));
		assertLegacyResource(changed.getFirst());
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void migratesAClosedRecordAccessorContract(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit holder= recordHolder();
		ICompilationUnit local= providers.createCompilationUnit("LocalTest.java", """
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				public class LocalTest {
					@Rule public Resource resource = new Resource();
					@Test public void example() { System.out.println(new Holder(resource).resource().getProject()); }
				}
				""", false, null);
		ICompilationUnit unrelated= callers.createCompilationUnit("Unrelated.java", """
				package test;
				public class Unrelated { public int value() { return 1; } }
				""", false, null);
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, holder, local),
				List.of(resource, holder, local, unrelated));
		assertTrue(changed.getFirst().contains("beforeEach("), changed.toString());
		assertFalse(changed.getFirst().contains("extends ExternalResource"), changed.toString());
		assertTrue(changed.get(2).contains("@RegisterExtension"), changed.toString());
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void preservesSelectedSuperclassChainUsedByUnselectedCaller(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit child= providers.createCompilationUnit("ChildResource.java", """
				package test;
				public class ChildResource extends Resource {
					@Override protected void before() throws Throwable { super.before(); }
					@Override protected void after() { super.after(); }
				}
				""", false, null);
		ICompilationUnit consumer= inheritedCaller("ChildResource");
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, child), List.of(resource, child, consumer));
		assertLegacyResource(changed.get(0));
		assertTrue(changed.get(1).contains("protected void before()"), changed.toString());
		assertTrue(changed.get(1).contains("super.before();"), changed.toString());
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void migratesClosedResourceComponentWithUnrelatedDependentProject(boolean bestEffort) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit local= localRule();
		ICompilationUnit support= providers.createCompilationUnit("Unrelated.java", """
				package test;
				public class Unrelated { public static int value() { return 1; } }
				""", false, null);
		ICompilationUnit consumer= callers.createCompilationUnit("Consumer.java", """
				package test;
				public class Consumer { public int value() { return Unrelated.value(); } }
				""", false, null);
		List<String> changed= applyAndUndo(bestEffort, List.of(resource, local, support),
				List.of(resource, local, support, consumer));
		assertTrue(changed.get(0).contains("beforeEach("), changed.toString());
		assertTrue(changed.get(0).contains("afterEach("), changed.toString());
		assertFalse(changed.get(0).contains("extends ExternalResource"), changed.toString());
		assertTrue(changed.get(1).contains("@RegisterExtension"), changed.toString());
		assertTrue(changed.get(1).contains("import org.junit.jupiter.api.Test;"), changed.toString());
	}

	@ParameterizedTest
	@ValueSource(strings= { "strict", "best-effort", "junit6", "test-without-rule-rewrite" })
	void retainsOwnersOfUnselectedRuleFixtures(String profile) throws Exception {
		ICompilationUnit resource= resource();
		ICompilationUnit child= providers.createCompilationUnit("ChildResource.java",
				"package test; public class ChildResource extends Resource {}", false, null);
		String ownerSource= """
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import static org.junit.Assert.assertNotNull;
				public class Owner {
					@Rule public Resource value = new Resource();
					@Test public void runsFixture() { assertNotNull(value.getProject()); }
				}
				""";
		ICompilationUnit owner= callers.createCompilationUnit("Owner.java", ownerSource, false, null);
		String peerSource= ownerSource.replace("class Owner", "class Peer")
				.replace("@Test public void", "@Rule public ChildResource child = new ChildResource();\n\t@Test public void")
				.replace("assertNotNull(value.getProject());", "assertNotNull(value.getProject()); assertNotNull(child.getProject());");
		ICompilationUnit peer= callers.createCompilationUnit("Peer.java", peerSource, false, null);
		ICompilationUnit unrelatedBase= providers.createCompilationUnit("OtherBase.java", """
				package test;
				public abstract class OtherBase {
					public @interface Rule {}
					@Rule public Object field;
					@Rule public void ordinaryMethod() {}
				}
				""", false, null);
		ICompilationUnit independent= callers.createCompilationUnit("Independent.java", """
				package test;
				import org.junit.Test;
				public class Independent extends OtherBase {
					@Test public void runnable() { System.out.println(getClass()); }
				}
				""", false, null);
		var selected= new ArrayList<>(List.of(owner, peer, independent));
		var affected= new ArrayList<>(List.of(resource, child, unrelatedBase, owner, peer, independent));
		var retained= new LinkedHashMap<ICompilationUnit, String>();
		retained.put(owner, ownerSource);
		retained.put(peer, peerSource);
		for (var fixture : List.of(
				new InheritedFixture("Concrete", "@org.junit.Rule public Resource value = new Resource();", "value.getProject()"),
				new InheritedFixture("Folder", "@Rule public org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();", "tmp.getRoot()"),
				new InheritedFixture("ErasedField", "public Resource value = new Resource(); @org.junit.Rule public org.junit.rules.TestRule rule = value;", "value.getProject()"),
				new InheritedFixture("Method", "public Resource value = new Resource(); @org.junit.Rule public Resource rule() { return value; }", "value.getProject()"),
				new InheritedFixture("ErasedMethod", "public static Resource value = new Resource(); @org.junit.ClassRule public static org.junit.rules.TestRule rule() { return value; }", "value.getProject()"))) {
			String baseName= fixture.name() + "Base";
			String declaration= fixture.declaration();
			String imports= declaration.startsWith("@Rule ") ? "import org.junit.Rule; " : "";
			affected.add(providers.createCompilationUnit(baseName + ".java",
					"package test; " + imports + "public abstract class " + baseName + " { " + declaration + " }", false, null));
			String source= "package test; public class " + fixture.name() + " extends " + baseName
					+ " { @org.junit.Test public void initialized() { org.junit.Assert.assertNotNull(" + fixture.observation() + "); } }";
			var unit= callers.createCompilationUnit(fixture.name() + ".java", source, false, null);
			selected.add(unit);
			affected.add(unit);
			retained.put(unit, source);
		}
		applyAndUndo(profile.equals("best-effort"), selected, affected,
				Map.of(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.TRUE,
						MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, Boolean.toString(!profile.equals("test-without-rule-rewrite")),
						MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, Boolean.toString(!profile.equals("test-without-rule-rewrite")),
						"cleanup.junitcleanup_6_compatibility", Boolean.toString(profile.equals("junit6"))),
				cleanup -> {
					var names= selected.stream().map(unit -> unit.findPrimaryType().getFullyQualifiedName()).toList();
					var expected= names.stream().map(name -> (name.equals("test.Independent") ? "junit-jupiter " : "junit-vintage ")
							+ name + " SUCCESSFUL").toList();
					assertEquals(expected, engineResults(names));
					for (var entry : retained.entrySet()) assertRetainedOwner(entry.getValue(), entry.getKey().getSource(), profile);
					assertTrue(independent.getSource().contains("import org.junit.jupiter.api.Test;"), independent.getSource());
					assertTrue(cleanup.getLastPlanningDiagnosticsJson(caller.getJavaProject())
							.contains("EXTERNAL_RESOURCE_FIXTURE_OUT_OF_SCOPE"));
				});
	}

	private record InheritedFixture(String name, String declaration, String observation) {}

	private static void assertRetainedOwner(String original, String applied, String profile) {
		if (profile.equals("best-effort")) {
			assertTrue(applied.contains("(EXTERNAL_RESOURCE_FIXTURE_OUT_OF_SCOPE)"), applied);
			applied= applied.replaceFirst("(?s)/\\*\\*.*?\\*/\\s*", "");
		}
		assertEquals(original, applied);
	}

	private List<String> engineResults(List<String> names) throws Exception {
		var classpath= new ArrayList<String>();
		for (var project : List.of(provider.getJavaProject(), caller.getJavaProject())) {
			project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
			classpath.add(ResourcesPlugin.getWorkspace().getRoot().findMember(project.getOutputLocation()).getLocation().toOSString());
		}
		for (var entry : caller.getJavaProject().getResolvedClasspath(true)) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) classpath.add(entry.getPath().toOSString());
		}
		Path probe= temporary.resolve("Observe.java");
		Files.writeString(probe, """
				import org.junit.platform.launcher.*;
				import org.junit.platform.launcher.core.*;
				import org.junit.platform.engine.*;
				import org.junit.platform.engine.discovery.DiscoverySelectors;
				import org.junit.platform.engine.support.descriptor.MethodSource;
				class Observe implements TestExecutionListener {
					public void executionFinished(TestIdentifier id, TestExecutionResult result) {
						if (id.isTest() && id.getSource().orElse(null) instanceof MethodSource method)
							System.out.println("TEST " + id.getUniqueIdObject().getEngineId().orElseThrow()
								+ " " + method.getClassName() + " " + result.getStatus());
					}
					public static void main(String[] args) {
						var launcher = LauncherFactory.create();
						for (String name : args) launcher.execute(LauncherDiscoveryRequestBuilder.request()
							.selectors(DiscoverySelectors.selectClass(name)).build(), new Observe());
					}
				}
				""", StandardCharsets.UTF_8);
		Path output= temporary.resolve("engine.log");
		var command= new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"--class-path", String.join(File.pathSeparator, classpath), probe.toString()));
		command.addAll(names);
		var process= new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		try {
			assertTrue(process.waitFor(60, TimeUnit.SECONDS));
			assertEquals(0, process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
		} finally {
			if (process.isAlive()) process.destroyForcibly();
		}
		return Files.readAllLines(output, StandardCharsets.UTF_8).stream().filter(line -> line.startsWith("TEST "))
				.map(line -> line.substring("TEST ".length())).toList();
	}

	private ICompilationUnit resource() throws Exception {
		return providers.createCompilationUnit("Resource.java", """
				package test;
				import org.junit.rules.ExternalResource;
				public class Resource extends ExternalResource {
					private Object project;
					@Override protected void before() throws Throwable { project = new Object(); }
					@Override protected void after() { project = null; }
					public Object getProject() { return project; }
				}
				""", false, null);
	}

	private ICompilationUnit recordHolder() throws Exception {
		return providers.createCompilationUnit("Holder.java", """
				package test;
				public record Holder(Resource resource) {}
				""", false, null);
	}

	private ICompilationUnit inheritedCaller(String baseName) throws Exception {
		return callers.createCompilationUnit("Consumer.java", """
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				public class Consumer {
					private Object observed;
					@Rule public $base resource = new $base() {
						@Override protected void before() throws Throwable {
							super.before();
							observed = getProject();
						}
					};
					@Test public void example() { System.out.println(observed); }
				}
				""".replace("$base", baseName), false, null);
	}

	private ICompilationUnit localRule() throws Exception {
		return providers.createCompilationUnit("LocalTest.java", """
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				public class LocalTest {
					@Rule public Resource resource = new Resource();
					@Test public void example() { System.out.println(resource.getProject()); }
				}
				""", false, null);
	}

	private static void assertLegacyResource(String source) {
		assertTrue(source.contains("extends ExternalResource"), source);
		assertTrue(source.contains("protected void before()"), source);
		assertTrue(source.contains("protected void after()"), source);
	}

	private List<String> applyAndUndo(boolean bestEffort, List<ICompilationUnit> selected,
			List<ICompilationUnit> affected) throws Exception {
		return applyAndUndo(bestEffort, selected, affected, Map.of(), cleanup -> {});
	}

	@FunctionalInterface
	private interface AppliedCheck {
		void verify(ReportingJUnitCleanUpCore cleanup) throws Exception;
	}

	private List<String> applyAndUndo(boolean bestEffort, List<ICompilationUnit> selected,
			List<ICompilationUnit> affected, Map<String, String> additionalOptions, AppliedCheck afterApply) throws Exception {
		Map<ICompilationUnit, String> originals= new LinkedHashMap<>();
		for (ICompilationUnit unit : affected) originals.put(unit, unit.getSource());
		var providerOptions= provider.getJavaProject().getOptions(false);
		var callerOptions= caller.getJavaProject().getOptions(false);
		var diagnostics= CompilerDiagnosticAssertions.capture(affected.toArray(ICompilationUnit[]::new));
		for (ICompilationUnit unit : affected) {
			assertTrue(Arrays.stream(parse(unit).getProblems()).noneMatch(IProblem::isError), unit.getSource());
		}
		var options= new LinkedHashMap<>(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, CleanUpOptions.TRUE,
				"cleanup.junitcleanup_best_effort", Boolean.toString(bestEffort)));
		options.putAll(additionalOptions);
		var cleanup= new ReportingJUnitCleanUpCore(options);
		CompositeChange change= new CompositeChange("ExternalResource caller scope");
		var monitor= new NullProgressMonitor();
		try {
			// Native JDT cleanup provides the selected project to this lifecycle;
			// optional workbench scope expansion is not required by ICleanUp.
			var status= cleanup.checkPreConditions(selected.getFirst().getJavaProject(), selected.toArray(ICompilationUnit[]::new), null);
			assertFalse(status.hasFatalError(), status.toString());
			for (ICompilationUnit unit : selected) {
				var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
				if (fix != null) change.add(fix.createChange(null));
			}
			change.initializeValidationData(monitor);
			var undo= change.perform(monitor);
			assertNotNull(undo);
			try {
				diagnostics.assertNoNewDiagnostics();
				for (ICompilationUnit unit : affected) {
					if (!selected.contains(unit)) assertEquals(originals.get(unit), unit.getSource());
				}
				afterApply.verify(cleanup);
				List<String> changed= new ArrayList<>();
				for (ICompilationUnit unit : selected) changed.add(unit.getSource());
				return changed;
			} finally {
				var redo= undo.perform(monitor);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
			cleanup.checkPostConditions(null);
			for (var entry : originals.entrySet()) assertEquals(entry.getValue(), entry.getKey().getSource());
			assertEquals(providerOptions, provider.getJavaProject().getOptions(false));
			assertEquals(callerOptions, caller.getJavaProject().getOptions(false));
			diagnostics.assertRestored();
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
