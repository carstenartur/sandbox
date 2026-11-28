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
package org.sandbox.jdt.core.cleanupapp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.internal.common.MigrationSupportProvider;

/** Jupiter package references introduced by any source cleanup, including hint rules. */
final class JupiterDependencies {

	// Match a child package before its parent; nested types belong to the same package.
	private static final List<String> API_PACKAGES= Set.of(
			"org.junit.jupiter.api", "org.junit.jupiter.api.condition", //$NON-NLS-1$ //$NON-NLS-2$
			"org.junit.jupiter.api.extension", "org.junit.jupiter.api.extension.support", //$NON-NLS-1$ //$NON-NLS-2$
			"org.junit.jupiter.api.function", "org.junit.jupiter.api.io", //$NON-NLS-1$ //$NON-NLS-2$
			"org.junit.jupiter.api.parallel", "org.junit.jupiter.api.util", //$NON-NLS-1$ //$NON-NLS-2$
			"org.junit.jupiter.params", "org.junit.jupiter.params.aggregator", //$NON-NLS-1$ //$NON-NLS-2$
			"org.junit.jupiter.params.converter", "org.junit.jupiter.params.provider", //$NON-NLS-1$ //$NON-NLS-2$
			"org.junit.jupiter.params.support") //$NON-NLS-1$
			.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();

	private JupiterDependencies() {
	}

	static final List<String> MIXED_PACKAGES= List.of(MigrationSupportProvider.PACKAGE, "org.junit.jupiter.api", //$NON-NLS-1$
			"org.junit.jupiter.api.extension", "org.junit.jupiter.api.function", "org.junit.jupiter.api.parallel"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	record Introduced(Set<String> packages, boolean mixedAdapter) {}
	private record References(Set<String> packages, Set<String> registrations) {}

	static Introduced introducedPackages(String before, String after, Map<String, String> options) {
		var original= references(before, options);
		var updated= references(after, options);
		var introduced= updated.packages();
		introduced.removeAll(original.packages());
		boolean addedAdapter= !original.registrations().containsAll(updated.registrations());
		if (addedAdapter) introduced.addAll(MIXED_PACKAGES);
		return new Introduced(introduced, addedAdapter);
	}

	private static References references(String source, Map<String, String> options) {
		var result= new References(new TreeSet<>(), new TreeSet<>());
		var packages= result.packages();
		if (!source.contains("org.junit.jupiter.") && !source.contains(MigrationSupportProvider.PACKAGE)) { //$NON-NLS-1$
			return result;
		}
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setCompilerOptions(options);
		parser.createAST(null).accept(new ASTVisitor() {
			private void reference(String name, boolean packageImport) {
				if (name.equals(MigrationSupportProvider.FACTORY) || name.startsWith(MigrationSupportProvider.FACTORY + '.')
						|| packageImport && name.equals(MigrationSupportProvider.PACKAGE)) {
					packages.add(MigrationSupportProvider.PACKAGE);
					return;
				}
				for (String candidate : API_PACKAGES) {
					if (packageImport && candidate.equals(name)
							|| name.startsWith(candidate + '.') && name.length() > candidate.length() + 1
									&& Character.isUpperCase(name.charAt(candidate.length() + 1))) {
						packages.add(candidate);
						return;
					}
				}
			}

			@Override
			public boolean visit(ImportDeclaration node) {
				reference(node.getName().getFullyQualifiedName(), node.isOnDemand() && !node.isStatic());
				return false;
			}

			@Override
			public boolean visit(QualifiedName node) {
				reference(node.getFullyQualifiedName(), false);
				return false;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				// The generated three-argument call site links ThrowingConsumer even though
				// its inferred type does not appear in the Java source.
				if (node.arguments().size() == 3 && node.getExpression() instanceof Name qualifier
						&& MigrationSupportProvider.FACTORY.equals(qualifier.getFullyQualifiedName())
						&& Set.of("perTest", "perClass").contains(node.getName().getIdentifier())) { //$NON-NLS-1$ //$NON-NLS-2$
					packages.add("org.junit.jupiter.api.function"); //$NON-NLS-1$
					if (node.getParent() instanceof VariableDeclarationFragment field
							&& field.getInitializer() == node && field.getParent() instanceof FieldDeclaration declaration
							&& declaration.getParent() instanceof TypeDeclaration owner && owner.isPackageMemberTypeDeclaration()) {
						for (Object modifier : declaration.modifiers()) {
							if (modifier instanceof Annotation annotation && "org.junit.jupiter.api.extension.RegisterExtension" //$NON-NLS-1$
									.equals(annotation.getTypeName().getFullyQualifiedName())) {
								result.registrations().add(owner.getName() + "." + field.getName() + "." + node.getName()); //$NON-NLS-1$ //$NON-NLS-2$
							}
						}
					}
				}
				return true;
			}

			@Override
			public boolean visit(Javadoc node) {
				return false;
			}

			@Override
			public boolean visit(PackageDeclaration node) {
				return false;
			}
		});
		return result;
	}
}
