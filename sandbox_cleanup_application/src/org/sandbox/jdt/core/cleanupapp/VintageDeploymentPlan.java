/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Retires test-scoped legacy engines only in a closed, source-only Maven Suite deployment. */
final class VintageDeploymentPlan {
	record SourceTransition(String before, String after) {
	}

	private static final Set<String> PROJECT_ELEMENTS= Set.of("modelVersion", "groupId", "artifactId", "version", "packaging",
			"dependencyManagement", "dependencies");
	private static final Set<String> DEP_FIELDS= Set.of("groupId", "artifactId", "scope", "version");
	private static final Set<String> KNOWN_DEPS= Set.of("org.junit.jupiter:junit-jupiter", "org.junit.platform:junit-platform-suite",
			"junit:junit", "org.junit.vintage:junit-vintage-engine");
	private static final Set<String> IMPORTS= Set.of("org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions",
			"org.junit.platform.suite.api.Suite", "org.junit.platform.suite.api.SelectClasses");
	private static final Pattern DEPENDENCY= Pattern.compile("<dependency>.*?</dependency>", Pattern.DOTALL);

	private VintageDeploymentPlan() {
	}

	static String afterSuiteMigration(String pom, IFile descriptor, Map<Path, SourceTransition> sources) throws IOException, CoreException {
		if (!pom.contains("junit-vintage-engine") && !pom.contains("<artifactId>junit</artifactId>")) {
			return pom;
		}
		Element project= PlatformSuiteDependencies.parse(pom);
		if (!plainDependencies(project) || !closedProject(descriptor.getProject(), sources)) {
			return pom;
		}
		String planned= pom;
		for (String id : new String[] { "junit-vintage-engine", "junit" }) {
			planned= removeDirectDependency(planned, id);
		}
		return planned;
	}

	private static boolean onlyElements(Element parent, Set<String> allowed) {
		if (parent == null) {
			return false;
		}
		for (Node n= parent.getFirstChild(); n != null; n= n.getNextSibling()) {
			if (n instanceof Element e && (!allowed.contains(e.getTagName()) || !parent.getNamespaceURI().equals(e.getNamespaceURI())
					|| e.hasAttributes())) {
				return false;
			}
		}
		return true;
	}

	private static boolean plainDependencies(Element project) {
		if (!onlyElements(project, PROJECT_ELEMENTS)) {
			return false;
		}
		Element management= PlatformSuiteDependencies.child(project, "dependencyManagement");
		if (!onlyElements(management, Set.of("dependencies"))) {
			return false;
		}
		Element managed= PlatformSuiteDependencies.child(management, "dependencies");
		if (!onlyElements(managed, Set.of("dependency"))) {
			return false;
		}
		var boms= PlatformSuiteDependencies.children(managed, "dependency");
		if (boms.size() != 1 || !PlatformSuiteDependencies.supportedBom(boms.getFirst())) {
			return false;
		}
		Set<String> found= new HashSet<>();
		Element dependencies= PlatformSuiteDependencies.child(project, "dependencies");
		if (!onlyElements(dependencies, Set.of("dependency"))) {
			return false;
		}
		for (Element dep : PlatformSuiteDependencies.children(dependencies, "dependency")) {
			String id= PlatformSuiteDependencies.value(dep, "groupId") + ":" + PlatformSuiteDependencies.value(dep, "artifactId");
			if (!onlyElements(dep, DEP_FIELDS) || !"test".equals(PlatformSuiteDependencies.value(dep, "scope"))
					|| !KNOWN_DEPS.contains(id) || !found.add(id)) {
				return false;
			}
			String version= PlatformSuiteDependencies.value(dep, "version");
			if ("junit:junit".equals(id) ? !"4.13.2".equals(version) : !version.isEmpty()) {
				return false;
			}
		}
		return found.contains("org.junit.jupiter:junit-jupiter") && found.contains("org.junit.platform:junit-platform-suite");
	}

