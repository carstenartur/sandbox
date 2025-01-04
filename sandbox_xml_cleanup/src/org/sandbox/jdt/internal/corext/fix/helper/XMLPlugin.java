/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore;

/**
 *
 */
public class XMLPlugin extends AbstractTool<XMLCandidateHit> {


	 // Klassenweiter Cache für bereits verarbeitete Dateien
    private final Set<Path> processedFiles = new HashSet<>();

    public void find(XMLCleanUpFixCore fixcore, CompilationUnit compilationUnit,
            Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed,
            boolean createForOnlyIfVarUsed) {
        try {
            // Hole die zugehörige Resource (allgemeiner Zugriff, nicht nur auf Java-Elemente beschränkt)
            IResource resource = compilationUnit.getJavaElement().getResource();

            if (resource == null || !resource.exists() || !(resource instanceof IFile)) {
                System.out.println("Skipping non-file resource: " + (resource != null ? resource.getName() : "null"));
                return;
            }

            // Iteriere über alle Ressourcen im Projekt
            IProject project = resource.getProject(); // Hole das Eclipse-Projekt
            project.accept(myResource -> {
                if (myResource.getType() == IResource.FILE && myResource instanceof IFile) {
                    IFile file = (IFile) myResource;
                    String extension = file.getFileExtension();
                    if ("xml".equalsIgnoreCase(extension) || "xsd".equalsIgnoreCase(extension) || "exsd".equalsIgnoreCase(extension)) {
                        System.out.println("Found matching file: " + file.getName());
                        try {
                            processFile(file); // Transformation durchführen
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                return true; // Weiter iterieren
            });

        } catch (Exception e) {
            throw new RuntimeException("Error during schema cleanup", e);
        }
    }

    private void processFile(IFile file) throws IOException, Exception {
        // Prüfen, ob die Datei bereits verarbeitet wurde
        Path filePath = file.getLocation().toFile().toPath();
        if (processedFiles.contains(filePath)) {
            System.out.println("Skipping already processed file: " + filePath);
            return; // Datei wurde bereits verarbeitet
        }

        // Prüfen, ob die Datei für die Transformation geeignet ist
        String extension = file.getFileExtension();
        if (extension == null || (!extension.equals("xml") && !extension.equals("xsd") && !extension.equals("exsd"))) {
            System.out.println("Skipping non-XML/XSD/EXSD file: " + file.getName());
            return; // Überspringen
        }

        // Quellcode (Inhalt der XML/XSD/EXSD-Datei) direkt aus der Datei lesen
        String sourceCode = Files.readString(filePath, StandardCharsets.UTF_8);

        // Temporäre Datei erstellen und Transformation anwenden
        Path tempFile = Files.createTempFile("cleanup-schema", "." + extension);
        Files.writeString(tempFile, sourceCode);

        File schemaFile = tempFile.toFile();
        String transformed = SchemaTransformationUtils.transform(schemaFile.toPath());

        // Geänderten Quellcode zurückschreiben
        Files.writeString(filePath, transformed, StandardCharsets.UTF_8);

        // Datei als verarbeitet markieren
        processedFiles.add(filePath);

        System.out.println("Processed file: " + file.getName());
    }

	@Override
	public void rewrite(XMLCleanUpFixCore upp, final XMLCandidateHit hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		//		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		//		AST ast= cuRewrite.getRoot().getAST();

		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRemover remover= cuRewrite.getImportRemover();

		remover.applyRemoves(importRewrite);
	}


	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "\nbla\n\n"; //$NON-NLS-1$
		}
		return "\nblubb\n\n"; //$NON-NLS-1$
	}
}
