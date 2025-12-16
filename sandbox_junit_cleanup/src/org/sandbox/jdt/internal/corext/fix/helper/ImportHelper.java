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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Helper class for managing import declarations during JUnit migration.
 * Handles adding, removing, and transforming imports from JUnit 4 to JUnit 5.
 */
public final class ImportHelper {

	// Private constructor to prevent instantiation
	private ImportHelper() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Adds an import to the class. This method should be used for every class
	 * reference added to the generated code.
	 *
	 * @param typeName  a fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast       AST
	 * @return simple name of a class if the import was added and fully qualified
	 *         name if there was a conflict
	 */
	public static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName = cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	/**
	 * Handles import declaration changes for migrating JUnit 4 to JUnit 5.
	 * Supports both static and regular imports, including wildcard imports.
	 * 
	 * @param node the import declaration to change
	 * @param importRewriter the import rewriter to use
	 * @param sourceClass the JUnit 4 fully qualified class name (e.g., "org.junit.Assert")
	 * @param targetClass the JUnit 5 fully qualified class name (e.g., "org.junit.jupiter.api.Assertions")
	 */
	public static void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter,
			String sourceClass, String targetClass) {
		String importName = node.getName().getFullyQualifiedName();

		// Handle static wildcard import (e.g., import static org.junit.Assert.*)
		if (node.isStatic() && importName.equals(sourceClass)) {
			importRewriter.removeStaticImport(sourceClass + ".*");
			importRewriter.addStaticImport(targetClass, "*", false);
			return;
		}

		// Handle regular class import (e.g., import org.junit.Assert)
		if (importName.equals(sourceClass)) {
			importRewriter.removeImport(sourceClass);
			importRewriter.addImport(targetClass);
			return;
		}

		// Handle static method import (e.g., import static org.junit.Assert.assertEquals)
		if (node.isStatic() && importName.startsWith(sourceClass + ".")) {
			String methodName = importName.substring((sourceClass + ".").length());
			importRewriter.removeStaticImport(sourceClass + "." + methodName);
			importRewriter.addStaticImport(targetClass, methodName, false);
		}
	}

	/**
	 * Adds a JUnit 5 callback interface to a type's super interface list if not already present.
	 * Used when refactoring ExternalResource implementations to implement callback interfaces.
	 * 
	 * @param listRewrite the list rewrite for the super interface types
	 * @param ast the AST instance
	 * @param simpleCallbackName the simple name of the callback interface (e.g., "BeforeEachCallback")
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 * @param fullyQualifiedCallbackName the fully qualified name of the callback interface to import
	 */
	public static void addInterfaceCallback(org.eclipse.jdt.core.dom.rewrite.ListRewrite listRewrite, AST ast,
			String simpleCallbackName, org.eclipse.text.edits.TextEditGroup group,
			ImportRewrite importRewriter, String fullyQualifiedCallbackName) {
		// Check if the interface already exists in the list
		boolean hasCallback = listRewrite.getRewrittenList().stream()
				.anyMatch(type -> type instanceof org.eclipse.jdt.core.dom.SimpleType
						&& simpleCallbackName.equals(((org.eclipse.jdt.core.dom.SimpleType) type).getName().getFullyQualifiedName()));

		if (!hasCallback) {
			// Add interface if it doesn't already exist
			listRewrite.insertLast(ast.newSimpleType(ast.newName(simpleCallbackName)), group);
		}
		importRewriter.addImport(fullyQualifiedCallbackName);
	}
}
