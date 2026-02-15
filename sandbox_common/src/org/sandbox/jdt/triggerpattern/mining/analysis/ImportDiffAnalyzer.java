/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.mining.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;

/**
 * Analyzes import changes between two {@link CompilationUnit}s and produces an
 * {@link ImportDirective} describing which imports were added or removed.
 *
 * @since 1.2.6
 */
public class ImportDiffAnalyzer {

	/**
	 * Compares the import lists of two compilation units.
	 *
	 * @param before the original compilation unit
	 * @param after  the modified compilation unit
	 * @return an {@link ImportDirective} representing the changes, or {@code null}
	 *         if there are no changes
	 */
	@SuppressWarnings("unchecked")
	public ImportDirective analyzeImportChanges(CompilationUnit before, CompilationUnit after) {
		Set<String> beforeImports = collectImports(before.imports());
		Set<String> afterImports = collectImports(after.imports());

		Set<String> beforeStatic = collectStaticImports(before.imports());
		Set<String> afterStatic = collectStaticImports(after.imports());

		ImportDirective directive = new ImportDirective();
		boolean hasChanges = false;

		// Added imports
		for (String imp : afterImports) {
			if (!beforeImports.contains(imp)) {
				directive.addImport(imp);
				hasChanges = true;
			}
		}

		// Removed imports
		for (String imp : beforeImports) {
			if (!afterImports.contains(imp)) {
				directive.removeImport(imp);
				hasChanges = true;
			}
		}

		// Added static imports
		for (String imp : afterStatic) {
			if (!beforeStatic.contains(imp)) {
				directive.addStaticImport(imp);
				hasChanges = true;
			}
		}

		// Removed static imports
		for (String imp : beforeStatic) {
			if (!afterStatic.contains(imp)) {
				directive.removeStaticImport(imp);
				hasChanges = true;
			}
		}

		return hasChanges ? directive : null;
	}

	private Set<String> collectImports(List<ImportDeclaration> imports) {
		Set<String> result = new HashSet<>();
		for (ImportDeclaration imp : imports) {
			if (!imp.isStatic()) {
				result.add(imp.getName().getFullyQualifiedName());
			}
		}
		return result;
	}

	private Set<String> collectStaticImports(List<ImportDeclaration> imports) {
		Set<String> result = new HashSet<>();
		for (ImportDeclaration imp : imports) {
			if (imp.isStatic()) {
				result.add(imp.getName().getFullyQualifiedName());
			}
		}
		return result;
	}
}
