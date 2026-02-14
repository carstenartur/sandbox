/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.HelperVisitorProvider;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Data holder for functional loop conversion context.
 * 
 * <p>
 * This class serves as a container for the enhanced for-loop being analyzed
 * for potential conversion to functional stream operations. It implements
 * {@link HelperVisitorProvider} to integrate with the sandbox common visitor
 * framework.
 * </p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * FunctionalHolder holder = new FunctionalHolder();
 * holder.setEnhancedForStatement(forLoop);
 * // Use holder in visitor pattern processing
 * }</pre>
 * 
 * @see HelperVisitorProvider
 * @see ReferenceHolder
 */
public class FunctionalHolder 
		implements HelperVisitorProvider<ReferenceHolder<String, Object>, String, FunctionalHolder> {

	/**
	 * The enhanced for-loop statement being analyzed for conversion.
	 */
	private EnhancedForStatement enhancedForStatement;
	
	/**
	 * The helper visitor used for traversing the AST.
	 */
	private HelperVisitor<FunctionalHolder, ReferenceHolder<String, Object>, String> helperVisitor;

	/**
	 * Default constructor.
	 */
	public FunctionalHolder() {
		// Default constructor
	}

	/**
	 * Gets the enhanced for-loop statement.
	 * 
	 * @return the enhanced for-loop statement, or null if not set
	 */
	public EnhancedForStatement getEnhancedForStatement() {
		return enhancedForStatement;
	}

	/**
	 * Sets the enhanced for-loop statement to analyze.
	 * 
	 * @param enhancedForStatement the for-loop to set
	 */
	public void setEnhancedForStatement(EnhancedForStatement enhancedForStatement) {
		this.enhancedForStatement = enhancedForStatement;
	}

	@Override
	public HelperVisitor<FunctionalHolder, ReferenceHolder<String, Object>, String> getHelperVisitor() {
		return helperVisitor;
	}

	@Override
	public void setHelperVisitor(HelperVisitor<FunctionalHolder, ReferenceHolder<String, Object>, String> hv) {
		this.helperVisitor = hv;
	}
}
