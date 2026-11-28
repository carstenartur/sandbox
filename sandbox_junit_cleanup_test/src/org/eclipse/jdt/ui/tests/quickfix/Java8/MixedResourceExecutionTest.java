/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

class MixedResourceExecutionTest {

	@RegisterExtension
	final ParameterizedCoordinatedExecutionTest.Context context= new ParameterizedCoordinatedExecutionTest.Context();
	@TempDir Path temporary;

	@Test
	void appliesPerUseAdaptersAndPreservesVintage() throws Exception {
		var pack= setup(true);
		var project= context.getJavaProject();
		ICompilationUnit fixture= unit(pack, "Resource", """
				public class Resource extends org.junit.rules.ExternalResource {
					public static final Object fixtureExtension = new Object();
					public final String name;
					public Resource(String name) { this.name = name; }
					@Override public void before() { event("before:" + name); if (name.equals("fail")) throw new AssertionError(name); }
					@Override public void after() { event("after:" + name); }
					public static void event(String value) {
						try { java.nio.file.Files.writeString(java.nio.file.Path.of("$trace"), value + "\\n",
							java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
						catch (java.io.IOException e) { throw new AssertionError(e); }
					}
				}
				""".replace("$trace", temporary.resolve("trace").toString().replace("\\", "\\\\")));
		ICompilationUnit instance= unit(pack, "InstanceTest", """
				import static mixed.Resource.*;
				import org.junit.Rule;
				import org.junit.Test;
				public class InstanceTest {
					@Rule public Resource fixture;
					public int receiver = 1;
					public InstanceTest() { fixture = new Resource("selected"); }
					@Test public void exercise() {
						if (this.receiver != 1 || fixtureExtension != Resource.fixtureExtension) throw new AssertionError("shadowed static import");
						event("instance");
						fixture = new Resource("other");
					}
				}
				""");
		ICompilationUnit shared= unit(pack, "ClassTest", """
				import org.junit.ClassRule;
				public class ClassTest {
					@ClassRule public static Resource fixture = new Resource("class");
					public ClassTest() { Resource.event("construct:class"); }
					@org.junit.Test public void aa() { Resource.event("aa"); }
					@org.junit.Test public void z() { Resource.event("z"); }
				}
				""");
		ICompilationUnit vintageBase= unit(pack, "VintageBase", """
				public abstract class VintageBase {
					@org.junit.Before public void setUp() { Resource.event("vintageSetup"); }
					@org.junit.After public void tearDown() { Resource.event("vintageTeardown"); }
				}
				""");
		ICompilationUnit retained= unit(pack, "VintageTest", """
				public class VintageTest extends VintageBase {
					@org.junit.Rule public Resource fixture = new Resource("retained");
					@org.junit.Rule public org.junit.rules.TestName name = new org.junit.rules.TestName();
					@org.junit.Test public void exercise() { Resource.event(name.getMethodName()); }
				}
				""");
		ICompilationUnit failed= unit(pack, "FailedTest", """
				public class FailedTest {
					@org.junit.Rule public Resource receiver = new Resource("fail");
					@org.junit.Test public void exercise() { Resource.event("unreachable"); }
				}
				""");
		var opaque= unit(pack, "OpaqueTest", """
				public class OpaqueTest {
					@org.junit.Rule public org.junit.rules.ExternalResource receiver;
					public OpaqueTest() { this.receiver = new Resource("opaque"); }
					@org.junit.Test public void exercise() { Resource.event("opaque"); }
				}
				""");
		addSuiteClasspath();
		var suite= unit(pack, "PlatformSuite", """
				@org.junit.platform.suite.api.Suite
				@org.junit.platform.suite.api.SelectClasses({ClassTest.class})
				public class PlatformSuite {}
				""");
		ICompilationUnit[] units= { fixture, instance, shared, retained, failed, vintageBase, suite, opaque };
		List<ICompilationUnit> owners= List.of(instance, shared, retained, failed, opaque);
		List<String> original= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		var planning= JUnitMultiFilePlanner.create(project, units, true, true, null);
		assertFalse(planning.status().hasFatalError(), planning.diagnostics().toString());
		Observation suiteBaseline= run(List.of(suite));
		assertEquals(List.of("mixed.ClassTest#z=SUCCESSFUL", "mixed.ClassTest#aa=SUCCESSFUL"),
				suiteBaseline.tests().stream().map(TestResult::outcome).toList());
		Observation baseline= run(owners);
		assertEquals(List.of("before:selected", "instance", "after:selected", "before:class", "construct:class", "z", "construct:class", "aa", "after:class",
				"before:retained", "vintageSetup", "exercise", "vintageTeardown", "after:retained", "before:fail", "before:opaque", "opaque", "after:opaque"), baseline.trace());
		assertEquals(List.of("mixed.InstanceTest#exercise=SUCCESSFUL", "mixed.ClassTest#z=SUCCESSFUL", "mixed.ClassTest#aa=SUCCESSFUL",
				"mixed.VintageTest#exercise=SUCCESSFUL", "mixed.FailedTest#exercise=FAILED", "mixed.OpaqueTest#exercise=SUCCESSFUL"),
				baseline.tests().stream().map(TestResult::outcome).toList());
		assertTrue(baseline.tests().stream().allMatch(test -> test.engine().equals("junit-vintage")), baseline.toString());
		context.apply(units);
		assertTrue(instance.getSource().contains("perTest("), instance.getSource());
		assertTrue(shared.getSource().contains("perClass("), shared.getSource());
		assertEquals(original.get(0), fixture.getSource());
		assertEquals(original.get(3), retained.getSource());
		assertEquals(original.get(5), vintageBase.getSource());
		assertEquals(original.get(7), opaque.getSource());
		Observation suiteMigrated= run(List.of(suite));
		assertEquals(suiteBaseline.trace(), suiteMigrated.trace());
		assertEquals(suiteBaseline.tests().stream().map(TestResult::outcome).toList(),
				suiteMigrated.tests().stream().map(TestResult::outcome).toList());
		Observation migrated= run(owners);
		assertEquals(baseline.trace(), migrated.trace());
		assertEquals(baseline.tests().stream().map(TestResult::outcome).toList(),
				migrated.tests().stream().map(TestResult::outcome).toList());
		assertEquals(List.of("junit-jupiter", "junit-jupiter", "junit-jupiter", "junit-vintage", "junit-jupiter", "junit-vintage"),
				migrated.tests().stream().map(TestResult::engine).toList());
		diagnostics.assertNoNewDiagnostics();
		List<String> converted= sources(units);
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(original, sources(units));
		diagnostics.assertRestored();
		context.apply(units);
		assertEquals(converted, sources(units));
		context.apply(units);
		assertEquals(converted, sources(units));
	}

