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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.VisitorConfigData;

/**
 * Tests for {@link VisitorConfigData} to verify type-safe visitor configuration.
 * 
 * <p>VisitorConfigData replaces the old stringly-typed Map&lt;String, Object&gt; pattern
 * with a type-safe, immutable value object using the builder pattern.</p>
 */
class VisitorConfigDataTest {

	@Test
	void testBuilderWithMethodName() {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName("toString") //$NON-NLS-1$
				.build();
		
		assertEquals("toString", config.getMethodName()); //$NON-NLS-1$
		assertNull(config.getTypeof());
		assertNull(config.getAnnotationName());
	}

	@Test
	void testBuilderWithTypeof() {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeof(String.class)
				.build();
		
		assertEquals(String.class, config.getTypeof());
		assertNull(config.getMethodName());
	}

	@Test
	void testBuilderWithTypeofByName() {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeofByName("java.lang.String") //$NON-NLS-1$
				.build();
		
		assertEquals("java.lang.String", config.getTypeofByName()); //$NON-NLS-1$
		assertNull(config.getTypeof());
	}

	@Test
	void testBuilderWithMultipleFields() {
		VisitorConfigData config = VisitorConfigData.builder()
				.methodName("equals") //$NON-NLS-1$
				.typeof(Object.class)
				.paramTypeNames(new String[] {"java.lang.Object"}) //$NON-NLS-1$
				.build();
		
		assertEquals("equals", config.getMethodName()); //$NON-NLS-1$
		assertEquals(Object.class, config.getTypeof());
		assertArrayEquals(new String[] {"java.lang.Object"}, config.getParamTypeNames()); //$NON-NLS-1$
	}

	@Test
	void testBuilderWithAnnotationName() {
		VisitorConfigData config = VisitorConfigData.builder()
				.annotationName("org.junit.Test") //$NON-NLS-1$
				.build();
		
		assertEquals("org.junit.Test", config.getAnnotationName()); //$NON-NLS-1$
	}

	@Test
	void testBuilderWithSuperClassName() {
		VisitorConfigData config = VisitorConfigData.builder()
				.superClassName("org.junit.rules.ExternalResource") //$NON-NLS-1$
				.build();
		
		assertEquals("org.junit.rules.ExternalResource", config.getSuperClassName()); //$NON-NLS-1$
	}

	@Test
	void testBuilderWithImportName() {
		VisitorConfigData config = VisitorConfigData.builder()
				.importName("java.util.List") //$NON-NLS-1$
				.build();
		
		assertEquals("java.util.List", config.getImportName()); //$NON-NLS-1$
	}

	@Test
	void testBuilderWithOperator() {
		VisitorConfigData config = VisitorConfigData.builder()
				.operator("==") //$NON-NLS-1$
				.build();
		
		assertEquals("==", config.getOperator()); //$NON-NLS-1$
	}

	@Test
	void testBuilderWithTypeName() {
		VisitorConfigData config = VisitorConfigData.builder()
				.typeName("MyClass") //$NON-NLS-1$
				.build();
		
		assertEquals("MyClass", config.getTypeName()); //$NON-NLS-1$
	}

	@Test
	void testBuilderWithExceptionType() {
		VisitorConfigData config = VisitorConfigData.builder()
				.exceptionType(RuntimeException.class)
				.build();
		
		assertEquals(RuntimeException.class, config.getExceptionType());
	}

	@Test
	void testBuilderDefaultsToNull() {
		VisitorConfigData config = VisitorConfigData.builder().build();
		
		assertNull(config.getTypeof());
		assertNull(config.getTypeofByName());
		assertNull(config.getMethodName());
		assertNull(config.getAnnotationName());
		assertNull(config.getImportName());
		assertNull(config.getSuperClassName());
		assertNull(config.getParamTypeNames());
		assertNull(config.getOperator());
		assertNull(config.getTypeName());
		assertNull(config.getExceptionType());
	}

	@Test
	void testBuilderIsImmutable() {
		VisitorConfigData.Builder builder = VisitorConfigData.builder()
				.methodName("test"); //$NON-NLS-1$
		
		VisitorConfigData config1 = builder.build();
		VisitorConfigData config2 = builder.methodName("test2").build(); //$NON-NLS-1$
		
		// First config should be unchanged
		assertEquals("test", config1.getMethodName()); //$NON-NLS-1$
		// Second config should have the new value
		assertEquals("test2", config2.getMethodName()); //$NON-NLS-1$
	}

	@Test
	void testArrayDefensiveCopy() {
		String[] original = {"Type1", "Type2"}; //$NON-NLS-1$ //$NON-NLS-2$
		VisitorConfigData config = VisitorConfigData.builder()
				.paramTypeNames(original)
				.build();
		
		// Modify original - should not affect config
		original[0] = "Modified"; //$NON-NLS-1$
		assertArrayEquals(new String[] {"Type1", "Type2"}, config.getParamTypeNames()); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Modify returned array - should not affect config
		String[] returned = config.getParamTypeNames();
		returned[0] = "Modified2"; //$NON-NLS-1$
		assertArrayEquals(new String[] {"Type1", "Type2"}, config.getParamTypeNames()); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
