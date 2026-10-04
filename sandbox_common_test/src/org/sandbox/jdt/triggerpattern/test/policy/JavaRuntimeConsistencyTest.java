/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/** Keeps the executable runtime independent of the Java level of edited projects. */
@SuppressWarnings("nls")
public class JavaRuntimeConsistencyTest {

	private static final String JUSTJ = "https://download.eclipse.org/justj/jres/25/updates/release/25.0.4.v20260826-0822";

	@Test
	void buildEntrypointsRequireExactlyJava25() throws Exception {
		assertEquals(25, Runtime.version().feature(), "Run the repository policy with the supported JVM");
		Document pom = xml("pom.xml");
		assertEquals("25", value(pom, "/project/properties/java-version"));
		assertEquals("JavaSE-25", value(pom, "/project/properties/java-execenv"));
		assertEquals("[25,26)", value(pom, "//requireJavaVersion/version"));
		assertEquals("25", read(".java-version").strip());
		assertTrue(read("Makefile").contains("SUPPORTED_JAVA_MAJOR := 25"));
		for (Path module : modules()) {
			Path file = module.resolve("pom.xml");
			if (Files.exists(file)) {
				String text = Files.readString(file);
				var literals = Pattern.compile("<(?:maven\\.compiler\\.(?:source|target|release)|release|source|target)>(\\d+)</").matcher(text);
				while (literals.find()) {
					assertEquals("25", literals.group(1), file.toString());
				}
			}
		}
	}

	@Test
	void bundlesAndWorkspaceProjectsUseJava25() throws Exception {
		int bundles = 0;
		for (Path module : modules()) {
			Path manifest = module.resolve("META-INF/MANIFEST.MF");
			if (Files.exists(manifest)) {
				try (var in = Files.newInputStream(manifest)) {
					String bree = new Manifest(in).getMainAttributes().getValue("Bundle-RequiredExecutionEnvironment");
					if (bree != null) {
						assertEquals("JavaSE-25", bree, manifest.toString());
						bundles++;
					}
				}
			}
			Path classpath = module.resolve(".classpath");
			if (Files.exists(classpath)) {
				assertFalse(Pattern.compile("JavaSE-(?!25(?:[\"/]|$))\\d+").matcher(Files.readString(classpath)).find(), classpath.toString());
			}
			Path prefs = module.resolve(".settings/org.eclipse.jdt.core.prefs");
			if (Files.exists(prefs)) {
				Properties values = new Properties();
				try (var in = Files.newInputStream(prefs)) { values.load(in); }
				for (String key : List.of("org.eclipse.jdt.core.compiler.codegen.targetPlatform", "org.eclipse.jdt.core.compiler.compliance", "org.eclipse.jdt.core.compiler.source")) {
					if (values.containsKey(key)) assertEquals("25", values.getProperty(key), prefs + ": " + key);
				}
			}
		}
		assertTrue(bundles >= 40, "The runtime inventory must include the cleanup bundles");
	}

	@Test
	void productUsesPinnedJustJWithoutChangingTheCompilationEnvironment() throws Exception {
		Document pom = xml("pom.xml");
		assertEquals(JUSTJ, value(pom, "/project/repositories/repository[id='justj']/url"));
		assertEquals("${java-execenv}", value(pom, "/project/build/plugins/plugin[artifactId='target-platform-configuration']/configuration/executionEnvironment"));
		Document product = xml("sandbox_product/sandbox.product");
		assertEquals("true", product.getDocumentElement().getAttribute("includeJRE"));
		assertTrue(value(product, "/product/launcherArgs/vmArgs").contains("-Dosgi.requiredJavaVersion=25"));
		assertEquals(JUSTJ, value(xml("sandbox_product/pom.xml"), "/project/build/plugins/plugin[artifactId='tycho-p2-director-plugin']/configuration/productRepository"));
		assertFalse(read("sandbox_target/eclipse.target").contains("org.eclipse.justj"), "Product runtime must not replace the JavaSE compilation environment");
		assertEquals("", value(product, "/product/features/feature[starts-with(@id,'org.eclipse.justj')]/@id"));
		for (Path module : modules()) {
			try (var files = Files.list(module)) {
				for (Path launch : files.filter(p -> p.toString().endsWith(".launch")).toList()) {
					String text = Files.readString(launch);
					assertFalse(text.contains("JavaSE-21"), launch.toString());
					assertFalse(text.contains("org.eclipse.justj"), "Development launch uses the configured Java25 VM: " + launch);
				}
			}
		}
	}

	@Test
	void contributorSetupUsesJava25WithoutChangingFrozenQaTargets() throws Exception {
		assertTrue(read("sandbox_oomph/sandboxproject.setup").contains("version=\"JavaSE-25\""));
		assertEquals("25", value(xml("sandbox_oomph/pom.xml"), "/project/properties/maven.compiler.release"));
		assertTrue(read("sandbox_oomph/src/test/java/org/sandbox/oomph/OomphSetupTest.java").contains("\"JavaSE-25\""));
		assertTrue(read("sandbox_oomph/src/test/resources/probe/SetupProbe.java").contains("getEnvironment(\"JavaSE-25\")"));
		assertTrue(read("sandbox_oomph/jdt-migration-qa.setup").contains("version=\"JavaSE-21\""), "Frozen upstream QA keeps its own target");
		assertTrue(read(".github/actions/cleanup-review/integration-fixture/.classpath").contains("JavaSE-21"), "Do not raise the target Java level of edited-project fixtures");
		for (String path : List.of("README.md", ".github/copilot-instructions.md", "sandbox_oomph/README.md", "sandbox_target/README.md", "docs/JAVA_BUILD_RUNTIME.md")) {
			assertTrue(read(path).contains("Java 25") || read(path).contains("JDK 25"), path);
		}
	}

