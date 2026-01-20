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
 * Unified Loop Representation (ULR) model.
 * 
 * <p>This class provides an AST-independent representation of loop structures
 * that can be transformed into functional/stream-based equivalents.</p>
 * 
 * <p>The model consists of three main components:</p>
 * <ul>
 * <li>{@link SourceDescriptor} - describes what is being iterated over</li>
 * <li>{@link ElementDescriptor} - describes the loop variable</li>
 * <li>{@link LoopMetadata} - describes loop characteristics and constraints</li>
 * </ul>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
 * @since 1.0.0
 */
public class LoopModel {
	
	private SourceDescriptor source;
	private ElementDescriptor element;
	private LoopMetadata metadata;
	
	/**
	 * Default constructor.
	 */
	public LoopModel() {
	}
	
	/**
	 * Creates a new LoopModel.
	 * 
	 * @param source the source descriptor
	 * @param element the element descriptor
	 * @param metadata the loop metadata
	 */
	public LoopModel(SourceDescriptor source, ElementDescriptor element, LoopMetadata metadata) {
		this.source = source;
		this.element = element;
		this.metadata = metadata;
	}
	
	/**
	 * Gets the source descriptor.
	 * 
	 * @return the source descriptor
	 */
	public SourceDescriptor getSource() {
		return source;
	}
	
	/**
	 * Sets the source descriptor.
	 * 
	 * @param source the source descriptor
	 */
	public void setSource(SourceDescriptor source) {
		this.source = source;
	}
	
	/**
	 * Gets the element descriptor.
	 * 
	 * @return the element descriptor
	 */
	public ElementDescriptor getElement() {
		return element;
	}
	
	/**
	 * Sets the element descriptor.
	 * 
	 * @param element the element descriptor
	 */
	public void setElement(ElementDescriptor element) {
		this.element = element;
	}
	
	/**
	 * Gets the loop metadata.
	 * 
	 * @return the loop metadata
	 */
	public LoopMetadata getMetadata() {
		return metadata;
	}
	
	/**
	 * Sets the loop metadata.
	 * 
	 * @param metadata the loop metadata
	 */
	public void setMetadata(LoopMetadata metadata) {
		this.metadata = metadata;
	}
	
	/**
	 * Returns a string representation of this loop model.
	 * 
	 * @return string representation
	 */
	@Override
	public String toString() {
		return "LoopModel[source=" + source + ", element=" + element 
				+ ", metadata=" + metadata + "]";
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		LoopModel other = (LoopModel) obj;
		return java.util.Objects.equals(source, other.source)
				&& java.util.Objects.equals(element, other.element)
				&& java.util.Objects.equals(metadata, other.metadata);
	}
	
	@Override
	public int hashCode() {
		return java.util.Objects.hash(source, element, metadata);
	}
}
