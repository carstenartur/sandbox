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

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/** Keeps existing wildcard-bound names unambiguous when a stream import becomes a wildcard. */
final class LoopImportPreservation {

	private LoopImportPreservation() {
	}

	static void retainOnDemandTypeBindings(CompilationUnitRewrite rewrite) throws JavaModelException {
		Set<String> addedPackages= new HashSet<>();
		for (String added : rewrite.getImportRewrite().getAddedImports()) {
			int separator= added.lastIndexOf('.');
			if (separator > 0) addedPackages.add(added.substring(0, separator));
		}
		if (addedPackages.isEmpty()) return;
		Set<String> originalPackages= new HashSet<>();
		for (Object item : rewrite.getRoot().imports()) {
			ImportDeclaration declaration= (ImportDeclaration) item;
			if (!declaration.isStatic() && declaration.isOnDemand()) {
				originalPackages.add(declaration.getName().getFullyQualifiedName());
			}
		}
		if (originalPackages.isEmpty()) return;
		Set<String> removedTypes= new HashSet<>();
		for (IBinding binding : rewrite.getImportRemover().getImportsToRemove()) {
			if (binding instanceof ITypeBinding type) removedTypes.add(type.getTypeDeclaration().getQualifiedName());
		}
		List<SimpleName> references= new ArrayList<>();
		ImportReferencesCollector.collect(rewrite.getRoot(), rewrite.getCu().getJavaProject(),
				null, references, new ArrayList<>());
		Set<String> considered= new HashSet<>();
		Set<String> pinnedPackages= new HashSet<>();
		for (SimpleName reference : references) {
			if (!(reference.resolveBinding() instanceof ITypeBinding type) || type.isRecovered()
					|| type.getPackage() == null || !originalPackages.contains(type.getPackage().getName())) continue;
			String name= type.getTypeDeclaration().getQualifiedName();
			if (removedTypes.contains(name) || !considered.add(name)) continue;
			for (String addedPackage : addedPackages) {
				if (!addedPackage.equals(type.getPackage().getName())
						&& rewrite.getCu().getJavaProject().findType(addedPackage + '.' + reference.getIdentifier()) != null) {
					rewrite.getImportRewrite().addImport(name, new ImportRewriteContext() {
						@Override
						public int findInContext(String qualifier, String simpleName, int kind) {
							return RES_NAME_UNKNOWN_NEEDS_EXPLICIT_IMPORT;
						}
					});
					pinnedPackages.add(type.getPackage().getName());
					break;
				}
			}
		}
		if (pinnedPackages.isEmpty()) return;
		Set<String> explicitTypes= new HashSet<>(List.of(rewrite.getImportRewrite().getAddedImports()));
		for (Object item : rewrite.getRoot().imports()) {
			ImportDeclaration declaration= (ImportDeclaration) item;
			if (!declaration.isStatic() && !declaration.isOnDemand()) {
				explicitTypes.add(declaration.getName().getFullyQualifiedName());
			}
		}
		for (SimpleName reference : references) {
			if (!(reference.resolveBinding() instanceof ITypeBinding type) || type.isRecovered()) return;
			String name= type.getTypeDeclaration().getQualifiedName();
			if (!removedTypes.contains(name) && !explicitTypes.contains(name) && type.getPackage() != null) {
				pinnedPackages.remove(type.getPackage().getName());
			}
		}
		for (String unusedPackage : pinnedPackages) {
			rewrite.getImportRewrite().removeImport(unusedPackage + ".*"); //$NON-NLS-1$
		}
	}
}
