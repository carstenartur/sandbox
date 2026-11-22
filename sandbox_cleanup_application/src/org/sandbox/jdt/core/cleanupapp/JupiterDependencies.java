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
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;

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

	static Set<String> introducedPackages(String before, String after, Map<String, String> options) {
		Set<String> introduced= referencedPackages(after, options);
		introduced.removeAll(referencedPackages(before, options));
		return introduced;
	}

	private static Set<String> referencedPackages(String source, Map<String, String> options) {
		Set<String> packages= new TreeSet<>();
		if (!source.contains("org.junit.jupiter.")) { //$NON-NLS-1$
			return packages;
		}
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setCompilerOptions(options);
		parser.createAST(null).accept(new ASTVisitor() {
			private void reference(String name, boolean packageImport) {
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
			public boolean visit(Javadoc node) {
				return false;
			}

			@Override
			public boolean visit(PackageDeclaration node) {
				return false;
			}
		});
		return packages;
	}
}
