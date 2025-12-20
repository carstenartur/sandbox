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

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Represents a candidate XML file for cleanup transformation.
 */
public class XMLCandidateHit {

	/** The AST node (used for Eclipse cleanup framework integration) */
	public ASTNode whileStatement;
	
	/** The XML file to be processed */
	public IFile file;
	
	/** Original content before transformation */
	public String originalContent;
	
	/** Transformed content after processing */
	public String transformedContent;
	
	/**
	 * Create a hit with file information.
	 * 
	 * @param file the file to process
	 * @param originalContent the original file content
	 */
	public XMLCandidateHit(IFile file, String originalContent) {
		this.file = file;
		this.originalContent = originalContent;
	}
	
	/**
	 * Default constructor for backward compatibility.
	 */
	public XMLCandidateHit() {
	}
}