	// Validate the exact XML node removal before taking its lexical slice.
	private static String removeDirectDependency(String source, String artifact) throws IOException {
		Element expected= PlatformSuiteDependencies.parse(source);
		Element dependencies= PlatformSuiteDependencies.child(expected, "dependencies");
		for (Element dep : PlatformSuiteDependencies.children(dependencies, "dependency")) {
			if (!artifact.equals(PlatformSuiteDependencies.value(dep, "artifactId"))) {
				continue;
			}
			dependencies.removeChild(dep);
			expected.normalize();
			Matcher matcher= DEPENDENCY.matcher(source);
			while (matcher.find()) {
				if (matcher.group().contains("<artifactId>" + artifact + "</artifactId>")) {
					String candidate= source.substring(0, matcher.start()) + source.substring(matcher.end());
					Element actual= PlatformSuiteDependencies.parse(candidate);
					actual.normalize();
					if (expected.isEqualNode(actual)) {
						int line= source.lastIndexOf('\n', matcher.start()) + 1;
						int end= source.indexOf('\n', matcher.end());
						if (end >= 0 && source.substring(line, matcher.start()).isBlank()
								&& source.substring(matcher.end(), end).isBlank()) {
							return source.substring(0, line) + source.substring(end + 1);
						}
						return candidate;
					}
				}
			}
			break;
		}
		return source;
	}

	private static boolean closedProject(IProject project, Map<Path, SourceTransition> sources) throws IOException, CoreException {
		if (project.getReferencedProjects().length != 0 || project.getReferencingProjects().length != 0
				|| project.getLocation() == null || sources.isEmpty()) {
			return false;
		}
		boolean[] linked= { false };
		project.accept(resource -> {
			linked[0] |= resource.isLinked();
			return !linked[0];
		});
		if (linked[0]) {
			return false;
		}
		Path root= project.getLocation().toFile().toPath().toAbsolutePath().normalize();
		Set<Path> remaining= new HashSet<>(sources.keySet());
		try (var files= Files.walk(root)) {
			var iterator= files.iterator();
			while (iterator.hasNext()) {
				Path path= iterator.next();
				if (Files.isSymbolicLink(path)) {
					return false;
				}
				if (Files.isDirectory(path)) {
					continue;
				}
				String name= root.relativize(path).toString().replace(java.io.File.separatorChar, '/');
				// The standalone Maven model never reads the Eclipse compiler's bin directory.
				// Only the class files corresponding to participating source files are expected there.
				if (name.startsWith("bin/") && name.endsWith(".class")
						&& sources.containsKey(root.resolve("src/test/java/" + name.substring(4, name.length() - 6) + ".java"))) {
					continue;
				}
				if (Set.of("pom.xml", ".project", ".classpath", ".settings/org.eclipse.jdt.core.prefs", ".settings/org.eclipse.core.resources.prefs").contains(name)) {
					continue;
				}
				if (!name.startsWith("src/test/java/") || !remaining.remove(path)) {
					return false;
				}
			}
		}
		if (!remaining.isEmpty()) {
			return false;
		}
		Set<String> selected= new HashSet<>();
		Set<String> migratedMembers= new HashSet<>();
		for (var source : sources.entrySet()) {
			Set<String> tests= new HashSet<>();
			SourceTransition transition= source.getValue();
			if (!classify(source.getKey(), transition.after(), tests, selected)) {
				return false;
			}
			if (!tests.isEmpty() && wasJUnit4Member(transition.before(), transition.after())) {
				migratedMembers.addAll(tests);
			}
		}
		// Jupiter-only tests were not runnable as children of an ordinary JUnit 4 Suite.
		return !selected.isEmpty() && migratedMembers.containsAll(selected);
	}

