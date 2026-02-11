/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.util;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Utility class for managing imports during AST transformations.
 * Provides common import operations used across multiple sandbox cleanup plugins.
 */
public final class ImportUtils {

	private ImportUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Adds an import to the compilation unit and returns the name to use in code.
	 * This method should be used for every class reference added to generated code.
	 *
	 * @param typeName  a fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast       AST
	 * @return simple name of a class if the import was added and fully qualified
	 *         name if there was a conflict
	 */
	public static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName= cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}
}