	@Test
	void activeBuildAndNativeCiUseJava25() throws Exception {
		for (String name : List.of("maven", "coverage", "core-module-build", "ast-api-build", "deploy-release", "deploy-snapshot", "distribution-smoke", "eclipse-help-screenshots", "codeql", "publish-cleanup-image")) {
			String workflow = read(".github/workflows/" + name + ".yml");
			var versions = Pattern.compile("java-version:\\s*['\"]?(\\d+)").matcher(workflow);
			assertTrue(versions.find(), name);
			do { assertEquals("25", versions.group(1), name); } while (versions.find());
		}
		String nativeWorkflow = read(".github/workflows/distribution-smoke.yml");
		for (String runner : List.of("ubuntu-24.04", "windows-2025", "macos-15-intel")) assertTrue(nativeWorkflow.contains(runner), runner);
		assertTrue(nativeWorkflow.contains("-Pdistribution"), "Retain the complete distribution gate");
	}

	@Test
	void cliDistributionContainsTrackedLaunchersAndTheMaterializedRuntime() throws Exception {
		String assembly = read("sandbox_cleanup_cli_dist/src/assembly/cli-dist.xml");
		assertTrue(assembly.contains("${project.basedir}/src/main/scripts"));
		String uid = xml("sandbox_product/sandbox.product").getDocumentElement().getAttribute("uid");
		assertTrue(assembly.contains("target/products/" + uid + "/linux/gtk/x86_64</directory>"), "Use the actual materialized product root");
		assertTrue(assembly.contains("org.eclipse.justj.openjdk.hotspot.jre."), "Include extracted JustJ runtime directories in the CLI archive");
		for (String name : List.of("sandbox-cleanup", "sandbox-cleanup.bat")) {
			String script = read("sandbox_cleanup_cli_dist/src/main/scripts/" + name);
			assertTrue(script.contains("25"), name);
			assertTrue(script.contains("JAVA_HOME"), name);
			assertTrue(script.contains("SANDBOX_WORKSPACE"), name);
			assertTrue(script.contains("org.eclipse.justj.openjdk.hotspot.jre."), name);
		}
		assertTrue(read("sandbox_cleanup_cli_dist/src/main/scripts/sandbox-cleanup").contains("-XstartOnFirstThread"));
	}

	@Test
	void tychoTestsKeepMacFirstThreadAndLateCoverageArguments() throws Exception {
		Document rootPom = xml("pom.xml");
		assertEquals("${tycho.platformArgLine}", value(rootPom, "/project/properties/tycho.testArgLine"));
		assertEquals("mac", value(rootPom, "/project/profiles/profile[id='macos-tests']/activation/os/family"));
		assertEquals("-XstartOnFirstThread", value(rootPom, "/project/profiles/profile[id='macos-tests']/properties/tycho.platformArgLine"));
		for (Path module : modules()) {
			Path pom = module.resolve("pom.xml");
			if (!Files.exists(pom)) continue;
			Document document = xml(root().relativize(pom).toString());
			assertEquals("0", value(document, "count(//plugin[artifactId='tycho-surefire-plugin']/configuration/argLine)"), "Let Tycho read the execution-time property: " + pom);
			var properties = document.getElementsByTagName("tycho.testArgLine");
			for (int i = 0; i < properties.getLength(); i++) {
				String arguments = properties.item(i).getTextContent();
				assertTrue(arguments.startsWith("${tycho.platformArgLine}"), pom.toString());
				assertFalse(Pattern.compile("-D[^\\s=]+=\"").matcher(arguments).find(), "Quote whole VM arguments so JaCoCo preserves spaced paths: " + pom);
			}
		}
	}

	private static List<Path> modules() throws Exception {
		try (var files = Files.list(root())) {
			return files.filter(Files::isDirectory).filter(path -> path.getFileName().toString().matches("sandbox[-_].*")).sorted().toList();
		}
	}

	private static Path root() {
		Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (path != null && !Files.exists(path.resolve("sandbox_target/eclipse.target"))) path = path.getParent();
		if (path == null) throw new AssertionError("Cannot locate the Sandbox repository");
		return path;
	}

	private static String read(String path) throws Exception { return Files.readString(root().resolve(path)); }

	private static Document xml(String path) throws Exception {
		var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		try (var in = Files.newInputStream(root().resolve(path))) { return factory.newDocumentBuilder().parse(in); }
	}

	private static String value(Document document, String expression) throws Exception {
		return XPathFactory.newInstance().newXPath().evaluate(expression, document).strip();
	}
}
