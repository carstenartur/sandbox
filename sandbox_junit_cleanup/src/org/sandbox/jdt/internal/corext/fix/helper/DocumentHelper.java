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

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Helper class for document-related operations during JUnit migration.
 * Handles document access and compilation unit change creation.
 */
public final class DocumentHelper {

	// Private constructor to prevent instantiation
	private DocumentHelper() {
		throw new UnsupportedOperationException("Utility class");
	}

	/**
	 * Gets the document for a compilation unit.
	 * Used to access the text buffer for AST rewrite operations.
	 * 
	 * @param compilationUnit the compilation unit
	 * @return the document associated with the compilation unit
	 */
	public static IDocument getDocumentForCompilationUnit(CompilationUnit compilationUnit) {
		if (compilationUnit == null || compilationUnit.getJavaElement() == null) {
			throw new IllegalArgumentException("Invalid CompilationUnit or missing JavaElement.");
		}

		ICompilationUnit icu = (ICompilationUnit) compilationUnit.getJavaElement();
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();

		try {
			// Connect to the file corresponding to the CompilationUnit
			bufferManager.connect(icu.getPath(), LocationKind.IFILE, null);

			// Get the associated TextFileBuffer
			ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(icu.getPath(), LocationKind.IFILE);

			if (textFileBuffer == null) {
				throw new RuntimeException("No text file buffer found for the provided compilation unit.");
			}

			// Return the document
			return textFileBuffer.getDocument();
		} catch (CoreException e) {
			throw new RuntimeException("Failed to connect to text file buffer: " + e.getMessage(), e);
		} finally {
			try {
				// Disconnect from the buffer
				bufferManager.disconnect(icu.getPath(), LocationKind.IFILE, null);
			} catch (CoreException e) {
				// Disconnection failed, wrap and re-throw
				throw new RuntimeException("Failed to disconnect from text file buffer: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Creates a compilation unit change for the given AST rewrite.
	 * Used when creating changes in separate compilation units during refactoring.
	 * 
	 * @param compilationUnit the compilation unit being modified
	 * @param rewrite the AST rewrite to apply
	 * @return the compilation unit change
	 */
	public static CompilationUnitChange createChangeForRewrite(CompilationUnit compilationUnit, ASTRewrite rewrite) {
		try {
			// Access the IDocument of the CompilationUnit
			IDocument document = getDocumentForCompilationUnit(compilationUnit);

			// Describe changes (but don't apply them)
			TextEdit edits = rewrite.rewriteAST(document, null);

			// Create a TextChange object
			CompilationUnitChange change = new CompilationUnitChange("JUnit Migration",
					(ICompilationUnit) compilationUnit.getJavaElement());
			change.setEdit(edits);

			// Optional: Add comments or markers
			change.addTextEditGroup(new TextEditGroup("Migrate JUnit", edits));

			return change;

		} catch (Exception e) {
			throw new RuntimeException("Error creating change for rewrite: " + e.getMessage(), e);
		}
	}
}
