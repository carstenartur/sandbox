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
package org.sandbox.functional.core.model;

/**
 * Metadata about loop characteristics and constraints.
 * 
 * <p>This class captures information about loop control flow and side effects
 * that affect convertibility to functional style.</p>
 * 
 * @since 1.0.0
 */
public class LoopMetadata {
	
	private boolean hasBreak;
	private boolean hasContinue;
	private boolean hasReturn;
	private boolean modifiesCollection;
	private boolean requiresOrdering;
	
	/**
	 * Default constructor.
	 */
	public LoopMetadata() {
	}
	
	/**
	 * Creates a new LoopMetadata.
	 * 
	 * @param hasBreak whether the loop contains break statements
	 * @param hasContinue whether the loop contains continue statements
	 * @param hasReturn whether the loop contains return statements
	 * @param modifiesCollection whether the loop modifies the collection being iterated
	 * @param requiresOrdering whether the loop requires ordering to be preserved
	 */
	public LoopMetadata(boolean hasBreak, boolean hasContinue, boolean hasReturn, 
			boolean modifiesCollection, boolean requiresOrdering) {
		this.hasBreak = hasBreak;
		this.hasContinue = hasContinue;
		this.hasReturn = hasReturn;
		this.modifiesCollection = modifiesCollection;
		this.requiresOrdering = requiresOrdering;
	}
	
	/**
	 * Checks if the loop has break statements.
	 * 
	 * @return true if the loop has break statements
	 */
	public boolean hasBreak() {
		return hasBreak;
	}
	
	/**
	 * Sets whether the loop has break statements.
	 * 
	 * @param hasBreak true if the loop has break statements
	 */
	public void setHasBreak(boolean hasBreak) {
		this.hasBreak = hasBreak;
	}
	
	/**
	 * Checks if the loop has continue statements.
	 * 
	 * @return true if the loop has continue statements
	 */
	public boolean hasContinue() {
		return hasContinue;
	}
	
	/**
	 * Sets whether the loop has continue statements.
	 * 
	 * @param hasContinue true if the loop has continue statements
	 */
	public void setHasContinue(boolean hasContinue) {
		this.hasContinue = hasContinue;
	}
	
	/**
	 * Checks if the loop has return statements.
	 * 
	 * @return true if the loop has return statements
	 */
	public boolean hasReturn() {
		return hasReturn;
	}
	
	/**
	 * Sets whether the loop has return statements.
	 * 
	 * @param hasReturn true if the loop has return statements
	 */
	public void setHasReturn(boolean hasReturn) {
		this.hasReturn = hasReturn;
	}
	
	/**
	 * Checks if the loop modifies the collection being iterated.
	 * 
	 * @return true if the loop modifies the collection
	 */
	public boolean modifiesCollection() {
		return modifiesCollection;
	}
	
	/**
	 * Sets whether the loop modifies the collection.
	 * 
	 * @param modifiesCollection true if the loop modifies the collection
	 */
	public void setModifiesCollection(boolean modifiesCollection) {
		this.modifiesCollection = modifiesCollection;
	}
	
	/**
	 * Checks if the loop requires ordering to be preserved.
	 * 
	 * @return true if ordering is required
	 */
	public boolean requiresOrdering() {
		return requiresOrdering;
	}
	
	/**
	 * Sets whether the loop requires ordering.
	 * 
	 * @param requiresOrdering true if ordering is required
	 */
	public void setRequiresOrdering(boolean requiresOrdering) {
		this.requiresOrdering = requiresOrdering;
	}
	
	@Override
	public String toString() {
		return "LoopMetadata[hasBreak=" + hasBreak + ", hasContinue=" + hasContinue 
				+ ", hasReturn=" + hasReturn + ", modifiesCollection=" + modifiesCollection 
				+ ", requiresOrdering=" + requiresOrdering + "]";
	}
}