	// With no generators, external test libraries, resources or referenced projects,
	// prove the deployed classes themselves. Unknown dispatch, inheritance, nested tests,
	// reflective probes and engine configuration must all retain the old dependencies.
	private static boolean wasJUnit4Member(String before, String after) {
		CompilationUnit original= parse(before);
		CompilationUnit migrated= parse(after);
		if (original.getProblems().length != 0 || original.types().size() != 1
				|| !(original.types().getFirst() instanceof TypeDeclaration type) || !org.eclipse.jdt.core.dom.Modifier.isPublic(type.getModifiers())
				|| type.bodyDeclarations().isEmpty() || original.getPackage() == null || migrated.getPackage() == null
				|| !original.getPackage().getName().toString().equals(migrated.getPackage().getName().toString())) {
			return false;
		}
		// This bounded case has only a Test import/annotation substitution.
		// Match the compilation units structurally after that substitution so
		// a changed method body, signature, fixture hook or class name cannot qualify.
		boolean legacyImport= original.imports().stream().anyMatch(item -> item instanceof ImportDeclaration declaration
				&& "org.junit.Test".equals(declaration.getName().toString()) && !declaration.isOnDemand() && !declaration.isStatic());
		for (Object member : type.bodyDeclarations()) {
			if (!(member instanceof MethodDeclaration method) || !org.eclipse.jdt.core.dom.Modifier.isPublic(method.getModifiers())
					|| org.eclipse.jdt.core.dom.Modifier.isStatic(method.getModifiers()) || !method.parameters().isEmpty()
					|| !method.modifiers().stream().anyMatch(it -> it instanceof MarkerAnnotation a
						&& ("org.junit.Test".equals(a.getTypeName().toString()) || legacyImport && "Test".equals(a.getTypeName().toString())))) {
				return false;
			}
		}
		String substituted= before.replace("org.junit.Test", "org.junit.jupiter.api.Test");
		return parse(substituted).subtreeMatch(new org.eclipse.jdt.core.dom.ASTMatcher(), migrated);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setCompilerOptions(org.eclipse.jdt.core.JavaCore.getOptions());
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	private static boolean classify(Path file, String source, Set<String> tests, Set<String> selected) {
		CompilationUnit unit= parse(source);
		if (unit.getProblems().length != 0 || unit.getPackage() == null
				|| !unit.getPackage().annotations().isEmpty() || unit.getPackage().getName().getFullyQualifiedName().startsWith("org.junit")
				|| unit.getPackage().getName().getFullyQualifiedName().startsWith("junit.") || unit.types().size() != 1
				|| !(unit.types().getFirst() instanceof TypeDeclaration type) || type.isInterface()
				|| type.getSuperclassType() != null || !type.superInterfaceTypes().isEmpty() || !type.typeParameters().isEmpty()
				|| !file.getFileName().toString().equals(type.getName().getIdentifier() + ".java")) {
			return false;
		}
		Set<String> imports= new HashSet<>();
		for (Object element : unit.imports()) {
			ImportDeclaration decl= (ImportDeclaration) element;
			String name= decl.getName().getFullyQualifiedName();
			if (decl.isStatic() || decl.isOnDemand() || !IMPORTS.contains(name) || !imports.add(name)) {
				return false;
			}
		}
		String prefix= unit.getPackage().getName().getFullyQualifiedName() + ".";
		boolean suite= false;
		Set<String> targets= new HashSet<>();
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof MarkerAnnotation a && resolves(a.getTypeName().toString(), "org.junit.platform.suite.api.Suite", imports)) {
				suite= true;
			} else if (modifier instanceof SingleMemberAnnotation a
					&& resolves(a.getTypeName().toString(), "org.junit.platform.suite.api.SelectClasses", imports)
					&& a.getValue() instanceof ArrayInitializer members) {
				for (Object item : members.expressions()) {
					if (!(item instanceof TypeLiteral literal) || !literal.getType().isSimpleType()) {
						return false;
					}
					targets.add(prefix + literal.getType().toString());
				}
			} else if (!(modifier instanceof org.eclipse.jdt.core.dom.Modifier)) {
				return false;
			}
		}
		if (suite || !targets.isEmpty()) {
			if (!suite || targets.isEmpty() || !type.bodyDeclarations().isEmpty()) {
				return false;
			}
			selected.addAll(targets);
			return true;
		}
		if (type.bodyDeclarations().isEmpty()) {
			return false;
		}
		for (Object item : type.bodyDeclarations()) {
			if (!(item instanceof MethodDeclaration method) || !plainJupiterTest(method, imports)) {
				return false;
			}
		}
		return tests.add(prefix + type.getName().getIdentifier());
	}

	private static boolean resolves(String name, String expected, Set<String> imports) {
		return name.equals(expected) || imports.contains(expected) && name.equals(expected.substring(expected.lastIndexOf('.') + 1));
	}

	private static boolean plainJupiterTest(MethodDeclaration method, Set<String> imports) {
		boolean test= false;
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof MarkerAnnotation a && resolves(a.getTypeName().toString(), "org.junit.jupiter.api.Test", imports)) {
				test= true;
			} else if (!(modifier instanceof org.eclipse.jdt.core.dom.Modifier)) {
				return false;
			}
		}
		Block body= method.getBody();
		if (!test || body == null || !method.parameters().isEmpty() || !method.thrownExceptionTypes().isEmpty()
				|| !"void".equals(String.valueOf(method.getReturnType2())) || !method.typeParameters().isEmpty()) {
			return false;
		}
		for (Object item : body.statements()) {
			if (!(item instanceof ExpressionStatement s) || !(s.getExpression() instanceof MethodInvocation call)
					|| !call.getName().getIdentifier().equals("assertEquals") || call.getExpression() == null
					|| !resolves(call.getExpression().toString(), "org.junit.jupiter.api.Assertions", imports)
					|| call.arguments().size() != 2 || !call.arguments().stream().allMatch(NumberLiteral.class::isInstance)) {
				return false;
			}
		}
		return true;
	}
}