	private void addSuiteClasspath() throws Exception {
		var project= context.getJavaProject();
		for (String name : List.of("junit-platform-suite-api", "junit-platform-suite-commons", "junit-platform-suite-engine")) {
			var bundle= Arrays.stream(Platform.getBundles(name, null)).filter(candidate -> candidate.getVersion().getMajor() == 1)
					.findFirst().orElseThrow();
			var jar= FileLocator.getBundleFileLocation(bundle).orElseThrow();
			AbstractEclipseJava.addToClasspath(project, JavaCore.newLibraryEntry(
					org.eclipse.core.runtime.Path.fromOSString(jar.getAbsolutePath()), null, null));
		}
	}

	private IPackageFragment setup(boolean withProvider) throws Exception {
		var project= context.getJavaProject();
		var pack= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH)
				.createPackageFragment("mixed", false, null);
		project.setRawClasspath(Arrays.stream(project.getRawClasspath())
				.map(entry -> entry.getPath().lastSegment().startsWith("rtstubs_") ? JavaRuntime.getDefaultJREContainerEntry() : entry)
				.toArray(org.eclipse.jdt.core.IClasspathEntry[]::new), null);
		if (withProvider) {
			var support= FileLocator.getBundleFileLocation(Platform.getBundle("org.sandbox.junit.migration.support")).orElseThrow();
			AbstractEclipseJava.addToClasspath(project, JavaCore.newLibraryEntry(
				org.eclipse.core.runtime.Path.fromOSString(support.getAbsolutePath()), null, null));
		}
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		return pack;
	}

	private List<ICompilationUnit> simpleFixture(IPackageFragment pack, String hooks, String peer) throws Exception {
		var resource= unit(pack, "Resource", "public class Resource extends org.junit.rules.ExternalResource { " + hooks + " }");
		String body= """
				public class InstanceTest {
					@org.junit.Rule public Resource fixture = new Resource();
					$peer
					@org.junit.Test public void example() {}
				}
				""".replace("$peer", peer);
		return List.of(resource, unit(pack, "InstanceTest", body), unit(pack, "ClassTest",
				body.replace("InstanceTest", "ClassTest").replace("@org.junit.Rule public Resource", "@org.junit.ClassRule public static Resource")));
	}

	@ParameterizedTest
	@ValueSource(strings= { "absent", "same-name-source", "old-bundle", "rule-peers", "custom-apply", "overloaded-hook",
			"parenthesized-writes", "wrong-static-field", "manual-registration", "vintage-callers", "vintage-suite", "filtered-platform", "self-discovery", "previous-disabled", "previous-invalid-test", "previous-object", "two-opaque-factories" })
	void rejectsUnprovedComponentsAtomically(String shape) throws Exception {
		var pack= setup(!List.of("absent", "same-name-source", "old-bundle").contains(shape));
		String hooks= "@Override public void before() {} @Override public void after() {}";
		if (shape.equals("overloaded-hook")) hooks= "public void before(Object... unrelated) {} @Override public void after() {}";
		if (shape.equals("custom-apply")) hooks+= """
				@Override public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base,
						org.junit.runner.Description description) { return base; }
				""";
		var selected= new ArrayList<>(simpleFixture(pack, hooks, (List.of("rule-peers", "manual-registration").contains(shape) || shape.startsWith("previous-"))
				? "@org.junit.Rule public org.junit.rules.TestName peer = new org.junit.rules.TestName();" : ""));
		switch (shape) {
			case "parenthesized-writes" -> {
				selected.add(unit(pack, "CustomApply", """
						public class CustomApply extends Resource {
							@Override public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base,
									org.junit.runner.Description description) { return base; }
						}
						"""));
				replace(selected.get(1), "@org.junit.Test", "public InstanceTest() { (this.fixture) = new CustomApply(); } @org.junit.Test");
				selected.add(unit(pack, "Writer", "public class Writer { void write() { (ClassTest.fixture) = new CustomApply(); } }"));
			}
			case "wrong-static-field" -> {
				replace(selected.get(1), "@org.junit.Test", "@org.junit.Rule public org.junit.rules.TestName name = new org.junit.rules.TestName(); @org.junit.Test");
				selected.add(unit(pack, "Holder", "public class Holder { public Resource fixture = new Resource(); }"));
				replace(selected.get(2), "@org.junit.ClassRule", "public static final Holder ClassTest = new Holder(); @org.junit.ClassRule");
			}
			case "manual-registration" -> selected.add(unit(pack, "Manual", """
					@org.junit.jupiter.api.parallel.Isolated
					@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD)
					public class Manual {
						@org.junit.jupiter.api.Test public void exists() {}
						final Resource fixture = new Resource();
						@org.junit.jupiter.api.extension.RegisterExtension
						final org.junit.jupiter.api.extension.Extension extension = org.sandbox.junit.jupiter.ExternalResourceExtension
								.perTest(() -> this.fixture, r -> {}, r -> {});
					}
					"""));
			case "vintage-callers" -> selected.add(unit(pack, "LegacyMain", """
					public class LegacyMain {
						public static int failures() {
							return org.junit.runner.JUnitCore.runClasses(InstanceTest.class, ClassTest.class).getFailureCount();
						}
					}
					"""));
			case "previous-disabled", "previous-invalid-test", "previous-object" -> {
				String previous= """
						@org.junit.jupiter.api.parallel.Isolated
						@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD)
						public class Previous {
							public Resource fixture = new Resource();
							@org.junit.jupiter.api.extension.RegisterExtension
							final org.junit.jupiter.api.extension.Extension extension = org.sandbox.junit.jupiter.ExternalResourceExtension
									.perTest(() -> this.fixture, r -> r.before(), r -> r.after());
							@org.junit.jupiter.api.Test public void runnable() {}
						}
						""";
				if (shape.equals("previous-disabled")) previous= "@org.junit.jupiter.api.Disabled " + previous;
				if (shape.equals("previous-invalid-test")) previous= previous.replace("void runnable() {}", "int runnable() { return 1; }");
				if (shape.equals("previous-object")) previous= previous.replace("final org.junit.jupiter.api.extension.Extension", "final Object");
				selected.add(unit(pack, "Previous", previous));
			}
			case "two-opaque-factories" -> {
				for (var owner : selected.subList(1, 3)) {
					replace(owner, "Resource fixture = new Resource();", "org.junit.rules.ExternalResource fixture = make(); "
							+ "private static org.junit.rules.ExternalResource make() { return new Resource(); }");
				}
			}
			case "self-discovery" -> {
				for (var owner : selected.subList(1, 3)) {
					replace(owner, "void example() {}", """
							void example() throws Exception {
								Object alias = this;
								if (new org.junit.runners.BlockJUnit4ClassRunner((alias).getClass()).testCount() != 1)
									throw new AssertionError();
							}""");
				}
			}
			case "vintage-suite" -> selected.add(unit(pack, "LegacySuite", """
					@org.junit.runner.RunWith(org.junit.runners.Suite.class)
					@org.junit.runners.Suite.SuiteClasses({InstanceTest.class, ClassTest.class})
					public class LegacySuite {}
					"""));
			case "filtered-platform" -> {
				addSuiteClasspath();
				selected.add(unit(pack, "Filtered", """
						@org.junit.platform.suite.api.Suite
						@org.junit.platform.suite.api.IncludeEngines("junit-vintage")
						@org.junit.platform.suite.api.SelectClasses({InstanceTest.class, ClassTest.class})
						public class Filtered {}
						"""));
			}
			default -> { }
		}
		if (shape.equals("same-name-source")) {
			var providerPackage= ((org.eclipse.jdt.core.IPackageFragmentRoot) pack.getParent())
					.createPackageFragment("org.sandbox.junit.jupiter", false, null);
			selected.add(providerPackage.createCompilationUnit("ExternalResourceExtension.java",
					"package org.sandbox.junit.jupiter; public class ExternalResourceExtension {}", false, null));
		}
		if (shape.equals("old-bundle")) addOldProvider();
		var units= selected.toArray(ICompilationUnit[]::new);
		var before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		var planning= JUnitMultiFilePlanner.create(context.getJavaProject(), units, true, true, null);
		assertTrue(planning.status().hasFatalError(), shape);
		assertTrue(planning.diagnostics().toJson().contains("MIXED_RULE_LIFECYCLE"), shape);
		assertThrows(CoreException.class, () -> context.apply(units));
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
	}

	@Test void bestEffortKeepsUnsupportedRulePeersOnVintage() throws Exception {
		var pack= setup(true);
		context.enable("cleanup.junitcleanup_best_effort");
		var fixtures= new ArrayList<>(simpleFixture(pack, "@Override public void before() {} @Override public void after() {}", ""));
		var retained= unit(pack, "WithPeers", """
				public class WithPeers {
					@org.junit.Rule public Resource resource = new Resource();
					@org.junit.Rule public org.junit.rules.TestName name = new org.junit.rules.TestName();
					@org.junit.Test public void usesBoth() { System.out.println(name.getMethodName()); }
				}
				""");
		fixtures.add(retained);
		var units= fixtures.toArray(ICompilationUnit[]::new);
		var before= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		context.apply(units);
		assertTrue(fixtures.get(1).getSource().contains(".perTest("));
		assertTrue(fixtures.get(2).getSource().contains(".perClass("));
		assertTrue(retained.getSource().contains("MIXED_RULE_CONTRACT_RETAINED"), retained.getSource());
		assertTrue(retained.getSource().contains("@org.junit.Test public void usesBoth()"));
		assertTrue(retained.getSource().contains("@org.junit.Rule public Resource resource"));
		diagnostics.assertNoNewDiagnostics();
		var converted= sources(units);
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(before, sources(units));
		diagnostics.assertRestored();
		context.apply(units);
		context.apply(units);
		assertEquals(converted, sources(units));
	}

	private void addOldProvider() throws Exception {
		String simple= "ExternalResourceExtension";
		Path source= Files.writeString(temporary.resolve(simple + ".java"),
				"package org.sandbox.junit.jupiter; public final class " + simple
				+ " { public static Object perTest(Runnable before, Runnable after) { return null; } }", StandardCharsets.UTF_8);
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"--release", "21", "-d", temporary.toString(), source.toString()));
		var manifest= new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().putValue("Bundle-SymbolicName", "org.sandbox.junit.migration.support");
		Path jar= temporary.resolve("old-support.jar");
		try (var output= new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			String entry= "org/sandbox/junit/jupiter/" + simple + ".class";
			output.putNextEntry(new JarEntry(entry));
			Files.copy(temporary.resolve(entry), output);
			output.closeEntry();
		}
		AbstractEclipseJava.addToClasspath(context.getJavaProject(), JavaCore.newLibraryEntry(
				org.eclipse.core.runtime.Path.fromOSString(jar.toString()), null, null));
		assertTrue(context.getJavaProject().findType("org.sandbox.junit.jupiter." + simple).isBinary());
	}

	@Test void staleFixtureAndExpandedScopeCannotAuthorizeARegistration() throws Exception {
		var pack= setup(true);
		var units= simpleFixture(pack, "@Override public void before() {} @Override public void after() {}", "")
				.toArray(ICompilationUnit[]::new);
		var result= JUnitMultiFilePlanner.create(context.getJavaProject(), units, true, true, null);
		assertFalse(result.status().hasFatalError(), result.diagnostics().toString());
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(units[1]);
		parser.setResolveBindings(true);
		var root= (CompilationUnit) parser.createAST(null);
		String original= units[0].getSource();
		units[0].getBuffer().setContents(original.replace("void after() {}", "void after() { System.out.println(1); }"));
		assertThrows(CoreException.class, () -> result.plan().addOperationsFor(units[1], root, new LinkedHashSet<>(), new LinkedHashSet<>()));
		units[0].getBuffer().setContents(original);
		unit(pack, "Added", "public class Added extends InstanceTest {}");
		assertThrows(CoreException.class, () -> result.plan().addOperationsFor(units[1], root, new LinkedHashSet<>(), new LinkedHashSet<>()));
	}

	@Test void classpathOnlyRebindingInvalidatesConstructorProof() throws Exception {
		var pack= setup(true);
		var units= new ArrayList<>(simpleFixture(pack, "@Override public void before() {} @Override public void after() {}", ""));
		var other= ((org.eclipse.jdt.core.IPackageFragmentRoot) pack.getParent()).createPackageFragment("other", false, null);
		units.add(other.createCompilationUnit("Chosen.java", "package other; public class Chosen extends mixed.Resource {}", false, null));
		var instance= units.get(1);
		replace(instance, "public class InstanceTest", "import other.*; public class InstanceTest");
		replace(instance, "new Resource()", "new Chosen()");
		var project= context.getJavaProject();
		var result= JUnitMultiFilePlanner.create(project, units.toArray(ICompilationUnit[]::new), true, true, null);
		assertFalse(result.status().hasFatalError());
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(instance);
		parser.setResolveBindings(true);
		var root= (CompilationUnit) parser.createAST(null);
		project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		Path output= ResourcesPlugin.getWorkspace().getRoot().findMember(project.getOutputLocation()).getLocation().toFile().toPath();
		Path source= Files.writeString(temporary.resolve("Chosen.java"), """
				package mixed;
				public class Chosen extends Resource {
					@Override public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base,
							org.junit.runner.Description description) { return base; }
				}
				""", StandardCharsets.UTF_8);
		var junit4= FileLocator.getBundleFileLocation(Platform.getBundle("org.junit")).orElseThrow();
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-cp",
				output + File.pathSeparator + junit4, "-d", temporary.toString(), source.toString()));
		AbstractEclipseJava.addToClasspath(project, JavaCore.newLibraryEntry(
				org.eclipse.core.runtime.Path.fromOSString(temporary.toString()), null, null));
		assertTrue(project.findType("mixed.Chosen").isBinary());
		assertThrows(CoreException.class, () -> result.plan().addOperationsFor(instance, root, new LinkedHashSet<>(), new LinkedHashSet<>()));
	}

	@Test void ruleOnlyProfilePreservesPreviouslyConvertedComponent() throws Exception {
		var pack= setup(true);
		var units= simpleFixture(pack, "@Override public void before() {} @Override public void after() {}", "")
				.toArray(ICompilationUnit[]::new);
		replace(units[2], "void example() {}", "void z() {} @org.junit.Test public void aa() {}");
		context.apply(units);
		assertTrue(units[1].getSource().contains(".perTest("));
		assertTrue(units[2].getSource().contains(".perClass("));
		var converted= sources(units);
		context.disable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.apply(units);
		assertEquals(converted, sources(units));
		units[1].delete(true, null);
		context.apply(new ICompilationUnit[] { units[0], units[2] });
		assertEquals(converted.get(0), units[0].getSource());
		assertEquals(converted.get(2), units[2].getSource());
	}

	@Test void classpathOnlyChangeToOwnerAnnotationRejectsPreviousPlan() throws Exception {
		var pack= setup(true);
		var project= context.getJavaProject();
		var units= simpleFixture(pack, "@Override public void before() {} @Override public void after() {}", "")
				.toArray(ICompilationUnit[]::new);
		var plain= markerLibrary("plain", "");
		var disabled= markerLibrary("disabled", "@org.junit.jupiter.api.Disabled ");
		AbstractEclipseJava.addToClasspath(project, plain);
		replace(units[1], "public class InstanceTest", "@qa.Marker public class InstanceTest");
		var result= JUnitMultiFilePlanner.create(project, units, true, true, null);
		assertFalse(result.status().hasFatalError());
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(units[1]);
		parser.setResolveBindings(true);
		var root= (CompilationUnit) parser.createAST(null);
		project.setRawClasspath(Arrays.stream(project.getRawClasspath()).map(entry -> entry.equals(plain) ? disabled : entry)
				.toArray(org.eclipse.jdt.core.IClasspathEntry[]::new), null);
		assertThrows(CoreException.class, () -> result.plan().addOperationsFor(units[1], root, new LinkedHashSet<>(), new LinkedHashSet<>()));
	}

	private org.eclipse.jdt.core.IClasspathEntry markerLibrary(String name, String meta) throws Exception {
		Path directory= Files.createDirectory(temporary.resolve(name));
		Path marker= Files.writeString(directory.resolve("Marker.java"), "package qa; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) "
				+ meta + "public @interface Marker {}", StandardCharsets.UTF_8);
		var api= Arrays.stream(Platform.getBundles("junit-jupiter-api", null)).filter(bundle -> bundle.getVersion().getMajor() == 5)
				.findFirst().orElseThrow();
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-cp",
				FileLocator.getBundleFileLocation(api).orElseThrow().toString(), "-d", directory.toString(), marker.toString()));
		return JavaCore.newLibraryEntry(org.eclipse.core.runtime.Path.fromOSString(directory.toString()), null, null);
	}

	@ParameterizedTest
	@ValueSource(strings= { "this::getClass", "super::getClass", "() -> super.getClass()" })
	void retainsSelfObservingVintageMethods(String access) throws Exception {
		var pack= setup(true);
		var units= simpleFixture(pack, "@Override public void before() {} @Override public void after() {}", "")
				.toArray(ICompilationUnit[]::new);
		var owners= List.of(units[1], units[2]);
		for (var owner : owners) {
			replace(owner, "void example() {}", """
					void example() throws Exception {
						java.util.function.Supplier<Class<?>> type = $access;
						if (!type.get().getMethod("example").isAnnotationPresent(org.junit.Test.class)) throw new AssertionError();
					}""".replace("$access", access));
		}
		var original= sources(units);
		var baseline= run(owners);
		assertEquals(List.of("mixed.InstanceTest#example=SUCCESSFUL", "mixed.ClassTest#example=SUCCESSFUL"),
				baseline.tests().stream().map(TestResult::outcome).toList());
		assertTrue(baseline.tests().stream().allMatch(test -> test.engine().equals("junit-vintage")));
		assertThrows(CoreException.class, () -> context.apply(units));
		assertEquals(original, sources(units));
		assertEquals(baseline, run(owners));
	}

	@ParameterizedTest
	@ValueSource(strings= { "resource", "interface", "generic" })
	void opaqueOppositeFactoryRetainsTheFixtureAndMigratesTheClassOwner(String projection) throws Exception {
		var pack= setup(true);
		String hooks= """
				public static int running;
				@Override public void before() { running++; }
				@Override public void after() { running--; }
				""";
		var selected= new ArrayList<>(simpleFixture(pack, hooks, ""));
		String returnType= "org.junit.rules.ExternalResource";
		String concrete= "Resource";
		if (!projection.equals("resource")) {
			boolean generic= projection.equals("generic");
			selected.add(unit(pack, "SpecialRule", "public interface SpecialRule" + (generic ? "<T>" : "")
					+ " extends org.junit.rules.TestRule {}"));
			selected.add(unit(pack, "Selected", "public class Selected extends Resource implements SpecialRule"
					+ (generic ? "<Integer>" : "") + " {}"));
			returnType= generic ? "SpecialRule<? extends Number>" : "SpecialRule";
			concrete= "Selected";
			replace(selected.get(2), "Resource fixture = new Resource()", "Selected fixture = new Selected()");
		}
		var units= selected.toArray(ICompilationUnit[]::new);
		for (var owner : List.of(units[1], units[2])) {
			replace(owner, "void example() {}", "void example() { if (Resource.running != 1) throw new AssertionError(); }");
		}
		replace(units[1], "public Resource fixture = new Resource();", "public " + returnType + " fixture = make(); private static "
				+ returnType + " make() { return new " + concrete + "(); }");
		var owners= List.of(units[1], units[2]);
		var original= sources(units);
		var diagnostics= CompilerDiagnosticAssertions.capture(units);
		var before= run(owners);
		assertEquals(List.of("mixed.InstanceTest#example=SUCCESSFUL", "mixed.ClassTest#example=SUCCESSFUL"),
				before.tests().stream().map(TestResult::outcome).toList());
		context.apply(units);
		assertEquals(original.get(0), units[0].getSource());
		assertEquals(original.get(1), units[1].getSource());
		assertTrue(units[2].getSource().contains(".perClass("));
		for (int i= 3; i < units.length; i++) assertEquals(original.get(i), units[i].getSource());
		diagnostics.assertNoNewDiagnostics();
		var after= run(owners);
		assertEquals(before.tests().stream().map(TestResult::outcome).toList(), after.tests().stream().map(TestResult::outcome).toList());
		assertEquals(List.of("junit-vintage", "junit-jupiter"), after.tests().stream().map(TestResult::engine).toList());
		RefactoringCore.getUndoManager().performUndo(null, null);
		assertEquals(original, sources(units));
		diagnostics.assertRestored();
	}

	private record TestResult(String outcome, String engine) {}
	private record Observation(List<TestResult> tests, List<String> trace) {}

	private Observation run(List<ICompilationUnit> owners) throws Exception {
		Path trace= temporary.resolve("trace");
		Files.deleteIfExists(trace);
		var project= context.getJavaProject();
		project.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		List<String> classpath= new ArrayList<>();
		classpath.add(ResourcesPlugin.getWorkspace().getRoot().findMember(project.getOutputLocation()).getLocation().toOSString());
		for (var entry : project.getResolvedClasspath(true)) {
			if (entry.getEntryKind() == org.eclipse.jdt.core.IClasspathEntry.CPE_LIBRARY) classpath.add(entry.getPath().toOSString());
		}
		Path driver= temporary.resolve("EngineProbe.java");
		Files.writeString(driver, """
				import org.junit.platform.launcher.*;
				import org.junit.platform.launcher.core.*;
				import org.junit.platform.engine.*;
				import org.junit.platform.engine.discovery.DiscoverySelectors;
				import org.junit.platform.engine.support.descriptor.MethodSource;
				class EngineProbe {
					public static void main(String[] classes) {
						var launcher = LauncherFactory.create();
						var listener = new TestExecutionListener() {
							@Override public void executionFinished(TestIdentifier id, TestExecutionResult result) {
								if (id.isTest() && id.getSource().orElse(null) instanceof MethodSource method)
									System.out.println("RESULT " + method.getClassName() + "#" + method.getMethodName()
										+ "=" + result.getStatus() + " " + id.getUniqueIdObject().getEngineId().orElseThrow());
							}
						};
						for (String type : classes) launcher.execute(LauncherDiscoveryRequestBuilder.request()
							.selectors(DiscoverySelectors.selectClass(type)).build(), listener);
					}
				}
				""", StandardCharsets.UTF_8);
		List<String> command= new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-Djunit.jupiter.testinstance.lifecycle.default=per_class",
				"-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$MethodName", "--class-path", String.join(File.pathSeparator, classpath), driver.toString()));
		owners.forEach(unit -> command.add(unit.findPrimaryType().getFullyQualifiedName()));
		Path output= temporary.resolve("engine.log");
		Process process= new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		try {
			assertTrue(process.waitFor(90, TimeUnit.SECONDS), "The prepared source must finish on the Platform engines");
			assertEquals(0, process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
		} finally {
			if (process.isAlive()) process.destroyForcibly();
		}
		List<TestResult> tests= Files.readAllLines(output, StandardCharsets.UTF_8).stream()
				.filter(line -> line.startsWith("RESULT ")).map(line -> line.split(" "))
				.map(parts -> new TestResult(parts[1], parts[2])).toList();
		return new Observation(tests, Files.exists(trace) ? Files.readAllLines(trace, StandardCharsets.UTF_8) : List.of());
	}

	private static void replace(ICompilationUnit unit, String from, String to) throws Exception {
		String source= unit.getSource();
		assertTrue(source.contains(from));
		unit.getBuffer().setContents(source.replace(from, to));
		unit.save(null, true);
	}

	private static ICompilationUnit unit(IPackageFragment pack, String name, String body) throws Exception {
		return pack.createCompilationUnit(name + ".java", "package mixed;\n" + body, false, null);
	}

	private static List<String> sources(ICompilationUnit[] units) throws Exception {
		List<String> snapshots= new ArrayList<>();
		for (ICompilationUnit unit : units) snapshots.add(unit.getSource());
		return snapshots;
	}
}
