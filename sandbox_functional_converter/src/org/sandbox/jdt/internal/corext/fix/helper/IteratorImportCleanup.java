/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/** Completes ImportRemover's single-type removal for erased iterator imports. */
final class IteratorImportCleanup {
	private IteratorImportCleanup() {
	}

	static void removeUnusedPackageImports(CompilationUnitRewrite cuRewrite) {
		Set<String> explicitTypes= new HashSet<>();
		List<ImportDeclaration> packageImports= new ArrayList<>();
		for (Object item : cuRewrite.getRoot().imports()) {
			ImportDeclaration declaration= (ImportDeclaration) item;
			if (declaration.isStatic()) {
				continue;
			}
			if (!declaration.isOnDemand()) {
				explicitTypes.add(declaration.getName().getFullyQualifiedName());
			} else if (declaration.resolveBinding() instanceof IPackageBinding) {
				packageImports.add(declaration);
			}
		}
		if (packageImports.isEmpty()) {
			return;
		}

		Set<String> removedTypes= new HashSet<>();
		Set<String> affectedPackages= new HashSet<>();
		for (IBinding binding : cuRewrite.getImportRemover().getImportsToRemove()) {
			if (binding instanceof ITypeBinding type && !type.isRecovered()) {
				String name= type.getTypeDeclaration().getQualifiedName();
				removedTypes.add(name);
				if (!explicitTypes.contains(name) && type.getPackage() != null) {
					affectedPackages.add(type.getPackage().getName());
				}
			}
		}
		if (affectedPackages.isEmpty()) {
			return;
		}

		// The collector also accounts for annotations, type arguments and Javadoc.
		// ImportRemover's result already excludes retained and registered added uses.
		List<SimpleName> references= new ArrayList<>();
		ImportReferencesCollector.collect(cuRewrite.getRoot(), cuRewrite.getCu().getJavaProject(),
				null, references, new ArrayList<>());
		for (SimpleName reference : references) {
			if (!(reference.resolveBinding() instanceof ITypeBinding type) || type.isRecovered()) {
				return; // An unresolved reference might still depend on any wildcard.
			}
			String name= type.getTypeDeclaration().getQualifiedName();
			if (!removedTypes.contains(name) && !explicitTypes.contains(name) && type.getPackage() != null) {
				affectedPackages.remove(type.getPackage().getName());
			}
		}
		for (ImportDeclaration declaration : packageImports) {
			String name= declaration.getName().getFullyQualifiedName();
			if (affectedPackages.contains(name)) {
				cuRewrite.getImportRewrite().removeImport(name + ".*"); //$NON-NLS-1$
			}
		}
	}
}
