/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.maven.cleanup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Supplies the Eclipse Java project required by the cleanup application. */
record MavenCleanupProject(Path directory, List<Path> sources, String cliScope) {
	private record Source(Path path, boolean test) {}

	@FunctionalInterface
	interface ClasspathResolver {
		MavenProject resolve(boolean includeTests) throws IOException;
	}

	static MavenCleanupProject prepare(MavenProject project, Path selection, String scope, Path run,
			boolean emitsPatch, ClasspathResolver resolver)
			throws IOException, DependencyResolutionRequiredException {
		Path base = project.getBasedir().toPath().toRealPath();
		Path selected = selection.toRealPath();
		String requested = scope == null || scope.isEmpty() ? "both" : scope.toLowerCase(Locale.ROOT);
		if (!List.of("main", "test", "both").contains(requested)) {
			throw new IOException("Unknown cleanup scope: " + scope);
		}
		if (MavenEclipseMetadata.canImportDirectly(base)) {
			return new MavenCleanupProject(base, List.of(selected), requested);
		}

		List<Source> roots = new ArrayList<>();
		addRoots(roots, project.getCompileSourceRoots(), false, base);
		addRoots(roots, project.getTestCompileSourceRoots(), true, base);
		List<Path> inputs = new ArrayList<>();
		boolean matched = selected.equals(base);
		boolean includeTests = false;
		for (Source root : roots) {
			if (!selected.equals(base) && !selected.startsWith(root.path()) && !root.path().startsWith(selected)) continue;
			matched = true;
			if (requested.equals(root.test() ? "main" : "test")) continue;
			inputs.add(selected.startsWith(root.path()) ? selected : root.path());
			includeTests |= root.test();
		}
		if (!matched) throw new IOException("Requested source is outside the Maven source roots: " + selected);
		Path patchBase = patchBase(base, inputs, emitsPatch);
		if (inputs.isEmpty()) inputs.add(Files.createDirectory(run.resolve("empty-input")));
		if (!includeTests) roots.removeIf(Source::test);

		Path descriptor = Files.createDirectory(run.resolve("project"));
		writeProject(resolver.resolve(includeTests), patchBase, roots, descriptor, requested, includeTests);
		boolean mavenLanguageConfigured = hasLanguageSetting(project, false) || (includeTests && hasLanguageSetting(project, true));
		MavenEclipseMetadata.preserve(base, descriptor, patchBase, mavenLanguageConfigured);
		// Maven already selected the source sets, including nonstandard test directories.
		return new MavenCleanupProject(descriptor, List.copyOf(inputs), "both");
	}

	private static Path patchBase(Path module, List<Path> inputs, boolean emitsPatch) throws IOException {
		if (!emitsPatch || inputs.stream().allMatch(input -> input.startsWith(module))) return module;
		Path checkout = checkoutRoot(module);
		for (Path input : inputs) {
			if (input.startsWith(module)) continue;
			if (checkout == null || !input.startsWith(checkout) || !checkout.equals(checkoutRoot(input))) {
				throw new IOException("Cannot create an applicable patch for Maven source outside this checkout: " + input);
			}
		}
		// A patch spanning sibling modules must use one common path prefix for all of its files.
		return checkout;
	}

	private static Path checkoutRoot(Path path) {
		for (Path parent = path; parent != null; parent = parent.getParent()) {
			// Linked worktrees and submodules have a .git file instead of a directory.
			if (Files.exists(parent.resolve(".git"))) return parent;
		}
		return null;
	}

	private static void addRoots(List<Source> roots, List<String> configured, boolean test, Path base) throws IOException {
		for (String entry : configured) {
			Path root = base.resolve(entry).normalize();
			if (Files.notExists(root)) continue; // Maven commonly declares a missing src/test/java.
			root = root.toRealPath();
			if (!Files.isDirectory(root)) throw new IOException("Maven source root is not a directory: " + root);
			if (base.startsWith(root)) throw new IOException("Maven source root includes the whole project: " + root);
			Source source = new Source(root, test);
			if (roots.contains(source)) continue;
			for (Source other : roots) {
				if (root.startsWith(other.path()) || other.path().startsWith(root)) {
					throw new IOException("Overlapping Maven source roots: " + root + " and " + other.path());
				}
			}
			roots.add(source);
		}
	}

