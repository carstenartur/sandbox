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

import java.util.ArrayList;
import java.util.List;
import org.sandbox.functional.core.operation.Operation;
import org.sandbox.functional.core.terminal.TerminalOperation;

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
	private List<Operation> operations = new ArrayList<>();
	private TerminalOperation terminal;
	
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
	 * @return this LoopModel for fluent API
	 */
	public LoopModel setSource(SourceDescriptor source) {
		this.source = source;
		return this;
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
	 * @return this LoopModel for fluent API
	 */
	public LoopModel setElement(ElementDescriptor element) {
		this.element = element;
		return this;
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
	 * @return this LoopModel for fluent API
	 */
	public LoopModel setMetadata(LoopMetadata metadata) {
		this.metadata = metadata;
		return this;
	}
	
	/**
	 * Gets an unmodifiable view of the operations list.
	 * 
	 * @return an unmodifiable list of operations
	 */
	public List<Operation> getOperations() { 
		return java.util.Collections.unmodifiableList(operations); 
	}
	
	/**
	 * Sets the list of operations.
	 * 
	 * <p>The contents of the provided list are copied into the internal
	 * operations list. A {@code null} value clears the current operations.</p>
	 * 
	 * @param operations the operations to set, may be {@code null} to clear
	 */
	public void setOperations(List<Operation> operations) {
		this.operations.clear();
		if (operations != null) {
			this.operations.addAll(operations);
		}
	}
	
	/**
	 * Adds an operation to the pipeline.
	 * 
	 * @param op the operation to add, must not be {@code null}
	 * @return this LoopModel for fluent API
	 * @throws NullPointerException if op is {@code null}
	 */
	public LoopModel addOperation(Operation op) {
		java.util.Objects.requireNonNull(op, "operation must not be null");
		this.operations.add(op);
		return this;
	}
	
	/**
	 * Gets the terminal operation.
	 * 
	 * @return the terminal operation
	 */
	public TerminalOperation getTerminal() { 
		return terminal; 
	}
	
	/**
	 * Sets the terminal operation.
	 * 
	 * @param terminal the terminal operation
	 */
	public void setTerminal(TerminalOperation terminal) {
		this.terminal = terminal;
	}
	
	/**
	 * Sets the terminal operation (fluent API).
	 * 
	 * @param terminal the terminal operation, must not be {@code null}
	 * @return this LoopModel for fluent API
	 * @throws NullPointerException if terminal is {@code null}
	 */
	public LoopModel withTerminal(TerminalOperation terminal) {
		java.util.Objects.requireNonNull(terminal, "terminal operation must not be null");
		this.terminal = terminal;
		return this;
	}
	
	/**
	 * Returns a string representation of this loop model.
	 * 
	 * @return string representation
	 */
	@Override
	public String toString() {
		return "LoopModel[source=" + source + ", element=" + element 
				+ ", metadata=" + metadata + ", operations=" + operations
				+ ", terminal=" + terminal + "]";
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
				&& java.util.Objects.equals(metadata, other.metadata)
				&& java.util.Objects.equals(operations, other.operations)
				&& java.util.Objects.equals(terminal, other.terminal);
	}
	
	@Override
	public int hashCode() {
		return java.util.Objects.hash(source, element, metadata, operations, terminal);
	}
}
