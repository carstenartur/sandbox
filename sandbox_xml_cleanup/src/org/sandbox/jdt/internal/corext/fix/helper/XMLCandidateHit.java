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

/** Represents a prepared XML cleanup candidate. */
public class XMLCandidateHit {

	/** The AST node used for Eclipse cleanup framework integration. */
	public ASTNode whileStatement;

	/** The XML file to be processed. */
	public IFile file;

	/** Prepared transformation including original bytes and conflict stamp. */
	public XMLResourceSupport.Transformation transformation;

	/** Create a hit with a safely prepared transformation. */
	public XMLCandidateHit(IFile file, XMLResourceSupport.Transformation transformation) {
		this.file= file;
		this.transformation= transformation;
	}

	/** Default constructor for framework compatibility. */
	public XMLCandidateHit() {
	}
}
