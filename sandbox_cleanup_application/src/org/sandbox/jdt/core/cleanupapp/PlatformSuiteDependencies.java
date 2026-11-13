/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Dependency additions for newly introduced JUnit Platform Suite annotations. */
final class PlatformSuiteDependencies {
	static final String API_PACKAGE= "org.junit.platform.suite.api"; //$NON-NLS-1$
	private static final String SUITE_TYPE= API_PACKAGE + ".Suite"; //$NON-NLS-1$
	private static final String MAVEN_NAMESPACE= "http://maven.apache.org/POM/4.0.0"; //$NON-NLS-1$

	private PlatformSuiteDependencies() {
	}

	static boolean usesSuite(String source, Map<String, String> options) {
		if (!source.contains(API_PACKAGE)) {
			return false;
		}
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setCompilerOptions(options);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		boolean imported= false;
		for (Object item : root.imports()) {
			ImportDeclaration declaration= (ImportDeclaration) item;
			String name= declaration.getName().getFullyQualifiedName();
			if (!declaration.isStatic() && SUITE_TYPE.equals(name)) {
				imported= true;
				break;
			}
		}
		final boolean simpleNameAllowed= imported;
		class SuiteVisitor extends ASTVisitor {
			boolean found;

			private boolean annotation(Annotation node) {
				String name= node.getTypeName().getFullyQualifiedName();
				found |= SUITE_TYPE.equals(name) || simpleNameAllowed && "Suite".equals(name); //$NON-NLS-1$
				return false;
			}

			@Override
			public boolean visit(MarkerAnnotation node) {
				return annotation(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				return annotation(node);
			}
		}
		SuiteVisitor visitor= new SuiteVisitor();
		root.accept(visitor);
		return visitor.found;
	}

	static String addToPom(String source) throws IOException {
		Element project= parse(source);
		if (!"project".equals(project.getTagName()) || !MAVEN_NAMESPACE.equals(project.getNamespaceURI())) { //$NON-NLS-1$
			throw unsupported("expected an unprefixed Maven 4.0.0 project"); //$NON-NLS-1$
		}
		Element dependencies= child(project, "dependencies"); //$NON-NLS-1$
		if (dependencies == null || dependencies.hasAttributes() || child(project, "profiles") != null //$NON-NLS-1$
				|| child(project, "parent") != null || child(project, "modules") != null) { //$NON-NLS-1$ //$NON-NLS-2$
			throw unsupported("expected standalone dependencies without inheritance, profiles or modules"); //$NON-NLS-1$
		}
		String packaging= value(project, "packaging"); //$NON-NLS-1$
		if (!packaging.isEmpty() && !"jar".equals(packaging)) { //$NON-NLS-1$
			throw unsupported("expected jar packaging"); //$NON-NLS-1$
		}
		for (Element dep : children(dependencies, "dependency")) { //$NON-NLS-1$
			if (coordinate(dep, "org.junit.platform", "junit-platform-suite")) { //$NON-NLS-1$ //$NON-NLS-2$
				String scope= value(dep, "scope"); //$NON-NLS-1$
				String type= value(dep, "type"); //$NON-NLS-1$
				if ((type.isEmpty() || "jar".equals(type)) && value(dep, "classifier").isEmpty() //$NON-NLS-1$ //$NON-NLS-2$
						&& child(dep, "exclusions") == null && (scope.isEmpty() || "test".equals(scope) //$NON-NLS-1$ //$NON-NLS-2$
								|| "compile".equals(scope))) { //$NON-NLS-1$
					return source;
				}
				throw unsupported("the existing Suite dependency has a nonstandard type, scope, classifier or exclusions"); //$NON-NLS-1$
			}
		}
		Element management= child(project, "dependencyManagement"); //$NON-NLS-1$
		Element managed= management == null ? null : child(management, "dependencies"); //$NON-NLS-1$
		List<Element> managedEntries= managed == null ? List.of() : children(managed, "dependency"); //$NON-NLS-1$
		if (managedEntries.size() != 1 || !supportedBom(managedEntries.getFirst())) {
			throw unsupported("expected a single imported JUnit 5.8+ or 6 BOM with a fixed release version"); //$NON-NLS-1$
		}
		// Reparse the candidate insertion and verify that it added exactly the intended
		// direct dependency. Literal tag text in a comment or CDATA cannot authorize it.
		String closing= "</dependencies>"; //$NON-NLS-1$
		String newline= source.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		for (int offset= source.indexOf(closing); offset >= 0; offset= source.indexOf(closing, offset + closing.length())) {
			int line= source.lastIndexOf('\n', offset) + 1;
			String indent= source.substring(line, offset);
			if (!indent.isBlank()) {
				continue;
			}
			String unit= indent.contains("\t") ? "\t" : "  "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String dependencyIndent= indent + unit;
			String fieldIndent= dependencyIndent + unit;
			String added= String.join(newline, dependencyIndent + "<dependency>", //$NON-NLS-1$
					fieldIndent + "<groupId>org.junit.platform</groupId>", //$NON-NLS-1$
					fieldIndent + "<artifactId>junit-platform-suite</artifactId>", //$NON-NLS-1$
					fieldIndent + "<scope>test</scope>", dependencyIndent + "</dependency>", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String candidate= source.substring(0, line) + added + source.substring(line);
			Element modified= child(parse(candidate), "dependencies"); //$NON-NLS-1$
			if (children(modified, "dependency").stream().anyMatch(dep -> //$NON-NLS-1$
					coordinate(dep, "org.junit.platform", "junit-platform-suite"))) { //$NON-NLS-1$ //$NON-NLS-2$
				return candidate;
			}
		}
		throw unsupported("could not locate the direct dependencies closing tag on its own line"); //$NON-NLS-1$
	}

	private static boolean supportedBom(Element dependency) {
		return coordinate(dependency, "org.junit", "junit-bom") && "pom".equals(value(dependency, "type")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				&& "import".equals(value(dependency, "scope")) && child(dependency, "exclusions") == null //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				&& value(dependency, "version").matches("(?:5\\.(?:[89]|[1-9][0-9]+)|6\\.[0-9]+)\\.[0-9]+"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Element parse(String source) throws IOException {
		try {
			DocumentBuilderFactory factory= DocumentBuilderFactory.newDefaultNSInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); //$NON-NLS-1$
			var builder= factory.newDocumentBuilder();
			builder.setErrorHandler(new DefaultHandler());
			return builder.parse(new InputSource(new StringReader(source))).getDocumentElement();
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Cannot read the project's Maven dependency model", e); //$NON-NLS-1$
		}
	}

	private static List<Element> children(Element parent, String name) {
		List<Element> result= new ArrayList<>();
		for (Node node= parent.getFirstChild(); node != null; node= node.getNextSibling()) {
			if (node instanceof Element element && MAVEN_NAMESPACE.equals(element.getNamespaceURI())
					&& name.equals(element.getTagName())) {
				result.add(element);
			}
		}
		return result;
	}

	private static Element child(Element parent, String name) {
		List<Element> found= children(parent, name);
		if (found.size() > 1) {
			throw new IllegalArgumentException("Ambiguous Maven element: " + name); //$NON-NLS-1$
		}
		return found.isEmpty() ? null : found.getFirst();
	}

	private static String value(Element parent, String name) {
		Element element= child(parent, name);
		return element == null ? "" : element.getTextContent().strip(); //$NON-NLS-1$
	}

	private static boolean coordinate(Element dependency, String group, String artifact) {
		return group.equals(value(dependency, "groupId")) && artifact.equals(value(dependency, "artifactId")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static IOException unsupported(String detail) {
		return new IOException("Cannot plan the JUnit Platform Suite dependency: " + detail); //$NON-NLS-1$
	}
}
