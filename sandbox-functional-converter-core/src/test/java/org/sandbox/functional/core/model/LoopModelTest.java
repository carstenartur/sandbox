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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LoopModel} and related classes.
 * 
 * <p>This tests the core model classes for the Unified Loop Representation.</p>
 */
class LoopModelTest {
	
	@Test
	void testLoopModelCanBeInstantiated() {
		LoopModel model = new LoopModel();
		assertNotNull(model, "LoopModel should not be null");
	}
	
	@Test
	void testLoopModelToString() {
		LoopModel model = new LoopModel();
		String result = model.toString();
		assertThat(result)
			.isNotNull()
			.contains("LoopModel");
	}
	
	@Test
	void testLoopModelWithComponents() {
		SourceDescriptor source = new SourceDescriptor(
			SourceDescriptor.SourceType.COLLECTION, 
			"myList", 
			"String"
		);
		ElementDescriptor element = new ElementDescriptor("item", "String", false);
		LoopMetadata metadata = new LoopMetadata(false, false, false, false, true);
		
		LoopModel model = new LoopModel(source, element, metadata);
		
		assertThat(model.getSource()).isEqualTo(source);
		assertThat(model.getElement()).isEqualTo(element);
		assertThat(model.getMetadata()).isEqualTo(metadata);
	}
	
	@Test
	void testSourceDescriptor() {
		SourceDescriptor source = new SourceDescriptor(
			SourceDescriptor.SourceType.ARRAY,
			"myArray",
			"Integer"
		);
		
		assertThat(source.type()).isEqualTo(SourceDescriptor.SourceType.ARRAY);
		assertThat(source.expression()).isEqualTo("myArray");
		assertThat(source.elementTypeName()).isEqualTo("Integer");
	}
	
	@Test
	void testElementDescriptor() {
		ElementDescriptor element = new ElementDescriptor("x", "int", true);
		
		assertThat(element.variableName()).isEqualTo("x");
		assertThat(element.typeName()).isEqualTo("int");
		assertThat(element.isFinal()).isTrue();
	}
	
	@Test
	void testLoopMetadata() {
		LoopMetadata metadata = new LoopMetadata(true, false, false, false, true);
		
		assertThat(metadata.hasBreak()).isTrue();
		assertThat(metadata.hasContinue()).isFalse();
		assertThat(metadata.hasReturn()).isFalse();
		assertThat(metadata.modifiesCollection()).isFalse();
		assertThat(metadata.requiresOrdering()).isTrue();
	}
}