	private static void writeProject(MavenProject project, Path base, List<Source> roots, Path directory, String scope, boolean includeTests)
			throws IOException, DependencyResolutionRequiredException {
		var description = new Xpp3Dom("projectDescription");
		child(description, "name", "sandbox-maven-cleanup");
		child(child(child(description, "buildSpec", null), "buildCommand", null), "name", "org.eclipse.jdt.core.javabuilder");
		child(child(description, "natures", null), "nature", "org.eclipse.jdt.core.javanature");
		var links = child(description, "linkedResources", null);
		var classpath = new Xpp3Dom("classpath");
		int external = 0;
		for (Source root : roots) {
			String name = root.path().startsWith(base) ? base.relativize(root.path()).toString().replace('\\', '/')
					: ".maven-external/source-" + external++;
			Path parent = Path.of(name).getParent();
			if (parent != null) Files.createDirectories(directory.resolve(parent));
			var link = child(links, "link", null);
			child(link, "name", name);
			child(link, "type", "2"); // Eclipse IResource.FOLDER
			child(link, "locationURI", root.path().toUri().toASCIIString());
			entry(classpath, "src", name, root.test());
		}
		entry(classpath, "con", "org.eclipse.jdt.launching.JRE_CONTAINER", false);
		var compile = new LinkedHashSet<>(project.getCompileClasspathElements());
		var libraries = new LinkedHashSet<>(compile);
		if (includeTests) libraries.addAll(project.getTestClasspathElements());
		for (String library : libraries) {
			Path path = Path.of(library);
			if (Files.notExists(path) && (library.equals(project.getBuild().getOutputDirectory())
					|| library.equals(project.getBuild().getTestOutputDirectory()))) {
				continue; // Direct goals may run before Maven creates the project's own output directories.
			}
			entry(classpath, "lib", path.toRealPath().toString(), !compile.contains(library));
		}
		entry(classpath, "output", ".sandbox-classes", false);
		Files.writeString(directory.resolve(".project"), description.toString());
		Files.writeString(directory.resolve(".classpath"), classpath.toString());
		writeCompilerPreferences(project, directory, scope, roots);
	}

	private record LanguageLevel(String source, String target, boolean release) {}

	private static void writeCompilerPreferences(MavenProject project, Path directory, String scope, List<Source> roots)
			throws IOException {
		boolean hasMain = roots.stream().anyMatch(root -> !root.test());
		boolean hasTests = roots.stream().anyMatch(Source::test);
		LanguageLevel main = languageLevel(project, false);
		LanguageLevel test = languageLevel(project, true);
		if (scope.equals("both") && hasMain && hasTests && !main.equals(test)) {
			throw new IOException("Maven main and test sources use different Java levels. Run separately with sandbox.scope=main and sandbox.scope=test.");
		}
		LanguageLevel level = scope.equals("test") || (!hasMain && hasTests) ? test : main;
		var prefs = new Properties();
		prefs.setProperty("eclipse.preferences.version", "1");
		if (level.release()) prefs.setProperty("org.eclipse.jdt.core.compiler.release", "enabled");
		prefs.setProperty("org.eclipse.jdt.core.compiler.compliance", level.target());
		prefs.setProperty("org.eclipse.jdt.core.compiler.source", level.source());
		prefs.setProperty("org.eclipse.jdt.core.compiler.codegen.targetPlatform", level.target());
		Path settings = Files.createDirectory(directory.resolve(".settings"));
		try (var writer = Files.newOutputStream(settings.resolve("org.eclipse.jdt.core.prefs"))) {
			prefs.store(writer, null);
		}
		String encoding = compilerSetting(project, "encoding", false);
		if (encoding == null) encoding = project.getProperties().getProperty("project.build.sourceEncoding");
		if (encoding != null) {
			var resources = new Properties();
			resources.setProperty("eclipse.preferences.version", "1");
			resources.setProperty("encoding/<project>", encoding);
			try (var writer = Files.newOutputStream(settings.resolve("org.eclipse.core.resources.prefs"))) {
				resources.store(writer, null);
			}
		}
	}

	private static boolean hasLanguageSetting(MavenProject project, boolean test) {
		return compilerSetting(project, "release", test) != null || compilerSetting(project, "source", test) != null
				|| compilerSetting(project, "target", test) != null;
	}

	private static LanguageLevel languageLevel(MavenProject project, boolean test) {
		String release = compilerSetting(project, "release", test);
		String source = release == null ? compilerSetting(project, "source", test) : release;
		String target = release == null ? compilerSetting(project, "target", test) : release;
		if (source == null) source = "1.8"; // Maven Compiler Plugin 3.x default.
		if (target == null) target = source;
		return new LanguageLevel(jdtVersion(source), jdtVersion(target), release != null);
	}

	private static String compilerSetting(MavenProject project, String key, boolean test) {
		String goal = test ? "testCompile" : "compile";
		var config = project.getGoalConfiguration("org.apache.maven.plugins", "maven-compiler-plugin", "default-" + goal, goal);
		if (test) {
			String testKey = "test" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
			String specific = setting(project, config, testKey);
			if (specific != null) return specific;
		}
		return setting(project, config, key);
	}

	private static String setting(MavenProject project, Xpp3Dom config, String key) {
		if (config != null && config.getChild(key) != null) return config.getChild(key).getValue();
		return project.getProperties().getProperty("maven.compiler." + key);
	}

	private static String jdtVersion(String version) {
		return version.matches("[5-8]") ? "1." + version : version;
	}

	private static Xpp3Dom child(Xpp3Dom parent, String name, String value) {
		var element = new Xpp3Dom(name);
		element.setValue(value);
		parent.addChild(element);
		return element;
	}

	private static void entry(Xpp3Dom classpath, String kind, String path, boolean test) {
		var entry = child(classpath, "classpathentry", null);
		entry.setAttribute("kind", kind);
		entry.setAttribute("path", path);
		if (test) {
			var attribute = child(child(entry, "attributes", null), "attribute", null);
			attribute.setAttribute("name", "test");
			attribute.setAttribute("value", "true");
		}
	}
}
